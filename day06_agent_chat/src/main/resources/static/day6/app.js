const state = { sessionId: localStorage.getItem("day6-session") || uuid(), busy: false };
if (!localStorage.getItem("day6-session")) localStorage.setItem("day6-session", state.sessionId);
const UI = { messages: document.getElementById("messages"), form: document.getElementById("chatForm"), input: document.getElementById("messageInput"), send: document.getElementById("sendButton"), reset: document.getElementById("resetButton"), status: document.getElementById("chatStatus"), error: document.getElementById("chatError"), tools: document.getElementById("toolList") };

document.addEventListener("DOMContentLoaded", async () => {
    UI.form.addEventListener("submit", event => { event.preventDefault(); send(UI.input.value); });
    UI.reset.addEventListener("click", resetChat);
    document.querySelectorAll(".example-button").forEach(button => button.addEventListener("click", () => send(button.dataset.prompt)));
    try {
        const meta = await fetchJson("/api/agent/meta");
        UI.tools.innerHTML = meta.tools.map(tool => `<span class="tool-chip">${escapeHtml(tool.name)}</span>`).join("");
    } catch (error) { showError("Не удалось загрузить инструменты: " + error.message); }
});

async function send(text) {
    const message = text == null ? "" : text.trim();
    if (!message || state.busy) return;
    state.busy = true; setBusy(true); UI.input.value = ""; clearError();
    removeEmpty();
    appendMessage("user", message);
    const toolCards = new Map();
    const apiCards = new Map();
    try {
        const response = await fetch("/api/agent/chat", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({sessionId: state.sessionId, message})
        });
        if (!response.ok || !response.body) {
            let detail = `HTTP ${response.status}`;
            try { const body = await response.json(); detail = body.message || body.detail || detail; } catch (ignored) {}
            throw new Error(detail);
        }
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = "";
        while (true) {
            const {value, done} = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, {stream: true});
            let boundary;
            while ((boundary = buffer.indexOf("\n\n")) >= 0) {
                const block = buffer.slice(0, boundary);
                buffer = buffer.slice(boundary + 2);
                handleEvent(parseEvent(block), toolCards, apiCards);
            }
        }
    } catch (error) {
        appendMessage("agent", error.message, true);
    } finally {
        state.busy = false; setBusy(false);
    }
}

function parseEvent(block) {
    let event = "message";
    const data = [];
    block.split("\n").forEach(line => {
        if (line.startsWith("event:")) event = line.slice(6).trim();
        else if (line.startsWith("data:")) data.push(line.slice(5).trim());
    });
    return {event, data: data.join("\n")};
}

function handleEvent({event, data}, toolCards, apiCards) {
    let payload = {};
    try { payload = data ? JSON.parse(data) : {}; } catch (ignored) {}
    if (event === "status") setStatus(payload.message || "Агент работает", true);
    else if (event === "llm_request") {
        const card = document.createElement("details");
        card.className = "api-card";
        const messageCount = Array.isArray(payload.request?.messages) ? payload.request.messages.length : 0;
        card.innerHTML = `<summary><span class="api-label">API · шаг ${payload.round}</span><span class="api-state">system + ${messageCount} сообщений + tools · запрос отправлен</span></summary>
            <div class="api-payloads"><div><b>Что ушло в API</b><pre class="api-request">${escapeHtml(JSON.stringify(payload.request, null, 2))}</pre></div>
            <div><b>Что вернулось из API</b><pre class="api-response">—</pre></div></div>`;
        UI.messages.appendChild(card);
        apiCards.set(String(payload.round), card);
        scrollBottom();
    } else if (event === "llm_response") {
        const card = apiCards.get(String(payload.round));
        if (card) {
            const stateEl = card.querySelector(".api-state");
            stateEl.textContent = "ответ получен";
            stateEl.classList.add("done");
            card.querySelector(".api-response").textContent = JSON.stringify(payload.response, null, 2);
            scrollBottom();
        }
    } else if (event === "tool_call") {
        const card = document.createElement("div");
        card.className = "tool-card";
        card.innerHTML = `<div class="tool-card-head"><strong>${escapeHtml(payload.name)}</strong><span class="tool-state">вызов…</span></div><div class="tool-args">${escapeHtml(JSON.stringify(payload.arguments ?? {}, null, 2))}</div><div class="tool-result"></div>`;
        UI.messages.appendChild(card); toolCards.set(payload.id, card); scrollBottom();
    } else if (event === "tool_result") {
        const card = toolCards.get(payload.id);
        if (card) {
            const stateEl = card.querySelector(".tool-state");
            stateEl.textContent = payload.ok ? "готово" : "ошибка";
            stateEl.classList.add(payload.ok ? "done" : "fail");
            card.querySelector(".tool-result").textContent = JSON.stringify(payload.ok ? payload.result : {error: payload.error}, null, 2);
            scrollBottom();
        }
    } else if (event === "assistant") {
        appendMessage("agent", payload.content || "—");
    } else if (event === "error") {
        appendMessage("agent", payload.message || "Ошибка агента", true);
    } else if (event === "done") {
        setStatus("готов", false);
    }
}

function appendMessage(role, text, error = false) {
    const node = document.createElement("div");
    node.className = `message ${role}${error ? " error" : ""}`;
    node.innerHTML = `<span class="message-label">${role === "user" ? "вы" : "агент"}</span>${escapeHtml(text)}`;
    UI.messages.appendChild(node); scrollBottom();
}
function removeEmpty() { const empty = UI.messages.querySelector(".chat-empty"); if (empty) empty.remove(); }
function scrollBottom() { UI.messages.scrollTop = UI.messages.scrollHeight; }
function setBusy(busy) { UI.send.disabled = busy; UI.input.disabled = busy; if (busy) setStatus("работает", true); }
function setStatus(text, busy) { UI.status.textContent = text; UI.status.classList.toggle("busy", busy); }
function showError(text) { UI.error.textContent = text; }
function clearError() { UI.error.textContent = ""; }
async function resetChat() {
    if (state.busy) return;
    try { await fetch("/api/agent/reset", {method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify({sessionId:state.sessionId})}); } catch (ignored) {}
    state.sessionId = uuid(); localStorage.setItem("day6-session", state.sessionId);
    UI.messages.innerHTML = '<div class="chat-empty"><p>Напиши сообщение или выбери пример слева.</p></div>';
    setStatus("готов", false); clearError();
}
async function fetchJson(url, options) { const r = await fetch(url, options); if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json(); }
function uuid() { return crypto.randomUUID ? crypto.randomUUID() : "s-" + Date.now() + "-" + Math.random().toString(16).slice(2); }
function escapeHtml(value) { return String(value ?? "").replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;").replaceAll("'","&#039;"); }
