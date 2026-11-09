package com.graphorm.core.util;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility parser for duration strings supporting units like ms, s, m, h, d,
 * as well as ISO-8601 duration formats (e.g. "60s", "5m", "1h", "2d", "500ms", "PT60S").
 */
public final class DurationParser {

    private static final Pattern SIMPLE_DURATION_PATTERN = Pattern.compile(
            "^(\\d+)\\s*(ms|s|m|h|d)$",
            Pattern.CASE_INSENSITIVE
    );

    private DurationParser() {}

    /**
     * Parses a duration string into a {@link Duration}.
     *
     * @param text the duration string (e.g. "60s", "5m", "1h", "2d", "500ms")
     * @return the parsed Duration
     * @throws IllegalArgumentException if the format is invalid or null
     */
    public static Duration parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Duration string must not be null or blank");
        }
        String trimmed = text.trim();

        Matcher matcher = SIMPLE_DURATION_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            long amount = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2).toLowerCase(Locale.ROOT);
            return switch (unit) {
                case "ms" -> Duration.of(amount, ChronoUnit.MILLIS);
                case "s" -> Duration.ofSeconds(amount);
                case "m" -> Duration.ofMinutes(amount);
                case "h" -> Duration.ofHours(amount);
                case "d" -> Duration.ofDays(amount);
                default -> throw new IllegalArgumentException("Unsupported duration unit: " + unit);
            };
        }

        // Try standard ISO-8601 duration
        try {
            return Duration.parse(trimmed);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid duration syntax: '" + text + "'. Expected format: e.g. \"60s\", \"5m\", \"1h\", \"2d\", \"500ms\"",
                    e
            );
        }
    }

    /**
     * Checks whether the given text is a syntactically valid duration.
     *
     * @param text the text to check
     * @return true if valid, false otherwise
     */
    public static boolean isValid(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        try {
            parse(text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
