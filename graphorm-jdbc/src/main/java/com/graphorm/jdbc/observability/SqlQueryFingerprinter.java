package com.graphorm.jdbc.observability;

import com.graphorm.jdbc.executor.BoundSql;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes SQL queries and computes deterministic SHA-256 fingerprints
 * for observability, metrics tagging, and query grouping without leaking sensitive data.
 */
public final class SqlQueryFingerprinter {

    public record FingerprintResult(
            String fingerprint,
            String normalizedSql,
            String operation,
            String targetTable
    ) {}

    private static final Pattern BLOCK_COMMENTS = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENTS = Pattern.compile("--.*?(\r?\n|$)", Pattern.DOTALL);
    private static final Pattern STRING_LITERALS = Pattern.compile("'([^']|'')*'");
    private static final Pattern NUMERIC_LITERALS = Pattern.compile("(?<![a-zA-Z0-9_$.])\\b\\d+(?:\\.\\d+)?\\b(?![a-zA-Z0-9_$.])");
    private static final Pattern PG_PARAM_PLACEHOLDERS = Pattern.compile("\\$\\d+");
    private static final Pattern IN_LIST_COLLAPSE = Pattern.compile("(?i)\\bIN\\s*\\(\\s*(?:\\?\\s*,?\\s*)+\\)");
    private static final Pattern VALUES_LIST_COLLAPSE = Pattern.compile("(?i)\\bVALUES\\s*(?:\\(\\s*(?:[^()]*)\\s*\\)\\s*,?\\s*)+");
    private static final Pattern MULTI_WHITESPACE = Pattern.compile("\\s+");

    private static final Pattern FROM_TABLE_PATTERN = Pattern.compile("(?i)\\bFROM\\s+(?:ONLY\\s+)?([\"`\\[]?[a-zA-Z0-9_]+[\"`\\]]?)");
    private static final Pattern INTO_TABLE_PATTERN = Pattern.compile("(?i)\\bINTO\\s+([\"`\\[]?[a-zA-Z0-9_]+[\"`\\]]?)");
    private static final Pattern UPDATE_TABLE_PATTERN = Pattern.compile("(?i)\\bUPDATE\\s+(?:ONLY\\s+)?([\"`\\[]?[a-zA-Z0-9_]+[\"`\\]]?)");
    private static final Pattern DELETE_TABLE_PATTERN = Pattern.compile("(?i)\\bDELETE\\s+FROM\\s+(?:ONLY\\s+)?([\"`\\[]?[a-zA-Z0-9_]+[\"`\\]]?)");

    private SqlQueryFingerprinter() {}

    /**
     * Normalizes the given SQL query and computes its deterministic SHA-256 fingerprint.
     *
     * @param sql the raw or parameterized SQL string
     * @return the fingerprint result
     */
    public static FingerprintResult fingerprint(String sql) {
        if (sql == null || sql.isBlank()) {
            String emptyHash = sha256Hex("");
            return new FingerprintResult(emptyHash, "", "UNKNOWN", "unknown");
        }

        String normalized = normalize(sql);
        String operation = extractOperation(normalized);
        String targetTable = extractTargetTable(normalized, operation);
        String hash = sha256Hex(normalized);

        return new FingerprintResult(hash, normalized, operation, targetTable);
    }

    /**
     * Fingerprints the SQL from a {@link BoundSql}.
     */
    public static FingerprintResult fingerprint(BoundSql boundSql) {
        Objects.requireNonNull(boundSql, "BoundSql must not be null");
        return fingerprint(boundSql.sql());
    }

    /**
     * Normalizes a SQL query by replacing literals with placeholders,
     * collapsing IN and VALUES lists, and normalizing whitespace.
     */
    public static String normalize(String sql) {
        if (sql == null || sql.isBlank()) {
            return "";
        }

        // 1. Remove comments
        String result = BLOCK_COMMENTS.matcher(sql).replaceAll(" ");
        result = LINE_COMMENTS.matcher(result).replaceAll(" ");

        // 2. Replace string literals with '?'
        result = STRING_LITERALS.matcher(result).replaceAll("?");

        // 3. Replace numeric literals with ?
        result = NUMERIC_LITERALS.matcher(result).replaceAll("?");

        // 4. Standardize postgres-style parameters ($1, $2) to ?
        result = PG_PARAM_PLACEHOLDERS.matcher(result).replaceAll("?");

        // 5. Collapse IN-lists: IN (?, ?, ?) -> IN (?)
        result = IN_LIST_COLLAPSE.matcher(result).replaceAll("IN (?)");

        // 6. Collapse VALUES-lists: VALUES (?, ?), (?, ?) -> VALUES (?)
        result = VALUES_LIST_COLLAPSE.matcher(result).replaceAll("VALUES (?)");

        // 7. Normalize whitespace
        result = MULTI_WHITESPACE.matcher(result).replaceAll(" ").trim();

        return result;
    }

    /**
     * Extracts the primary operational type of the query.
     */
    public static String extractOperation(String sql) {
        if (sql == null || sql.isBlank()) {
            return "UNKNOWN";
        }
        String upper = sql.toUpperCase(Locale.ROOT);
        if (upper.contains("ON CONFLICT")) {
            return "UPSERT";
        }
        if (upper.startsWith("SELECT")) {
            return "SELECT";
        }
        if (upper.startsWith("INSERT")) {
            return "INSERT";
        }
        if (upper.startsWith("UPDATE")) {
            return "UPDATE";
        }
        if (upper.startsWith("DELETE")) {
            return "DELETE";
        }
        if (upper.startsWith("WITH")) {
            // Find operation after CTEs
            if (upper.contains("SELECT")) return "SELECT";
            if (upper.contains("INSERT")) return "INSERT";
            if (upper.contains("UPDATE")) return "UPDATE";
            if (upper.contains("DELETE")) return "DELETE";
            return "CTE";
        }
        if (upper.startsWith("EXPLAIN")) {
            return "EXPLAIN";
        }
        return "OTHER";
    }

    /**
     * Extracts the target table/model name from the query.
     */
    public static String extractTargetTable(String sql, String operation) {
        if (sql == null || sql.isBlank()) {
            return "unknown";
        }

        Pattern pattern = switch (operation) {
            case "INSERT", "UPSERT" -> INTO_TABLE_PATTERN;
            case "UPDATE" -> UPDATE_TABLE_PATTERN;
            case "DELETE" -> DELETE_TABLE_PATTERN;
            default -> FROM_TABLE_PATTERN;
        };

        Matcher matcher = pattern.matcher(sql);
        if (matcher.find()) {
            String rawTable = matcher.group(1);
            return cleanIdentifier(rawTable);
        }

        // Fallback: try other patterns
        for (Pattern p : new Pattern[]{FROM_TABLE_PATTERN, INTO_TABLE_PATTERN, UPDATE_TABLE_PATTERN, DELETE_TABLE_PATTERN}) {
            Matcher m = p.matcher(sql);
            if (m.find()) {
                return cleanIdentifier(m.group(1));
            }
        }

        return "unknown";
    }

    private static String cleanIdentifier(String id) {
        if (id == null) return "unknown";
        String cleaned = id.replaceAll("[\"`\\[\\]]", "").trim();
        return cleaned.isEmpty() ? "unknown" : cleaned.toLowerCase(Locale.ROOT);
    }

    /**
     * Computes the SHA-256 hex string for the input string.
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(64);
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
