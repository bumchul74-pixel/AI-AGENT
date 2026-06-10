package com.hanwha.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AIMain {
    public static void main(String[] args) {
        SpringApplication.run(AIMain.class, args);
    }
}
