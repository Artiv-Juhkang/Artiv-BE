package com.juhkang.apptoon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class AppToonApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppToonApplication.class, args);
    }

}
