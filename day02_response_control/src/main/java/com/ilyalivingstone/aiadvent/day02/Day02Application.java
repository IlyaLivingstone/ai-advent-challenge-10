package com.ilyalivingstone.aiadvent.day02;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Day02Application {

    public static void main(String[] args) {
        SpringApplication.run(Day02Application.class, args);
    }
}
