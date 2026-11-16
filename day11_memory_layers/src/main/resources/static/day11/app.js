const state = { sessionId: null, sessions: [], meta: null, busy: false, compression: true, lastAssistant: "", tokenPoints: [], lastTokenStats: null, memoryRules: {}, experimentResults: new Map(), assistantNode: null, assistantRound: 0, assistantBuffer: "", assistantFlushPending: false, reasoningNode: null, reasoningRound: 0 };
const $ = id => document.getElementById(id);
const UI = { sessions: $("sessionsPanel"), experiments: $("experimentPanel"), list: $("sessionList"), meta: $("sessionMeta"), messages: $("messages"), input: $("messageInput"), form: $("chatForm"), send: $("sendButton"), status: $("chatStatus"), error: $("chatError"), compression: null, compressionBadge: $("compressionBadge"), compressionHint: $("compressionHint"), summary: $("summaryText"), scenarioResult: $("scenarioResult"), scenarioStatus: $("scenarioStatus"), scenarioButton: $("scenarioButton"), strategySelect: $("strategySelect"), factsText: $("factsText"), branchLastButton: $("branchLastButton"), scenarioFiveButton: $("scenarioFiveButton"), reportButton: $("reportButton"), reportOverlay: $("reportOverlay"), reportBody: $("reportBody"), reportConclusion: $("reportConclusion"), reportClose: $("reportClose"), memoryLayers: $("memoryLayers"), memoryForm: $("memoryForm"), memoryLayer: $("memoryLayer"), memoryKey: $("memoryKey"), memoryValue: $("memoryValue"), tokenLimit: $("tokenLimit"), historyTokens: $("historyTokens"), responseTokens: $("responseTokens"), totalTokens: $("totalTokens"), sessionCost: $("sessionCost"), contextFill: $("contextFill"), tokenFoot: $("tokenFoot") };

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

    UI.scenarioButton.addEventListener("click", runScenario);
    $("restartButton").addEventListener("click", restartService);
    UI.memoryForm.addEventListener("submit", saveMemory);
    UI.scenarioFiveButton.addEventListener("click", runFiveScenario);
    UI.strategySelect.addEventListener("change", changeStrategy);
    UI.branchLastButton.addEventListener("click", branchLast);
    UI.reportButton.addEventListener("click", openReport);
    UI.reportClose.addEventListener("click", closeReport);
    UI.reportOverlay.addEventListener("click", event => { if (event.target === UI.reportOverlay) closeReport(); });
    document.addEventListener("keydown", event => { if (event.key === "Escape") closeReport(); });
}

async function refreshSessions(createIfEmpty = false) {
    const sessions = await api("/api/agent/sessions");
    state.sessions = sessions;
    renderSessions();
    const preferred = localStorage.getItem("day11-session");
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
async function loadMemory() {
    if (!state.sessionId) { UI.memoryLayers.innerHTML = '<span class="muted-small">Сессия не выбрана.</span>'; return; }
    const memory = await api(`/api/agent/memory?sessionId=${encodeURIComponent(state.sessionId)}`);
    state.memoryRules = memory.rules || {};
    const cards = [
        ["SHORT_TERM", "Краткосрочная", "текущий диалог · все сообщения сессии", memory.shortTerm],
        ["WORKING", "Рабочая", "данные текущей задачи · только для этой сессии", memory.working],
        ["LONG_TERM", "Долговременная", "профиль, решения, знания · общая для всех сессий", memory.longTerm]
    ];
    UI.memoryLayers.innerHTML = cards.map(([key, title, rule, data]) => {
        const entries = Array.isArray(data.entries) ? data.entries : Object.entries(data.entries || {});
        const rows = key === "SHORT_TERM"
            ? `<div class="memory-entry"><span>${entries.length} сообщений</span></div>`
            : entries.length ? entries.map(([k, v]) => `<div class="memory-entry"><span><b>${escape(k)}</b>: ${escape(v)}</span><button type="button" data-memory-delete="${escape(k)}" data-layer="${key}" title="Удалить">×</button></div>`).join("") : '<div class="memory-entry"><span>пусто</span></div>';
        const prompt = key === "SHORT_TERM"
            ? `<details class="layer-rule"><summary>Правило слоя</summary><div class="rule-readonly">В промпт попадают все сообщения текущей сессии. Отдельного извлечения нет.</div></details>`
            : `<details class="layer-rule"><summary>Правило слоя</summary><textarea data-rule-input="${key}">${escape(state.memoryRules[key] || "")}</textarea><button type="button" class="rule-save" data-rule-save="${key}">Сохранить правило</button></details>`;
        return `<div class="memory-card ${data.enabled ? "" : "disabled"}"><div class="memory-card-head"><span class="memory-card-title">${title}</span><label class="memory-toggle"><input type="checkbox" data-memory-toggle="${key}" ${data.enabled ? "checked" : ""}> вкл</label><span class="memory-card-tokens">~${fmt(data.tokens)} токенов</span><button type="button" class="memory-clear" data-memory-clear="${key}">Очистить</button></div><div class="memory-card-rule">${rule}</div>${rows}${prompt}</div>`;
    }).join("");
    UI.memoryLayers.querySelectorAll("[data-memory-delete]").forEach(button => button.addEventListener("click", async () => {
        await removeMemory(button.dataset.layer, button.dataset.memoryDelete);
    }));
    UI.memoryLayers.querySelectorAll("[data-memory-toggle]").forEach(toggle => toggle.addEventListener("change", async () => {
        await api("/api/agent/memory/toggle", {method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify({sessionId:state.sessionId, layer:toggle.dataset.memoryToggle, enabled:toggle.checked})});
        await loadMemory();
    }));
    UI.memoryLayers.querySelectorAll("[data-rule-save]").forEach(button => button.addEventListener("click", async () => {
        const layer = button.dataset.ruleSave;
        const input = UI.memoryLayers.querySelector(`[data-rule-input="${layer}"]`);
        state.memoryRules[layer] = input.value.trim();
        await api("/api/agent/memory/rules", {method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify({rules:state.memoryRules})});
        await loadMemory();
    }));
    UI.memoryLayers.querySelectorAll("[data-memory-clear]").forEach(button => button.addEventListener("click", async () => {
        if (!confirm("Очистить этот слой памяти?")) return;
        await api("/api/agent/memory/clear", {method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify({sessionId:state.sessionId, layer:button.dataset.memoryClear})});
        await loadMemory();
        if (button.dataset.memoryClear === "SHORT_TERM") await selectSession(state.sessionId);
    }));
}
async function saveMemory(event) {
    event.preventDefault();
    const key = UI.memoryKey.value.trim(); const value = UI.memoryValue.value.trim();
    if (!key) return;
    await api("/api/agent/memory/entry", {method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify({sessionId:state.sessionId, layer:UI.memoryLayer.value, key, value})});
    UI.memoryKey.value = ""; UI.memoryValue.value = "";
    await loadMemory();
}
async function removeMemory(layer, key) {
    await api(`/api/agent/memory/entry?sessionId=${encodeURIComponent(state.sessionId)}&layer=${layer}&key=${encodeURIComponent(key)}`, {method:"DELETE"});
    await loadMemory();
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
            <small>${s.messageCount} сообщений · память</small>
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
    localStorage.setItem("day11-session", id);
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
        applyStrategy(history.strategy || "SUMMARY");
        updateFacts(history.facts || {});
        updateSummary(history.summary || "");
        applyTokenStats(history.tokenStats);
        state.lastTokenStats = history.tokenStats || null;
        await loadMemory();
        if (history.messages && history.messages.length) {
            removeEmpty();
            renderHistory(history.messages);
        }
    } catch (ignored) { /* empty session */ }
}
function renderHistory(messages) {
    const toolCards = new Map();
    messages.forEach((message, index) => {
        const role = message.role;
        if (role === "system") return;
        if (role === "user" && message.content) appendMessage("user", message.content, index);
        else if (role === "assistant") {
            if (Array.isArray(message.tool_calls)) {
                message.tool_calls.forEach(call => toolCards.set(call.id, makeTool(call.id, call.function?.name || "tool", call.function?.arguments || "{}")));
            }
            if (message.content) appendMessage("agent", message.content, index);
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
    if (saved) renderResults(saved.compressed, saved.answers, false, saved.strategy);
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
    } else if (event === "user_saved") {
        const nodes = UI.messages.querySelectorAll(".message.user");
        if (nodes.length) addBranchButton(nodes[nodes.length - 1], p.messageIndex);
    } else if (event === "assistant_done" || event === "assistant") {
        finishAssistant(p.content || "", p.messageIndex);
    } else if (event === "memory_update") {
        loadMemory().catch(() => {});
    } else if (event === "facts_update") {
        updateFacts(p.facts || {});
    } else if (event === "compression_update") {
        updateSummary(p.summary || "");
        UI.compressionHint.textContent = `сжато: ${p.summarizedCount ?? "—"} · полных ${p.fullMessages ?? "—"} → ${p.contextMessages ?? "—"}`;
        appendSystemNotice(`Сработала суммаризация: старые сообщения свёрнуты в summary. В контекст уходят summary + последние ${p.keepLast ?? 8} сообщений.`);
    } else if (event === "token_usage") {
        state.lastTokenStats = p;
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
function renderMarkdown(text) {
    const lines = String(text ?? "").split("\n");
    let html = "", inCode = false, inList = false;
    const closeList = () => { if (inList) { html += "</ul>"; inList = false; } };
    const inline = value => escape(value)
        .replace(/`([^`]+)`/g, "<code>$1</code>")
        .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
        .replace(/(^|[^*])\*([^*]+)\*/g, "$1<em>$2</em>");
    const cells = line => line.replace(/^\s*\|/, "").replace(/\|\s*$/, "").split("|").map(cell => cell.trim());
    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        if (line.startsWith("```")) {
            closeList();
            if (inCode) { html += "</code></pre>"; inCode = false; }
            else { html += "<pre><code>"; inCode = true; }
            continue;
        }
        if (inCode) { html += escape(line) + "\n"; continue; }
        if (/^\s*\|/.test(line) && i + 1 < lines.length && /\|/.test(lines[i + 1]) && /-/.test(lines[i + 1])) {
            closeList();
            const header = cells(line);
            let j = i + 2;
            const rows = [];
            while (j < lines.length && /^\s*\|/.test(lines[j])) { rows.push(cells(lines[j])); j++; }
            html += "<table><thead><tr>" + header.map(cell => `<th>${inline(cell)}</th>`).join("") + "</tr></thead><tbody>";
            html += rows.map(row => "<tr>" + header.map((_, index) => `<td>${inline(row[index] || "")}</td>`).join("") + "</tr>").join("");
            html += "</tbody></table>";
            i = j - 1;
            continue;
        }
        const heading = line.match(/^(#{1,3})\s+(.*)$/);
        if (heading) { closeList(); const level = heading[1].length + 2; html += `<h${level}>${inline(heading[2])}</h${level}>`; continue; }
        const bullet = line.match(/^\s*[-*]\s+(.*)$/);
        if (bullet) { if (!inList) { html += "<ul>"; inList = true; } html += `<li>${inline(bullet[1])}</li>`; continue; }
        closeList();
        if (line.trim() === "") html += "<br>";
        else html += `<p>${inline(line)}</p>`;
    }
    closeList();
    if (inCode) html += "</code></pre>";
    return html;
}

function appendMessage(role, text, messageIndex) {
    const node = document.createElement("div"); node.className = "message " + role;
    const body = role === "agent" ? renderMarkdown(text) : escape(text);
    node.innerHTML = `<span class="message-label">${role === "user" ? "вы" : "агент"}</span><span class="message-body">${body}</span>`;
    if (Number.isInteger(messageIndex)) addBranchButton(node, messageIndex);
    UI.messages.appendChild(node); scroll();
}
function addBranchButton(node, messageIndex) {
    if (!node || !Number.isInteger(messageIndex) || node.querySelector(".branch-button")) return;
    const button = document.createElement("button"); button.type = "button";
    button.className = "branch-button"; button.textContent = "Ветка";
    button.title = "Создать новую сессию с контекстом до этого сообщения";
    button.addEventListener("click", () => branchSession(messageIndex));
    node.appendChild(button);
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
function finishAssistant(text, messageIndex) {
    state.lastAssistant = text || "";
    state.assistantBuffer = ""; state.assistantFlushPending = false;
    if (text && !state.assistantNode) {
        appendMessage("agent", text, messageIndex);
    } else if (state.assistantNode && text) {
        state.assistantNode.querySelector(".stream-body").innerHTML = renderMarkdown(text);
        if (Number.isInteger(messageIndex)) addBranchButton(state.assistantNode, messageIndex);
        state.assistantNode = null;
    }
    scroll();
}
function renderTimelineIfAny() { /* kept for session compatibility */ }
function updateSummary(summary) { UI.summary.textContent = summary && summary.trim() ? summary : "Пока пусто"; }
async function changeStrategy() {
    const strategy = UI.strategySelect.value;
    try {
        await api("/api/agent/strategy", {method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify({sessionId:state.sessionId, strategy})});
        if (strategy === "SUMMARY") await api("/api/agent/compression", {method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify({sessionId:state.sessionId, enabled:true})});
        applyStrategy(strategy);
    } catch (error) { showError("Не удалось переключить стратегию: " + error.message); }
}
function applyStrategy(strategy) {
    UI.strategySelect.value = strategy;
    UI.compressionBadge.textContent = strategy === "SUMMARY" ? "Summary" : strategy === "FACTS" ? "Sticky Facts" : "Sliding Window";
    UI.compressionHint.textContent = strategy === "SUMMARY"
        ? "Summary: старые сообщения сжимаются; в запрос идут summary + последние 8."
        : strategy === "FACTS" ? "Facts + последние 10 сообщений." : "Только последние 10 сообщений.";
}
function updateFacts(facts) {
    const entries = Object.entries(facts || {});
    UI.factsText.textContent = entries.length ? entries.map(([k,v]) => `${k}: ${v}`).join("\n") : "Пока пусто";
}
async function branchSession(index) {
    if (!state.sessionId || !confirm("Создать новую сессию с контекстом до этого сообщения?")) return;
    const result = await api(`/api/agent/sessions/${encodeURIComponent(state.sessionId)}/branch`, {method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify({messageIndex:index})});
    await refreshSessionsSilent();
    await selectSession(result.sessionId);
}
async function branchLast() {
    const current = state.sessions.find(s => s.id === state.sessionId);
    if (!current || current.messageCount === 0) { showError("В сессии ещё нет сообщений"); return; }
    await branchSession(current.messageCount - 1);
}
function applyCompression(enabled) {
    state.compression = enabled;
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
function renderResults(compressed, answers, persist = true, strategy = UI.strategySelect.value) {
    if (persist) state.experimentResults.set(state.sessionId, {compressed, answers, strategy, tokenStats: state.lastTokenStats});
    const rows = KEY_DETAILS.map((detail, index) => {
        const answer = answers[index] || "";
        const ok = detail.match.test(answer);
        return `<div class="detail-result ${ok ? "" : "miss"}"><span class="detail-check">${ok ? "✓" : "✕"}</span><div class="detail-name">${escape(detail.label)}</div><div class="detail-meta">ожидается: <b>${escape(detail.expected)}</b><br>агент: ${escape(answer.slice(0, 180) || "—")}</div></div>`;
    }).join("");
    const correct = KEY_DETAILS.filter((detail, index) => detail.match.test(answers[index] || "")).length;
    UI.scenarioStatus.textContent = `${strategyLabel(strategy)} · проверка завершена`;
    UI.scenarioResult.innerHTML = `<div class="result-summary"><span>Помнит деталей</span><b>${correct}/${KEY_DETAILS.length}</b></div>${rows}`;
}

function strategyLabel(strategy) {
    return strategy === "SLIDING" ? "Sliding Window" : strategy === "FACTS" ? "Sticky Facts" : "Summary";
}
function strategyComplexity(strategy) {
    return strategy === "SLIDING" ? "низкая · только обрезка" : strategy === "FACTS" ? "средняя · LLM-извлечение facts" : "средняя · LLM-summary";
}
function latestResult(strategy) {
    let latest = null;
    for (const result of state.experimentResults.values()) {
        if (result.strategy === strategy) latest = result;
    }
    return latest;
}
function openReport() {
    const strategies = ["SLIDING", "FACTS", "SUMMARY"];
    const rows = strategies.map(strategy => {
        const result = latestResult(strategy);
        if (!result) return {strategy, missing: true};
        const correct = KEY_DETAILS.filter((detail, index) => detail.match.test(result.answers[index] || "")).length;
        return {strategy, correct, total: KEY_DETAILS.length, tokens: result.tokenStats?.total ?? null, calls: result.tokenStats?.calls ?? 0};
    });
    UI.reportBody.innerHTML = rows.map(row => row.missing
        ? `<tr><td>${strategyLabel(row.strategy)}</td><td colspan="2" class="report-missing">сценарий не запускался</td><td>${strategyComplexity(row.strategy)}</td></tr>`
        : `<tr><td>${strategyLabel(row.strategy)}</td><td class="${row.correct === row.total ? "report-good" : "report-bad"}">${row.correct} / ${row.total}</td><td>${row.tokens == null ? "—" : fmt(row.tokens)} <small>· вызовов ${row.calls}</small></td><td>${strategyComplexity(row.strategy)}</td></tr>`
    ).join("");
    const completed = rows.filter(row => !row.missing);
    let recommendation;
    if (!completed.length) {
        recommendation = "<p>Запусти сценарий для каждой стратегии, чтобы получить сравнение.</p>";
    } else {
        const best = [...completed].sort((a, b) => b.correct - a.correct || (a.tokens || 0) - (b.tokens || 0))[0];
        recommendation = `<h3>Вывод</h3>
            <p><b>Лучший результат на этом прогоне: ${strategyLabel(best.strategy)}</b> — ${best.correct}/${best.total} деталей${best.tokens != null ? `, ${fmt(best.tokens)} токенов` : ""}. ${completed.length < 3 ? "Для полной картины запусти оставшиеся стратегии." : ""}</p>
            <p><b>Sliding Window</b> — для коротких диалогов, жёсткого бюджета и когда не нужна память о старых деталях.</p>
            <p><b>Sticky Facts</b> — для длинных диалогов с устойчивыми фактами: сроки, бюджет, ограничения, решения.</p>
            <p><b>Summary</b> — для длинных свободных диалогов, где важны связи и общий контекст, а не только отдельные факты.</p>`;
    }
    UI.reportConclusion.innerHTML = recommendation;
    UI.reportOverlay.hidden = false;
}
function closeReport() { UI.reportOverlay.hidden = true; }

const FIVE_DETAILS = [
    "пользователя зовут Варфоломей, он тимлид проекта «Колобок»",
    "текущая задача — релиз 1.0; дедлайн релиза 15 декабря, бюджет проекта 300 тысяч рублей",
    "в текущей задаче осталось собрать APK и проверить подпись; следующий шаг — тест на устройстве",
    "долговременное ограничение — внешние API нельзя; стек Java решили не менять",
    "текущий артефакт — JSON-отчёт по релизу с версией и хешем APK; дедлайн отчёта 14 декабря"
];
async function runFiveScenario() {
    if (state.busy) return;
    UI.scenarioFiveButton.disabled = true;
    UI.scenarioFiveButton.textContent = "Сценарий запускается…";
    try {
        const current = state.sessions.find(s => s.id === state.sessionId);
        if (!current || current.messageCount > 0) await createSession();
        for (let i = 0; i < FIVE_DETAILS.length; i++) {
            UI.scenarioFiveButton.textContent = `Сценарий ${i + 1}/${FIVE_DETAILS.length}`;
            const generated = await api("/api/agent/scenario/next", {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({sessionId: state.sessionId, detail: FIVE_DETAILS[i]})
            });
            await send(generated.message);
        }
    } catch (error) {
        showError("Сценарий остановлен: " + error.message);
    } finally {
        UI.scenarioFiveButton.textContent = "Сценарий 5 сообщений";
        UI.scenarioFiveButton.disabled = false;
    }
}

async function runScenario() {
    if (state.busy) return;
    const strategy = UI.strategySelect.value;
    const compressed = strategy === "SUMMARY";
    const current = state.sessions.find(s => s.id === state.sessionId);
    if (!current || current.messageCount > 0) await createSession();
    else {
        UI.scenarioStatus.textContent = "Сценарий выполняется…";
        UI.scenarioResult.innerHTML = '<span class="muted-small">Сценарий выполняется…</span>';
    }
    await api("/api/agent/compression", {method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify({sessionId:state.sessionId, enabled:compressed})});
    await api("/api/agent/strategy", {method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify({sessionId:state.sessionId, strategy})});
    applyCompression(compressed); applyStrategy(strategy);
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
        renderResults(compressed, answers, true, strategy);
    } catch (error) { UI.scenarioStatus.textContent = `Ошибка: ${error.message}`; }
    finally { UI.scenarioButton.disabled = false; }
}
