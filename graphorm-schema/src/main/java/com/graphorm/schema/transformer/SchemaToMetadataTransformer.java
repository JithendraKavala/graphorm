package com.graphorm.schema.transformer;

import com.graphorm.core.metadata.*;
import com.graphorm.core.event.NotifyEventType;
import com.graphorm.core.query.JoinType;
import com.graphorm.schema.ast.*;
import com.graphorm.schema.parser.GraphOrmParserFacade;
import com.graphorm.schema.validation.SchemaValidator;
import com.graphorm.schema.validation.ValidationResult;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import com.graphorm.core.util.DurationParser;

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

        boolean isMultiTenant = modelNode.isMultiTenant();
        String tenantFieldName = modelNode.tenantFieldName().orElse(null);
        String tenantColumn = modelNode.tenantColumnName().orElse(null);

        if (tenantColumn == null && tenantFieldName != null) {
            Optional<FieldNode> tf = modelNode.findField(tenantFieldName);
            if (tf.isPresent()) {
                tenantColumn = tf.get().mapAttribute()
                        .flatMap(attr -> attr.getPositionalArgumentValue(0).or(() -> attr.getArgumentValue("name")))
                        .map(SchemaToMetadataTransformer::extractStringValue)
                        .orElse(tenantFieldName);
            }
        }

        // Transform fields
        List<FieldMetadata> fields = new ArrayList<>();
        for (FieldNode f : modelNode.fields()) {
            boolean isRelation = document.containsModel(f.type().name());
            boolean isTenant = f.isTenantId() || (tenantFieldName != null && tenantFieldName.equals(f.name()));
            FieldMetadata fm = transformField(f, isRelation, pkFieldSet, isTenant);
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

        CacheMetadata cacheMetadata = CacheMetadata.notCached();
        if (modelNode.isCached()) {
            Duration ttl = modelNode.cacheTtl()
                    .map(DurationParser::parse)
                    .orElse(null);
            List<String> tags = modelNode.cacheTags();
            Set<String> tagSet = tags.isEmpty() ? Set.of(modelNode.name()) : Set.copyOf(tags);
            cacheMetadata = CacheMetadata.of(ttl, tagSet);
        }

        NotifyMetadata notifyMetadata = NotifyMetadata.disabled();
        if (modelNode.isNotifyEnabled()) {
            Set<NotifyEventType> events = new LinkedHashSet<>();
            for (String evStr : modelNode.notifyEvents()) {
                try {
                    events.add(NotifyEventType.fromString(evStr));
                } catch (IllegalArgumentException ignored) {}
            }
            notifyMetadata = NotifyMetadata.of(events);
        }

        var builder = ModelMetadata.builder(modelNode.name())
                .tableName(tableName)
                .fields(fields)
                .primaryKeyFields(new ArrayList<>(pkFieldSet))
                .uniqueConstraints(uniqueConstraints)
                .relations(relations)
                .readOnly(modelNode.isReadOnly())
                .authRoles(modelNode.authRoles())
                .exposure(modelNode.exposure())
                .indexes(indexes)
                .checkConstraints(checkConstraints)
                .schemaName(schemaName)
                .multiTenant(isMultiTenant)
                .cacheMetadata(cacheMetadata)
                .notifyMetadata(notifyMetadata);
        if (tenantColumn != null) {
            builder.tenantColumn(tenantColumn);
        }
        return builder.build();
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

        CacheMetadata vmCacheMetadata = CacheMetadata.notCached();
        if (vmNode.isCached()) {
            Duration ttl = vmNode.cacheTtl()
                    .map(DurationParser::parse)
                    .orElse(null);
            List<String> tags = vmNode.cacheTags();
            Set<String> tagSet = tags.isEmpty() ? Set.of(vmNode.name()) : Set.copyOf(tags);
            vmCacheMetadata = CacheMetadata.of(ttl, tagSet);
        }

        return ViewModelMetadata.builder(vmNode.name())
                .viewName(viewName)
                .fields(fields)
                .sourceModel(sourceModel, sourceAlias)
                .joins(joins)
                .groupBy(groupBy)
                .databaseBacked(isDbBacked)
                .rawQuery(vmNode.queryClause().orElse(null))
                .authRoles(vmNode.authRoles())
                .exposure(vmNode.exposure())
                .cacheMetadata(vmCacheMetadata)
                .build();
    }

    private static FieldMetadata transformField(FieldNode field, boolean isRelation, Set<String> pkFieldSet) {
        return transformField(field, isRelation, pkFieldSet, field.isTenantId());
    }

    private static FieldMetadata transformField(FieldNode field, boolean isRelation, Set<String> pkFieldSet, boolean isTenant) {
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
        DefaultValue defaultValueDefinition = null;
        boolean isGenerated = false;
        Optional<FieldAttributeNode> defaultAttr = field.defaultAttribute();
        if (defaultAttr.isPresent()) {
            Optional<ExpressionNode> valOpt = defaultAttr.get().getPositionalArgumentValue(0);
            if (valOpt.isPresent()) {
                ExpressionNode valNode = valOpt.get();
                if (valNode instanceof FunctionCallNode func) {
                    if ("autoincrement".equalsIgnoreCase(func.functionName())) {
                        isGenerated = true;
                        defaultValue = "autoincrement()";
                        defaultValueDefinition = DefaultValue.ofExpression(defaultValue);
                    } else {
                        defaultValue = serializeSqlExpression(func);
                        defaultValueDefinition = DefaultValue.ofExpression(defaultValue);
                    }
                } else if (valNode instanceof StringLiteralNode str) {
                    defaultValue = str.value();
                    defaultValueDefinition = DefaultValue.ofString(str.value());
                } else if (valNode instanceof IntLiteralNode intLit) {
                    defaultValue = intLit.rawText() != null ? intLit.rawText() : String.valueOf(intLit.value());
                    defaultValueDefinition = DefaultValue.ofNumeric(defaultValue);
                } else if (valNode instanceof FloatLiteralNode floatLit) {
                    defaultValue = floatLit.rawText() != null ? floatLit.rawText() : String.valueOf(floatLit.value());
                    defaultValueDefinition = DefaultValue.ofNumeric(defaultValue);
                } else if (valNode instanceof BooleanLiteralNode boolLit) {
                    defaultValue = String.valueOf(boolLit.value());
                    defaultValueDefinition = DefaultValue.ofBoolean(boolLit.value());
                } else if (valNode instanceof IdentifierNode ident) {
                    String identName = ident.name();
                    if ("null".equalsIgnoreCase(identName)) {
                        defaultValue = "NULL";
                        defaultValueDefinition = DefaultValue.ofNull();
                    } else if ("true".equalsIgnoreCase(identName) || "false".equalsIgnoreCase(identName)) {
                        boolean b = Boolean.parseBoolean(identName);
                        defaultValue = String.valueOf(b);
                        defaultValueDefinition = DefaultValue.ofBoolean(b);
                    } else if (isSqlExpressionKeyword(identName)) {
                        String keyword = identName.toUpperCase(Locale.ROOT);
                        defaultValue = keyword;
                        defaultValueDefinition = DefaultValue.ofExpression(keyword);
                    } else {
                        defaultValue = identName;
                        defaultValueDefinition = DefaultValue.ofString(identName);
                    }
                } else if (valNode instanceof ArrayLiteralNode arr) {
                    defaultValue = serializeSqlExpression(arr);
                    defaultValueDefinition = DefaultValue.ofExpression(defaultValue);
                } else {
                    defaultValue = extractStringValue(valNode);
                    if (defaultValue != null) {
                        defaultValueDefinition = DefaultValue.from(defaultValue, javaType, typeName);
                    }
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
        boolean isVersion = field.isVersion();

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
                injectedFrom,
                isVersion,
                isTenant,
                defaultValueDefinition
        );
    }

    private static final Set<String> SQL_EXPRESSION_KEYWORDS = Set.of(
            "CURRENT_TIMESTAMP",
            "CURRENT_DATE",
            "CURRENT_TIME",
            "LOCALTIMESTAMP",
            "LOCALTIME"
    );

    private static boolean isSqlExpressionKeyword(String name) {
        return name != null && SQL_EXPRESSION_KEYWORDS.contains(name.toUpperCase(Locale.ROOT));
    }

    /**
     * Serializes an AST ExpressionNode into a valid SQL expression representation preserving string quoting and numeric formatting.
     */
    public static String serializeSqlExpression(AstNode expr) {
        if (expr == null) {
            return null;
        }
        if (expr instanceof StringLiteralNode str) {
            return "'" + str.value().replace("'", "''") + "'";
        } else if (expr instanceof IntLiteralNode intLit) {
            return intLit.rawText() != null ? intLit.rawText() : String.valueOf(intLit.value());
        } else if (expr instanceof FloatLiteralNode floatLit) {
            return floatLit.rawText() != null ? floatLit.rawText() : String.valueOf(floatLit.value());
        } else if (expr instanceof BooleanLiteralNode boolLit) {
            return boolLit.value() ? "TRUE" : "FALSE";
        } else if (expr instanceof IdentifierNode ident) {
            String name = ident.name();
            if ("null".equalsIgnoreCase(name)) {
                return "NULL";
            } else if ("true".equalsIgnoreCase(name) || "false".equalsIgnoreCase(name)) {
                return name.toUpperCase(Locale.ROOT);
            }
            return name;
        } else if (expr instanceof QualifiedIdentifierNode qid) {
            return qid.asQualifiedString();
        } else if (expr instanceof FunctionCallNode func) {
            if (func.arguments().isEmpty()) {
                return func.functionName() + "()";
            }
            StringBuilder sb = new StringBuilder(func.functionName()).append("(");
            for (int i = 0; i < func.arguments().size(); i++) {
                if (i > 0) sb.append(", ");
                ArgumentNode arg = func.arguments().get(i);
                if (arg.isNamed()) {
                    sb.append(arg.name()).append(": ");
                }
                sb.append(serializeSqlExpression(arg.value()));
            }
            sb.append(")");
            return sb.toString();
        } else if (expr instanceof ArrayLiteralNode arr) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < arr.elements().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(serializeSqlExpression(arr.elements().get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        throw new IllegalArgumentException("Unsupported expression AST node for SQL serialization: " + expr.getClass().getName());
    }

    public static String extractStringValue(AstNode expr) {
        if (expr == null) {
            return null;
        }
        if (expr instanceof StringLiteralNode str) {
            return str.value();
        } else if (expr instanceof IdentifierNode ident) {
            return ident.name();
        } else if (expr instanceof QualifiedIdentifierNode qid) {
            return qid.asQualifiedString();
        } else if (expr instanceof IntLiteralNode intLit) {
            return intLit.rawText() != null ? intLit.rawText() : String.valueOf(intLit.value());
        } else if (expr instanceof FloatLiteralNode floatLit) {
            return floatLit.rawText() != null ? floatLit.rawText() : String.valueOf(floatLit.value());
        } else if (expr instanceof BooleanLiteralNode boolLit) {
            return String.valueOf(boolLit.value());
        } else if (expr instanceof FunctionCallNode func) {
            return serializeSqlExpression(func);
        } else if (expr instanceof ArrayLiteralNode arr) {
            return serializeSqlExpression(arr);
        }
        throw new IllegalArgumentException("Unsupported expression AST node for string extraction: " + expr.getClass().getName());
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
