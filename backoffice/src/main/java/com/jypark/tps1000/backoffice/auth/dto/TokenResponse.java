package com.jypark.tps1000.backoffice.auth.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
