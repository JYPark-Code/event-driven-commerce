package com.jypark.tps1000.backoffice.auth.dto;

import com.jypark.tps1000.backoffice.user.User;

public record SignupResponse(Long id, String username, String role) {

    public static SignupResponse from(User user) {
        return new SignupResponse(user.getId(), user.getUsername(), user.getRole().name());
    }
}
