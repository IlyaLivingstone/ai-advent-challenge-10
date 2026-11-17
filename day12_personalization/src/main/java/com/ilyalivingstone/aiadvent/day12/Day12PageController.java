package com.ilyalivingstone.aiadvent.day12;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class Day12PageController {
    @GetMapping({"/", "/day12", "/day12/"})
    public String day7() {
        return "forward:/day12/index.html";
    }
}
