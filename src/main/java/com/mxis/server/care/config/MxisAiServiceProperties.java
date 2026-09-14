package com.mxis.server.care.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Component
@Validated
@ConfigurationProperties(prefix = "mxis.ai.service")
public class MxisAiServiceProperties {

    private boolean enabled = false;
    @NotBlank
    private String baseUrl = "http://127.0.0.1:8765";
    @Min(1)
    private int timeoutSeconds = 45;
    @Min(1)
    private int connectTimeoutSeconds = 5;
    private String internalApiKey = "";
    private boolean llmEnabled = false;
    private String model = "gpt-5-mini";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }

    public void setConnectTimeoutSeconds(int value) { this.connectTimeoutSeconds = value; }

    public String getInternalApiKey() {
        return internalApiKey;
    }

    public void setInternalApiKey(String internalApiKey) {
        this.internalApiKey = internalApiKey;
    }

    public boolean isLlmEnabled() {
        return llmEnabled;
    }

    public void setLlmEnabled(boolean llmEnabled) {
        this.llmEnabled = llmEnabled;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public boolean hasInternalApiKey() {
        return internalApiKey != null && !internalApiKey.isBlank();
    }
}
