const state = { sessionId: null, sessions: [], meta: null, busy: false, compression: true, lastAssistant: "", tokenPoints: [], experimentResults: new Map(), assistantNode: null, assistantRound: 0, assistantBuffer: "", assistantFlushPending: false, reasoningNode: null, reasoningRound: 0 };
const $ = id => document.getElementById(id);
const UI = { sessions: $("sessionsPanel"), experiments: $("experimentPanel"), list: $("sessionList"), meta: $("sessionMeta"), messages: $("messages"), input: $("messageInput"), form: $("chatForm"), send: $("sendButton"), status: $("chatStatus"), error: $("chatError"), compression: $("compressionToggle"), compressionBadge: $("compressionBadge"), compressionHint: $("compressionHint"), summary: $("summaryText"), scenarioResult: $("scenarioResult"), scenarioStatus: $("scenarioStatus"), scenarioButton: $("scenarioButton"), tokenLimit: $("tokenLimit"), historyTokens: $("historyTokens"), responseTokens: $("responseTokens"), totalTokens: $("totalTokens"), sessionCost: $("sessionCost"), contextFill: $("contextFill"), tokenFoot: $("tokenFoot") };

document.addEventListener("DOMContentLoaded", init);
async function init() {
    bindEvents();
    try {
        state.meta = await api("/api/agent/meta");
        UI.tokenLimit.textContent = `лимит ${fmt(state.meta.contextLimit)}`;
        await refreshSessions(true);
    } catch (error) { showError("Не удалось загрузить: " + error.message); }
}

function bindEvents() {
    UI.form.addEventListener("submit", e => { e.preventDefault(); send(UI.input.value); });
    $("newSessionButton").addEventListener("click", createSession);
    $("toggleSessions").addEventListener("click", () => UI.sessions.classList.toggle("collapsed"));
    $("toggleExperiment").addEventListener("click", () => UI.experiments.classList.toggle("collapsed"));
    $("closeExperiment").addEventListener("click", () => UI.experiments.classList.add("collapsed"));
    UI.compression.addEventListener("change", toggleCompression);
    UI.scenarioButton.addEventListener("click", runScenario);
    $("restartButton").addEventListener("click", restartService);
}

async function refreshSessions(createIfEmpty = false) {
    const sessions = await api("/api/agent/sessions");
    state.sessions = sessions;
    renderSessions();
    const preferred = localStorage.getItem("day9-session");
    if (!state.sessionId) {
        const found = sessions.find(s => s.id === preferred) || sessions[0];
        if (found) await selectSession(found.id);
        else if (createIfEmpty) await createSession();
        else showNoSession();
    } else if (!sessions.some(s => s.id === state.sessionId)) {
        state.sessionId = null;
        if (createIfEmpty) await createSession();
        else showNoSession();
    }
}
function showNoSession() {
    UI.messages.innerHTML = ""; showEmpty();
    UI.summary.textContent = "Пока пусто";
    UI.scenarioStatus.textContent = "Сессия не выбрана.";
    UI.scenarioResult.innerHTML = '<span class="muted-small">Создай или выбери сессию.</span>';
    resetTokenPanel();
}
function renderSessions() {
    UI.list.innerHTML = state.sessions.map(s => `
        <button class="session-item ${s.id === state.sessionId ? "active" : ""}" data-session="${escape(s.id)}">
            <strong>${escape(s.id.slice(0, 18))}</strong>
            <small>${s.messageCount} сообщений · ${s.compressionEnabled ? "сжатие" : "полная"}</small>
            <span class="session-delete" data-delete="${escape(s.id)}" title="Удалить">×</span>
        </button>`).join("");
    UI.list.querySelectorAll(".session-item").forEach(button => {
        button.addEventListener("click", e => {
            if (e.target.dataset.delete) return;
            selectSession(button.dataset.session);
        });
    });
    UI.list.querySelectorAll("[data-delete]").forEach(button => {
        button.addEventListener("click", async e => {
            e.stopPropagation();
            await deleteSession(button.dataset.delete);
        });
    });
    UI.meta.textContent = `сессий: ${state.sessions.length}`;
}
async function createSession() {
    const result = await api("/api/agent/sessions", {method: "POST"});
    await refreshSessionsSilent();
    await selectSession(result.sessionId);
}
async function refreshSessionsSilent() {
    state.sessions = await api("/api/agent/sessions");
    renderSessions();
}
async function deleteSession(id) {
    if (!window.confirm("Удалить сессию?")) return;
    await api(`/api/agent/sessions/${encodeURIComponent(id)}`, {method: "DELETE"});
    if (state.sessionId === id) state.sessionId = null;
    state.experimentResults.delete(id);
    await refreshSessions(false);
}
async function selectSession(id) {
    state.sessionId = id;
    localStorage.setItem("day9-session", id);
    renderSessions();
    UI.messages.innerHTML = "";
    showEmpty();
    state.lastAssistant = "";
    state.tokenPoints = [];
    resetTokenPanel();
    restoreExperiment();
    try {
        const history = await api(`/api/agent/history?sessionId=${encodeURIComponent(id)}`);
        applyCompression(history.compressionEnabled !== false);
        updateSummary(history.summary || "");
        applyTokenStats(history.tokenStats);
        if (history.messages && history.messages.length) {
            removeEmpty();
            renderHistory(history.messages);
        }
    } catch (ignored) { /* empty session */ }
}
function renderHistory(messages) {
    const toolCards = new Map();
    messages.forEach(message => {
        const role = message.role;
        if (role === "system") return;
        if (role === "user" && message.content) appendMessage("user", message.content);
        else if (role === "assistant") {
            if (Array.isArray(message.tool_calls)) {
                message.tool_calls.forEach(call => toolCards.set(call.id, makeTool(call.id, call.function?.name || "tool", call.function?.arguments || "{}")));
            }
            if (message.content) appendMessage("agent", message.content);
        } else if (role === "tool") {
            const card = toolCards.get(message.tool_call_id);
            if (card) setToolResult(card, message.content, true);
        }
    });
}

function applyTokenStats(stats) {
    if (!stats) return;
    UI.historyTokens.textContent = fmt(stats.history);
    UI.responseTokens.textContent = fmt(stats.response);
    UI.totalTokens.textContent = fmt(stats.total);
    UI.sessionCost.textContent = "$" + Number(stats.totalCost || 0).toFixed(4);
    const limit = stats.contextLimit || 0;
    UI.contextFill.style.width = limit > 0 ? Math.min(100, Math.round((stats.history || 0) / limit * 100)) + "%" : "0%";
    UI.tokenFoot.textContent = `контекст ${fmt(stats.history)} / ${fmt(limit)} · вызовов ${stats.calls || 0}`;
}
function resetTokenPanel() {
    UI.historyTokens.textContent = "—";
    UI.responseTokens.textContent = "—";
    UI.totalTokens.textContent = "—";
    UI.sessionCost.textContent = "—";
    UI.contextFill.style.width = "0%";
    UI.tokenFoot.textContent = "—";
}
function restoreExperiment() {
    const saved = state.experimentResults.get(state.sessionId);
    if (saved) renderResults(saved.compressed, saved.answers, false);
    else {
        UI.scenarioStatus.textContent = "Готов к запуску.";
        UI.scenarioResult.innerHTML = '<span class="muted-small">Запусти сценарий, чтобы проверить ключевые детали.</span>';
    }
}

async function send(text) {
    const message = (text || "").trim();
    if (!message || state.busy || !state.sessionId) return;
    state.busy = true; setBusy(true); clearError(); removeEmpty(); appendMessage("user", message); UI.input.value = "";
    state.assistantNode = null; state.reasoningNode = null; state.assistantBuffer = ""; state.assistantFlushPending = false; state.lastAssistant = "";
    const tools = new Map(); const apiSteps = new Map();
    try {
        const response = await fetch("/api/agent/chat", {method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify({sessionId: state.sessionId, message})});
        if (!response.ok || !response.body) throw new Error(`HTTP ${response.status}`);
        await consumeSse(response, tools, apiSteps);
    } catch (error) { appendMessage("system-error", error.message); }
    finally { state.busy = false; setBusy(false); refreshSessionsSilent().catch(() => {}); }
}
async function consumeSse(response, tools, apiSteps) {
    const reader = response.body.getReader(); const decoder = new TextDecoder(); let buffer = "";
    while (true) {
        const {value, done} = await reader.read(); if (done) break;
        buffer += decoder.decode(value, {stream: true});
        let cut;
        while ((cut = buffer.indexOf("\n\n")) >= 0) {
            const block = buffer.slice(0, cut); buffer = buffer.slice(cut + 2);
            handleEvent(parseSse(block), tools, apiSteps);
        }
    }
}
function parseSse(block) {
    let event = "message"; const data = [];
    block.split("\n").forEach(line => {
        if (line.startsWith("event:")) event = line.slice(6).trim();
        else if (line.startsWith("data:")) data.push(line.slice(5).trim());
    });
    return {event, data: data.join("\n")};
}
function handleEvent({event, data}, tools, apiSteps) {
    let p = {}; try { p = data ? JSON.parse(data) : {}; } catch (ignored) {}
    if (event === "status") setStatus(p.message || "работает", true);
    else if (event === "reasoning_delta") {
        appendReasoningDelta(p.content || "", p.round || 1);
    } else if (event === "reasoning") {
        appendReasoningDelta(p.content || "", p.round || 1);
    } else if (event === "llm_request") {
        const details = document.createElement("details"); details.className = "api-step";
        const count = Array.isArray(p.request?.messages) ? p.request.messages.length : 0;
        details.innerHTML = `<summary>API #${p.round} ↑ запрос · ${p.summarized ? "сводка" : "полный"} · ${count} сообщений</summary><pre>${escape(pretty(p.request))}</pre>`;
        UI.messages.appendChild(details); apiSteps.set(String(p.round), details); scroll();
    } else if (event === "llm_response") {
        const details = apiSteps.get(String(p.round));
        if (details) details.insertAdjacentHTML("beforeend", `<pre>↓ ${escape(pretty(p.response))}</pre>`);
    } else if (event === "tool_call") {
        tools.set(p.id, makeTool(p.id, p.name, p.arguments || {}));
    } else if (event === "tool_result") {
        const card = tools.get(p.id); if (card) setToolResult(card, p.ok ? p.result : {error: p.error}, p.ok);
    } else if (event === "assistant_delta") {
        appendAssistantDelta(p.content || "", p.round || 1);
    } else if (event === "assistant_done" || event === "assistant") {
        finishAssistant(p.content || "");
    } else if (event === "compression_update") {
        updateSummary(p.summary || "");
        UI.compressionHint.textContent = `сжато: ${p.summarizedCount ?? "—"} · полных ${p.fullMessages ?? "—"} → ${p.contextMessages ?? "—"}`;
        appendSystemNotice(`Сработала суммаризация: старые сообщения свёрнуты в summary. В контекст уходят summary + последние ${p.keepLast ?? 8} сообщений.`);
    } else if (event === "token_usage") {
        UI.historyTokens.textContent = fmt(p.history);
        UI.responseTokens.textContent = fmt(p.response);
        UI.totalTokens.textContent = fmt(p.total);
        UI.sessionCost.textContent = "$" + Number(p.totalCostUsd || 0).toFixed(4);
        const percent = Math.min(100, Math.round((p.history || 0) / p.limit * 100));
        UI.contextFill.style.width = percent + "%";
        UI.contextFill.classList.toggle("danger", percent >= 100);
        UI.tokenFoot.textContent = `контекст ${fmt(p.history)} / ${fmt(p.limit)} · вызов ${p.calls}`;
        state.tokenPoints.push(p); renderTimelineIfAny();
    } else if (event === "request_failed" || event === "error") {
        if (event === "error") appendMessage("system-error", p.message || "Ошибка");
    } else if (event === "done") setStatus("готов", false);
}
function makeTool(id, name, args) {
    const card = document.createElement("details"); card.className = "tool-card";
    card.innerHTML = `<summary class="tool-head"><span class="ok">●</span><b>${escape(name)}</b><span>вызов…</span></summary><div class="tool-body"><pre class="tool-args">${escape(format(args))}</pre><pre class="tool-result"></pre></div>`;
    UI.messages.appendChild(card); scroll(); return card;
}
function setToolResult(card, result, ok) {
    const stateEl = card.querySelector(".tool-head span:last-child");
    stateEl.textContent = ok ? "готово" : "ошибка";
    stateEl.className = ok ? "ok" : "danger";
    card.querySelector(".tool-result").textContent = format(result);
    scroll();
}
function appendSystemNotice(text) {
    removeEmpty();
    const node = document.createElement("div"); node.className = "system-note";
    node.textContent = "⚙ " + text;
    UI.messages.appendChild(node); scroll();
}
function appendMessage(role, text) {
    const node = document.createElement("div"); node.className = "message " + role;
    node.innerHTML = `<span class="message-label">${role === "user" ? "вы" : "агент"}</span>${escape(text)}`;
    UI.messages.appendChild(node); scroll();
}
function appendReasoningDelta(text, round) {
    if (!text) return;
    if (!state.reasoningNode || state.reasoningRound !== round) {
        const details = document.createElement("details"); details.className = "reasoning";
        details.innerHTML = `<summary>Размышления модели · шаг ${round}</summary><pre></pre>`;
        UI.messages.appendChild(details); state.reasoningNode = details; state.reasoningRound = round;
    }
    state.reasoningNode.querySelector("pre").textContent += text; scroll();
}
function appendAssistantDelta(text, round) {
    if (!text) return;
    if (!state.assistantNode || state.assistantRound !== round) {
        const node = document.createElement("div"); node.className = "message agent";
        node.innerHTML = '<span class="message-label">агент</span><span class="stream-body"></span>';
        UI.messages.appendChild(node); state.assistantNode = node; state.assistantRound = round;
        state.assistantBuffer = "";
    }
    state.assistantBuffer += text;
    if (!state.assistantFlushPending) {
        state.assistantFlushPending = true;
        requestAnimationFrame(() => {
            state.assistantFlushPending = false;
            if (!state.assistantNode || !state.assistantBuffer) return;
            state.assistantNode.querySelector(".stream-body").textContent += state.assistantBuffer;
            state.assistantBuffer = "";
            scroll();
        });
    }
}
function finishAssistant(text) {
    state.lastAssistant = text || "";
    state.assistantBuffer = ""; state.assistantFlushPending = false;
    if (text && !state.assistantNode) {
        appendMessage("agent", text);
    } else if (state.assistantNode && text) {
        state.assistantNode.querySelector(".stream-body").textContent = text;
        state.assistantNode = null;
    }
    scroll();
}
function renderTimelineIfAny() { /* kept for session compatibility */ }
function updateSummary(summary) { UI.summary.textContent = summary && summary.trim() ? summary : "Пока пусто"; }
function applyCompression(enabled) {
    state.compression = enabled; UI.compression.checked = enabled;
    UI.compressionBadge.textContent = enabled ? "сжатие вкл" : "сжатие выкл";
    UI.compressionBadge.classList.toggle("off", !enabled);
}
async function toggleCompression() {
    try {
        await api("/api/agent/compression", {method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify({sessionId: state.sessionId, enabled: UI.compression.checked})});
        applyCompression(UI.compression.checked);
    } catch (error) { UI.compression.checked = !UI.compression.checked; showError(error.message); }
}
async function restartService() {
    if (!confirm("Перезапустить сервис?")) return;
    await fetch("/api/admin/restart", {method: "POST"}).catch(() => {});
    for (let i = 0; i < 60; i++) { await sleep(500); try { if ((await fetch("/api/health")).ok) { location.reload(); return; } } catch (ignored) {} }
    showError("Сервис не поднялся");
}
function showEmpty() { UI.messages.innerHTML = '<div class="chat-empty"><strong>Начни диалог</strong><p>Сообщения, reasoning, вызовы инструментов и API-трассы появятся здесь.</p></div>'; }
function removeEmpty() { UI.messages.querySelector(".chat-empty")?.remove(); }
let scrollPending = false;
function scroll() {
    if (scrollPending) return;
    scrollPending = true;
    requestAnimationFrame(() => {
        scrollPending = false;
        UI.messages.scrollTop = UI.messages.scrollHeight;
    });
}
function pretty(value) {
    const text = JSON.stringify(value, null, 2);
    return text.length > 16000 ? text.slice(0, 16000) + "\n… (трасса обрезана)" : text;
}
function setBusy(value) { UI.send.disabled = value; UI.input.disabled = value; if (value) setStatus("работает", true); }
function setStatus(text, busy) { UI.status.textContent = text; UI.status.classList.toggle("busy", busy); }
function showError(text) { UI.error.textContent = text; }
function clearError() { UI.error.textContent = ""; }
function fmt(value) { return new Intl.NumberFormat("ru-RU").format(value || 0); }
function format(value) { if (typeof value === "string") { try { return JSON.stringify(JSON.parse(value), null, 2); } catch (ignored) { return value; } } return JSON.stringify(value ?? {}, null, 2); }
function escape(value) { return String(value ?? "").replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;").replaceAll("'","&#039;"); }
function sleep(ms) { return new Promise(resolve => setTimeout(resolve, ms)); }
async function api(url, options) { const response = await fetch(url, options); if (!response.ok) throw new Error(`HTTP ${response.status}`); return response.json(); }

/* Experiment scenario */
const SCENARIO_DETAILS = [
    "имя проекта — «Колобок»",
    "аудитория — небольшие семьи",
    "срок — 15 декабря",
    "бюджет — 300 тысяч рублей",
    "ограничение — нельзя использовать внешние API",
    "решение — хранить данные локально",
    "платформа — Android",
    "стек — Java",
    "ответственный — Иван",
    "старт — 1 ноября"
];
const KEY_DETAILS = [
    { label: "Имя проекта", question: "Как называется проект?", expected: "Колобок", match: /колобок/i },
    { label: "Срок", question: "Какой срок проекта?", expected: "15 декабря", match: /15\s*(декабря|декабр|12)/i },
    { label: "Бюджет", question: "Какой бюджет проекта?", expected: "300 тысяч рублей", match: /300\s*(тыс|000|тысяч)/i },
    { label: "Ограничение", question: "Какое ограничение по внешним API?", expected: "запрет внешних API", match: /(никак|нельз|запрет|запрещ|не\s+использ|без\s+(внешн|сторонн|api)).*(api|внешн|сторонн)|api.*(никак|нельз|запрет|запрещ|не\s+использ|без)/i },
    { label: "Стек", question: "Какой стек решили использовать?", expected: "Java", match: /java/i }
];
function renderResults(compressed, answers, persist = true) {
    if (persist) state.experimentResults.set(state.sessionId, {compressed, answers});
    const rows = KEY_DETAILS.map((detail, index) => {
        const answer = answers[index] || "";
        const ok = detail.match.test(answer);
        return `<div class="detail-result ${ok ? "" : "miss"}"><span class="detail-check">${ok ? "✓" : "✕"}</span><div class="detail-name">${escape(detail.label)}</div><div class="detail-meta">ожидается: <b>${escape(detail.expected)}</b><br>агент: ${escape(answer.slice(0, 180) || "—")}</div></div>`;
    }).join("");
    const correct = KEY_DETAILS.filter((detail, index) => detail.match.test(answers[index] || "")).length;
    UI.scenarioStatus.textContent = `${compressed ? "Со сжатием" : "Без сжатия"} · проверка завершена`;
    UI.scenarioResult.innerHTML = `<div class="result-summary"><span>Помнит деталей</span><b>${correct}/${KEY_DETAILS.length}</b></div>${rows}`;
}

async function runScenario() {
    if (state.busy) return;
    const compressed = UI.compression.checked;
    const current = state.sessions.find(s => s.id === state.sessionId);
    if (!current || current.messageCount > 0) await createSession();
    else {
        UI.scenarioStatus.textContent = "Сценарий выполняется…";
        UI.scenarioResult.innerHTML = '<span class="muted-small">Сценарий выполняется…</span>';
    }
    await api("/api/agent/compression", {method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify({sessionId:state.sessionId, enabled:compressed})});
    applyCompression(compressed);
    UI.scenarioButton.disabled = true;
    UI.scenarioResult.innerHTML = '<span>Сценарий выполняется…</span>';
    const answers = [];
    try {
        for (let i=0;i<SCENARIO_DETAILS.length;i++) {
            UI.scenarioResult.innerHTML = `<span>${compressed?"Сжатие":"Полная история"} · контекстная реплика ${i+1}/${SCENARIO_DETAILS.length}</span>`;
            const generated = await api("/api/agent/scenario/next", {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({sessionId: state.sessionId, detail: SCENARIO_DETAILS[i]})
            });
            await send(generated.message);
        }
        for (let i=0;i<KEY_DETAILS.length;i++) {
            UI.scenarioStatus.textContent = `Проверка ${i+1}/${KEY_DETAILS.length}: ${KEY_DETAILS[i].label}`;
            await send(KEY_DETAILS[i].question);
            answers.push(state.lastAssistant || "");
        }
        renderResults(compressed, answers);
    } catch (error) { UI.scenarioStatus.textContent = `Ошибка: ${error.message}`; }
    finally { UI.scenarioButton.disabled = false; }
}
