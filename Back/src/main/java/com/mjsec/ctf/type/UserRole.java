package com.mjsec.ctf.type;

public enum UserRole {
    ROLE_ADMIN("관리자"),
    ROLE_USER("일반 사용자");

    private final String description;

    UserRole(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}