package com.graphorm.schema.transformer;

import com.graphorm.core.metadata.*;
import com.graphorm.core.query.JoinType;
import com.graphorm.schema.ast.*;
import com.graphorm.schema.parser.GraphOrmParserFacade;
import com.graphorm.schema.validation.SchemaValidator;
import com.graphorm.schema.validation.ValidationResult;

import java.time.Instant;
import java.util.*;

/**
 * Transforms a parsed and validated {@link SchemaDocument} into a runtime immutable {@link ModelRegistry}.
 */
public final class SchemaToMetadataTransformer {

    private static final Map<String, Class<?>> TYPE_MAPPINGS = Map.of(
            "Int", Integer.class,
            "BigInt", Long.class,
            "String", String.class,
            "Boolean", Boolean.class,
            "Float", Double.class,
            "DateTime", Instant.class,
            "Json", String.class,
            "Bytes", byte[].class
    );

    private SchemaToMetadataTransformer() {}

    /**
     * Parses, validates, and transforms raw schema DSL text into an immutable {@link ModelRegistry}.
     *
     * @param source schema source text
     * @return populated, immutable ModelRegistry
     */
    public static ModelRegistry transform(String source) {
        SchemaDocument document = GraphOrmParserFacade.parse(source);
        ValidationResult validation = SchemaValidator.validate(document);
        validation.throwIfInvalid(source);
        return transform(document);
    }

    /**
     * Transforms an already validated {@link SchemaDocument} AST into an immutable {@link ModelRegistry}.
     *
     * @param document validated schema document
     * @return populated, immutable ModelRegistry
     */
    public static ModelRegistry transform(SchemaDocument document) {
        Objects.requireNonNull(document, "SchemaDocument must not be null");

        ModelRegistry.Builder builder = ModelRegistry.builder();

        // 1. Transform Enums
        for (EnumNode enumNode : document.enums()) {
            List<String> values = enumNode.values().stream().map(EnumValueNode::name).toList();
            builder.addEnum(EnumMetadata.builder(enumNode.name()).values(values).build());
        }

        // 2. Transform Composite Types
        for (CompositeTypeNode ct : document.compositeTypes()) {
            List<FieldMetadata> fields = ct.fields().stream()
                    .map(f -> transformField(f, false, Set.of()))
                    .toList();
            builder.addCompositeType(CompositeTypeMetadata.builder(ct.name()).fields(fields).build());
        }

        // 3. Transform Regular Models
        for (ModelNode modelNode : document.models()) {
            builder.addModel(transformModel(modelNode, document));
        }

        // 4. Transform View Models
        for (ViewModelNode vmNode : document.viewModels()) {
            builder.addViewModel(transformViewModel(vmNode));
        }

        return builder.build();
    }

    private static ModelMetadata transformModel(ModelNode modelNode, SchemaDocument document) {
        String tableName = modelNode.mapAttribute()
                .flatMap(attr -> attr.getPositionalArgumentValue(0).or(() -> attr.getArgumentValue("name")))
                .map(SchemaToMetadataTransformer::extractStringValue)
                .orElse(modelNode.name().toLowerCase());

        // Determine primary key fields
        Set<String> pkFieldSet = new LinkedHashSet<>();
        for (FieldNode f : modelNode.primaryKeyFields()) {
            pkFieldSet.add(f.name());
        }
        Optional<BlockAttributeNode> pkBlock = modelNode.primaryKeyBlockAttribute();
        if (pkBlock.isPresent()) {
            Optional<ExpressionNode> argVal = pkBlock.get().getPositionalArgumentValue(0);
            if (argVal.isEmpty()) {
                argVal = pkBlock.get().getArgumentValue("fields");
            }
            if (argVal.isPresent() && argVal.get() instanceof ArrayLiteralNode arr) {
                for (ExpressionNode elem : arr.elements()) {
                    String name = extractStringValue(elem);
                    if (name != null) {
                        pkFieldSet.add(name);
                    }
                }
            }
        }

        // Transform fields
        List<FieldMetadata> fields = new ArrayList<>();
        for (FieldNode f : modelNode.fields()) {
            boolean isRelation = document.containsModel(f.type().name());
            FieldMetadata fm = transformField(f, isRelation, pkFieldSet);
            fields.add(fm);
        }

        // Unique constraints
        List<List<String>> uniqueConstraints = new ArrayList<>();
        for (FieldNode f : modelNode.fields()) {
            if (f.isUnique()) {
                uniqueConstraints.add(List.of(f.name()));
            }
        }
        for (BlockAttributeNode uqBlock : modelNode.uniqueBlockAttributes()) {
            Optional<ExpressionNode> argVal = uqBlock.getPositionalArgumentValue(0);
            if (argVal.isEmpty()) {
                argVal = uqBlock.getArgumentValue("fields");
            }
            if (argVal.isPresent() && argVal.get() instanceof ArrayLiteralNode arr) {
                List<String> uqCols = new ArrayList<>();
                for (ExpressionNode elem : arr.elements()) {
                    String name = extractStringValue(elem);
                    if (name != null) {
                        uqCols.add(name);
                    }
                }
                if (!uqCols.isEmpty()) {
                    uniqueConstraints.add(uqCols);
                }
            }
        }

        // Indexes
        List<IndexMetadata> indexes = new ArrayList<>();
        for (FieldNode f : modelNode.fields()) {
            if (f.hasAttribute("index")) {
                String idxName = "idx_" + tableName + "_" + f.name();
                indexes.add(IndexMetadata.builder(idxName)
                        .addField(f.name())
                        .build());
            }
        }
        for (BlockAttributeNode idxBlock : modelNode.indexBlockAttributes()) {
            Optional<ExpressionNode> fieldsArg = idxBlock.getArgumentValue("fields")
                    .or(() -> idxBlock.getPositionalArgumentValue(0));
            List<String> idxFields = new ArrayList<>();
            if (fieldsArg.isPresent()) {
                idxFields.addAll(extractStringList(fieldsArg.get()));
            }
            if (!idxFields.isEmpty()) {
                String idxName = idxBlock.getArgumentValue("name")
                        .map(SchemaToMetadataTransformer::extractStringValue)
                        .orElseGet(() -> "idx_" + tableName + "_" + String.join("_", idxFields));

                boolean isUnique = idxBlock.getArgumentValue("unique")
                        .map(expr -> "true".equalsIgnoreCase(extractStringValue(expr)))
                        .orElse(false);

                IndexType indexType = idxBlock.getArgumentValue("type")
                        .map(SchemaToMetadataTransformer::extractStringValue)
                        .map(IndexType::from)
                        .orElse(IndexType.BTREE);

                indexes.add(IndexMetadata.builder(idxName)
                        .fields(idxFields)
                        .unique(isUnique)
                        .type(indexType)
                        .build());
            }
        }

        // Check constraints
        List<CheckConstraintMetadata> checkConstraints = new ArrayList<>();
        int chkCounter = 1;
        for (BlockAttributeNode chkBlock : modelNode.checkBlockAttributes()) {
            Optional<ExpressionNode> exprArg = chkBlock.getPositionalArgumentValue(0)
                    .or(() -> chkBlock.getArgumentValue("expression"))
                    .or(() -> chkBlock.getArgumentValue("expr"));
            if (exprArg.isPresent()) {
                String expr = extractStringValue(exprArg.get());
                String chkName = chkBlock.getArgumentValue("name")
                        .map(SchemaToMetadataTransformer::extractStringValue)
                        .orElse("chk_" + tableName + "_" + (chkCounter++));
                if (expr != null && !expr.isBlank()) {
                    checkConstraints.add(new CheckConstraintMetadata(chkName, expr));
                }
            }
        }

        // Schema namespace
        String schemaName = modelNode.schemaAttribute()
                .flatMap(attr -> attr.getPositionalArgumentValue(0).or(() -> attr.getArgumentValue("name")))
                .map(SchemaToMetadataTransformer::extractStringValue)
                .orElse(null);

        // Relations
        List<RelationMetadata> relations = new ArrayList<>();
        for (FieldNode f : modelNode.fields()) {
            String targetTypeName = f.type().name();
            if (document.containsModel(targetTypeName)) {
                RelationMetadata rel = transformRelation(modelNode, f, document);
                relations.add(rel);
            }
        }

        return ModelMetadata.builder(modelNode.name())
                .tableName(tableName)
                .fields(fields)
                .primaryKeyFields(new ArrayList<>(pkFieldSet))
                .uniqueConstraints(uniqueConstraints)
                .relations(relations)
                .readOnly(modelNode.isReadOnly())
                .authRoles(modelNode.authRoles())
                .indexes(indexes)
                .checkConstraints(checkConstraints)
                .schemaName(schemaName)
                .build();
    }

    private static RelationMetadata transformRelation(ModelNode model, FieldNode field, SchemaDocument document) {
        String targetModelName = field.type().name();
        Optional<FieldAttributeNode> relationAttr = field.relationAttribute();

        String relName = field.name();
        List<String> fkFields = List.of();
        List<String> refFields = List.of();
        ReferentialAction onDelete = ReferentialAction.NO_ACTION;
        ReferentialAction onUpdate = ReferentialAction.NO_ACTION;

        if (relationAttr.isPresent()) {
            FieldAttributeNode attr = relationAttr.get();
            Optional<ExpressionNode> nameArg = attr.getArgumentValue("name");
            if (nameArg.isEmpty()) {
                nameArg = attr.getPositionalArgumentValue(0).filter(v -> v instanceof StringLiteralNode);
            }
            if (nameArg.isPresent()) {
                relName = extractStringValue(nameArg.get());
            }

            Optional<ExpressionNode> fieldsArg = attr.getArgumentValue("fields");
            if (fieldsArg.isPresent()) {
                fkFields = extractStringList(fieldsArg.get());
            }

            Optional<ExpressionNode> referencesArg = attr.getArgumentValue("references");
            if (referencesArg.isPresent()) {
                refFields = extractStringList(referencesArg.get());
            }

            Optional<ExpressionNode> onDeleteArg = attr.getArgumentValue("onDelete");
            if (onDeleteArg.isPresent()) {
                onDelete = ReferentialAction.from(extractStringValue(onDeleteArg.get()));
            }

            Optional<ExpressionNode> onUpdateArg = attr.getArgumentValue("onUpdate");
            if (onUpdateArg.isPresent()) {
                onUpdate = ReferentialAction.from(extractStringValue(onUpdateArg.get()));
            }
        }

        // Determine relation type
        RelationType relationType = determineRelationType(field, targetModelName, document);

        return RelationMetadata.builder(relName)
                .relationType(relationType)
                .sourceModel(model.name())
                .targetModel(targetModelName)
                .foreignKeyFields(fkFields)
                .referenceFields(refFields)
                .onDelete(onDelete)
                .onUpdate(onUpdate)
                .build();
    }

    private static RelationType determineRelationType(FieldNode field, String targetModelName, SchemaDocument document) {
        boolean sourceIsList = field.isList();
        boolean targetIsList = false;

        Optional<ModelNode> targetModel = document.findModel(targetModelName);
        if (targetModel.isPresent()) {
            for (FieldNode tf : targetModel.get().fields()) {
                if (tf.type().name().equals(document.models().stream().filter(m -> m.fields().contains(field)).map(ModelNode::name).findFirst().orElse(""))) {
                    targetIsList = tf.isList();
                    break;
                }
            }
        }

        if (sourceIsList && targetIsList) {
            return RelationType.MANY_TO_MANY;
        } else if (sourceIsList) {
            return RelationType.ONE_TO_MANY;
        } else if (targetIsList) {
            return RelationType.MANY_TO_ONE;
        } else {
            return RelationType.ONE_TO_ONE;
        }
    }

    private static ViewModelMetadata transformViewModel(ViewModelNode vmNode) {
        String viewName = vmNode.mapAttribute()
                .flatMap(attr -> attr.getPositionalArgumentValue(0).or(() -> attr.getArgumentValue("name")))
                .map(SchemaToMetadataTransformer::extractStringValue)
                .orElse(vmNode.name().toLowerCase());

        boolean isDbBacked = vmNode.isDatabaseBacked();

        String sourceModel = null;
        String sourceAlias = null;
        Optional<BlockAttributeNode> fromAttr = vmNode.fromAttribute();
        if (fromAttr.isPresent()) {
            BlockAttributeNode from = fromAttr.get();
            sourceModel = from.getPositionalArgumentValue(0)
                    .or(() -> from.getArgumentValue("model"))
                    .map(SchemaToMetadataTransformer::extractStringValue)
                    .orElse(null);
            sourceAlias = from.getArgumentValue("as")
                    .or(() -> from.getPositionalArgumentValue(1))
                    .map(SchemaToMetadataTransformer::extractStringValue)
                    .orElse(null);
        }

        List<ViewJoinMetadata> joins = new ArrayList<>();
        for (BlockAttributeNode joinAttr : vmNode.joinAttributes()) {
            JoinType joinType = "innerJoin".equals(joinAttr.name()) ? JoinType.INNER : JoinType.LEFT;
            String target = joinAttr.getPositionalArgumentValue(0)
                    .or(() -> joinAttr.getArgumentValue("model"))
                    .map(SchemaToMetadataTransformer::extractStringValue)
                    .orElse("");
            String alias = joinAttr.getArgumentValue("as")
                    .or(() -> joinAttr.getPositionalArgumentValue(1))
                    .map(SchemaToMetadataTransformer::extractStringValue)
                    .orElse("");
            String on = joinAttr.getArgumentValue("on")
                    .or(() -> joinAttr.getPositionalArgumentValue(2))
                    .map(SchemaToMetadataTransformer::extractStringValue)
                    .orElse("");

            joins.add(ViewJoinMetadata.builder()
                    .joinType(joinType)
                    .targetModel(target)
                    .alias(alias)
                    .onCondition(on)
                    .build());
        }

        List<String> groupBy = new ArrayList<>();
        Optional<GroupByAttributeNode> gbClause = vmNode.groupByClause();
        if (gbClause.isPresent()) {
            groupBy.addAll(gbClause.get().columns());
        } else {
            Optional<BlockAttributeNode> groupByAttr = vmNode.groupByAttribute();
            if (groupByAttr.isPresent()) {
                Optional<ExpressionNode> argVal = groupByAttr.get().getPositionalArgumentValue(0);
                if (argVal.isEmpty()) {
                    argVal = groupByAttr.get().getArgumentValue("fields");
                }
                if (argVal.isPresent()) {
                    groupBy.addAll(extractStringList(argVal.get()));
                }
            }
        }

        List<FieldMetadata> fields = vmNode.fields().stream()
                .map(f -> transformField(f, false, Set.of()))
                .toList();

        return ViewModelMetadata.builder(vmNode.name())
                .viewName(viewName)
                .fields(fields)
                .sourceModel(sourceModel, sourceAlias)
                .joins(joins)
                .groupBy(groupBy)
                .databaseBacked(isDbBacked)
                .rawQuery(vmNode.queryClause().orElse(null))
                .authRoles(vmNode.authRoles())
                .build();
    }

    private static FieldMetadata transformField(FieldNode field, boolean isRelation, Set<String> pkFieldSet) {
        String name = field.name();
        String typeName = field.type().name();
        Class<?> javaType = TYPE_MAPPINGS.get(typeName);

        boolean isPk = pkFieldSet.contains(name) || field.isPrimaryKey();
        boolean isNullable = field.isOptional();
        boolean isUnique = field.isUnique();

        String dbCol = field.mapAttribute()
                .flatMap(attr -> attr.getPositionalArgumentValue(0).or(() -> attr.getArgumentValue("name")))
                .map(SchemaToMetadataTransformer::extractStringValue)
                .orElse(name);

        String defaultValue = null;
        boolean isGenerated = false;
        Optional<FieldAttributeNode> defaultAttr = field.defaultAttribute();
        if (defaultAttr.isPresent()) {
            Optional<ExpressionNode> valOpt = defaultAttr.get().getPositionalArgumentValue(0);
            if (valOpt.isPresent()) {
                ExpressionNode valNode = valOpt.get();
                if (valNode instanceof FunctionCallNode func) {
                    defaultValue = func.functionName() + "()";
                    if ("autoincrement".equalsIgnoreCase(func.functionName())) {
                        isGenerated = true;
                    }
                } else {
                    defaultValue = extractStringValue(valNode);
                }
            }
        }

        boolean isAggregate = false;
        String aggregateFunc = null;
        String expression = null;

        Optional<FieldAttributeNode> aggAttr = field.aggregateAttribute();
        if (aggAttr.isPresent()) {
            isAggregate = true;
            Optional<AggregateAttributeNode> aggClause = field.aggregateClause();
            if (aggClause.isPresent()) {
                aggregateFunc = aggClause.get().function().toUpperCase(Locale.ROOT);
                expression = aggClause.get().expression();
            } else {
                for (ArgumentNode arg : aggAttr.get().arguments()) {
                    if (arg.isNamed()) {
                        aggregateFunc = arg.name().toUpperCase(Locale.ROOT);
                        expression = extractStringValue(arg.value());
                        break;
                    }
                }
            }
        }

        Optional<FieldAttributeNode> exprAttr = field.expressionAttribute();
        if (exprAttr.isPresent()) {
            Optional<ExpressionAttributeNode> exprClause = field.expressionClause();
            if (exprClause.isPresent()) {
                expression = exprClause.get().expression();
            } else {
                expression = exprAttr.get().getPositionalArgumentValue(0)
                        .map(SchemaToMetadataTransformer::extractStringValue)
                        .orElse(null);
            }
        }

        String relName = null;
        if (isRelation) {
            relName = field.relationAttribute()
                    .flatMap(a -> a.getArgumentValue("name"))
                    .map(SchemaToMetadataTransformer::extractStringValue)
                    .orElse(name);
        }

        List<String> authRoles = field.authRoles();
        boolean isInjected = field.isInjected();
        String injectedFrom = field.injectedFrom().orElse(null);

        return new FieldMetadata(
                name,
                javaType,
                typeName,
                dbCol,
                isPk,
                isNullable,
                isUnique,
                isGenerated,
                defaultValue,
                isAggregate,
                aggregateFunc,
                expression,
                isRelation,
                relName,
                authRoles,
                isInjected,
                injectedFrom
        );
    }

    private static String extractStringValue(ExpressionNode expr) {
        if (expr instanceof StringLiteralNode str) {
            return str.value();
        } else if (expr instanceof IdentifierNode ident) {
            return ident.name();
        } else if (expr instanceof QualifiedIdentifierNode qid) {
            return qid.asQualifiedString();
        }
        return expr != null ? expr.toString() : null;
    }

    private static List<String> extractStringList(ExpressionNode expr) {
        if (expr instanceof ArrayLiteralNode arr) {
            List<String> list = new ArrayList<>();
            for (ExpressionNode elem : arr.elements()) {
                String val = extractStringValue(elem);
                if (val != null) {
                    list.add(val);
                }
            }
            return list;
        } else {
            String val = extractStringValue(expr);
            return val != null ? List.of(val) : List.of();
        }
    }
}
