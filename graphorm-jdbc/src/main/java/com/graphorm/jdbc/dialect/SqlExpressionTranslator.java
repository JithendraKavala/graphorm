package com.graphorm.jdbc.dialect;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parses and translates database-neutral schema expressions into dialect-specific SQL expressions,
 * requiring complete input consumption, validating syntax safety, guaranteeing parser progress,
 * and rejecting unsupported constructs with clear errors.
 */
public final class SqlExpressionTranslator {

    private static final Pattern NUMERIC_PATTERN =
            Pattern.compile("^-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?$");

    private SqlExpressionTranslator() {
    }

    public static String translateToPostgreSql(String expression) {
        if (expression == null || expression.isBlank()) {
            return expression;
        }
        AstExpr ast = parse(expression);
        return renderPostgreSql(ast);
    }

    public static String translateToMySql(String expression) {
        if (expression == null || expression.isBlank()) {
            return expression;
        }
        AstExpr ast = parse(expression);
        validateSupportedInMySql(ast, expression);
        return renderStandard(ast);
    }

    public static String translateToSqlite(String expression) {
        if (expression == null || expression.isBlank()) {
            return expression;
        }
        AstExpr ast = parse(expression);
        validateSupportedInSqlite(ast, expression);
        return renderStandard(ast);
    }

    private static void validateSupportedInMySql(AstExpr expr, String original) {
        if (expr instanceof NamedArg) {
            throw new UnsupportedOperationException(
                    "MySQL dialect does not support named arguments in default expressions: " + original);
        } else if (expr instanceof ArrayLiteral) {
            throw new UnsupportedOperationException(
                    "MySQL dialect does not support array literal expressions in column defaults: " + original);
        } else if (expr instanceof FunctionCall func) {
            for (AstExpr arg : func.arguments()) {
                validateSupportedInMySql(arg, original);
            }
        }
    }

    private static void validateSupportedInSqlite(AstExpr expr, String original) {
        if (expr instanceof NamedArg) {
            throw new UnsupportedOperationException(
                    "SQLite dialect does not support named arguments in default expressions: " + original);
        } else if (expr instanceof ArrayLiteral) {
            throw new UnsupportedOperationException(
                    "SQLite dialect does not support array literal expressions in column defaults: " + original);
        } else if (expr instanceof FunctionCall func) {
            for (AstExpr arg : func.arguments()) {
                validateSupportedInSqlite(arg, original);
            }
        }
    }

    private static String renderPostgreSql(AstExpr expr) {
        if (expr instanceof StringLiteral str) {
            return str.raw();
        } else if (expr instanceof NumericLiteral num) {
            return num.text();
        } else if (expr instanceof BooleanLiteral bool) {
            return bool.value() ? "TRUE" : "FALSE";
        } else if (expr instanceof NullLiteral) {
            return "NULL";
        } else if (expr instanceof Identifier ident) {
            return ident.name();
        } else if (expr instanceof NamedArg named) {
            return named.name() + " => " + renderPostgreSql(named.value());
        } else if (expr instanceof ArrayLiteral arr) {
            StringBuilder sb = new StringBuilder("ARRAY[");
            for (int i = 0; i < arr.elements().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(renderPostgreSql(arr.elements().get(i)));
            }
            sb.append("]");
            return sb.toString();
        } else if (expr instanceof FunctionCall func) {
            StringBuilder sb = new StringBuilder(func.name()).append("(");
            for (int i = 0; i < func.arguments().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(renderPostgreSql(func.arguments().get(i)));
            }
            sb.append(")");
            return sb.toString();
        }
        return expr != null ? expr.toString() : "";
    }

    private static String renderStandard(AstExpr expr) {
        if (expr instanceof StringLiteral str) {
            return str.raw();
        } else if (expr instanceof NumericLiteral num) {
            return num.text();
        } else if (expr instanceof BooleanLiteral bool) {
            return bool.value() ? "TRUE" : "FALSE";
        } else if (expr instanceof NullLiteral) {
            return "NULL";
        } else if (expr instanceof Identifier ident) {
            return ident.name();
        } else if (expr instanceof FunctionCall func) {
            StringBuilder sb = new StringBuilder(func.name()).append("(");
            for (int i = 0; i < func.arguments().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(renderStandard(func.arguments().get(i)));
            }
            sb.append(")");
            return sb.toString();
        }
        return expr != null ? expr.toString() : "";
    }

    // --- AST representation ---

    sealed interface AstExpr permits StringLiteral, NumericLiteral, BooleanLiteral, NullLiteral, Identifier, NamedArg, FunctionCall, ArrayLiteral {}

    record StringLiteral(String raw) implements AstExpr {}
    record NumericLiteral(String text) implements AstExpr {}
    record BooleanLiteral(boolean value) implements AstExpr {}
    record NullLiteral() implements AstExpr {}
    record Identifier(String name) implements AstExpr {}
    record NamedArg(String name, AstExpr value) implements AstExpr {}
    record FunctionCall(String name, List<AstExpr> arguments) implements AstExpr {}
    record ArrayLiteral(List<AstExpr> elements) implements AstExpr {}

    // --- Parser ---

    static AstExpr parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Expression must not be null or blank");
        }
        Parser parser = new Parser(text.trim());
        AstExpr result = parser.parseRoot();
        return result;
    }

    private static final class Parser {
        private final String src;
        private int pos = 0;

        Parser(String src) {
            this.src = src;
        }

        private void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        AstExpr parseRoot() {
            AstExpr expr = parseExpression();
            skipWhitespace();
            if (pos < src.length()) {
                throw new IllegalArgumentException("Unexpected trailing token at index " + pos + ": '"
                        + src.substring(pos) + "' in expression: " + src);
            }
            return expr;
        }

        AstExpr parseExpression() {
            skipWhitespace();
            if (pos >= src.length()) {
                throw new IllegalArgumentException("Unexpected end of input, expected expression in: " + src);
            }

            char c = src.charAt(pos);

            // String literal '...'
            if (c == '\'') {
                return parseStringLiteral();
            }

            // Array literal [...]
            if (c == '[') {
                return parseArrayLiteral();
            }

            // Identifier, FunctionCall, or ArrayLiteral (ARRAY[...])
            if (Character.isLetter(c) || c == '_') {
                int start = pos;
                while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_' || src.charAt(pos) == '.')) {
                    pos++;
                }
                String name = src.substring(start, pos);
                skipWhitespace();

                // Check for ARRAY[...]
                if ("ARRAY".equalsIgnoreCase(name) && pos < src.length() && src.charAt(pos) == '[') {
                    return parseArrayLiteral();
                }

                // Check for Function Call name(...)
                if (pos < src.length() && src.charAt(pos) == '(') {
                    pos++; // consume '('
                    List<AstExpr> args = new ArrayList<>();
                    skipWhitespace();
                    boolean expectArg = false;

                    while (pos < src.length() && src.charAt(pos) != ')') {
                        AstExpr arg = parseArgument();
                        args.add(arg);
                        skipWhitespace();

                        if (pos < src.length() && src.charAt(pos) == ',') {
                            pos++; // consume ','
                            skipWhitespace();
                            expectArg = true;
                            if (pos < src.length() && src.charAt(pos) == ')') {
                                throw new IllegalArgumentException("Unexpected trailing comma in function argument list: " + src);
                            }
                        } else if (pos < src.length() && src.charAt(pos) != ')') {
                            throw new IllegalArgumentException("Expected ',' or ')' after argument in function call: " + src);
                        } else {
                            expectArg = false;
                        }
                    }

                    if (pos >= src.length() || src.charAt(pos) != ')') {
                        throw new IllegalArgumentException("Unclosed parenthesis in function call '" + name + "': " + src);
                    }
                    if (expectArg) {
                        throw new IllegalArgumentException("Missing argument after comma in function call '" + name + "': " + src);
                    }
                    pos++; // consume ')'
                    return new FunctionCall(name, args);
                }

                // Keywords: TRUE, FALSE, NULL
                if ("TRUE".equalsIgnoreCase(name)) {
                    return new BooleanLiteral(true);
                }
                if ("FALSE".equalsIgnoreCase(name)) {
                    return new BooleanLiteral(false);
                }
                if ("NULL".equalsIgnoreCase(name)) {
                    return new NullLiteral();
                }

                return new Identifier(name);
            }

            // Numeric literal: 42, -5, 9.99, 1.5e-3, etc.
            if (Character.isDigit(c) || (c == '-' && pos + 1 < src.length() && Character.isDigit(src.charAt(pos + 1)))) {
                int start = pos;
                if (c == '-') {
                    pos++;
                }
                while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '.' || src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                    // Check if '+' or '-' is preceded by 'e' or 'E'
                    if ((src.charAt(pos) == '+' || src.charAt(pos) == '-') && pos > start) {
                        char prev = src.charAt(pos - 1);
                        if (prev != 'e' && prev != 'E') {
                            break;
                        }
                    }
                    pos++;
                }
                String numStr = src.substring(start, pos);
                if (!NUMERIC_PATTERN.matcher(numStr).matches()) {
                    throw new IllegalArgumentException("Malformed numeric literal '" + numStr + "' in: " + src);
                }
                return new NumericLiteral(numStr);
            }

            throw new IllegalArgumentException("Unexpected character '" + c + "' at position " + pos + " in expression: " + src);
        }

        private AstExpr parseArgument() {
            skipWhitespace();
            if (pos >= src.length()) {
                throw new IllegalArgumentException("Unexpected end of input, expected argument in: " + src);
            }

            // Check if this is a named argument: <name> : <val> or <name> => <val>
            char c = src.charAt(pos);
            if (Character.isLetter(c) || c == '_') {
                int savedPos = pos;
                int start = pos;
                while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) {
                    pos++;
                }
                String name = src.substring(start, pos);
                skipWhitespace();

                if (pos < src.length()) {
                    if (src.charAt(pos) == ':' && (pos + 1 >= src.length() || src.charAt(pos + 1) != ':')) {
                        // Found name: value
                        pos++; // consume ':'
                        skipWhitespace();
                        if (pos >= src.length() || src.charAt(pos) == ',' || src.charAt(pos) == ')') {
                            throw new IllegalArgumentException("Missing value for named argument '" + name + "' in: " + src);
                        }
                        AstExpr val = parseExpression();
                        return new NamedArg(name, val);
                    } else if (src.charAt(pos) == '=' && pos + 1 < src.length() && src.charAt(pos + 1) == '>') {
                        // Found name => value
                        pos += 2; // consume '=>'
                        skipWhitespace();
                        if (pos >= src.length() || src.charAt(pos) == ',' || src.charAt(pos) == ')') {
                            throw new IllegalArgumentException("Missing value for named argument '" + name + "' in: " + src);
                        }
                        AstExpr val = parseExpression();
                        return new NamedArg(name, val);
                    }
                }

                // Not a named argument, backtrack
                pos = savedPos;
            }

            return parseExpression();
        }

        private AstExpr parseStringLiteral() {
            int start = pos;
            pos++; // consume opening '\''
            boolean closed = false;
            while (pos < src.length()) {
                if (src.charAt(pos) == '\'') {
                    if (pos + 1 < src.length() && src.charAt(pos + 1) == '\'') {
                        pos += 2; // escaped quote ''
                    } else {
                        pos++; // consume closing quote
                        closed = true;
                        break;
                    }
                } else {
                    pos++;
                }
            }
            if (!closed) {
                throw new IllegalArgumentException("Unclosed string literal starting at position " + start + " in: " + src);
            }
            return new StringLiteral(src.substring(start, pos));
        }

        private AstExpr parseArrayLiteral() {
            int start = pos;
            pos++; // consume '['
            List<AstExpr> elements = new ArrayList<>();
            skipWhitespace();
            boolean expectElem = false;

            while (pos < src.length() && src.charAt(pos) != ']') {
                AstExpr elem = parseExpression();
                elements.add(elem);
                skipWhitespace();

                if (pos < src.length() && src.charAt(pos) == ',') {
                    pos++; // consume ','
                    skipWhitespace();
                    expectElem = true;
                    if (pos < src.length() && src.charAt(pos) == ']') {
                        throw new IllegalArgumentException("Unexpected trailing comma in array literal: " + src);
                    }
                } else if (pos < src.length() && src.charAt(pos) != ']') {
                    throw new IllegalArgumentException("Expected ',' or ']' after element in array literal: " + src);
                } else {
                    expectElem = false;
                }
            }

            if (pos >= src.length() || src.charAt(pos) != ']') {
                throw new IllegalArgumentException("Unclosed array literal starting at position " + start + " in: " + src);
            }
            if (expectElem) {
                throw new IllegalArgumentException("Missing element after comma in array literal: " + src);
            }
            pos++; // consume ']'
            return new ArrayLiteral(elements);
        }
    }
}
