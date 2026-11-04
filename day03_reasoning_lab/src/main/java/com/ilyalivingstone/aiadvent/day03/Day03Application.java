package com.ilyalivingstone.aiadvent.day03;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Day03Application {

    public static void main(String[] args) {
        SpringApplication.run(Day03Application.class, args);
    }
}
