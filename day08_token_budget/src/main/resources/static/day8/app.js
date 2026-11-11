const querySession = new URLSearchParams(location.search).get("session");
const state = { sessionId: querySession || localStorage.getItem("day8-session") || uuid(), busy: false };
localStorage.setItem("day8-session", state.sessionId);
const UI = { messages: document.getElementById("messages"), form: document.getElementById("chatForm"), input: document.getElementById("messageInput"), send: document.getElementById("sendButton"), reset: document.getElementById("resetButton"), status: document.getElementById("chatStatus"), error: document.getElementById("chatError"), tools: document.getElementById("toolList"), gateToggle: document.getElementById("gateToggle"), gateHint: document.getElementById("gateHint"), stressButton: document.getElementById("stressButton"), currentTokens: document.getElementById("currentTokens"), historyTokens: document.getElementById("historyTokens"), responseTokens: document.getElementById("responseTokens"), totalTokens: document.getElementById("totalTokens"), callCost: document.getElementById("callCost"), sessionCost: document.getElementById("sessionCost"), tokenLimit: document.getElementById("tokenLimit"), contextFill: document.getElementById("contextFill"), tokenFoot: document.getElementById("tokenFoot"), failedCount: document.getElementById("failedCount"), tokenTimeline: document.getElementById("tokenTimeline") };
state.tokenPoints = [];

document.addEventListener("DOMContentLoaded", async () => {
    UI.form.addEventListener("submit", event => { event.preventDefault(); send(UI.input.value); });
    UI.reset.addEventListener("click", resetChat);
    UI.gateToggle.addEventListener("change", toggleGate);
    UI.stressButton.addEventListener("click", sendStress);
    const restartButton = document.getElementById("restartButton");
    if (restartButton) restartButton.addEventListener("click", restartService);
    document.querySelectorAll(".example-button[data-prompt]").forEach(button => button.addEventListener("click", () => send(button.dataset.prompt)));
    try {
        const meta = await fetchJson("/api/agent/meta");
        UI.tools.innerHTML = meta.tools.map(tool => `<span class="tool-chip">${escapeHtml(tool.name)}</span>`).join("");
        applyGate(meta.gateEnabled, meta.contextLimit);
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

async function send(text, options = {}) {
    const message = text == null ? "" : text.trim();
    if ((!message && !options.endpoint) || state.busy) return;
    state.busy = true; setBusy(true); UI.input.value = ""; clearError();
    removeEmpty();
    appendMessage("user", options.display || message);
    const toolCards = new Map();
    const apiCards = new Map();
    try {
        const response = await fetch(options.endpoint || "/api/agent/chat", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(options.payload || {sessionId: state.sessionId, message})
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

async function sendStress() {
    if (state.gateEnabled || state.busy) return;
    await send("", {
        endpoint: "/api/agent/stress",
        payload: {sessionId: state.sessionId},
        display: "Сказка про колобка ≈ 1 000 000 токенов"
    });
}

async function toggleGate() {
    if (state.busy) {
        UI.gateToggle.checked = !UI.gateToggle.checked;
        return;
    }
    const enabled = !UI.gateToggle.checked;
    try {
        const result = await fetchJson("/api/agent/gate", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({enabled})
        });
        applyGate(result.gateEnabled, result.contextLimit);
    } catch (error) {
        UI.gateToggle.checked = !UI.gateToggle.checked;
        showError("Не удалось переключить гейт: " + error.message);
    }
}

function applyGate(enabled, limit) {
    const effectiveLimit = Number(limit);
    state.gateEnabled = enabled;
    UI.gateToggle.checked = !enabled;
    UI.stressButton.disabled = enabled;
    UI.tokenLimit.textContent = `лимит ${formatNumber(effectiveLimit)}`;
    UI.gateHint.textContent = enabled ? `гейт активен · ${formatNumber(effectiveLimit)}` : `гейт снят · ${formatNumber(effectiveLimit)}`;
    state.tokenPoints.forEach(point => { point.limit = effectiveLimit; });
    renderTimeline();
    const last = state.tokenPoints[state.tokenPoints.length - 1];
    if (last) {
        const context = last.history || 0;
        const percent = scalePercent(context, effectiveLimit);
        UI.contextFill.style.width = percent + "%";
        UI.contextFill.classList.toggle("danger", context >= effectiveLimit);
        UI.tokenFoot.textContent = `контекст ${formatNumber(context)} / ${formatNumber(effectiveLimit)} · вызов ${last.calls} · «За сессию» — накопительно`;
        UI.tokenFoot.classList.toggle("danger", context >= effectiveLimit);
    } else {
        UI.contextFill.style.width = "0%";
        UI.contextFill.classList.remove("danger");
        UI.tokenFoot.textContent = "Лимит относится к контексту, «За сессию» — накопительно.";
        UI.tokenFoot.classList.remove("danger");
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
    else if (event === "token_usage") {
        UI.currentTokens.textContent = formatNumber(payload.currentRequest);
        UI.historyTokens.textContent = formatNumber(payload.history);
        UI.responseTokens.textContent = formatNumber(payload.response);
        UI.totalTokens.textContent = formatNumber(payload.total);
        UI.callCost.textContent = formatCost(payload.callCostUsd);
        UI.sessionCost.textContent = formatCost(payload.totalCostUsd);
        UI.failedCount.textContent = formatNumber(payload.failedRequests);
        state.tokenPoints.push(payload);
        renderTimeline();
        const context = payload.history || payload.estimatedContext;
        const percent = scalePercent(context, payload.limit);
        UI.contextFill.style.width = percent + "%";
        UI.contextFill.classList.toggle("danger", context >= payload.limit);
        UI.tokenFoot.textContent = `контекст ${formatNumber(context)} / ${formatNumber(payload.limit)} · вызов ${payload.calls} · «За сессию» — накопительно`;
        UI.tokenFoot.classList.toggle("danger", context >= payload.limit);
    } else if (event === "request_failed") {
        UI.failedCount.textContent = formatNumber(payload.failedRequests);
        const card = apiCards.get(String(payload.round));
        if (card && !card.querySelector(".api-charge")) {
            const note = document.createElement("div");
            note.className = "api-charge";
            note.textContent = "Стоимость не начислена · API не вернул usage";
            card.querySelector(".api-payloads").appendChild(note);
            scrollBottom();
        }
    } else if (event === "token_limit") {
        UI.contextFill.style.width = "100%";
        UI.contextFill.classList.add("danger");
        UI.tokenFoot.textContent = `лимит превышен: ≈ ${formatNumber(payload.estimated)} / ${formatNumber(payload.limit)}`;
        UI.tokenFoot.classList.add("danger");
        const previous = state.tokenPoints[state.tokenPoints.length - 1] || {total: 0, totalCostUsd: 0, calls: 0};
        state.tokenPoints.push({
            history: payload.estimated,
            response: 0,
            total: previous.total,
            totalCostUsd: previous.totalCostUsd,
            calls: previous.calls + 1,
            limit: payload.limit,
            skipped: true
        });
        renderTimeline();
    }
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

function scalePercent(value, limit) {
    if (!limit || value <= 0) return 0;
    return Math.min(100, Math.round(value / limit * 100));
}

function formatNumber(value) { return new Intl.NumberFormat("ru-RU").format(value || 0); }
function formatCost(value) { return value == null ? "—" : `$${Number(value).toFixed(4)}`; }

function renderTimeline() {
    if (state.tokenPoints.length === 0) {
        UI.tokenTimeline.innerHTML = '<p class="token-empty">График появится после первого вызова.</p>';
        return;
    }
    UI.tokenTimeline.innerHTML = state.tokenPoints.map((point, index) => {
        const skipped = point.skipped === true;
        const percent = skipped ? 100 : scalePercent(point.history || 0, point.limit);
        return `<div class="token-point${skipped ? " skipped" : ""}">
            <div class="token-point-head"><b>#${index + 1}</b>${skipped ? '<span class="skipped-label">не отправлен</span>' : ""}<span>ctx ${skipped ? "≈ " : ""}<b>${formatNumber(point.history)}</b></span><span>resp <b>${formatNumber(point.response)}</b></span><span>Σ <b>${formatNumber(point.total)}</b></span><span><b>${formatCost(point.totalCostUsd)}</b></span></div>
            <div class="token-point-bar"><span class="${percent >= 100 || skipped ? "danger" : ""}" style="width:${percent}%"></span></div>
        </div>`;
    }).join("");
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
    state.tokenPoints = [];
    UI.tokenTimeline.innerHTML = '<p class="token-empty">График появится после первого вызова.</p>';
    UI.failedCount.textContent = "0";
    try { await fetch("/api/agent/reset", {method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify({sessionId:state.sessionId})}); } catch (ignored) {}
    state.sessionId = uuid(); localStorage.setItem("day8-session", state.sessionId);
    UI.messages.innerHTML = '<div class="chat-empty"><p>Напиши сообщение или выбери пример слева.</p></div>';
    setStatus("готов", false); clearError();
}
async function fetchJson(url, options) { const r = await fetch(url, options); if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json(); }
function uuid() { return crypto.randomUUID ? crypto.randomUUID() : "s-" + Date.now() + "-" + Math.random().toString(16).slice(2); }
function sleep(ms) { return new Promise(resolve => setTimeout(resolve, ms)); }

function escapeHtml(value) { return String(value ?? "").replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;").replaceAll("'","&#039;"); }
