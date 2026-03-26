package com.erise.ai.backend.common.security;

public record CurrentUser(Long userId, String username, String roleCode) {

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(roleCode);
    }
}
