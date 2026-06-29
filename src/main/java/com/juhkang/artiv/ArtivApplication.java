package com.juhkang.artiv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class ArtivApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArtivApplication.class, args);
    }

}
