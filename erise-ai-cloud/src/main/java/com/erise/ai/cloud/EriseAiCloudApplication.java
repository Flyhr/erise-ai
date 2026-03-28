package com.erise.ai.cloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Legacy Java AI application kept in the repository for rollback/reference only.
 * Active runtime traffic has moved to the Python AI service in {@code AiAssistant/}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class EriseAiCloudApplication {

    public static void main(String[] args) {
        SpringApplication.run(EriseAiCloudApplication.class, args);
    }
}