package com.graphorm.jdbc.dialect;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses and translates database-neutral schema expressions into dialect-specific SQL expressions,
 * or rejects unsupported constructs (e.g. named arguments or array literals in MySQL/SQLite) with clear errors.
 */
public final class SqlExpressionTranslator {

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
        } else if (expr instanceof RawToken tok) {
            return tok.text();
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
        } else if (expr instanceof RawToken tok) {
            return tok.text();
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

    sealed interface AstExpr permits StringLiteral, RawToken, Identifier, NamedArg, FunctionCall, ArrayLiteral {}

    record StringLiteral(String raw) implements AstExpr {}
    record RawToken(String text) implements AstExpr {}
    record Identifier(String name) implements AstExpr {}
    record NamedArg(String name, AstExpr value) implements AstExpr {}
    record FunctionCall(String name, List<AstExpr> arguments) implements AstExpr {}
    record ArrayLiteral(List<AstExpr> elements) implements AstExpr {}

    // --- Parser ---

    static AstExpr parse(String text) {
        Parser parser = new Parser(text.trim());
        return parser.parseExpression();
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

        AstExpr parseExpression() {
            skipWhitespace();
            if (pos >= src.length()) {
                return null;
            }

            char c = src.charAt(pos);

            // String literal
            if (c == '\'') {
                return parseStringLiteral();
            }

            // Array literal [...]
            if (c == '[') {
                return parseArrayLiteral();
            }

            // Identifier or function call or ARRAY[...]
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
                    pos++; // skip '('
                    List<AstExpr> args = new ArrayList<>();
                    skipWhitespace();
                    while (pos < src.length() && src.charAt(pos) != ')') {
                        AstExpr arg = parseArgument();
                        if (arg != null) {
                            args.add(arg);
                        }
                        skipWhitespace();
                        if (pos < src.length() && src.charAt(pos) == ',') {
                            pos++;
                            skipWhitespace();
                        }
                    }
                    if (pos < src.length() && src.charAt(pos) == ')') {
                        pos++; // skip ')'
                    }
                    return new FunctionCall(name, args);
                }

                return new Identifier(name);
            }

            // Number or raw token (e.g. 42, -5, 9.99, etc.)
            int start = pos;
            while (pos < src.length() && !Character.isWhitespace(src.charAt(pos))
                    && src.charAt(pos) != ',' && src.charAt(pos) != ')' && src.charAt(pos) != ']') {
                pos++;
            }
            String tok = src.substring(start, pos);
            return new RawToken(tok);
        }

        private AstExpr parseArgument() {
            skipWhitespace();
            if (pos >= src.length()) {
                return null;
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
                        pos++; // skip ':'
                        skipWhitespace();
                        AstExpr val = parseExpression();
                        return new NamedArg(name, val);
                    } else if (src.charAt(pos) == '=' && pos + 1 < src.length() && src.charAt(pos + 1) == '>') {
                        // Found name => value
                        pos += 2; // skip '=>'
                        skipWhitespace();
                        AstExpr val = parseExpression();
                        return new NamedArg(name, val);
                    }
                }

                // Not a named argument, restore position
                pos = savedPos;
            }

            return parseExpression();
        }

        private AstExpr parseStringLiteral() {
            int start = pos;
            pos++; // skip opening '\''
            while (pos < src.length()) {
                if (src.charAt(pos) == '\'') {
                    if (pos + 1 < src.length() && src.charAt(pos + 1) == '\'') {
                        pos += 2; // escaped quote ''
                    } else {
                        pos++; // closing quote
                        break;
                    }
                } else {
                    pos++;
                }
            }
            return new StringLiteral(src.substring(start, pos));
        }

        private AstExpr parseArrayLiteral() {
            pos++; // skip '['
            List<AstExpr> elements = new ArrayList<>();
            skipWhitespace();
            while (pos < src.length() && src.charAt(pos) != ']') {
                AstExpr elem = parseExpression();
                if (elem != null) {
                    elements.add(elem);
                }
                skipWhitespace();
                if (pos < src.length() && src.charAt(pos) == ',') {
                    pos++;
                    skipWhitespace();
                }
            }
            if (pos < src.length() && src.charAt(pos) == ']') {
                pos++; // skip ']'
            }
            return new ArrayLiteral(elements);
        }
    }
}
