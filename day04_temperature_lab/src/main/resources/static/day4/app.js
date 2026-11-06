const state = { meta: null, busy: false, restoring: false, startedAt: null, pollTimer: null };

const elements = {
    taskList: document.getElementById("taskList"),
    temperatureList: document.getElementById("temperatureList"),
    runEstimate: document.getElementById("runEstimate"),
    runButton: document.getElementById("runButton"),
    buttonLabel: document.querySelector("#runButton .button-label"),
    errorMessage: document.getElementById("errorMessage"),
    emptyState: document.getElementById("emptyState"),
    runningState: document.getElementById("runningState"),
    runningTitle: document.getElementById("runningTitle"),
    runningMeta: document.getElementById("runningMeta"),
    errorState: document.getElementById("errorState"),
    jobError: document.getElementById("jobError"),
    report: document.getElementById("report"),
    reportTime: document.getElementById("reportTime"),
    metricGrid: document.getElementById("metricGrid"),
    zeroBanner: document.getElementById("zeroBanner"),
    matrixBody: document.getElementById("matrixBody"),
    insight: document.getElementById("insight"),
    details: document.getElementById("details")
};

document.addEventListener("DOMContentLoaded", init);

async function init() {
    elements.runButton.addEventListener("click", startExperiment);
    try {
        state.meta = await fetchJson("/api/temperature/meta");
        renderMeta();
        elements.runButton.disabled = false;
        const jobId = new URLSearchParams(window.location.search).get("job");
        if (jobId) restoreJob(jobId);
    } catch (error) {
        showInlineError("Не удалось загрузить параметры: " + error.message);
    }
}

function restoreJob(jobId) {
    state.busy = true;
    state.restoring = true;
    setLoading(true);
    hide(elements.emptyState);
    hide(elements.report);
    hide(elements.errorState);
    show(elements.runningState);
    elements.runningTitle.textContent = "Загружаем результаты…";
    elements.runningMeta.textContent = `job ${jobId.slice(0, 8)}`;
    pollJob(jobId);
}

async function startExperiment() {
    if (state.busy || !state.meta) return;
    state.busy = true;
    state.restoring = false;
    state.startedAt = Date.now();
    setLoading(true);
    elements.errorMessage.textContent = "";
    hide(elements.emptyState);
    hide(elements.report);
    hide(elements.errorState);
    show(elements.runningState);
    elements.runningTitle.textContent = "Проверяем температуры…";
    elements.runningMeta.textContent = `${state.meta.totalRequests} запросов: 2 задачи × 3 температуры × ${state.meta.runsPerCombination} повторов.`;
    try {
        const job = await fetchJson("/api/temperature/experiments", {method: "POST"});
        const url = new URL(window.location.href);
        url.searchParams.set("job", job.id);
        window.history.replaceState({}, "", url);
        pollJob(job.id);
    } catch (error) {
        showFailure(error.message);
    }
}

function pollJob(jobId) {
    stopPolling();
    const tick = async () => {
        try {
            const job = await fetchJson(`/api/temperature/experiments/${encodeURIComponent(jobId)}`);
            if (job.status === "COMPLETED") {
                state.restoring = false;
                renderReport(job.report);
                finishRun();
                return;
            }
            if (job.status === "FAILED") {
                showFailure(job.error || "Неизвестная ошибка эксперимента");
                return;
            }
            const seconds = state.startedAt ? Math.round((Date.now() - state.startedAt) / 1000) : 0;
            elements.runningMeta.textContent = `Выполняется ${seconds} с · job ${jobId.slice(0, 8)}`;
            state.pollTimer = window.setTimeout(tick, 1200);
        } catch (error) {
            if (state.restoring && error.status === 404) {
                resetMissingJob();
                return;
            }
            showFailure(error.message);
        }
    };
    tick();
}

function renderMeta() {
    elements.taskList.innerHTML = "";
    state.meta.tasks.forEach((task, index) => {
        const item = document.createElement("div");
        item.className = "task-item";
        item.innerHTML = `
            <div class="task-head">
                <span class="task-number">${String(index + 1).padStart(2, "0")}</span>
                <span class="task-title">${escapeHtml(task.title)}</span>
                <span class="task-category">${escapeHtml(task.kindTitle)}</span>
            </div>
            <p class="task-question">${escapeHtml(task.prompt)}</p>
        `;
        elements.taskList.appendChild(item);
    });
    state.meta.temperatures.forEach(temp => {
        const chip = document.createElement("span");
        chip.className = "protocol-chip mode";
        chip.textContent = `t = ${temp.label}`;
        elements.temperatureList.appendChild(chip);
    });
    elements.runEstimate.textContent = `${state.meta.totalRequests} запросов к модели`;
    elements.buttonLabel.textContent = `Запустить ${state.meta.totalRequests} запросов`;
}

function renderReport(report) {
    hide(elements.runningState);
    hide(elements.errorState);
    hide(elements.emptyState);
    show(elements.report);

    const cells = report.cells || [];
    const exactCells = cells.filter(cell => cell.kind === "exact");
    const creativeCells = cells.filter(cell => cell.kind === "creative");
    const avgDiversity = creativeCells.length
        ? creativeCells.reduce((sum, cell) => sum + cell.diversity, 0) / creativeCells.length
        : 0;

    elements.reportTime.textContent = `${formatDate(report.startedAt)} · ${report.runsPerCombination} прогонов × 6 комбинаций`;
    const perTemperature = state.meta.temperatures.map(temp => {
        const tempCells = cells.filter(cell => cell.temperatureId === temp.id);
        const unique = tempCells.reduce((sum, cell) => sum + cell.uniqueAnswers, 0);
        const total = tempCells.reduce((sum, cell) => sum + cell.runCount, 0);
        return {label: temp.label, unique, total};
    });
    elements.metricGrid.innerHTML = [
        ...perTemperature.map(item => metricCard(`Уникальных при t = ${item.label}`, `${item.unique}/${item.total}`)),
        metricCard("Средний diversity", avgDiversity.toFixed(2))
    ].join("");

    const zeroCells = cells.filter(cell => cell.temperatureId === "0");
    const zeroExact = zeroCells.find(cell => cell.kind === "exact");
    const zeroCreative = zeroCells.find(cell => cell.kind === "creative");
    elements.zeroBanner.innerHTML = `<strong>temperature = 0:</strong> точная задача — ${zeroExact ? `${zeroExact.uniqueAnswers}/${zeroExact.runCount}` : "—"} уникальных, генерация — ${zeroCreative ? `${zeroCreative.uniqueAnswers}/${zeroCreative.runCount}` : "—"} уникальных.`;

    renderMatrix(report);
    renderInsight(report);
    renderDetails(report);
}

function renderMatrix(report) {
    elements.matrixBody.innerHTML = "";
    state.meta.tasks.forEach((task, index) => {
        const row = document.createElement("tr");
        if (index > 0) row.className = "matrix-task-row";
        const taskCell = `<td class="task-cell"><strong>${escapeHtml(task.title)}</strong><small>${escapeHtml(task.kindTitle)}</small></td>`;
        const tempCells = state.meta.temperatures.map(temp => {
            const cell = report.cells.find(item => item.taskId === task.id && item.temperatureId === temp.id);
            return `<td>${renderCell(cell)}</td>`;
        }).join("");
        row.innerHTML = taskCell + tempCells;
        elements.matrixBody.appendChild(row);
    });
}

function renderCell(cell) {
    if (!cell) return '<span class="temp-sub">нет данных</span>';
    if (cell.kind === "exact") {
        return `<span class="temp-score"><span class="${cell.uniqueAnswers === 1 ? "ok" : "bad"}">${cell.uniqueAnswers}/${cell.runCount}</span> уникальных</span>
            <span class="temp-sub">верно ${cell.correctCount}/${cell.runCount} · формат ${cell.formatValidCount}/${cell.runCount}<br>${escapeHtml(cell.summary)}</span>`;
    }
    return `<span class="temp-score"><span class="${cell.uniqueAnswers === cell.runCount ? "ok" : "bad"}">${cell.uniqueAnswers}/${cell.runCount}</span> уникальных</span>
        <span class="temp-sub">diversity ${cell.diversity.toFixed(2)} · формат ${cell.formatValidCount}/${cell.runCount}<br>${escapeHtml(cell.summary)}</span>`;
}

function renderInsight(report) {
    const exact = report.cells.filter(cell => cell.kind === "exact");
    const creative = report.cells.filter(cell => cell.kind === "creative");
    const tempLabel = id => state.meta.temperatures.find(temp => temp.id === id)?.label || id;
    const list = cells => cells
        .slice()
        .sort((a, b) => a.temperature - b.temperature)
        .map(cell => `t=${tempLabel(cell.temperatureId)} — ${cell.uniqueAnswers}/${cell.runCount}`)
        .join(", ");
    const bestCreative = [...creative].sort((a, b) => b.diversity - a.diversity)[0];
    const totalFormat = report.cells.reduce((sum, cell) => sum + cell.formatValidCount, 0);
    const totalRuns = report.cells.reduce((sum, cell) => sum + cell.runCount, 0);
    elements.insight.innerHTML = `
        <div class="insight-title">Вывод · главная метрика</div>
        <p>
            Уникальных семантических ответов из пяти прогонов: точная задача — ${list(exact)};
            генерация — ${list(creative)}. Максимальное разнообразие у температуры
            <strong>${bestCreative ? bestCreative.temperatureLabel : "—"}</strong>
            (diversity ${bestCreative ? bestCreative.diversity.toFixed(2) : "—"}).
            Формат и правильность — ограничители: ${totalFormat}/${totalRuns} ответов прошли формат.
        </p>
    `;
}

function renderDetails(report) {
    elements.details.innerHTML = "";
    report.cells.forEach(cell => {
        const task = state.meta.tasks.find(item => item.id === cell.taskId);
        const group = document.createElement("details");
        group.className = "run-group";
        group.innerHTML = `
            <summary>
                <span class="run-group-title">${escapeHtml(task?.title || cell.taskId)}</span>
                <span class="run-group-meta">t = ${escapeHtml(cell.temperatureLabel)} · ${escapeHtml(cell.summary)}</span>
            </summary>
            <div class="run-list"></div>
        `;
        const list = group.querySelector(".run-list");
        cell.runs.forEach(run => {
            const item = document.createElement("div");
            item.className = "run-card";
            const formatBadge = run.formatValid
                ? '<span class="badge ok">формат ок</span>'
                : '<span class="badge bad">формат сломан</span>';
            const correctness = run.correct === null || run.correct === undefined
                ? ""
                : (run.correct ? '<span class="badge ok">верно</span>' : '<span class="badge bad">неверно</span>');
            const output = run.parsedAnswer && run.parsedAnswer !== run.rawContent
                ? `<div class="creative-names">${escapeHtml(run.parsedAnswer)}</div>`
                : "";
            item.innerHTML = `
                <div class="run-head">
                    ${formatBadge}${correctness}
                    <span class="run-index">прогон ${run.runNumber}</span>
                    <span class="run-stat">${run.totalTokens} tok · ${formatDuration(run.latencyMs)}</span>
                </div>
                ${output}
                <pre>${escapeHtml(run.rawContent || "— пустой ответ —")}</pre>
                ${run.error ? `<p class="run-error">${escapeHtml(run.error)}</p>` : ""}
            `;
            list.appendChild(item);
        });
        group.addEventListener("toggle", () => {
            if (!group.open) return;
            elements.details.querySelectorAll("details[open]").forEach(other => {
                if (other !== group) other.open = false;
            });
        });
        elements.details.appendChild(group);
    });
}

function metricCard(label, value) {
    return `<div class="metric-card"><span class="metric-label">${escapeHtml(label)}</span><span class="metric-value">${escapeHtml(value)}</span></div>`;
}

function resetMissingJob() {
    stopPolling();
    state.restoring = false;
    const url = new URL(window.location.href);
    url.searchParams.delete("job");
    window.history.replaceState({}, "", url);
    hide(elements.runningState);
    hide(elements.report);
    hide(elements.errorState);
    show(elements.emptyState);
    finishRun();
}

function showFailure(message) {
    stopPolling();
    state.restoring = false;
    hide(elements.runningState);
    hide(elements.emptyState);
    hide(elements.report);
    show(elements.errorState);
    elements.jobError.textContent = message || "Не удалось выполнить эксперимент.";
    finishRun();
}

function showInlineError(message) {
    elements.errorMessage.textContent = message;
    elements.runButton.disabled = true;
}

function finishRun() {
    state.busy = false;
    state.startedAt = null;
    setLoading(false);
    elements.runButton.disabled = false;
}

function setLoading(loading) {
    elements.runButton.classList.toggle("loading", loading);
    elements.runButton.disabled = loading;
}

function stopPolling() {
    if (state.pollTimer) {
        window.clearTimeout(state.pollTimer);
        state.pollTimer = null;
    }
}

async function fetchJson(url, options) {
    const response = await fetch(url, options);
    if (!response.ok) {
        let message = `HTTP ${response.status}`;
        try {
            const body = await response.json();
            message = body.detail || body.message || message;
        } catch (ignored) {}
        const error = new Error(message);
        error.status = response.status;
        throw error;
    }
    return response.json();
}

function formatDuration(milliseconds) {
    if (milliseconds < 1000) return `${milliseconds || 0} мс`;
    return `${((milliseconds || 0) / 1000).toFixed(1)} с`;
}

function formatDate(value) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "";
    return new Intl.DateTimeFormat("ru-RU", {
        day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit"
    }).format(date);
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;").replaceAll("'", "&#039;");
}

function show(element) { element.classList.remove("hidden"); }
function hide(element) { element.classList.add("hidden"); }
