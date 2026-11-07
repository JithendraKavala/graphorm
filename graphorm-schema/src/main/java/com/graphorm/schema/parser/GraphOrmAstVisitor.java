package com.graphorm.schema.parser;

import com.graphorm.schema.GraphOrmBaseVisitor;
import com.graphorm.schema.GraphOrmParser;
import com.graphorm.schema.ast.ArgumentNode;
import com.graphorm.schema.ast.ArrayLiteralNode;
import com.graphorm.schema.ast.AstNode;
import com.graphorm.schema.ast.BlockAttributeNode;
import com.graphorm.schema.ast.BooleanLiteralNode;
import com.graphorm.schema.ast.CompositeTypeNode;
import com.graphorm.schema.ast.DatasourceNode;
import com.graphorm.schema.ast.EnumNode;
import com.graphorm.schema.ast.EnumValueNode;
import com.graphorm.schema.ast.ExpressionNode;
import com.graphorm.schema.ast.FieldAttributeNode;
import com.graphorm.schema.ast.FieldNode;
import com.graphorm.schema.ast.FloatLiteralNode;
import com.graphorm.schema.ast.FunctionCallNode;
import com.graphorm.schema.ast.GeneratorNode;
import com.graphorm.schema.ast.IdentifierNode;
import com.graphorm.schema.ast.IntLiteralNode;
import com.graphorm.schema.ast.ModelNode;
import com.graphorm.schema.ast.QualifiedIdentifierNode;
import com.graphorm.schema.ast.SchemaDocument;
import com.graphorm.schema.ast.SourcePosition;
import com.graphorm.schema.ast.StringLiteralNode;
import com.graphorm.schema.ast.TypeNode;
import com.graphorm.schema.ast.ViewModelNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Concrete ANTLR AST Visitor constructing the immutable GraphORM AST hierarchy.
 */
public final class GraphOrmAstVisitor extends GraphOrmBaseVisitor<AstNode> {

    @Override
    public AstNode visitSchemaDocument(GraphOrmParser.SchemaDocumentContext ctx) {
        List<DatasourceNode> datasources = new ArrayList<>();
        List<GeneratorNode> generators = new ArrayList<>();
        List<ModelNode> models = new ArrayList<>();
        List<ViewModelNode> views = new ArrayList<>();
        List<CompositeTypeNode> compositeTypes = new ArrayList<>();
        List<EnumNode> enums = new ArrayList<>();

        if (ctx.declaration() != null) {
            for (GraphOrmParser.DeclarationContext declCtx : ctx.declaration()) {
                AstNode node = visit(declCtx);
                if (node instanceof DatasourceNode ds) {
                    datasources.add(ds);
                } else if (node instanceof GeneratorNode gen) {
                    generators.add(gen);
                } else if (node instanceof ModelNode model) {
                    models.add(model);
                } else if (node instanceof ViewModelNode view) {
                    views.add(view);
                } else if (node instanceof CompositeTypeNode ct) {
                    compositeTypes.add(ct);
                } else if (node instanceof EnumNode en) {
                    enums.add(en);
                }
            }
        }

        return new SchemaDocument(
                SourcePosition.from(ctx),
                datasources,
                generators,
                models,
                views,
                compositeTypes,
                enums
        );
    }

    @Override
    public AstNode visitDatasourceBlock(GraphOrmParser.DatasourceBlockContext ctx) {
        String name = ctx.identifier().getText();
        Map<String, ExpressionNode> configs = new HashMap<>();
        if (ctx.configEntry() != null) {
            for (GraphOrmParser.ConfigEntryContext entryCtx : ctx.configEntry()) {
                String key = entryCtx.identifier().getText();
                ExpressionNode val = (ExpressionNode) visit(entryCtx.value());
                configs.put(key, val);
            }
        }
        return new DatasourceNode(SourcePosition.from(ctx), name, configs);
    }

    @Override
    public AstNode visitGeneratorBlock(GraphOrmParser.GeneratorBlockContext ctx) {
        String name = ctx.identifier().getText();
        Map<String, ExpressionNode> configs = new HashMap<>();
        if (ctx.configEntry() != null) {
            for (GraphOrmParser.ConfigEntryContext entryCtx : ctx.configEntry()) {
                String key = entryCtx.identifier().getText();
                ExpressionNode val = (ExpressionNode) visit(entryCtx.value());
                configs.put(key, val);
            }
        }
        return new GeneratorNode(SourcePosition.from(ctx), name, configs);
    }

    @Override
    public AstNode visitModelBlock(GraphOrmParser.ModelBlockContext ctx) {
        String name = ctx.identifier().getText();
        List<FieldNode> fields = new ArrayList<>();
        List<BlockAttributeNode> attributes = new ArrayList<>();

        if (ctx.modelElement() != null) {
            for (GraphOrmParser.ModelElementContext elemCtx : ctx.modelElement()) {
                AstNode node = visit(elemCtx);
                if (node instanceof FieldNode field) {
                    fields.add(field);
                } else if (node instanceof BlockAttributeNode attr) {
                    attributes.add(attr);
                }
            }
        }

        return new ModelNode(SourcePosition.from(ctx), name, fields, attributes);
    }

    @Override
    public AstNode visitViewBlock(GraphOrmParser.ViewBlockContext ctx) {
        String name = ctx.identifier().getText();
        List<FieldNode> fields = new ArrayList<>();
        List<BlockAttributeNode> attributes = new ArrayList<>();

        if (ctx.viewElement() != null) {
            for (GraphOrmParser.ViewElementContext elemCtx : ctx.viewElement()) {
                AstNode node = visit(elemCtx);
                if (node instanceof FieldNode field) {
                    fields.add(field);
                } else if (node instanceof BlockAttributeNode attr) {
                    attributes.add(attr);
                }
            }
        }

        return new ViewModelNode(SourcePosition.from(ctx), name, fields, attributes);
    }

    @Override
    public AstNode visitCompositeTypeBlock(GraphOrmParser.CompositeTypeBlockContext ctx) {
        String name = ctx.identifier().getText();
        List<FieldNode> fields = new ArrayList<>();

        if (ctx.compositeTypeElement() != null) {
            for (GraphOrmParser.CompositeTypeElementContext elemCtx : ctx.compositeTypeElement()) {
                AstNode node = visit(elemCtx);
                if (node instanceof FieldNode field) {
                    fields.add(field);
                }
            }
        }

        return new CompositeTypeNode(SourcePosition.from(ctx), name, fields);
    }

    @Override
    public AstNode visitEnumBlock(GraphOrmParser.EnumBlockContext ctx) {
        String name = ctx.identifier().getText();
        List<EnumValueNode> values = new ArrayList<>();
        List<BlockAttributeNode> attributes = new ArrayList<>();

        if (ctx.enumElement() != null) {
            for (GraphOrmParser.EnumElementContext elemCtx : ctx.enumElement()) {
                AstNode node = visit(elemCtx);
                if (node instanceof EnumValueNode val) {
                    values.add(val);
                } else if (node instanceof BlockAttributeNode attr) {
                    attributes.add(attr);
                }
            }
        }

        return new EnumNode(SourcePosition.from(ctx), name, values, attributes);
    }

    @Override
    public AstNode visitEnumValue(GraphOrmParser.EnumValueContext ctx) {
        String name = ctx.identifier().getText();
        List<FieldAttributeNode> attributes = new ArrayList<>();
        if (ctx.fieldAttribute() != null) {
            for (GraphOrmParser.FieldAttributeContext attrCtx : ctx.fieldAttribute()) {
                attributes.add((FieldAttributeNode) visit(attrCtx));
            }
        }
        return new EnumValueNode(SourcePosition.from(ctx), name, attributes);
    }

    @Override
    public AstNode visitFieldDeclaration(GraphOrmParser.FieldDeclarationContext ctx) {
        String name = ctx.identifier().getText();
        TypeNode type = (TypeNode) visit(ctx.fieldType());
        List<FieldAttributeNode> attributes = new ArrayList<>();
        if (ctx.fieldAttribute() != null) {
            for (GraphOrmParser.FieldAttributeContext attrCtx : ctx.fieldAttribute()) {
                attributes.add((FieldAttributeNode) visit(attrCtx));
            }
        }
        return new FieldNode(SourcePosition.from(ctx), name, type, attributes);
    }

    @Override
    public AstNode visitFieldType(GraphOrmParser.FieldTypeContext ctx) {
        String typeName = ctx.typeName().getText();
        boolean optional = ctx.optionalModifier() != null;
        boolean list = ctx.listModifier() != null;
        return new TypeNode(SourcePosition.from(ctx), typeName, optional, list);
    }

    @Override
    public AstNode visitFieldAttribute(GraphOrmParser.FieldAttributeContext ctx) {
        String name = ctx.identifier().getText();
        List<ArgumentNode> args = new ArrayList<>();
        if (ctx.argumentList() != null && ctx.argumentList().argument() != null) {
            for (GraphOrmParser.ArgumentContext argCtx : ctx.argumentList().argument()) {
                args.add((ArgumentNode) visit(argCtx));
            }
        }
        return new FieldAttributeNode(SourcePosition.from(ctx), name, args);
    }

    @Override
    public AstNode visitBlockAttribute(GraphOrmParser.BlockAttributeContext ctx) {
        String name = ctx.identifier().getText();
        List<ArgumentNode> args = new ArrayList<>();
        if (ctx.argumentList() != null && ctx.argumentList().argument() != null) {
            for (GraphOrmParser.ArgumentContext argCtx : ctx.argumentList().argument()) {
                args.add((ArgumentNode) visit(argCtx));
            }
        }
        return new BlockAttributeNode(SourcePosition.from(ctx), name, args);
    }

    @Override
    public AstNode visitNamedArgument(GraphOrmParser.NamedArgumentContext ctx) {
        String name = ctx.identifier().getText();
        ExpressionNode val = (ExpressionNode) visit(ctx.value());
        return ArgumentNode.named(SourcePosition.from(ctx), name, val);
    }

    @Override
    public AstNode visitPositionalArgument(GraphOrmParser.PositionalArgumentContext ctx) {
        ExpressionNode val = (ExpressionNode) visit(ctx.value());
        return ArgumentNode.positional(SourcePosition.from(ctx), val);
    }

    @Override
    public AstNode visitStringLiteralValue(GraphOrmParser.StringLiteralValueContext ctx) {
        String raw = ctx.STRING_LITERAL().getText();
        String unescaped = unescapeString(raw);
        return new StringLiteralNode(SourcePosition.from(ctx), unescaped);
    }

    @Override
    public AstNode visitNumberLiteralValue(GraphOrmParser.NumberLiteralValueContext ctx) {
        GraphOrmParser.NumberLiteralContext numCtx = ctx.numberLiteral();
        String text = numCtx.getText();
        if (numCtx.FLOAT_LITERAL() != null) {
            double val = Double.parseDouble(text);
            return new FloatLiteralNode(SourcePosition.from(ctx), val);
        } else {
            long val = Long.parseLong(text);
            return new IntLiteralNode(SourcePosition.from(ctx), val);
        }
    }

    @Override
    public AstNode visitBooleanLiteralValue(GraphOrmParser.BooleanLiteralValueContext ctx) {
        boolean val = "true".equalsIgnoreCase(ctx.booleanLiteral().getText());
        return new BooleanLiteralNode(SourcePosition.from(ctx), val);
    }

    @Override
    public AstNode visitArrayLiteralValue(GraphOrmParser.ArrayLiteralValueContext ctx) {
        List<ExpressionNode> elements = new ArrayList<>();
        if (ctx.arrayLiteral().value() != null) {
            for (GraphOrmParser.ValueContext valCtx : ctx.arrayLiteral().value()) {
                elements.add((ExpressionNode) visit(valCtx));
            }
        }
        return new ArrayLiteralNode(SourcePosition.from(ctx), elements);
    }

    @Override
    public AstNode visitFunctionCallValue(GraphOrmParser.FunctionCallValueContext ctx) {
        String funcName = ctx.functionCall().identifier().getText();
        List<ArgumentNode> args = new ArrayList<>();
        if (ctx.functionCall().argumentList() != null && ctx.functionCall().argumentList().argument() != null) {
            for (GraphOrmParser.ArgumentContext argCtx : ctx.functionCall().argumentList().argument()) {
                args.add((ArgumentNode) visit(argCtx));
            }
        }
        return new FunctionCallNode(SourcePosition.from(ctx), funcName, args);
    }

    @Override
    public AstNode visitIdentifierValue(GraphOrmParser.IdentifierValueContext ctx) {
        List<GraphOrmParser.IdentifierContext> idParts = ctx.qualifiedIdentifier().identifier();
        if (idParts.size() == 1) {
            return new IdentifierNode(SourcePosition.from(ctx), idParts.get(0).getText());
        }
        List<String> parts = idParts.stream().map(GraphOrmParser.IdentifierContext::getText).toList();
        return new QualifiedIdentifierNode(SourcePosition.from(ctx), parts);
    }

    private static String unescapeString(String quoted) {
        if (quoted == null || quoted.length() < 2) {
            return "";
        }
        boolean isTriple = (quoted.startsWith("\"\"\"") && quoted.endsWith("\"\"\"") && quoted.length() >= 6)
                || (quoted.startsWith("'''") && quoted.endsWith("'''") && quoted.length() >= 6);

        String s;
        if (isTriple) {
            s = quoted.substring(3, quoted.length() - 3);
            s = s.stripIndent().trim();
            return s;
        } else {
            s = quoted.substring(1, quoted.length() - 1);
        }

        StringBuilder sb = new StringBuilder();
        boolean escape = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) {
                switch (c) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case '"' -> sb.append('"');
                    case '\'' -> sb.append('\'');
                    case '\\' -> sb.append('\\');
                    default -> sb.append(c);
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else {
                sb.append(c);
            }
        }
        if (escape) {
            sb.append('\\');
        }
        return sb.toString();
    }
}
