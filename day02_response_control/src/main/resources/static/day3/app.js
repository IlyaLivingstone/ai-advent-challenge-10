const state = {
    meta: null,
    busy: false,
    startedAt: null,
    pollTimer: null
};

const elements = {
    taskList: document.getElementById("taskList"),
    methodList: document.getElementById("methodList"),
    modeList: document.getElementById("modeList"),
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
    matrixBody: document.getElementById("matrixBody"),
    insight: document.getElementById("insight"),
    details: document.getElementById("details")
};

document.addEventListener("DOMContentLoaded", init);

async function init() {
    elements.runButton.addEventListener("click", startExperiment);
    try {
        state.meta = await fetchJson("/api/reasoning/meta");
        renderMeta();
        elements.runButton.disabled = false;
        const jobId = new URLSearchParams(window.location.search).get("job");
        if (jobId) {
            restoreJob(jobId);
        }
    } catch (error) {
        showInlineError("Не удалось загрузить параметры: " + error.message);
    }
}

function restoreJob(jobId) {
    state.busy = true;
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
    if (state.busy || !state.meta) {
        return;
    }
    state.busy = true;
    state.startedAt = Date.now();
    setLoading(true);
    elements.errorMessage.textContent = "";
    hide(elements.emptyState);
    hide(elements.report);
    hide(elements.errorState);
    show(elements.runningState);
    elements.runningTitle.textContent = "Решаем 7 задач…";
    elements.runningMeta.textContent = "56 решений и 14 генераций промптов.";

    try {
        const job = await fetchJson("/api/reasoning/experiments", {method: "POST"});
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
            const job = await fetchJson(`/api/reasoning/experiments/${encodeURIComponent(jobId)}`);
            if (job.status === "COMPLETED") {
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
            state.pollTimer = window.setTimeout(tick, 1500);
        } catch (error) {
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
                <span class="task-category">${escapeHtml(task.category)}</span>
            </div>
            <p class="task-question">${escapeHtml(task.question)}</p>
        `;
        elements.taskList.appendChild(item);
    });

    state.meta.methods.forEach(method => {
        const chip = document.createElement("span");
        chip.className = "protocol-chip";
        chip.textContent = method.title;
        elements.methodList.appendChild(chip);
    });

    state.meta.modes.forEach(mode => {
        const chip = document.createElement("span");
        chip.className = "protocol-chip mode";
        chip.textContent = mode.title;
        elements.modeList.appendChild(chip);
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
    const lowCells = cells.filter(cell => cell.mode === "low");
    const highCells = cells.filter(cell => cell.mode === "high");
    const lowCorrect = sum(lowCells, "correctCount");
    const highCorrect = sum(highCells, "correctCount");
    const totalTokens = sum(cells, "totalTokens");
    const taskCount = report.taskCount;

    elements.reportTime.textContent = `${formatDate(report.startedAt)} · ${report.taskCount} задач`;
    elements.metricGrid.innerHTML = [
        metricCard("Low: верно", `${lowCorrect}/${lowCells.length * taskCount}`, "low"),
        metricCard("High: верно", `${highCorrect}/${highCells.length * taskCount}`, "high"),
        metricCard("Всего токенов", formatNumber(totalTokens), "neutral"),
        metricCard("Время", formatDuration(report.durationMs), "neutral")
    ].join("");

    renderMatrix(report);
    renderInsight(report);
    renderDetails(report);
}

function renderMatrix(report) {
    elements.matrixBody.innerHTML = "";
    state.meta.methods.forEach(method => {
        const low = findCell(report, method.id, "low");
        const high = findCell(report, method.id, "high");
        const row = document.createElement("tr");
        row.innerHTML = `
            <td class="method-cell">
                <strong>${escapeHtml(method.title)}</strong>
                <small>${method.id}</small>
            </td>
            <td>${accuracyCell(low, "low")}</td>
            <td>${statsCell(low)}</td>
            <td>${accuracyCell(high, "high")}</td>
            <td>${statsCell(high)}</td>
        `;
        elements.matrixBody.appendChild(row);
    });
}

function renderInsight(report) {
    const low = report.cells.filter(cell => cell.mode === "low");
    const high = report.cells.filter(cell => cell.mode === "high");
    const lowBest = bestCell(low);
    const highBest = bestCell(high);
    const lowCorrect = sum(low, "correctCount");
    const highCorrect = sum(high, "correctCount");
    const lowTokens = sum(low, "totalTokens");
    const highTokens = sum(high, "totalTokens");
    const tokenRatio = lowTokens === 0 ? null : (highTokens / lowTokens);
    const methodTitle = id => state.meta.methods.find(method => method.id === id)?.title || id;

    const lowName = lowBest ? methodTitle(lowBest.method) : "—";
    const highName = highBest ? methodTitle(highBest.method) : "—";
    const tokenText = tokenRatio == null ? "—" : `×${tokenRatio.toFixed(1)}`;

    elements.insight.innerHTML = `
        <div class="insight-title">Вывод</div>
        <p>
            При <strong>Low</strong> лучший результат показал способ «${escapeHtml(lowName)}»
            (${lowBest ? lowBest.correctCount : 0}/6), при <strong>High</strong> — «${escapeHtml(highName)}»
            (${highBest ? highBest.correctCount : 0}/6). Суммарный расход токенов вырос ${tokenText}.
            ${lowCorrect === highCorrect
                ? "В этом прогоне максимальное рассуждение не добавило правильных ответов."
                : "Максимальное рассуждение изменило число правильных ответов."}
        </p>
    `;
}

function renderDetails(report) {
    elements.details.innerHTML = "";
    report.cells.forEach((cell, index) => {
        const method = state.meta.methods.find(item => item.id === cell.method);
        const mode = state.meta.modes.find(item => item.id === cell.mode);
        const group = document.createElement("details");
        group.className = "run-group";
        group.innerHTML = `
            <summary>
                <span class="run-group-title">${escapeHtml(method?.title || cell.method)}</span>
                <span class="run-group-meta">${escapeHtml(mode?.title || cell.mode)} · ${cell.summary}</span>
            </summary>
            <div class="run-list"></div>
        `;

        const list = group.querySelector(".run-list");
        cell.runs.forEach(run => {
            const item = document.createElement("div");
            item.className = "run-card";
            const mark = run.correct
                ? `<span class="badge ok">верно</span>`
                : `<span class="badge bad">неверно</span>`;
            const generatedPrompt = run.generatedPrompt
                ? `<details class="generated-prompt">
                       <summary>Сгенерированный промпт</summary>
                       <pre>${escapeHtml(run.generatedPrompt)}</pre>
                   </details>`
                : "";
            item.innerHTML = `
                <div class="run-head">
                    ${mark}
                    <span class="run-number">${escapeHtml(run.taskTitle)}</span>
                    <span class="run-stat">${escapeHtml(run.category)}</span>
                    <span class="run-stat">${run.apiRequests} API · ${run.totalTokens || (run.promptTokens + run.completionTokens)} tok</span>
                    <span class="run-stat">${run.reasoningTokens} reasoning · ${formatDuration(run.latencyMs)}</span>
                </div>
                <div class="task-answer">
                    <strong>FINAL: ${escapeHtml(run.finalAnswer || "—")}</strong>
                    <span>эталон: ${escapeHtml(run.expectedAnswer)}</span>
                    <span>${escapeHtml(run.evaluationNote || "")}</span>
                </div>
                ${run.error ? `<p class="run-error">${escapeHtml(run.error)}</p>` : `<pre>${escapeHtml(run.rawContent || "— пустой ответ —")}</pre>`}
                ${generatedPrompt}
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

function findCell(report, method, mode) {
    return report.cells.find(cell => cell.method === method && cell.mode === mode);
}

function accuracyCell(cell, variant) {
    if (!cell) return `<span class="cell-stats">нет данных</span>`;
    const percent = cell.taskCount ? Math.round(cell.correctCount / cell.taskCount * 100) : 0;
    return `<div class="cell-accuracy">
                <span class="accuracy-number">${cell.correctCount}/${cell.taskCount}</span>
                <span class="accuracy-bar"><span class="accuracy-fill ${variant}" style="width:${percent}%"></span></span>
            </div>`;
}

function statsCell(cell) {
    if (!cell) return `<span class="cell-stats">нет данных</span>`;
    return `<span class="cell-stats">${formatNumber(cell.totalTokens)} tok · ${formatDuration(cell.latencyMs)}<br>${cell.reasoningTokens} reasoning</span>`;
}

function metricCard(label, value, kind) {
    return `<div class="metric-card">
        <span class="metric-label">${escapeHtml(label)}</span>
        <span class="metric-value ${kind === "neutral" ? "neutral" : ""}">${escapeHtml(value)}</span>
    </div>`;
}

function bestCell(cells) {
    return [...cells].sort((a, b) => b.correctCount - a.correctCount || a.totalTokens - b.totalTokens)[0];
}

function sum(items, field) {
    return items.reduce((total, item) => total + (item[field] || 0), 0);
}

function showFailure(message) {
    stopPolling();
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
        } catch (ignored) {
            // Keep HTTP status when response is not JSON.
        }
        throw new Error(message);
    }
    return response.json();
}

function formatNumber(value) {
    return new Intl.NumberFormat("ru-RU").format(value || 0);
}

function formatDuration(milliseconds) {
    if (milliseconds < 1000) return `${milliseconds || 0} мс`;
    return `${((milliseconds || 0) / 1000).toFixed(1)} с`;
}

function formatDate(value) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "";
    return new Intl.DateTimeFormat("ru-RU", {
        day: "2-digit",
        month: "short",
        hour: "2-digit",
        minute: "2-digit"
    }).format(date);
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function show(element) {
    element.classList.remove("hidden");
}

function hide(element) {
    element.classList.add("hidden");
}
