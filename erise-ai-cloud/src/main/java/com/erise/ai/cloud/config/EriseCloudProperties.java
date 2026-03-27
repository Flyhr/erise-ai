package com.erise.ai.cloud.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "erise")
public class EriseCloudProperties {

    private final Internal internal = new Internal();
    private final Backend backend = new Backend();
    private final DeepSeek deepseek = new DeepSeek();

    @Getter
    @Setter
    public static class Internal {
        private String apiKey;
    }

    @Getter
    @Setter
    public static class Backend {
        private String baseUrl;
    }

    @Getter
    @Setter
    public static class DeepSeek {
        private String apiKey;
        private String baseUrl;
        private String chatModel;
        private String embeddingModel;
    }
}