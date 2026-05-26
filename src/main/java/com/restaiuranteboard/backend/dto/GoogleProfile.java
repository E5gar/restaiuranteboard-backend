package com.restaiuranteboard.backend.dto;

public record GoogleProfile(
        String sub,
        String email,
        String givenName,
        String familyName
) {
    public String resolvedFullName() {
        String given = givenName != null ? givenName.trim() : "";
        String family = familyName != null ? familyName.trim() : "";
        String combined = (given + " " + family).trim();
        return combined.isEmpty() ? email : combined;
    }
}
