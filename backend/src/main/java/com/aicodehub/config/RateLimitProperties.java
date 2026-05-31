package com.aicodehub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;
    private int defaultRpm = 60;
    private int chatRpm = 20;
    private int agentRpm = 20;
    private int adminRpm = 120;
}
