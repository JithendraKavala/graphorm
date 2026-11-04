package com.graphorm.schema.parser;

import com.graphorm.schema.GraphOrmBaseVisitor;
import com.graphorm.schema.GraphOrmParser;
import com.graphorm.schema.ast.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ANTLR visitor converting the CST parse trees into strongly typed, immutable AST nodes.
 */
public class GraphOrmAstVisitor extends GraphOrmBaseVisitor<AstNode> {

    @Override
    public AstNode visitSchemaDocument(GraphOrmParser.SchemaDocumentContext ctx) {
        List<DatasourceNode> datasources = new ArrayList<>();
        List<GeneratorNode> generators = new ArrayList<>();
        List<ModelNode> models = new ArrayList<>();
        List<ViewModelNode> viewModels = new ArrayList<>();
        List<CompositeTypeNode> compositeTypes = new ArrayList<>();
        List<EnumNode> enums = new ArrayList<>();

        if (ctx.declaration() != null) {
            for (GraphOrmParser.DeclarationContext declCtx : ctx.declaration()) {
                if (declCtx.datasourceBlock() != null) {
                    datasources.add((DatasourceNode) visit(declCtx.datasourceBlock()));
                } else if (declCtx.generatorBlock() != null) {
                    generators.add((GeneratorNode) visit(declCtx.generatorBlock()));
                } else if (declCtx.modelBlock() != null) {
                    models.add((ModelNode) visit(declCtx.modelBlock()));
                } else if (declCtx.viewBlock() != null) {
                    viewModels.add((ViewModelNode) visit(declCtx.viewBlock()));
                } else if (declCtx.compositeTypeBlock() != null) {
                    compositeTypes.add((CompositeTypeNode) visit(declCtx.compositeTypeBlock()));
                } else if (declCtx.enumBlock() != null) {
                    enums.add((EnumNode) visit(declCtx.enumBlock()));
                }
            }
        }

        return new SchemaDocument(
                SourcePosition.from(ctx),
                datasources,
                generators,
                models,
                viewModels,
                compositeTypes,
                enums
        );
    }

    @Override
    public AstNode visitDatasourceBlock(GraphOrmParser.DatasourceBlockContext ctx) {
        String name = ctx.identifier().getText();
        Map<String, ExpressionNode> properties = new LinkedHashMap<>();
        if (ctx.configEntry() != null) {
            for (GraphOrmParser.ConfigEntryContext entry : ctx.configEntry()) {
                String key = entry.identifier().getText();
                ExpressionNode val = (ExpressionNode) visit(entry.value());
                properties.put(key, val);
            }
        }
        return new DatasourceNode(SourcePosition.from(ctx), name, properties);
    }

    @Override
    public AstNode visitGeneratorBlock(GraphOrmParser.GeneratorBlockContext ctx) {
        String name = ctx.identifier().getText();
        Map<String, ExpressionNode> properties = new LinkedHashMap<>();
        if (ctx.configEntry() != null) {
            for (GraphOrmParser.ConfigEntryContext entry : ctx.configEntry()) {
                String key = entry.identifier().getText();
                ExpressionNode val = (ExpressionNode) visit(entry.value());
                properties.put(key, val);
            }
        }
        return new GeneratorNode(SourcePosition.from(ctx), name, properties);
    }

    @Override
    public AstNode visitModelBlock(GraphOrmParser.ModelBlockContext ctx) {
        String name = ctx.identifier().getText();
        List<FieldNode> fields = new ArrayList<>();
        List<BlockAttributeNode> attributes = new ArrayList<>();

        if (ctx.modelElement() != null) {
            for (GraphOrmParser.ModelElementContext elem : ctx.modelElement()) {
                if (elem.fieldDeclaration() != null) {
                    fields.add((FieldNode) visit(elem.fieldDeclaration()));
                } else if (elem.blockAttribute() != null) {
                    attributes.add((BlockAttributeNode) visit(elem.blockAttribute()));
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
            for (GraphOrmParser.ViewElementContext elem : ctx.viewElement()) {
                if (elem.fieldDeclaration() != null) {
                    fields.add((FieldNode) visit(elem.fieldDeclaration()));
                } else if (elem.blockAttribute() != null) {
                    attributes.add((BlockAttributeNode) visit(elem.blockAttribute()));
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
            for (GraphOrmParser.CompositeTypeElementContext elem : ctx.compositeTypeElement()) {
                if (elem.fieldDeclaration() != null) {
                    fields.add((FieldNode) visit(elem.fieldDeclaration()));
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
            for (GraphOrmParser.EnumElementContext elem : ctx.enumElement()) {
                if (elem.enumValue() != null) {
                    values.add((EnumValueNode) visit(elem.enumValue()));
                } else if (elem.blockAttribute() != null) {
                    attributes.add((BlockAttributeNode) visit(elem.blockAttribute()));
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
            for (GraphOrmParser.FieldAttributeContext attr : ctx.fieldAttribute()) {
                attributes.add((FieldAttributeNode) visit(attr));
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
            for (GraphOrmParser.FieldAttributeContext attr : ctx.fieldAttribute()) {
                attributes.add((FieldAttributeNode) visit(attr));
            }
        }
        return new FieldNode(SourcePosition.from(ctx), name, type, attributes);
    }

    @Override
    public AstNode visitFieldType(GraphOrmParser.FieldTypeContext ctx) {
        String typeName = ctx.typeName().getText();
        boolean isOptional = ctx.optionalModifier() != null;
        boolean isList = ctx.listModifier() != null;
        return new TypeNode(SourcePosition.from(ctx), typeName, isOptional, isList);
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
        String paramName = ctx.identifier().getText();
        ExpressionNode val = (ExpressionNode) visit(ctx.value());
        return ArgumentNode.named(SourcePosition.from(ctx), paramName, val);
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
        String s = quoted.substring(1, quoted.length() - 1);
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
