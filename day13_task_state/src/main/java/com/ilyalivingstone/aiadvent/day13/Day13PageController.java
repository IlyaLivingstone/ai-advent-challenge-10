package com.ilyalivingstone.aiadvent.day13;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class Day13PageController {
    @GetMapping({"/", "/day13", "/day13/"})
    public String page() {
        return "forward:/day13/index.html";
    }
}
