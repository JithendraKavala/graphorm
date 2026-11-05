package com.graphorm.core.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Parser for join conditions defined in GraphORM schemas (e.g. in {@code @@leftJoin} and {@code @@innerJoin}).
 * <p>
 * Parses expressions such as:
 * <ul>
 *     <li>{@code "s.departmentId == d.id"}</li>
 *     <li>{@code "s.departmentId = d.id"}</li>
 *     <li>{@code "s.id == e.studentId AND e.status == 'ACTIVE'"}</li>
 *     <li>{@code "s.id == e.studentId && e.active == true"}</li>
 *     <li>{@code "s.deptId == d.id OR s.altDeptId == d.id"}</li>
 *     <li>{@code "d.deletedAt IS NULL"}</li>
 * </ul>
 * into strongly typed, immutable {@link WherePredicate} trees.
 */
public final class JoinConditionParser {

    private JoinConditionParser() {}

    /**
     * Parses a join condition string into a {@link WherePredicate}.
     *
     * @param condition the condition string (e.g. "s.departmentId == d.id")
     * @return the parsed WherePredicate AST
     * @throws IllegalArgumentException if the condition is null, blank, or syntactically invalid
     */
    public static WherePredicate parse(String condition) {
        return parse(condition, null);
    }

    /**
     * Parses a join condition string into a {@link WherePredicate}, applying a column resolver
     * to resolve qualified field names (e.g. "s.departmentId" -&gt; "s.dept_id").
     *
     * @param condition      the condition string
     * @param columnResolver optional function resolving field names to column names
     * @return the parsed WherePredicate AST
     * @throws IllegalArgumentException if the condition is null, blank, or syntactically invalid
     */
    public static WherePredicate parse(String condition, Function<String, String> columnResolver) {
        Objects.requireNonNull(condition, "Join condition must not be null");
        String trimmed = condition.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Join condition must not be blank");
        }

        List<Token> tokens = tokenize(trimmed);
        Parser parser = new Parser(tokens, columnResolver);
        WherePredicate result = parser.parseExpression();
        if (parser.hasMore()) {
            throw new IllegalArgumentException("Unexpected token after join condition: '" + parser.peek().text() + "' in: " + condition);
        }
        return result;
    }

    // --- Tokenizer ---

    private enum TokenType {
        LPAREN, RPAREN,
        AND, OR, NOT,
        OPERATOR,
        IS, NULL,
        IDENTIFIER,
        STRING_LITERAL,
        NUMBER_LITERAL,
        BOOLEAN_LITERAL
    }

    private record Token(TokenType type, String text, Object value) {}

    private static List<Token> tokenize(String input) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int n = input.length();

        while (i < n) {
            char c = input.charAt(i);

            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            if (c == '(') {
                tokens.add(new Token(TokenType.LPAREN, "(", null));
                i++;
                continue;
            }
            if (c == ')') {
                tokens.add(new Token(TokenType.RPAREN, ")", null));
                i++;
                continue;
            }

            // String literals: '...' or "..."
            if (c == '\'' || c == '"') {
                char quote = c;
                int start = ++i;
                StringBuilder sb = new StringBuilder();
                boolean escaped = false;
                while (i < n) {
                    char ch = input.charAt(i);
                    if (escaped) {
                        sb.append(ch);
                        escaped = false;
                    } else if (ch == '\\') {
                        escaped = true;
                    } else if (ch == quote) {
                        break;
                    } else {
                        sb.append(ch);
                    }
                    i++;
                }
                if (i >= n) {
                    throw new IllegalArgumentException("Unterminated string literal in condition: " + input);
                }
                i++; // skip closing quote
                tokens.add(new Token(TokenType.STRING_LITERAL, sb.toString(), sb.toString()));
                continue;
            }

            // Multi-char operators: ==, !=, <=, >=, <>, &&, ||
            if (c == '=' && i + 1 < n && input.charAt(i + 1) == '=') {
                tokens.add(new Token(TokenType.OPERATOR, "==", ComparisonOperator.EQUALS));
                i += 2;
                continue;
            }
            if (c == '!' && i + 1 < n && input.charAt(i + 1) == '=') {
                tokens.add(new Token(TokenType.OPERATOR, "!=", ComparisonOperator.NOT_EQUALS));
                i += 2;
                continue;
            }
            if (c == '<' && i + 1 < n && input.charAt(i + 1) == '>') {
                tokens.add(new Token(TokenType.OPERATOR, "<>", ComparisonOperator.NOT_EQUALS));
                i += 2;
                continue;
            }
            if (c == '<' && i + 1 < n && input.charAt(i + 1) == '=') {
                tokens.add(new Token(TokenType.OPERATOR, "<=", ComparisonOperator.LESS_THAN_OR_EQUAL));
                i += 2;
                continue;
            }
            if (c == '>' && i + 1 < n && input.charAt(i + 1) == '=') {
                tokens.add(new Token(TokenType.OPERATOR, ">=", ComparisonOperator.GREATER_THAN_OR_EQUAL));
                i += 2;
                continue;
            }
            if (c == '&' && i + 1 < n && input.charAt(i + 1) == '&') {
                tokens.add(new Token(TokenType.AND, "&&", null));
                i += 2;
                continue;
            }
            if (c == '|' && i + 1 < n && input.charAt(i + 1) == '|') {
                tokens.add(new Token(TokenType.OR, "||", null));
                i += 2;
                continue;
            }

            // Single-char operators: =, <, >, !
            if (c == '=') {
                tokens.add(new Token(TokenType.OPERATOR, "=", ComparisonOperator.EQUALS));
                i++;
                continue;
            }
            if (c == '<') {
                tokens.add(new Token(TokenType.OPERATOR, "<", ComparisonOperator.LESS_THAN));
                i++;
                continue;
            }
            if (c == '>') {
                tokens.add(new Token(TokenType.OPERATOR, ">", ComparisonOperator.GREATER_THAN));
                i++;
                continue;
            }
            if (c == '!') {
                tokens.add(new Token(TokenType.NOT, "!", null));
                i++;
                continue;
            }

            // Numeric literals (including negative numbers when after operator/lparen)
            boolean isSign = (c == '-' || c == '+');
            boolean isDigit = Character.isDigit(c);
            if (isDigit || (isSign && (tokens.isEmpty() || tokens.get(tokens.size() - 1).type() == TokenType.OPERATOR || tokens.get(tokens.size() - 1).type() == TokenType.LPAREN))) {
                int start = i;
                if (isSign) {
                    i++;
                }
                while (i < n && (Character.isDigit(input.charAt(i)) || input.charAt(i) == '.')) {
                    i++;
                }
                String numStr = input.substring(start, i);
                if (numStr.contains(".")) {
                    tokens.add(new Token(TokenType.NUMBER_LITERAL, numStr, Double.parseDouble(numStr)));
                } else {
                    tokens.add(new Token(TokenType.NUMBER_LITERAL, numStr, Long.parseLong(numStr)));
                }
                continue;
            }

            // Words: keywords (AND, OR, NOT, IS, NULL, true, false, LIKE, ILIKE) or identifiers
            if (Character.isLetter(c) || c == '_' || c == '$') {
                int start = i;
                while (i < n && (Character.isLetterOrDigit(input.charAt(i)) || input.charAt(i) == '_' || input.charAt(i) == '$' || input.charAt(i) == '.')) {
                    i++;
                }
                String word = input.substring(start, i);
                String upper = word.toUpperCase();
                switch (upper) {
                    case "AND" -> tokens.add(new Token(TokenType.AND, word, null));
                    case "OR" -> tokens.add(new Token(TokenType.OR, word, null));
                    case "NOT" -> tokens.add(new Token(TokenType.NOT, word, null));
                    case "IS" -> tokens.add(new Token(TokenType.IS, word, null));
                    case "NULL" -> tokens.add(new Token(TokenType.NULL, word, null));
                    case "TRUE" -> tokens.add(new Token(TokenType.BOOLEAN_LITERAL, word, Boolean.TRUE));
                    case "FALSE" -> tokens.add(new Token(TokenType.BOOLEAN_LITERAL, word, Boolean.FALSE));
                    case "LIKE" -> tokens.add(new Token(TokenType.OPERATOR, word, ComparisonOperator.LIKE));
                    case "ILIKE" -> tokens.add(new Token(TokenType.OPERATOR, word, ComparisonOperator.ILIKE));
                    default -> tokens.add(new Token(TokenType.IDENTIFIER, word, word));
                }
                continue;
            }

            throw new IllegalArgumentException("Unexpected character '" + c + "' at position " + i + " in condition: " + input);
        }

        return tokens;
    }

    // --- Recursive Descent Parser ---

    private static final class Parser {
        private final List<Token> tokens;
        private final Function<String, String> columnResolver;
        private int pos = 0;

        Parser(List<Token> tokens, Function<String, String> columnResolver) {
            this.tokens = tokens;
            this.columnResolver = columnResolver;
        }

        boolean hasMore() {
            return pos < tokens.size();
        }

        Token peek() {
            if (!hasMore()) {
                throw new IllegalArgumentException("Unexpected end of condition");
            }
            return tokens.get(pos);
        }

        Token next() {
            Token t = peek();
            pos++;
            return t;
        }

        boolean match(TokenType type) {
            if (hasMore() && peek().type() == type) {
                pos++;
                return true;
            }
            return false;
        }

        WherePredicate parseExpression() {
            return parseOr();
        }

        private WherePredicate parseOr() {
            WherePredicate left = parseAnd();
            List<WherePredicate> orList = new ArrayList<>();
            orList.add(left);

            while (match(TokenType.OR)) {
                orList.add(parseAnd());
            }

            return orList.size() == 1 ? left : WherePredicate.or(orList);
        }

        private WherePredicate parseAnd() {
            WherePredicate left = parseUnary();
            List<WherePredicate> andList = new ArrayList<>();
            andList.add(left);

            while (match(TokenType.AND)) {
                andList.add(parseUnary());
            }

            return andList.size() == 1 ? left : WherePredicate.and(andList);
        }

        private WherePredicate parseUnary() {
            if (match(TokenType.NOT)) {
                return parseUnary().negate();
            }
            return parsePrimary();
        }

        private WherePredicate parsePrimary() {
            if (match(TokenType.LPAREN)) {
                WherePredicate inner = parseExpression();
                if (!match(TokenType.RPAREN)) {
                    throw new IllegalArgumentException("Missing closing parenthesis in join condition");
                }
                return inner;
            }

            Token left = next();

            // Check for IS [NOT] NULL
            if (left.type() == TokenType.IDENTIFIER && hasMore() && peek().type() == TokenType.IS) {
                next(); // consume IS
                boolean not = match(TokenType.NOT);
                if (!match(TokenType.NULL)) {
                    throw new IllegalArgumentException("Expected NULL after IS [NOT] in join condition");
                }
                String col = resolveColumn(left.text());
                return not ? WherePredicate.isNotNull(col) : WherePredicate.isNull(col);
            }

            // Otherwise, expect a comparison operator
            if (!hasMore() || peek().type() != TokenType.OPERATOR) {
                throw new IllegalArgumentException("Expected comparison operator after '" + left.text() + "' in join condition");
            }

            Token opToken = next();
            ComparisonOperator op = (ComparisonOperator) opToken.value();

            if (!hasMore()) {
                throw new IllegalArgumentException("Expected right-hand operand after operator '" + opToken.text() + "' in join condition");
            }

            Token right = next();

            return buildComparisonPredicate(left, op, right);
        }

        private WherePredicate buildComparisonPredicate(Token left, ComparisonOperator op, Token right) {
            boolean leftIsIdent = left.type() == TokenType.IDENTIFIER;
            boolean rightIsIdent = right.type() == TokenType.IDENTIFIER;

            if (leftIsIdent && rightIsIdent) {
                // Column-to-column comparison
                String resolvedLeft = resolveColumn(left.text());
                String resolvedRight = resolveColumn(right.text());
                return WherePredicate.columnComparison(resolvedLeft, op, resolvedRight);
            }

            if (leftIsIdent) {
                String resolvedLeft = resolveColumn(left.text());
                if (right.type() == TokenType.NULL) {
                    return op == ComparisonOperator.EQUALS
                            ? WherePredicate.isNull(resolvedLeft)
                            : WherePredicate.isNotNull(resolvedLeft);
                }
                return new WherePredicate.BinaryPredicate(resolvedLeft, op, right.value());
            }

            if (rightIsIdent) {
                String resolvedRight = resolveColumn(right.text());
                if (left.type() == TokenType.NULL) {
                    return op == ComparisonOperator.EQUALS
                            ? WherePredicate.isNull(resolvedRight)
                            : WherePredicate.isNotNull(resolvedRight);
                }
                // Flip operator for (literal OP column)
                ComparisonOperator flipped = flipOperator(op);
                return new WherePredicate.BinaryPredicate(resolvedRight, flipped, left.value());
            }

            throw new IllegalArgumentException("At least one operand in comparison must be a column identifier: " + left.text() + " " + op + " " + right.text());
        }

        private ComparisonOperator flipOperator(ComparisonOperator op) {
            return switch (op) {
                case EQUALS -> ComparisonOperator.EQUALS;
                case NOT_EQUALS -> ComparisonOperator.NOT_EQUALS;
                case GREATER_THAN -> ComparisonOperator.LESS_THAN;
                case GREATER_THAN_OR_EQUAL -> ComparisonOperator.LESS_THAN_OR_EQUAL;
                case LESS_THAN -> ComparisonOperator.GREATER_THAN;
                case LESS_THAN_OR_EQUAL -> ComparisonOperator.GREATER_THAN_OR_EQUAL;
                case LIKE -> ComparisonOperator.LIKE;
                case ILIKE -> ComparisonOperator.ILIKE;
            };
        }

        private String resolveColumn(String name) {
            if (columnResolver == null) {
                return name;
            }
            String resolved = columnResolver.apply(name);
            return (resolved != null && !resolved.isBlank()) ? resolved : name;
        }
    }
}
