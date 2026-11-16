package com.ilyalivingstone.aiadvent.day11;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class Day11PageController {
    @GetMapping({"/", "/day11", "/day11/"})
    public String day7() {
        return "forward:/day11/index.html";
    }
}
