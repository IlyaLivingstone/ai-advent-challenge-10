const state = {
    meta: null,
    pollTimer: null,
    startedAt: null,
    busy: false
};

const elements = {
    promptInput: document.getElementById("promptInput"),
    promptCount: document.getElementById("promptCount"),
    resetPrompt: document.getElementById("resetPrompt"),
    variantList: document.getElementById("variantList"),
    repeats: document.getElementById("repeats"),
    requestCount: document.getElementById("requestCount"),
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
    reportPrompt: document.getElementById("reportPrompt"),
    metricGrid: document.getElementById("metricGrid"),
    summaryBody: document.getElementById("summaryBody"),
    runDetails: document.getElementById("runDetails"),
    promptDialog: document.getElementById("promptDialog"),
    dialogTitle: document.getElementById("dialogTitle"),
    dialogConfig: document.getElementById("dialogConfig"),
    dialogPrompt: document.getElementById("dialogPrompt"),
    closePromptDialog: document.getElementById("closePromptDialog"),
    dialogDone: document.getElementById("dialogDone")
};

document.addEventListener("DOMContentLoaded", init);

async function init() {
    elements.repeats.addEventListener("change", () => {
        updateRequestCount();
        updateButtonLabel();
    });
    elements.runButton.addEventListener("click", startExperiment);
    elements.promptInput.addEventListener("input", () => {
        updatePromptCount();
        elements.errorMessage.textContent = "";
    });
    elements.resetPrompt.addEventListener("click", resetPrompt);
    elements.closePromptDialog.addEventListener("click", () => elements.promptDialog.close());
    elements.dialogDone.addEventListener("click", () => elements.promptDialog.close());
    elements.promptDialog.addEventListener("click", event => {
        if (event.target === elements.promptDialog) {
            elements.promptDialog.close();
        }
    });
    updateButtonLabel();

    try {
        state.meta = await fetchJson("/api/experiments/meta");
        renderMeta();
        elements.runButton.disabled = false;

        const jobId = new URLSearchParams(window.location.search).get("job");
        if (jobId) {
            restoreJob(jobId);
        }
    } catch (error) {
        showInlineError("Не удалось загрузить параметры эксперимента: " + error.message);
    }
}

function restoreJob(jobId) {
    state.busy = true;
    setButtonLoading(true);
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

    const prompt = currentPrompt();
    if (!prompt) {
        showPromptError("Введите запрос к модели перед запуском.");
        elements.promptInput.focus();
        return;
    }

    const repeats = Number(elements.repeats.value);
    state.busy = true;
    state.startedAt = Date.now();
    setButtonLoading(true);
    elements.errorMessage.textContent = "";
    hide(elements.emptyState);
    hide(elements.report);
    hide(elements.errorState);
    show(elements.runningState);
    elements.runningTitle.textContent = `Отправляем ${5 * repeats} запросов…`;
    elements.runningMeta.textContent = "Пять вариантов, " + repeats + " " + plural(repeats, ["повтор", "повтора", "повторов"]) + ". Обычно это занимает до минуты.";

    try {
        const job = await fetchJson("/api/experiments", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({repeats, prompt})
        });
        const url = new URL(window.location.href);
        url.searchParams.set("job", job.id);
        window.history.replaceState({}, "", url);
        pollJob(job.id);
    } catch (error) {
        showFailure(error.message);
    }
}

function pollJob(id) {
    stopPolling();
    const tick = async () => {
        try {
            const job = await fetchJson(`/api/experiments/${encodeURIComponent(id)}`);
            if (job.status === "COMPLETED") {
                renderReport(job.report);
                finishRun();
                return;
            }
            if (job.status === "FAILED") {
                showFailure(job.error || "Неизвестная ошибка эксперимента");
                return;
            }
            updateRunningTime(id);
            state.pollTimer = window.setTimeout(tick, 1200);
        } catch (error) {
            showFailure(error.message);
        }
    };
    tick();
}

function updateRunningTime(id) {
    if (!state.startedAt) {
        return;
    }
    const seconds = Math.max(0, Math.round((Date.now() - state.startedAt) / 1000));
    elements.runningMeta.textContent = `Выполняется ${seconds} с · job ${id.slice(0, 8)}`;
}

function renderMeta() {
    elements.promptInput.value = state.meta.prompt;
    elements.promptInput.maxLength = state.meta.maxPromptLength;
    updatePromptCount();
    elements.variantList.innerHTML = "";
    state.meta.variants.forEach((variant, index) => {
        const item = document.createElement("button");
        item.type = "button";
        item.className = "variant-item";
        item.setAttribute("aria-label", `Показать итоговый запрос: ${variant.title}`);

        const number = document.createElement("span");
        number.className = "variant-index";
        number.textContent = String(index + 1).padStart(2, "0");

        const copy = document.createElement("div");
        copy.className = "variant-copy";
        const title = document.createElement("span");
        title.className = "variant-title";
        title.textContent = variant.title;
        const config = document.createElement("div");
        config.className = "variant-config";
        variantChips(variant).forEach(chip => config.appendChild(chip));

        copy.append(title, config);
        item.append(number, copy);
        item.addEventListener("click", () => showVariantPrompt(variant));
        elements.variantList.appendChild(item);
    });
    updateRequestCount();
    updateButtonLabel();
}

function updatePromptCount() {
    const max = state.meta?.maxPromptLength || 4000;
    elements.promptCount.textContent = `${elements.promptInput.value.length} / ${max}`;
}

function resetPrompt() {
    if (!state.meta) {
        return;
    }
    elements.promptInput.value = state.meta.prompt;
    updatePromptCount();
    elements.promptInput.focus();
    elements.errorMessage.textContent = "";
}

function currentPrompt() {
    return elements.promptInput.value.trim();
}

async function showVariantPrompt(variant) {
    const prompt = currentPrompt();
    if (!prompt) {
        showPromptError("Введите запрос, чтобы увидеть итоговый промпт уровня контроля.");
        elements.promptInput.focus();
        return;
    }

    elements.dialogTitle.textContent = variant.title;
    elements.dialogConfig.innerHTML = "";
    elements.dialogPrompt.textContent = "Формируем итоговый запрос…";
    if (!elements.promptDialog.open) {
        elements.promptDialog.showModal();
    }

    try {
        const preview = await fetchJson("/api/experiments/preview", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({prompt})
        });
        const detailed = preview.variants.find(item => item.id === variant.id);
        if (!detailed) {
            throw new Error("Вариант не найден");
        }
        elements.dialogConfig.innerHTML = "";
        variantChips(detailed).forEach(chip => elements.dialogConfig.appendChild(chip));
        elements.dialogPrompt.textContent = detailed.prompt;
    } catch (error) {
        elements.dialogPrompt.textContent = "Не удалось получить итоговый запрос: " + error.message;
    }
}

function showPromptError(message) {
    elements.errorMessage.textContent = message;
}

function variantChips(variant) {
    const chips = [configChip(`max ${variant.maxTokens}`)];
    if (variant.stopSequences && variant.stopSequences.length) {
        chips.push(configChip("stop " + variant.stopSequences.map(visibleNewlines).join(", ")));
    }
    if (variant.formatInstruction) {
        chips.push(configChip("format prompt"));
    }
    if (variant.jsonMode) {
        chips.push(configChip("JSON mode"));
    }
    if (variant.schemaInstruction) {
        chips.push(configChip("schema"));
    }
    if (!variant.formatInstruction && !variant.stopSequences?.length && !variant.jsonMode && !variant.schemaInstruction) {
        chips.push(configChip("plain prompt"));
    }
    return chips;
}

function updateRequestCount() {
    if (!state.meta) {
        return;
    }
    const repeats = Number(elements.repeats.value);
    const total = state.meta.variants.length * repeats;
    elements.requestCount.textContent = `Итого ${total} ${plural(total, ["запрос", "запроса", "запросов"])} к модели`;
}

function updateButtonLabel() {
    if (state.busy || !state.meta) {
        return;
    }
    const repeats = Number(elements.repeats.value);
    elements.buttonLabel.textContent = `Запустить ${state.meta.variants.length * repeats} запросов`;
}

function renderReport(report) {
    hide(elements.runningState);
    hide(elements.errorState);
    hide(elements.emptyState);
    show(elements.report);

    const runs = report.variants.flatMap(variant => variant.runs);
    const total = runs.length;
    const direct = runs.filter(run => run.analysis?.directJson).length;
    const schema = runs.filter(run => run.analysis?.schemaValid).length;
    const averageLatency = total
        ? Math.round(runs.reduce((sum, run) => sum + run.latencyMs, 0) / total)
        : 0;

    elements.reportTime.textContent = `${formatDate(report.startedAt)} · ${report.repeats} ${plural(report.repeats, ["повтор", "повтора", "повторов"])}`;
    elements.reportPrompt.textContent = report.basePrompt || "";
    elements.metricGrid.innerHTML = [
        metricCard("Прямой JSON", `${direct}/${total}`, direct === total ? "" : "neutral"),
        metricCard("Валидно по схеме", `${schema}/${total}`, schema === total ? "" : "neutral"),
        metricCard("Средний ответ", formatDuration(averageLatency), "neutral"),
        metricCard("Время прогона", formatDuration(report.durationMs), "neutral")
    ].join("");

    elements.summaryBody.innerHTML = "";
    report.variants.forEach(variant => {
        const meta = variantMeta(variant.spec.variant);
        const summary = variant.summary;
        const title = meta?.title || variant.spec.variant;
        const config = meta ? describeConfig(meta) : "";
        const directBadge = countBadge(summary.directJson, summary.runs);
        const normalizedBadge = countBadge(summary.normalizedJson, summary.runs);
        const schemaBadge = countBadge(summary.schemaValid, summary.runs);
        const finish = formatFinishReasons(summary.finishReasons);
        const findings = summary.findings.join(" ");
        const conclusion = summary.schemaValid === summary.runs
            ? "Стабильно"
            : summary.normalizedJson < summary.runs
                ? "Ответ не парсится"
                : "Схема нарушена";

        const row = document.createElement("tr");
        row.innerHTML = `
            <td class="variant-cell">
                <strong>${escapeHtml(title)}</strong>
                <small>${escapeHtml(config)}</small>
            </td>
            <td>${directBadge}</td>
            <td>${normalizedBadge}</td>
            <td>${schemaBadge}</td>
            <td>${summary.averageCompletionTokens}</td>
            <td>${escapeHtml(finish)}</td>
            <td><span class="finding">${escapeHtml(conclusion)}. ${escapeHtml(findings)}</span></td>
        `;
        elements.summaryBody.appendChild(row);
    });

    renderRunDetails(report);
}

function renderRunDetails(report) {
    elements.runDetails.innerHTML = "";
    report.variants.forEach((variant, index) => {
        const meta = variantMeta(variant.spec.variant);
        const title = meta?.title || variant.spec.variant;
        const group = document.createElement("details");
        group.className = "run-group";
        group.innerHTML = `
            <summary>
                <span class="run-group-title">${escapeHtml(title)}</span>
                <span class="run-group-meta">${variant.summary.schemaValid}/${variant.summary.runs} schema · ${variant.runs.length} ${plural(variant.runs.length, ["прогон", "прогона", "прогонов"])}</span>
            </summary>
            <div class="run-list"></div>
        `;

        const list = group.querySelector(".run-list");
        variant.runs.forEach(run => {
            const card = document.createElement("div");
            card.className = "run-card";
            const statusBadge = run.error
                ? badge("ошибка", "bad")
                : run.analysis?.schemaValid
                    ? badge("schema ✓", "ok")
                    : run.analysis?.normalizedJson
                        ? badge("JSON без схемы", "warn")
                        : badge("не JSON", "bad");
            const errors = run.analysis?.schemaErrors || [];
            const raw = run.content || "— пустой ответ —";
            card.innerHTML = `
                <div class="run-head">
                    <span class="run-number">Прогон ${run.runNumber}</span>
                    ${statusBadge}
                    <span class="run-stat">${formatDuration(run.latencyMs)}</span>
                    <span class="run-stat">${run.completionTokens} tok / ${run.reasoningTokens} reasoning</span>
                    <span class="run-stat">finish: ${escapeHtml(run.finishReason || "—")}</span>
                </div>
                ${run.error
                    ? `<p class="run-error">${escapeHtml(run.error)}</p>`
                    : `<pre>${escapeHtml(raw)}</pre>`}
                ${errors.length
                    ? `<ul class="schema-errors">${errors.map(error => `<li>${escapeHtml(error)}</li>`).join("")}</ul>`
                    : ""}
            `;
            list.appendChild(card);
        });
        group.addEventListener("toggle", () => {
            if (!group.open) {
                return;
            }
            elements.runDetails.querySelectorAll("details[open]").forEach(other => {
                if (other !== group) {
                    other.open = false;
                }
            });
        });
        elements.runDetails.appendChild(group);
    });
}

function variantMeta(id) {
    return state.meta?.variants.find(variant => variant.id === id);
}

function describeConfig(variant) {
    const parts = [`max ${variant.maxTokens}`];
    if (variant.stopSequences?.length) {
        parts.push("stop " + variant.stopSequences.map(visibleNewlines).join(", "));
    }
    if (variant.formatInstruction) {
        parts.push("format prompt");
    }
    if (variant.jsonMode) {
        parts.push("JSON mode");
    }
    if (variant.schemaInstruction) {
        parts.push("schema");
    }
    return parts.join(" · ");
}

function metricCard(label, value, valueClass) {
    return `<div class="metric-card">
        <span class="metric-label">${escapeHtml(label)}</span>
        <span class="metric-value ${valueClass}">${escapeHtml(value)}</span>
    </div>`;
}

function countBadge(value, total) {
    const kind = value === total ? "ok" : value > 0 ? "warn" : "bad";
    return badge(`${value}/${total}`, kind);
}

function badge(text, kind) {
    return `<span class="badge ${kind}">${escapeHtml(text)}</span>`;
}

function configChip(text) {
    const chip = document.createElement("span");
    chip.className = "config-chip";
    chip.textContent = text;
    return chip;
}

function showFailure(message) {
    stopPolling();
    hide(elements.runningState);
    hide(elements.report);
    hide(elements.emptyState);
    show(elements.errorState);
    elements.jobError.textContent = message || "Не удалось выполнить эксперимент.";
    finishRun();
}

function finishRun() {
    state.busy = false;
    state.startedAt = null;
    setButtonLoading(false);
    elements.runButton.disabled = false;
    updateButtonLabel();
}

function setButtonLoading(loading) {
    elements.runButton.classList.toggle("loading", loading);
    elements.runButton.disabled = loading;
}

function showInlineError(message) {
    elements.errorMessage.textContent = message;
    elements.runButton.disabled = true;
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
            // Keep the HTTP status if the response has no JSON body.
        }
        throw new Error(message);
    }
    return response.json();
}

function formatDuration(milliseconds) {
    if (milliseconds < 1000) {
        return `${milliseconds} мс`;
    }
    return `${(milliseconds / 1000).toFixed(1)} с`;
}

function formatDate(value) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return "";
    }
    return new Intl.DateTimeFormat("ru-RU", {
        day: "2-digit",
        month: "short",
        hour: "2-digit",
        minute: "2-digit"
    }).format(date);
}

function formatFinishReasons(reasons) {
    const entries = Object.entries(reasons || {});
    if (!entries.length) {
        return "—";
    }
    return entries.map(([reason, count]) => `${reason} ×${count}`).join(", ");
}

function visibleNewlines(value) {
    return String(value).replace(/\n/g, "↵");
}

function plural(number, forms) {
    const mod10 = number % 10;
    const mod100 = number % 100;
    if (mod10 === 1 && mod100 !== 11) {
        return forms[0];
    }
    if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
        return forms[1];
    }
    return forms[2];
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
