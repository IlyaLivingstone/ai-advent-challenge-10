package com.ilyalivingstone.aiadvent.day07;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class Day7PageController {
    @GetMapping({"/", "/day7", "/day7/"})
    public String day7() {
        return "forward:/day7/index.html";
    }
}
