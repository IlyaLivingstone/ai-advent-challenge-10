package com.ilyalivingstone.aiadvent.day05;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ModelCatalog {

    private final List<ModelProfile> models = List.of(
            new ModelProfile(
                    "qwen-local",
                    "Qwen2.5 Coder 3B",
                    "маленькая · бесплатная",
                    "http://127.0.0.1:11434/v1/chat/completions",
                    "qwen2.5-coder:3b",
                    null,
                    180,
                    0.0,
                    0.0,
                    0.0
            ),
            new ModelProfile(
                    "deepseek-flash",
                    "DeepSeek Flash",
                    "средняя · дешёвая",
                    "https://api.deepseek.com/chat/completions",
                    "deepseek-flash",
                    "DEEPSEEK_API_KEY",
                    180,
                    0.30,
                    1.20,
                    0.0
            ),
            new ModelProfile(
                    "kimi-pro",
                    "Kimi K3.1 Pro",
                    "флагман · подписка",
                    "https://api.kimi.ai/coding/v1/chat/completions",
                    "k3.1-pro",
                    "KIMI_API_KEY",
                    180,
                    2.00,
                    10.00,
                    1.0
            )
    );

    public List<ModelProfile> models() {
        return models;
    }
}
