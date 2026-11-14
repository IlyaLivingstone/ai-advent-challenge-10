package com.ilyalivingstone.aiadvent.day09;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class Day9PageController {
    @GetMapping({"/", "/day9", "/day9/"})
    public String day7() {
        return "forward:/day9/index.html";
    }
}
