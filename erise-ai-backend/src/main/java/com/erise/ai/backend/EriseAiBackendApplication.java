package com.erise.ai.backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.erise.ai.backend")
public class EriseAiBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(EriseAiBackendApplication.class, args);
    }
}
