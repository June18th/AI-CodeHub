package com.aicodehub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AiCodeHubApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiCodeHubApplication.class, args);
    }
}
