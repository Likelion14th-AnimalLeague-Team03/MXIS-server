package com.mxis.server.device.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "mxis.device.ble")
public class DeviceConnectionProperties {

    private List<String> allowedServiceUuids = new ArrayList<>();
    private int scanTimeoutSeconds = 10;
    private int connectTimeoutSeconds = 15;

    public List<String> getAllowedServiceUuids() {
        return allowedServiceUuids;
    }

    public void setAllowedServiceUuids(List<String> allowedServiceUuids) {
        this.allowedServiceUuids = allowedServiceUuids;
    }

    public int getScanTimeoutSeconds() {
        return scanTimeoutSeconds;
    }

    public void setScanTimeoutSeconds(int scanTimeoutSeconds) {
        this.scanTimeoutSeconds = scanTimeoutSeconds;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }
}
