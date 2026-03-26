package com.erise.ai.backend.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "erise")
public class EriseProperties {

    private final Jwt jwt = new Jwt();
    private final Storage storage = new Storage();
    private final Internal internal = new Internal();
    private final Cloud cloud = new Cloud();
    private final Bootstrap bootstrap = new Bootstrap();

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private long accessTokenExpireMinutes = 120;
        private long refreshTokenExpireDays = 14;
    }

    @Getter
    @Setter
    public static class Storage {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket;
    }

    @Getter
    @Setter
    public static class Internal {
        private String apiKey;
    }

    @Getter
    @Setter
    public static class Cloud {
        private String baseUrl;
    }

    @Getter
    @Setter
    public static class Bootstrap {
        private String adminUsername;
        private String adminPassword;
        private String adminDisplayName;
    }
}
