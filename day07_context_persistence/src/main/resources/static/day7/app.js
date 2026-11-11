const querySession = new URLSearchParams(location.search).get("session");
const state = { sessionId: querySession || localStorage.getItem("day7-session") || uuid(), busy: false };
localStorage.setItem("day7-session", state.sessionId);
const UI = { messages: document.getElementById("messages"), form: document.getElementById("chatForm"), input: document.getElementById("messageInput"), send: document.getElementById("sendButton"), reset: document.getElementById("resetButton"), status: document.getElementById("chatStatus"), error: document.getElementById("chatError"), tools: document.getElementById("toolList") };

document.addEventListener("DOMContentLoaded", async () => {
    UI.form.addEventListener("submit", event => { event.preventDefault(); send(UI.input.value); });
    UI.reset.addEventListener("click", resetChat);
    const restartButton = document.getElementById("restartButton");
    if (restartButton) restartButton.addEventListener("click", restartService);
    document.querySelectorAll(".example-button").forEach(button => button.addEventListener("click", () => send(button.dataset.prompt)));
    try {
        const meta = await fetchJson("/api/agent/meta");
        UI.tools.innerHTML = meta.tools.map(tool => `<span class="tool-chip">${escapeHtml(tool.name)}</span>`).join("");
        await loadHistory();
    } catch (error) { showError("Не удалось загрузить инструменты: " + error.message); }
});

async function loadHistory() {
    try {
        const history = await fetchJson(`/api/agent/history?sessionId=${encodeURIComponent(state.sessionId)}`);
        const messages = history.messages || [];
        if (messages.length === 0) return;
        removeEmpty();
        const cards = new Map();
        const note = document.createElement("div");
        note.className = "history-note";
        note.textContent = "История восстановлена из JSON · API-трассы не сохраняются";
        UI.messages.appendChild(note);
        messages.forEach(message => {
            const role = message.role;
            if (role === "system") return;
            if (role === "user") {
                appendMessage("user", String(message.content || ""));
            } else if (role === "assistant") {
                if (Array.isArray(message.tool_calls)) {
                    message.tool_calls.forEach(call => {
                        const card = createToolCard(call.id, call.function?.name || "tool", call.function?.arguments || "{}");
                        cards.set(call.id, card);
                    });
                }
                if (message.content && String(message.content).trim()) {
                    appendMessage("agent", String(message.content));
                }
            } else if (role === "tool") {
                const card = cards.get(message.tool_call_id);
                if (card) updateToolCard(card, message.content, true, true);
            }
        });
        scrollBottom();
    } catch (ignored) {
        // A missing history must not block a new conversation.
    }
}

function createToolCard(id, name, args) {
    const card = document.createElement("div");
    card.className = "tool-card";
    card.innerHTML = `<div class="tool-card-head"><strong>${escapeHtml(name)}</strong><span class="tool-state">вызов…</span></div><div class="tool-args">${escapeHtml(formatArgs(args))}</div><div class="tool-result"></div>`;
    UI.messages.appendChild(card);
    scrollBottom();
    return card;
}

function updateToolCard(card, rawResult, ok, restored = false) {
    const stateEl = card.querySelector(".tool-state");
    stateEl.textContent = restored ? "восстановлено" : (ok ? "готово" : "ошибка");
    stateEl.classList.add(ok ? "done" : "fail");
    card.querySelector(".tool-result").textContent = formatArgs(rawResult);
    scrollBottom();
}

function formatArgs(value) {
    if (typeof value !== "string") return JSON.stringify(value, null, 2);
    try { return JSON.stringify(JSON.parse(value), null, 2); } catch (ignored) { return value; }
}

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
        card.innerHTML = `<summary><span class="api-label">API · шаг ${payload.round}</span><span class="api-state">system + ${messageCount} сообщений + tools · запрос отправлен · live</span></summary>
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
        const card = createToolCard(payload.id, payload.name, payload.arguments ?? {});
        toolCards.set(payload.id, card);
    } else if (event === "tool_result") {
        const card = toolCards.get(payload.id);
        if (card) updateToolCard(card, payload.ok ? payload.result : {error: payload.error}, payload.ok);
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
async function restartService() {
    if (state.busy) return;
    if (!window.confirm("Перезапустить сервис? Текущая история сохранится в JSON.")) return;
    state.busy = true;
    setBusy(true);
    setStatus("перезапуск…", true);
    clearError();
    try {
        const response = await fetch("/api/admin/restart", {method: "POST"});
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
    } catch (error) {
        state.busy = false;
        setBusy(false);
        showError("Не удалось запустить перезапуск: " + error.message);
        return;
    }
    for (let attempt = 0; attempt < 60; attempt++) {
        await sleep(500);
        try {
            const health = await fetch("/api/health", {cache: "no-store"});
            if (health.ok) {
                window.location.reload();
                return;
            }
        } catch (ignored) {
            // Сервис ещё не поднялся.
        }
    }
    state.busy = false;
    setBusy(false);
    setStatus("ошибка", false);
    showError("Сервис не поднялся за 30 секунд. Проверь restart.log.");
}

async function resetChat() {
    if (state.busy) return;
    try { await fetch("/api/agent/reset", {method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify({sessionId:state.sessionId})}); } catch (ignored) {}
    state.sessionId = uuid(); localStorage.setItem("day7-session", state.sessionId);
    UI.messages.innerHTML = '<div class="chat-empty"><p>Напиши сообщение или выбери пример слева.</p></div>';
    setStatus("готов", false); clearError();
}
async function fetchJson(url, options) { const r = await fetch(url, options); if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json(); }
function uuid() { return crypto.randomUUID ? crypto.randomUUID() : "s-" + Date.now() + "-" + Math.random().toString(16).slice(2); }
function sleep(ms) { return new Promise(resolve => setTimeout(resolve, ms)); }

function escapeHtml(value) { return String(value ?? "").replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;").replaceAll("'","&#039;"); }
