package com.graphorm.schema.formatter;

import com.graphorm.schema.GraphOrmLexer;
import com.graphorm.schema.ast.*;
import com.graphorm.schema.parser.GraphOrmParserFacade;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Canonical, idempotent schema formatter for GraphORM schema DSL files.
 * Normalizes indentation (2 spaces), column alignments, directive formatting,
 * and preserves both line and block comments in their contextual positions.
 */
public final class SchemaFormatter {

    private SchemaFormatter() {}

    /**
     * Formats the given GraphORM schema source code into canonical form.
     *
     * @param source the raw schema source
     * @return canonical formatted schema source
     */
    public static String format(String source) {
        if (source == null || source.isBlank()) {
            return "";
        }

        // 1. Lex and extract all comment tokens
        GraphOrmLexer lexer = new GraphOrmLexer(CharStreams.fromString(source));
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        tokenStream.fill();

        List<Token> commentTokens = new ArrayList<>();
        for (Token t : tokenStream.getTokens()) {
            if (t.getType() == GraphOrmLexer.LINE_COMMENT || t.getType() == GraphOrmLexer.BLOCK_COMMENT) {
                commentTokens.add(t);
            }
        }

        // 2. Parse AST
        SchemaDocument doc = GraphOrmParserFacade.parse(source);

        // 3. Track emitted comments to prevent duplication
        CommentTracker comments = new CommentTracker(commentTokens);
        StringBuilder out = new StringBuilder();

        // Top-level declarations
        List<AstNode> declarations = new ArrayList<>();
        declarations.addAll(doc.datasources());
        declarations.addAll(doc.generators());
        declarations.addAll(doc.models());
        declarations.addAll(doc.viewModels());
        declarations.addAll(doc.compositeTypes());
        declarations.addAll(doc.enums());

        declarations.sort(Comparator.comparingInt(a -> a.position().line()));

        // Emit header comments before the first declaration
        int firstLine = declarations.isEmpty() ? Integer.MAX_VALUE : declarations.getFirst().position().line();
        List<Token> headerComments = comments.claimBeforeLine(firstLine);
        for (Token c : headerComments) {
            out.append(c.getText().trim()).append("\n");
        }
        if (!headerComments.isEmpty() && !declarations.isEmpty()) {
            out.append("\n");
        }

        for (int i = 0; i < declarations.size(); i++) {
            AstNode decl = declarations.get(i);
            if (i > 0) {
                out.append("\n");
            }

            // Comments before this declaration
            List<Token> declLeading = comments.claimBeforeLine(decl.position().line());
            for (Token c : declLeading) {
                out.append(c.getText().trim()).append("\n");
            }

            if (decl instanceof DatasourceNode ds) {
                formatDatasource(ds, comments, out);
            } else if (decl instanceof GeneratorNode gen) {
                formatGenerator(gen, comments, out);
            } else if (decl instanceof ModelNode model) {
                formatModel(model, comments, out);
            } else if (decl instanceof ViewModelNode view) {
                formatViewModel(view, comments, out);
            } else if (decl instanceof CompositeTypeNode ct) {
                formatCompositeType(ct, comments, out);
            } else if (decl instanceof EnumNode en) {
                formatEnum(en, comments, out);
            }
        }

        // Remaining comments at end of file
        List<Token> remaining = comments.claimAllRemaining();
        if (!remaining.isEmpty()) {
            out.append("\n");
            for (Token c : remaining) {
                out.append(c.getText().trim()).append("\n");
            }
        }

        // Ensure single trailing newline
        String result = out.toString();
        if (!result.endsWith("\n")) {
            result = result + "\n";
        }
        return result;
    }

    private static void formatDatasource(DatasourceNode ds, CommentTracker comments, StringBuilder out) {
        out.append("datasource ").append(ds.name()).append(" {\n");
        int maxKeyLen = ds.properties().keySet().stream().mapToInt(String::length).max().orElse(0);

        List<Map.Entry<String, ExpressionNode>> entries = new ArrayList<>(ds.properties().entrySet());
        for (Map.Entry<String, ExpressionNode> entry : entries) {
            int line = entry.getValue().position().line();
            for (Token c : comments.claimBeforeLine(line)) {
                out.append("  ").append(c.getText().trim()).append("\n");
            }
            out.append("  ").append(entry.getKey());
            out.append(" ".repeat(Math.max(1, maxKeyLen - entry.getKey().length() + 1)));
            out.append("= ").append(formatExpression(entry.getValue()));

            Token trailing = comments.claimOnLine(line);
            if (trailing != null) {
                out.append(" ").append(trailing.getText().trim());
            }
            out.append("\n");
        }
        out.append("}\n");
    }

    private static void formatGenerator(GeneratorNode gen, CommentTracker comments, StringBuilder out) {
        out.append("generator ").append(gen.name()).append(" {\n");
        int maxKeyLen = gen.properties().keySet().stream().mapToInt(String::length).max().orElse(0);

        List<Map.Entry<String, ExpressionNode>> entries = new ArrayList<>(gen.properties().entrySet());
        for (Map.Entry<String, ExpressionNode> entry : entries) {
            int line = entry.getValue().position().line();
            for (Token c : comments.claimBeforeLine(line)) {
                out.append("  ").append(c.getText().trim()).append("\n");
            }
            out.append("  ").append(entry.getKey());
            out.append(" ".repeat(Math.max(1, maxKeyLen - entry.getKey().length() + 1)));
            out.append("= ").append(formatExpression(entry.getValue()));

            Token trailing = comments.claimOnLine(line);
            if (trailing != null) {
                out.append(" ").append(trailing.getText().trim());
            }
            out.append("\n");
        }
        out.append("}\n");
    }

    private static void formatEnum(EnumNode en, CommentTracker comments, StringBuilder out) {
        out.append("enum ").append(en.name()).append(" {\n");
        for (EnumValueNode val : en.values()) {
            for (Token c : comments.claimBeforeLine(val.position().line())) {
                out.append("  ").append(c.getText().trim()).append("\n");
            }
            out.append("  ").append(val.name());
            Token trailing = comments.claimOnLine(val.position().line());
            if (trailing != null) {
                out.append(" ").append(trailing.getText().trim());
            }
            out.append("\n");
        }
        out.append("}\n");
    }

    private static void formatModel(ModelNode model, CommentTracker comments, StringBuilder out) {
        out.append("model ").append(model.name()).append(" {\n");
        formatFieldsAndBlockAttributes(model.fields(), model.attributes(), comments, out);
        out.append("}\n");
    }

    private static void formatViewModel(ViewModelNode view, CommentTracker comments, StringBuilder out) {
        out.append("view ").append(view.name()).append(" {\n");
        formatFieldsAndBlockAttributes(view.fields(), view.attributes(), comments, out);
        out.append("}\n");
    }

    private static void formatCompositeType(CompositeTypeNode ct, CommentTracker comments, StringBuilder out) {
        out.append("type ").append(ct.name()).append(" {\n");
        formatFieldsAndBlockAttributes(ct.fields(), List.of(), comments, out);
        out.append("}\n");
    }

    private static void formatFieldsAndBlockAttributes(
            List<FieldNode> fields,
            List<BlockAttributeNode> attributes,
            CommentTracker comments,
            StringBuilder out
    ) {
        int maxNameLen = fields.stream().mapToInt(f -> f.name().length()).max().orElse(0);
        int maxTypeLen = fields.stream().mapToInt(f -> formatType(f.type()).length()).max().orElse(0);

        boolean hasAttrsOnAnyField = fields.stream().anyMatch(f -> !f.attributes().isEmpty());

        for (FieldNode f : fields) {
            for (Token c : comments.claimBeforeLine(f.position().line())) {
                out.append("  ").append(c.getText().trim()).append("\n");
            }
            out.append("  ").append(f.name());
            String formattedType = formatType(f.type());

            if (hasAttrsOnAnyField || !f.attributes().isEmpty()) {
                out.append(" ".repeat(Math.max(1, maxNameLen - f.name().length() + 2)));
                out.append(formattedType);

                if (!f.attributes().isEmpty()) {
                    out.append(" ".repeat(Math.max(1, maxTypeLen - formattedType.length() + 2)));
                    String attrsStr = f.attributes().stream()
                            .map(SchemaFormatter::formatFieldAttribute)
                            .collect(Collectors.joining(" "));
                    out.append(attrsStr);
                }
            } else {
                out.append(" ".repeat(Math.max(1, maxNameLen - f.name().length() + 2)));
                out.append(formattedType);
            }

            Token trailing = comments.claimOnLine(f.position().line());
            if (trailing != null) {
                out.append(" ").append(trailing.getText().trim());
            }
            out.append("\n");
        }

        if (!attributes.isEmpty()) {
            if (!fields.isEmpty()) {
                out.append("\n");
            }
            for (BlockAttributeNode attr : attributes) {
                for (Token c : comments.claimBeforeLine(attr.position().line())) {
                    out.append("  ").append(c.getText().trim()).append("\n");
                }
                out.append("  ").append(formatBlockAttribute(attr));
                Token trailing = comments.claimOnLine(attr.position().line());
                if (trailing != null) {
                    out.append(" ").append(trailing.getText().trim());
                }
                out.append("\n");
            }
        }
    }

    public static String formatType(TypeNode type) {
        StringBuilder sb = new StringBuilder(type.name());
        if (type.isList()) {
            sb.append("[]");
        }
        if (type.isOptional()) {
            sb.append("?");
        }
        return sb.toString();
    }

    public static String formatFieldAttribute(FieldAttributeNode attr) {
        StringBuilder sb = new StringBuilder("@").append(attr.name());
        if (!attr.arguments().isEmpty()) {
            sb.append("(");
            sb.append(attr.arguments().stream().map(SchemaFormatter::formatArgument).collect(Collectors.joining(", ")));
            sb.append(")");
        }
        return sb.toString();
    }

    public static String formatBlockAttribute(BlockAttributeNode attr) {
        StringBuilder sb = new StringBuilder("@@").append(attr.name());
        if (!attr.arguments().isEmpty()) {
            sb.append("(");
            sb.append(attr.arguments().stream().map(SchemaFormatter::formatArgument).collect(Collectors.joining(", ")));
            sb.append(")");
        }
        return sb.toString();
    }

    public static String formatArgument(ArgumentNode arg) {
        if (arg.isNamed()) {
            return arg.name() + ": " + formatExpression(arg.value());
        }
        return formatExpression(arg.value());
    }

    public static String formatExpression(ExpressionNode expr) {
        if (expr instanceof StringLiteralNode str) {
            String val = str.value();
            if (val.contains("\n")) {
                return "\"\"\"\n" + val + "\n\"\"\"";
            }
            return "\"" + val.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        } else if (expr instanceof IntLiteralNode i) {
            return String.valueOf(i.value());
        } else if (expr instanceof FloatLiteralNode f) {
            return String.valueOf(f.value());
        } else if (expr instanceof BooleanLiteralNode b) {
            return b.value() ? "true" : "false";
        } else if (expr instanceof IdentifierNode id) {
            return id.name();
        } else if (expr instanceof QualifiedIdentifierNode qid) {
            return qid.asQualifiedString();
        } else if (expr instanceof ArrayLiteralNode arr) {
            return "[" + arr.elements().stream().map(SchemaFormatter::formatExpression).collect(Collectors.joining(", ")) + "]";
        } else if (expr instanceof FunctionCallNode func) {
            String args = func.arguments().stream().map(SchemaFormatter::formatArgument).collect(Collectors.joining(", "));
            return func.functionName() + "(" + args + ")";
        }
        return expr != null ? expr.toString() : "";
    }

    private static class CommentTracker {
        private final List<Token> tokens;
        private int index = 0;

        CommentTracker(List<Token> tokens) {
            this.tokens = new ArrayList<>(tokens);
        }

        List<Token> claimBeforeLine(int line) {
            List<Token> result = new ArrayList<>();
            while (index < tokens.size()) {
                Token t = tokens.get(index);
                if (t.getLine() < line) {
                    result.add(t);
                    index++;
                } else {
                    break;
                }
            }
            return result;
        }

        Token claimOnLine(int line) {
            if (index < tokens.size()) {
                Token t = tokens.get(index);
                if (t.getLine() == line) {
                    index++;
                    return t;
                }
            }
            return null;
        }

        List<Token> claimAllRemaining() {
            List<Token> result = new ArrayList<>();
            while (index < tokens.size()) {
                result.add(tokens.get(index));
                index++;
            }
            return result;
        }
    }
}
