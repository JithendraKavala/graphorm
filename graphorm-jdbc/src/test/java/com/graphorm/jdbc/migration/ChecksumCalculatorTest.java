package com.graphorm.jdbc.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChecksumCalculatorTest {

    @Test
    @DisplayName("Should produce consistent SHA-256 checksum across different line endings")
    void shouldProduceConsistentChecksumAcrossLineEndings() {
        String unixScript = "CREATE TABLE users (\n  id SERIAL PRIMARY KEY\n);\n";
        String windowsScript = "CREATE TABLE users (\r\n  id SERIAL PRIMARY KEY\r\n);\r\n";

        String hash1 = ChecksumCalculator.calculateChecksum(unixScript);
        String hash2 = ChecksumCalculator.calculateChecksum(windowsScript);

        assertThat(hash1).isNotEmpty();
        assertThat(hash1).hasSize(64);
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("Should produce different checksums for different scripts")
    void shouldProduceDifferentChecksumsForDifferentScripts() {
        String script1 = "CREATE TABLE users (id SERIAL PRIMARY KEY);";
        String script2 = "CREATE TABLE orders (id SERIAL PRIMARY KEY);";

        String hash1 = ChecksumCalculator.calculateChecksum(script1);
        String hash2 = ChecksumCalculator.calculateChecksum(script2);

        assertThat(hash1).isNotEqualTo(hash2);
    }
}
