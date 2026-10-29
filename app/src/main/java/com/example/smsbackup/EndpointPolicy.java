package com.example.smsbackup;

import java.net.URI;

public final class EndpointPolicy {
    private EndpointPolicy() {}

    public static void validate(String endpoint, boolean allowHttp) {
        URI uri = URI.create(endpoint);
        boolean secure = "https".equalsIgnoreCase(uri.getScheme());
        boolean localTesting = allowHttp && "http".equalsIgnoreCase(uri.getScheme());
        if ((!secure && !localTesting) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                || uri.getPort() == 0 || uri.getPort() > 65535) {
            throw new IllegalArgumentException("Use a valid HTTPS URL, or HTTP in a debug build.");
        }
    }
}