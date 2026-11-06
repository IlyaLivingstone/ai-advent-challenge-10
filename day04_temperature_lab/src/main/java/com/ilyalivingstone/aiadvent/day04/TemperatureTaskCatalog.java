package com.ilyalivingstone.aiadvent.day04;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TemperatureTaskCatalog {

    private final List<TemperatureTask> tasks = List.of(
            new TemperatureTask(
                    "library",
                    PromptKind.EXACT,
                    "Библиотека",
                    "В библиотеке было 480 книг. В первый день выдали 3/8 всех книг, во второй — 1/3 остатка. Сколько книг осталось?",
                    "200",
                    true
            ),
            new TemperatureTask(
                    "names",
                    PromptKind.CREATIVE,
                    "Названия приложения",
                    "Придумай ровно 5 коротких названий для приложения, которое помогает семье планировать бюджет. Верни ТОЛЬКО JSON-массив из 5 строк без Markdown и пояснений.",
                    null,
                    false
            )
    );

    public List<TemperatureTask> tasks() {
        return tasks;
    }
}
