package com.ilyalivingstone.aiadvent.day10;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class Day10PageController {
    @GetMapping({"/", "/day10", "/day10/"})
    public String day7() {
        return "forward:/day10/index.html";
    }
}
