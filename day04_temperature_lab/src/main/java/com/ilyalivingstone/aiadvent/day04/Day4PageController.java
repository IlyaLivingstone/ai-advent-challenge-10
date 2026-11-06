package com.ilyalivingstone.aiadvent.day04;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class Day4PageController {

    @GetMapping({"/", "/day4", "/day4/"})
    public String day4() {
        return "forward:/day4/index.html";
    }
}
