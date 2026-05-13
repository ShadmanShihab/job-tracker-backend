package com.jobtracker.security;

import java.util.Map;

public class OAuth2UserInfoFactory {

    public static String getEmail(String registrationId, Map<String, Object> attributes) {
        if ("google".equals(registrationId)) {
            return (String) attributes.get("email");
        }
        throw new IllegalArgumentException("Unsupported OAuth2 provider: " + registrationId);
    }

    public static String getName(String registrationId, Map<String, Object> attributes) {
        if ("google".equals(registrationId)) {
            return (String) attributes.get("name");
        }
        throw new IllegalArgumentException("Unsupported OAuth2 provider: " + registrationId);
    }

    public static String getId(String registrationId, Map<String, Object> attributes) {
        if ("google".equals(registrationId)) {
            return (String) attributes.get("sub");
        }
        throw new IllegalArgumentException("Unsupported OAuth2 provider: " + registrationId);
    }
}
