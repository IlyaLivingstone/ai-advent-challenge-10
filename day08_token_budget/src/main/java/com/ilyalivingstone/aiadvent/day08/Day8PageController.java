package com.ilyalivingstone.aiadvent.day08;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class Day8PageController {
    @GetMapping({"/", "/day8", "/day8/"})
    public String day7() {
        return "forward:/day8/index.html";
    }
}
