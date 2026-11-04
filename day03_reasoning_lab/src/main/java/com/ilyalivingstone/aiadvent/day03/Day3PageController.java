package com.ilyalivingstone.aiadvent.day03;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class Day3PageController {

    @GetMapping({"/", "/day3", "/day3/"})
    public String day3() {
        return "forward:/day3/index.html";
    }
}
