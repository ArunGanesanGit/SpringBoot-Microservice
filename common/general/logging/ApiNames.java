package com.arun.general.logging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@PropertySource(value = "classpath:api-log/api-names.properties", ignoreResourceNotFound = true)
@ConfigurationProperties()
@Component
public class ApiNames {

    private Map<String, String> api = new HashMap<>();  // it will store all properties start with api

    public Map<String, String> getApi() {
        return api;
    }

    public void setApi(Map<String, String> api) {
        this.api = api;
    }
}
