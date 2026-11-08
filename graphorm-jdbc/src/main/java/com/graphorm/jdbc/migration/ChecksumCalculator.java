package com.graphorm.jdbc.migration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Utility for calculating deterministic SHA-256 checksums of migration scripts,
 * normalizing line endings across different operating systems.
 */
public final class ChecksumCalculator {

    private ChecksumCalculator() {}

    /**
     * Calculates the SHA-256 hexadecimal checksum of the given SQL content after
     * normalizing CRLF line endings to LF.
     *
     * @param content the migration script content
     * @return 64-character lowercase hex SHA-256 hash
     */
    public static String calculateChecksum(String content) {
        Objects.requireNonNull(content, "Migration content must not be null");
        String normalized = content.replace("\r\n", "\n").stripTrailing();

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
