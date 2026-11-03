package com.ilyalivingstone.aiadvent.day02.api;

import jakarta.validation.constraints.Size;

public record PreviewPromptRequest(
        @Size(max = 4000) String prompt
) {
}
