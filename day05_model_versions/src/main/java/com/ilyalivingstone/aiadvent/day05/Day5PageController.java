package com.ilyalivingstone.aiadvent.day05;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class Day5PageController {
    @GetMapping({"/", "/day5", "/day5/"})
    public String day5() {
        return "forward:/day5/index.html";
    }
}
