const state = { meta: null, busy: false, restoring: false, startedAt: null, pollTimer: null };
const el = id => document.getElementById(id);
const UI = {
    taskList: el("taskList"), modelList: el("modelList"), runEstimate: el("runEstimate"),
    runButton: el("runButton"), buttonLabel: document.querySelector("#runButton .button-label"),
    errorMessage: el("errorMessage"), emptyState: el("emptyState"), runningState: el("runningState"),
    runningTitle: el("runningTitle"), runningMeta: el("runningMeta"), errorState: el("errorState"),
    jobError: el("jobError"), report: el("report"), reportTime: el("reportTime"),
    metricGrid: el("metricGrid"), matrixBody: el("matrixBody"), insight: el("insight"), details: el("details")
};

document.addEventListener("DOMContentLoaded", init);

async function init() {
    UI.runButton.addEventListener("click", startExperiment);
    try {
        state.meta = await fetchJson("/api/models/meta");
        renderMeta();
        UI.runButton.disabled = false;
        const jobId = new URLSearchParams(location.search).get("job");
        if (jobId) restoreJob(jobId);
    } catch (error) {
        UI.errorMessage.textContent = "Не удалось загрузить параметры: " + error.message;
    }
}

function renderMeta() {
    UI.taskList.innerHTML = state.meta.tasks.map((task, index) => `
        <div class="task-item">
            <div class="task-head"><span class="task-number">${String(index + 1).padStart(2, "0")}</span><span class="task-title">${escapeHtml(task.title)}</span><span class="task-category">${escapeHtml(task.category)}</span></div>
        </div>`).join("");
    UI.modelList.innerHTML = state.meta.models.map(model => `
        <div class="model-item"><strong>${escapeHtml(model.title)}</strong><span>${escapeHtml(model.role)}</span><span class="price">${priceText(model)} · timeout ${model.timeoutSeconds} с</span></div>
    `).join("");
    UI.runEstimate.textContent = `${state.meta.totalRequests} замеров`;
    UI.buttonLabel.textContent = `Запустить ${state.meta.totalRequests} замеров`;
}

function restoreJob(id) {
    state.busy = true; state.restoring = true; setLoading(true);
    hide(UI.emptyState); hide(UI.report); hide(UI.errorState); show(UI.runningState);
    UI.runningTitle.textContent = "Загружаем результаты…";
    UI.runningMeta.textContent = `job ${id.slice(0, 8)}`;
    pollJob(id);
}

async function startExperiment() {
    if (state.busy || !state.meta) return;
    state.busy = true; state.restoring = false; state.startedAt = Date.now(); setLoading(true);
    UI.errorMessage.textContent = "";
    hide(UI.emptyState); hide(UI.report); hide(UI.errorState); show(UI.runningState);
    UI.runningTitle.textContent = "Опрашиваем модели…";
    UI.runningMeta.textContent = "21 замер: 7 задач × 3 модели. Сначала прогрев.";
    try {
        const job = await fetchJson("/api/models/experiments", {method: "POST"});
        const url = new URL(location.href); url.searchParams.set("job", job.id); history.replaceState({}, "", url);
        pollJob(job.id);
    } catch (error) { showFailure(error.message); }
}

function pollJob(id) {
    stopPolling();
    const tick = async () => {
        try {
            const job = await fetchJson(`/api/models/experiments/${encodeURIComponent(id)}`);
            if (job.status === "COMPLETED") {
                state.restoring = false; renderReport(job.report); finishRun(); return;
            }
            if (job.status === "FAILED") { showFailure(job.error || "Неизвестная ошибка"); return; }
            const seconds = state.startedAt ? Math.round((Date.now() - state.startedAt) / 1000) : 0;
            UI.runningMeta.textContent = `Выполняется ${seconds} с · job ${id.slice(0, 8)}`;
            state.pollTimer = setTimeout(tick, 1500);
        } catch (error) {
            if (state.restoring && error.status === 404) { resetMissingJob(); return; }
            showFailure(error.message);
        }
    };
    tick();
}

function renderReport(report) {
    hide(UI.runningState); hide(UI.errorState); hide(UI.emptyState); show(UI.report);
    const cells = report.cells || [];
    const best = [...cells].sort((a, b) => b.correctCount - a.correctCount || a.totalLatencyMs - b.totalLatencyMs)[0];
    const fastest = [...cells].sort((a, b) => a.totalLatencyMs - b.totalLatencyMs)[0];
    const totalTokens = cells.reduce((sum, cell) => sum + cell.totalTokens, 0);
    const totalCost = cells.reduce((sum, cell) => sum + cell.totalCostUsd, 0);
    UI.reportTime.textContent = `${formatDate(report.startedAt)} · ${report.taskCount} задач × ${cells.length} модели`;
    UI.metricGrid.innerHTML = [
        metricCard("Лучшая точность", best ? `${best.correctCount}/${best.taskCount}` : "—"),
        metricCard("Самая быстрая", fastest ? fastest.modelTitle : "—"),
        metricCard("Всего токенов", formatNumber(totalTokens)),
        metricCard("Стоимость", totalCost === 0 ? "$0" : `$${totalCost.toFixed(4)}`)
    ].join("");
    renderMatrix(report); renderInsight(report); renderDetails(report);
}

function renderMatrix(report) {
    UI.matrixBody.innerHTML = "";
    report.cells.forEach(cell => {
        const row = document.createElement("tr");
        row.innerHTML = `
            <td class="model-name"><strong>${escapeHtml(cell.modelTitle)}</strong><small>${escapeHtml(cell.role)}</small></td>
            <td><span class="correct-big ${cell.correctCount === cell.taskCount ? "" : "bad"}">${cell.correctCount}/${cell.taskCount}</span><span class="model-sub">формат ${cell.formatValidCount}/${cell.taskCount}</span></td>
            <td><span class="correct-big">${formatDuration(cell.totalLatencyMs)}</span><span class="model-sub">${formatDuration(cell.avgLatencyMs)} / задача</span></td>
            <td><span class="correct-big">${formatNumber(cell.totalTokens)}</span><span class="model-sub">${formatNumber(cell.totalTokens / cell.taskCount)} / задача</span></td>
            <td><span class="correct-big">${cell.totalCostUsd === 0 ? "$0" : `$${cell.totalCostUsd.toFixed(4)}`}</span><span class="model-sub">${cell.totalCostUsd === 0 ? "локально" : "по тарифу"}</span></td>`;
        UI.matrixBody.appendChild(row);
    });
}

function renderInsight(report) {
    const small = report.cells.find(cell => cell.modelId === "qwen-local");
    const medium = report.cells.find(cell => cell.modelId === "deepseek-flash");
    const flagship = report.cells.find(cell => cell.modelId === "kimi-pro");
    const ratio = (a, b) => (a && b && b.totalCostUsd > 0 ? (a.totalCostUsd / b.totalCostUsd).toFixed(1) : "—");
    const costText = small && small.totalCostUsd === 0
        ? `Локальная Qwen бесплатна; Kimi стоит ${flagship ? "$" + flagship.totalCostUsd.toFixed(4) : "—"} за прогон, DeepSeek — ${medium ? "$" + medium.totalCostUsd.toFixed(4) : "—"}. Превышение Kimi над DeepSeek по стоимости: ${ratio(flagship, medium)}×.`
        : "Стоимость рассчитана по тарифам из конфигурации.";
    UI.insight.innerHTML = `<div class="insight-title">Вывод</div><p>${costText} Сравнивайте точность и время в таблице: маленькая модель может быть достаточной там, где задачи не требуют сложного рассуждения. Локальная скорость зависит от CPU/GPU.</p>`;
}

function renderDetails(report) {
    UI.details.innerHTML = "";
    report.cells.forEach(cell => {
        const group = document.createElement("details");
        group.className = "run-group";
        group.innerHTML = `<summary><span class="run-group-title">${escapeHtml(cell.modelTitle)}</span><span class="run-group-meta">${escapeHtml(cell.summary)}</span></summary><div class="run-list"></div>`;
        const list = group.querySelector(".run-list");
        cell.runs.forEach(run => {
            const item = document.createElement("div");
            item.className = "run-card";
            item.innerHTML = `<div class="run-head">${run.correct ? '<span class="badge ok">верно</span>' : '<span class="badge bad">неверно</span>'}<span class="run-index">${escapeHtml(run.taskTitle)}</span><span class="run-stat">${run.totalTokens} tok · ${formatDuration(run.latencyMs)} · ${run.costUsd === 0 ? "$0" : "$" + run.costUsd.toFixed(6)}</span></div>
                <div class="task-answer"><strong>FINAL: ${escapeHtml(run.finalAnswer || "—")}</strong><span>эталон: ${escapeHtml(run.expectedAnswer)}</span></div>
                ${run.error ? `<p class="run-error">${escapeHtml(run.error)}</p>` : `<pre>${escapeHtml(run.rawContent || "— пустой ответ —")}</pre>`}`;
            list.appendChild(item);
        });
        group.addEventListener("toggle", () => {
            if (!group.open) return;
            UI.details.querySelectorAll("details[open]").forEach(other => { if (other !== group) other.open = false; });
        });
        UI.details.appendChild(group);
    });
}

function priceText(model) {
    if (model.inputPricePerMillion === 0 && model.outputPricePerMillion === 0) return "бесплатно";
    return `$${model.inputPricePerMillion.toFixed(2)} / $${model.outputPricePerMillion.toFixed(2)} за 1M`;
}
function metricCard(label, value) {
    return `<div class="metric-card"><span class="metric-label">${escapeHtml(label)}</span><span class="metric-value">${escapeHtml(value)}</span></div>`;
}
function showFailure(message) {
    stopPolling(); state.restoring = false; hide(UI.runningState); hide(UI.emptyState); hide(UI.report); show(UI.errorState);
    UI.jobError.textContent = message || "Не удалось выполнить эксперимент"; finishRun();
}
function resetMissingJob() {
    stopPolling(); state.restoring = false; const url = new URL(location.href); url.searchParams.delete("job"); history.replaceState({}, "", url);
    hide(UI.runningState); hide(UI.report); hide(UI.errorState); show(UI.emptyState); finishRun();
}
function finishRun() { state.busy = false; state.startedAt = null; setLoading(false); UI.runButton.disabled = false; }
function setLoading(loading) { UI.runButton.classList.toggle("loading", loading); UI.runButton.disabled = loading; }
function stopPolling() { if (state.pollTimer) { clearTimeout(state.pollTimer); state.pollTimer = null; } }
async function fetchJson(url, options) {
    const response = await fetch(url, options);
    if (!response.ok) {
        let message = `HTTP ${response.status}`;
        try { const body = await response.json(); message = body.detail || body.message || message; } catch (ignored) {}
        const error = new Error(message); error.status = response.status; throw error;
    }
    return response.json();
}
function formatNumber(value) { return new Intl.NumberFormat("ru-RU").format(value || 0); }
function formatDuration(ms) { return ms < 1000 ? `${Math.round(ms || 0)} мс` : `${((ms || 0) / 1000).toFixed(1)} с`; }
function formatDate(value) { const date = new Date(value); return Number.isNaN(date.getTime()) ? "" : new Intl.DateTimeFormat("ru-RU", {day:"2-digit",month:"short",hour:"2-digit",minute:"2-digit"}).format(date); }
function escapeHtml(value) { return String(value ?? "").replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;").replaceAll("'","&#039;"); }
function show(node) { node.classList.remove("hidden"); }
function hide(node) { node.classList.add("hidden"); }
