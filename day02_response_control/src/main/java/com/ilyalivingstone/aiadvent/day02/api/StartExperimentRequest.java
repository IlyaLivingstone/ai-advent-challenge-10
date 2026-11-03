package com.ilyalivingstone.aiadvent.day02.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record StartExperimentRequest(
        @Min(1) @Max(10) Integer repeats,
        @Size(max = 4000) String prompt
) {

    public int repeatCount() {
        return repeats == null ? 3 : repeats;
    }
}
