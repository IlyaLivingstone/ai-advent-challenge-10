package com.ilyalivingstone.aiadvent.day06;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class Day6PageController {
    @GetMapping({"/", "/day6", "/day6/"})
    public String day6() {
        return "forward:/day6/index.html";
    }
}
