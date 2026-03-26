package com.erise.ai.cloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EriseAiCloudApplication {

    public static void main(String[] args) {
        SpringApplication.run(EriseAiCloudApplication.class, args);
    }
}
