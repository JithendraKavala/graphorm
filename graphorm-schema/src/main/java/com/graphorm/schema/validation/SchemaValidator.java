package com.graphorm.schema.validation;

import com.graphorm.schema.ast.AggregateAttributeNode;
import com.graphorm.schema.ast.ArgumentNode;
import com.graphorm.schema.ast.ArrayLiteralNode;
import com.graphorm.schema.ast.AstNode;
import com.graphorm.schema.ast.BlockAttributeNode;
import com.graphorm.schema.ast.BooleanLiteralNode;
import com.graphorm.schema.ast.CompositeTypeNode;
import com.graphorm.schema.ast.EnumNode;
import com.graphorm.schema.ast.EnumValueNode;
import com.graphorm.schema.ast.ExpressionAttributeNode;
import com.graphorm.schema.ast.ExpressionNode;
import com.graphorm.schema.ast.FieldAttributeNode;
import com.graphorm.schema.ast.FieldNode;
import com.graphorm.schema.ast.IdentifierNode;
import com.graphorm.schema.ast.ModelNode;
import com.graphorm.schema.ast.QualifiedIdentifierNode;
import com.graphorm.schema.ast.SchemaDocument;
import com.graphorm.schema.ast.SourcePosition;
import com.graphorm.schema.ast.StringLiteralNode;
import com.graphorm.schema.ast.ViewModelNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Semantic validation engine for GraphORM schema documents.
 * Verifies naming uniqueness, primary keys, relationships, circular dependencies,
 * indexes, constraints, and view model read-only invariants.
 */
public final class SchemaValidator {

    private static final Set<String> BUILT_IN_SCALARS = Set.of(
            "String", "Int", "BigInt", "Float", "Decimal",
            "Boolean", "DateTime", "Date", "Time", "Json", "UUID", "Bytes"
    );

    private static final Set<String> SUPPORTED_AGGREGATES = Set.of(
            "count", "sum", "avg", "min", "max"
    );

    private static final Set<String> VALID_REFERENTIAL_ACTIONS = Set.of(
            "CASCADE", "RESTRICT", "SETNULL", "SET_NULL", "SETDEFAULT", "SET_DEFAULT", "NOACTION", "NO_ACTION"
    );

    private static final Set<String> VALID_INDEX_TYPES = Set.of(
            "BTREE", "B_TREE", "HASH", "GIN", "GIST"
    );

    private static final Pattern FORBIDDEN_SQL = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private SchemaValidator() {}

    /**
     * Validates a parsed {@link SchemaDocument} and returns a {@link ValidationResult}.
     *
     * @param document the parsed AST document
     * @return the validation result containing any diagnostic errors or warnings
     */
    public static ValidationResult validate(SchemaDocument document) {
        Objects.requireNonNull(document, "SchemaDocument must not be null");
        List<Diagnostic> diagnostics = new ArrayList<>();

        // 1. Top-level declaration name uniqueness
        validateTopLevelUniqueness(document, diagnostics);

        // 2. Validate Enum declarations
        validateEnums(document, diagnostics);

        // 3. Validate Composite Types
        validateCompositeTypes(document, diagnostics);

        // 4. Validate Models (PK, unique, indexes, relations, @@check, @@auth, @@readonly)
        validateModels(document, diagnostics);

        // 5. Validate Views (read-only invariant, strategy exclusivity, dependencies, @@auth)
        validateViews(document, diagnostics);

        // 6. View dependency cycle detection
        validateViewCycles(document, diagnostics);

        return new ValidationResult(diagnostics);
    }

    private static void validateTopLevelUniqueness(SchemaDocument document, List<Diagnostic> diagnostics) {
        Map<String, SourcePosition> declared = new HashMap<>();

        for (ModelNode m : document.models()) {
            checkUniqueName(declared, m.name(), m.position(), "entity", diagnostics);
        }
        for (ViewModelNode v : document.viewModels()) {
            checkUniqueName(declared, v.name(), v.position(), "entity", diagnostics);
        }
        for (CompositeTypeNode ct : document.compositeTypes()) {
            checkUniqueName(declared, ct.name(), ct.position(), "entity", diagnostics);
        }
        for (EnumNode e : document.enums()) {
            checkUniqueName(declared, e.name(), e.position(), "entity", diagnostics);
        }
    }

    private static void checkUniqueName(
            Map<String, SourcePosition> declared,
            String name,
            SourcePosition pos,
            String kind,
            List<Diagnostic> diagnostics
    ) {
        if (declared.containsKey(name)) {
            diagnostics.add(Diagnostic.error(
                    "Duplicate declaration: " + kind + " '" + name + "' conflicts with previous declaration at " + declared.get(name),
                    pos
            ));
        } else {
            declared.put(name, pos);
        }
    }

    private static void validateEnums(SchemaDocument document, List<Diagnostic> diagnostics) {
        for (EnumNode e : document.enums()) {
            if (e.values().isEmpty()) {
                diagnostics.add(Diagnostic.error(
                        "Enum '" + e.name() + "' must declare at least one value",
                        e.position()
                ));
            }

            Set<String> values = new HashSet<>();
            for (EnumValueNode val : e.values()) {
                if (!values.add(val.name())) {
                    diagnostics.add(Diagnostic.error(
                            "Duplicate enum value '" + val.name() + "' in enum '" + e.name() + "'",
                            val.position()
                    ));
                }
            }
        }
    }

    private static void validateCompositeTypes(SchemaDocument document, List<Diagnostic> diagnostics) {
        for (CompositeTypeNode ct : document.compositeTypes()) {
            validateFieldList(ct.name(), ct.fields(), document, diagnostics, false);
        }
    }

    private static void validateModels(SchemaDocument document, List<Diagnostic> diagnostics) {
        for (ModelNode model : document.models()) {
            if (model.hasAttribute("groupBy")) {
                diagnostics.add(Diagnostic.error(
                        "Model '" + model.name() + "' cannot declare block @@groupBy. Group-by is only permitted on view models.",
                        model.findAttribute("groupBy").get().position()
                ));
            }

            // Validate model-level @cached / @@cached
            validateCachedAttribute(model.name(), model.cachedAttribute(), document, diagnostics);

            // Validate model-level @@auth
            validateAuthAttribute(model.name(), model.authAttribute(), diagnostics, true);

            // Validate model-level @@expose
            validateExposeAttribute(model.name(), model.exposeAttribute(), diagnostics, false);

            // Field uniqueness and type resolution
            validateFieldList(model.name(), model.fields(), document, diagnostics, true);

            // Primary key validation
            validateModelPrimaryKey(model, diagnostics);

            // Version validation
            validateModelVersion(model, diagnostics);

            // Multi-tenancy validation
            validateModelTenancy(model, diagnostics);

            // Unique constraints validation
            validateModelUniqueConstraints(model, diagnostics);

            // Index validation
            validateModelIndexes(model, diagnostics);

            // Check constraints validation
            validateModelCheckConstraints(model, diagnostics);

            // Relation validation
            validateModelRelations(model, document, diagnostics);
        }
    }

    private static void validateModelTenancy(ModelNode model, List<Diagnostic> diagnostics) {
        List<FieldNode> tenantFields = model.fields().stream()
                .filter(FieldNode::isTenantId)
                .toList();

        if (tenantFields.size() > 1) {
            diagnostics.add(Diagnostic.error(
                    "Model '" + model.name() + "' cannot have multiple @tenantId fields",
                    tenantFields.get(1).tenantIdAttribute().get().position()
            ));
            return;
        }

        Optional<BlockAttributeNode> tenantBlockOpt = model.tenantAttribute();
        String referencedFieldName = null;
        if (tenantBlockOpt.isPresent()) {
            BlockAttributeNode block = tenantBlockOpt.get();
            Optional<ExpressionNode> fieldArg = block.getArgumentValue("field")
                    .or(() -> block.getPositionalArgumentValue(0));
            Optional<ExpressionNode> colArg = block.getArgumentValue("column");

            if (fieldArg.isEmpty() && colArg.isEmpty()) {
                diagnostics.add(Diagnostic.error(
                        "Block @@tenant in model '" + model.name() + "' must specify \"field\" or \"column\", e.g. @@tenant(field: \"tenantId\")",
                        block.position()
                ));
            }

            if (fieldArg.isPresent()) {
                referencedFieldName = extractIdentifierOrString(fieldArg.get());
                if (referencedFieldName == null || !model.hasField(referencedFieldName)) {
                    diagnostics.add(Diagnostic.error(
                            "Field '" + (referencedFieldName != null ? referencedFieldName : "") + "' specified in @@tenant does not exist on model '" + model.name() + "'",
                            fieldArg.get().position()
                    ));
                    return;
                }
                if (!tenantFields.isEmpty()) {
                    FieldNode tf = tenantFields.get(0);
                    if (!tf.name().equals(referencedFieldName)) {
                        diagnostics.add(Diagnostic.error(
                                "Model '" + model.name() + "' defines conflicting tenant fields: @" + tf.name() + " and @@tenant(field: \'" + referencedFieldName + "\")",
                                block.position()
                        ));
                    }
                }
            } else if (colArg.isPresent()) {
                String colName = extractIdentifierOrString(colArg.get());
                Optional<FieldNode> matchingField = model.fields().stream()
                        .filter(f -> {
                            String fCol = f.mapAttribute()
                                    .flatMap(m -> m.getPositionalArgumentValue(0).or(() -> m.getArgumentValue("name")))
                                    .map(SchemaValidator::extractIdentifierOrString)
                                    .orElse(f.name());
                            return fCol.equals(colName) || f.name().equals(colName);
                        })
                        .findFirst();
                if (matchingField.isPresent()) {
                    referencedFieldName = matchingField.get().name();
                }
            }
        }

        FieldNode targetTenantField = null;
        if (!tenantFields.isEmpty()) {
            targetTenantField = tenantFields.get(0);
        } else if (referencedFieldName != null) {
            targetTenantField = model.findField(referencedFieldName).orElse(null);
        }

        if (targetTenantField != null) {
            if (targetTenantField.isOptional()) {
                diagnostics.add(Diagnostic.error(
                        "Tenant field '" + targetTenantField.name() + "' in model '" + model.name() + "' cannot be optional",
                        targetTenantField.position()
                ));
            }
            if (targetTenantField.isList()) {
                diagnostics.add(Diagnostic.error(
                        "Tenant field '" + targetTenantField.name() + "' in model '" + model.name() + "' cannot be a list",
                        targetTenantField.position()
                ));
            }
            if (targetTenantField.relationAttribute().isPresent()) {
                diagnostics.add(Diagnostic.error(
                        "Relation field '" + targetTenantField.name() + "' cannot be used as a tenant field",
                        targetTenantField.position()
                ));
            }
            String type = targetTenantField.type().name();
            if (!Set.of("String", "UUID", "Int", "BigInt").contains(type)) {
                diagnostics.add(Diagnostic.error(
                        "Field '" + targetTenantField.name() + "' used as tenant field must be of type String, UUID, Int, or BigInt, found '" + type + "'",
                        targetTenantField.type().position()
                ));
            }
        }
    }

    private static void validateModelVersion(ModelNode model, List<Diagnostic> diagnostics) {
        List<FieldNode> versionFields = model.fields().stream()
                .filter(FieldNode::isVersion)
                .toList();

        if (versionFields.size() > 1) {
            diagnostics.add(Diagnostic.error(
                    "Model '" + model.name() + "' cannot have multiple @version fields",
                    versionFields.get(1).position()
            ));
            return;
        }

        if (versionFields.size() == 1) {
            FieldNode vf = versionFields.get(0);
            if (vf.isPrimaryKey()) {
                diagnostics.add(Diagnostic.error(
                        "Primary key field '" + vf.name() + "' cannot be annotated with @version",
                        vf.versionAttribute().get().position()
                ));
            }
            if (vf.relationAttribute().isPresent()) {
                diagnostics.add(Diagnostic.error(
                        "Relation field '" + vf.name() + "' cannot be annotated with @version",
                        vf.versionAttribute().get().position()
                ));
            }
            String type = vf.type().name();
            if (!List.of("Int", "BigInt", "DateTime", "Timestamp").contains(type)) {
                diagnostics.add(Diagnostic.error(
                        "Field '" + vf.name() + "' annotated with @version must be of type Int, BigInt, or DateTime, found '" + type + "'",
                        vf.type().position()
                ));
            }
        }
    }

    private static void validateModelPrimaryKey(ModelNode model, List<Diagnostic> diagnostics) {
        List<FieldNode> pkFields = model.primaryKeyFields();
        Optional<BlockAttributeNode> pkBlock = model.primaryKeyBlockAttribute();

        if (pkFields.isEmpty() && pkBlock.isEmpty()) {
            diagnostics.add(Diagnostic.error(
                    "Model '" + model.name() + "' must define a primary key using @id or @@id",
                    model.position()
            ));
            return;
        }

        if (pkFields.size() > 1) {
            diagnostics.add(Diagnostic.error(
                    "Model '" + model.name() + "' cannot have multiple fields annotated with @id. Use @@id([...]) for compound keys.",
                    pkFields.get(1).position()
            ));
        }

        if (!pkFields.isEmpty() && pkBlock.isPresent()) {
            diagnostics.add(Diagnostic.error(
                    "Model '" + model.name() + "' cannot define both field-level @id and block-level @@id",
                    pkBlock.get().position()
            ));
        }

        // Validate field-level @id constraints
        for (FieldNode pkField : pkFields) {
            if (pkField.isOptional()) {
                diagnostics.add(Diagnostic.error(
                        "Primary key field '" + pkField.name() + "' in model '" + model.name() + "' cannot be optional",
                        pkField.position()
                ));
            }
            if (pkField.isList()) {
                diagnostics.add(Diagnostic.error(
                        "Primary key field '" + pkField.name() + "' in model '" + model.name() + "' cannot be a list",
                        pkField.position()
                ));
            }
        }

        // Validate compound @@id constraints
        if (pkBlock.isPresent()) {
            BlockAttributeNode block = pkBlock.get();
            Optional<ExpressionNode> argVal = block.getPositionalArgumentValue(0);
            if (argVal.isEmpty()) {
                argVal = block.getArgumentValue("fields");
            }

            if (argVal.isEmpty() || !(argVal.get() instanceof ArrayLiteralNode arrayNode)) {
                diagnostics.add(Diagnostic.error(
                        "Block @@id in model '" + model.name() + "' must specify an array of field names, e.g. @@id([field1, field2])",
                        block.position()
                ));
            } else if (arrayNode.elements().isEmpty()) {
                diagnostics.add(Diagnostic.error(
                        "Block @@id in model '" + model.name() + "' cannot be empty",
                        block.position()
                ));
            } else {
                for (ExpressionNode elem : arrayNode.elements()) {
                    String fieldName = extractIdentifierOrString(elem);
                    if (fieldName == null) {
                        diagnostics.add(Diagnostic.error(
                                "Invalid field name in @@id in model '" + model.name() + "'",
                                elem.position()
                        ));
                    } else if (!model.hasField(fieldName)) {
                        diagnostics.add(Diagnostic.error(
                                "Field '" + fieldName + "' specified in @@id does not exist on model '" + model.name() + "'",
                                elem.position()
                        ));
                    }
                }
            }
        }
    }

    private static void validateModelUniqueConstraints(ModelNode model, List<Diagnostic> diagnostics) {
        for (BlockAttributeNode uqBlock : model.uniqueBlockAttributes()) {
            Optional<ExpressionNode> argVal = uqBlock.getPositionalArgumentValue(0);
            if (argVal.isEmpty()) {
                argVal = uqBlock.getArgumentValue("fields");
            }

            if (argVal.isEmpty() || !(argVal.get() instanceof ArrayLiteralNode arrayNode)) {
                diagnostics.add(Diagnostic.error(
                        "Block @@unique in model '" + model.name() + "' must specify an array of field names, e.g. @@unique([field1, field2])",
                        uqBlock.position()
                ));
            } else if (arrayNode.elements().isEmpty()) {
                diagnostics.add(Diagnostic.error(
                        "Block @@unique in model '" + model.name() + "' cannot be empty",
                        uqBlock.position()
                ));
            } else {
                for (ExpressionNode elem : arrayNode.elements()) {
                    String fieldName = extractIdentifierOrString(elem);
                    if (fieldName == null) {
                        diagnostics.add(Diagnostic.error(
                                "Invalid field name in @@unique in model '" + model.name() + "'",
                                elem.position()
                        ));
                    } else if (!model.hasField(fieldName)) {
                        diagnostics.add(Diagnostic.error(
                                "Field '" + fieldName + "' specified in @@unique does not exist on model '" + model.name() + "'",
                                elem.position()
                        ));
                    }
                }
            }
        }
    }

    private static void validateModelIndexes(ModelNode model, List<Diagnostic> diagnostics) {
        for (BlockAttributeNode idxBlock : model.indexBlockAttributes()) {
            Optional<ExpressionNode> fieldsArg = idxBlock.getArgumentValue("fields")
                    .or(() -> idxBlock.getPositionalArgumentValue(0));

            if (fieldsArg.isEmpty()) {
                diagnostics.add(Diagnostic.error(
                        "Block @@index in model '" + model.name() + "' must specify fields, e.g. @@index([field1, field2])",
                        idxBlock.position()
                ));
            } else {
                ExpressionNode expr = fieldsArg.get();
                List<String> fieldNames = new ArrayList<>();
                if (expr instanceof ArrayLiteralNode arrayNode) {
                    if (arrayNode.elements().isEmpty()) {
                        diagnostics.add(Diagnostic.error(
                                "Block @@index in model '" + model.name() + "' cannot specify empty fields list",
                                idxBlock.position()
                        ));
                    } else {
                        for (ExpressionNode elem : arrayNode.elements()) {
                            String fName = extractIdentifierOrString(elem);
                            if (fName == null || fName.isBlank()) {
                                diagnostics.add(Diagnostic.error(
                                        "Invalid field in @@index in model '" + model.name() + "'",
                                        elem.position()
                                ));
                            } else {
                                fieldNames.add(fName);
                            }
                        }
                    }
                } else {
                    String fName = extractIdentifierOrString(expr);
                    if (fName != null && !fName.isBlank()) {
                        fieldNames.add(fName);
                    }
                }

                for (String fName : fieldNames) {
                    if (!model.hasField(fName)) {
                        diagnostics.add(Diagnostic.error(
                                "Field '" + fName + "' specified in @@index does not exist on model '" + model.name() + "'",
                                fieldsArg.get().position()
                        ));
                    }
                }
            }

            Optional<ExpressionNode> typeArg = idxBlock.getArgumentValue("type");
            if (typeArg.isPresent()) {
                String typeStr = extractIdentifierOrString(typeArg.get());
                if (typeStr == null || !VALID_INDEX_TYPES.contains(typeStr.toUpperCase(Locale.ROOT))) {
                    diagnostics.add(Diagnostic.error(
                            "Invalid index type '" + typeStr + "' in @@index on model '" + model.name() + "'. Supported types: " + String.join(", ", VALID_INDEX_TYPES),
                            typeArg.get().position()
                    ));
                }
            }
        }
    }

    private static void validateModelCheckConstraints(ModelNode model, List<Diagnostic> diagnostics) {
        for (BlockAttributeNode chkBlock : model.checkBlockAttributes()) {
            Optional<ExpressionNode> exprArg = chkBlock.getPositionalArgumentValue(0)
                    .or(() -> chkBlock.getArgumentValue("expression"))
                    .or(() -> chkBlock.getArgumentValue("expr"));

            if (exprArg.isEmpty()) {
                diagnostics.add(Diagnostic.error(
                        "Block @@check in model '" + model.name() + "' must specify a check expression, e.g. @@check(\"age >= 18\")",
                        chkBlock.position()
                ));
            } else {
                String expr = extractIdentifierOrString(exprArg.get());
                if (expr == null || expr.isBlank()) {
                    diagnostics.add(Diagnostic.error(
                            "Block @@check in model '" + model.name() + "' cannot specify an empty expression",
                            exprArg.get().position()
                    ));
                }
            }
        }
    }

    private static void validateModelRelations(ModelNode model, SchemaDocument document, List<Diagnostic> diagnostics) {
        for (FieldNode field : model.fields()) {
            String targetTypeName = field.type().name();
            if (document.containsModel(targetTypeName)) {
                Optional<FieldAttributeNode> relationAttr = field.relationAttribute();
                if (relationAttr.isPresent()) {
                    FieldAttributeNode rel = relationAttr.get();
                    Optional<ExpressionNode> fieldsArg = rel.getArgumentValue("fields");
                    Optional<ExpressionNode> referencesArg = rel.getArgumentValue("references");

                    if (fieldsArg.isPresent() && referencesArg.isEmpty()) {
                        diagnostics.add(Diagnostic.error(
                                "Relation on field '" + field.name() + "' in model '" + model.name() + "' defines 'fields' but is missing 'references'",
                                rel.position()
                        ));
                    } else if (fieldsArg.isEmpty() && referencesArg.isPresent()) {
                        diagnostics.add(Diagnostic.error(
                                "Relation on field '" + field.name() + "' in model '" + model.name() + "' defines 'references' but is missing 'fields'",
                                rel.position()
                        ));
                    } else if (fieldsArg.isPresent() && referencesArg.isPresent()) {
                        List<String> fkFields = extractStringList(fieldsArg.get());
                        List<String> refFields = extractStringList(referencesArg.get());

                        if (fkFields.size() != refFields.size()) {
                            diagnostics.add(Diagnostic.error(
                                    "Relation 'fields' size (" + fkFields.size() + ") does not match 'references' size (" + refFields.size() + ") on field '" + field.name() + "'",
                                    rel.position()
                            ));
                        }

                        // Validate local FK fields exist on current model
                        for (String localFk : fkFields) {
                            if (!model.hasField(localFk)) {
                                diagnostics.add(Diagnostic.error(
                                        "Local foreign key field '" + localFk + "' declared in @relation does not exist on model '" + model.name() + "'",
                                        fieldsArg.get().position()
                                ));
                            }
                        }

                        // Validate referenced fields exist on target model
                        Optional<ModelNode> targetModelOpt = document.findModel(targetTypeName);
                        if (targetModelOpt.isPresent()) {
                            ModelNode targetModel = targetModelOpt.get();
                            for (String targetRef : refFields) {
                                if (!targetModel.hasField(targetRef)) {
                                    diagnostics.add(Diagnostic.error(
                                            "Referenced field '" + targetRef + "' declared in @relation does not exist on target model '" + targetModel.name() + "'",
                                            referencesArg.get().position()
                                    ));
                                }
                            }
                        }
                    }

                    // Validate optional onDelete and onUpdate
                    Optional<ExpressionNode> onDeleteArg = rel.getArgumentValue("onDelete");
                    if (onDeleteArg.isPresent()) {
                        String actionStr = extractIdentifierOrString(onDeleteArg.get());
                        if (actionStr == null || !VALID_REFERENTIAL_ACTIONS.contains(actionStr.toUpperCase(Locale.ROOT).replace(" ", "_"))) {
                            diagnostics.add(Diagnostic.error(
                                    "Invalid onDelete action '" + actionStr + "' in @relation on field '" + field.name() + "' in model '" + model.name() + "'. Supported actions: Cascade, Restrict, SetNull, SetDefault, NoAction",
                                    onDeleteArg.get().position()
                            ));
                        }
                    }

                    Optional<ExpressionNode> onUpdateArg = rel.getArgumentValue("onUpdate");
                    if (onUpdateArg.isPresent()) {
                        String actionStr = extractIdentifierOrString(onUpdateArg.get());
                        if (actionStr == null || !VALID_REFERENTIAL_ACTIONS.contains(actionStr.toUpperCase(Locale.ROOT).replace(" ", "_"))) {
                            diagnostics.add(Diagnostic.error(
                                    "Invalid onUpdate action '" + actionStr + "' in @relation on field '" + field.name() + "' in model '" + model.name() + "'. Supported actions: Cascade, Restrict, SetNull, SetDefault, NoAction",
                                    onUpdateArg.get().position()
                            ));
                        }
                    }
                }
            }
        }
    }

    private static void validateViews(SchemaDocument document, List<Diagnostic> diagnostics) {
        for (ViewModelNode vm : document.viewModels()) {
            // Validate view-level @cached / @@cached
            validateCachedAttribute(vm.name(), vm.cachedAttribute(), document, diagnostics);

            // Validate view-level @@auth
            validateAuthAttribute(vm.name(), vm.authAttribute(), diagnostics, true);

            // Validate view-level @@expose
            validateExposeAttribute(vm.name(), vm.exposeAttribute(), diagnostics, true);

            // Field uniqueness and type resolution
            validateFieldList(vm.name(), vm.fields(), document, diagnostics, false);

            // Read-only invariant check
            for (FieldNode field : vm.fields()) {
                if (field.hasAttribute("default")) {
                    diagnostics.add(Diagnostic.error(
                            "Read-only view '" + vm.name() + "' cannot declare default value attribute @default on field '" + field.name() + "'",
                            field.defaultAttribute().get().position()
                    ));
                }
                if (field.hasAttribute("unique")) {
                    diagnostics.add(Diagnostic.error(
                            "Read-only view '" + vm.name() + "' cannot declare unique constraint @unique on field '" + field.name() + "'",
                            field.findAttribute("unique").get().position()
                    ));
                }
            }

            // Field-level aggregate and expression validation
            for (FieldNode field : vm.fields()) {
                Optional<FieldAttributeNode> aggOpt = field.aggregateAttribute();
                Optional<FieldAttributeNode> exprOpt = field.expressionAttribute();

                if (aggOpt.isPresent() && exprOpt.isPresent()) {
                    diagnostics.add(Diagnostic.error(
                            "Field '" + field.name() + "' in view '" + vm.name() + "' cannot declare both @aggregate and @expression",
                            aggOpt.get().position()
                    ));
                }

                if (aggOpt.isPresent()) {
                    FieldAttributeNode agg = aggOpt.get();
                    if (agg.arguments().isEmpty()) {
                        diagnostics.add(Diagnostic.error(
                                "Aggregate directive @aggregate on field '" + field.name() + "' must specify a function (count, sum, avg, min, max) and target expression",
                                agg.position()
                        ));
                    } else {
                        Optional<AggregateAttributeNode> parsedAgg = field.aggregateClause();
                        if (parsedAgg.isEmpty()) {
                            String funcName = agg.arguments().get(0).isNamed()
                                    ? agg.arguments().get(0).name()
                                    : extractIdentifierOrString(agg.arguments().get(0).value());
                            if (funcName != null && !SUPPORTED_AGGREGATES.contains(funcName.toLowerCase())) {
                                diagnostics.add(Diagnostic.error(
                                        "Unsupported aggregate function '" + funcName + "' on field '" + field.name() + "'. Supported functions: count, sum, avg, min, max",
                                        agg.position()
                                ));
                            } else {
                                diagnostics.add(Diagnostic.error(
                                        "Aggregate directive @aggregate on field '" + field.name() + "' must specify a function and target expression (e.g. @aggregate(count: \"column\"))",
                                        agg.position()
                                ));
                            }
                        } else {
                            AggregateAttributeNode aggNode = parsedAgg.get();
                            if (!SUPPORTED_AGGREGATES.contains(aggNode.function().toLowerCase())) {
                                diagnostics.add(Diagnostic.error(
                                        "Unsupported aggregate function '" + aggNode.function() + "' on field '" + field.name() + "'. Supported functions: count, sum, avg, min, max",
                                        agg.position()
                                ));
                            }
                        }
                    }
                }

                if (exprOpt.isPresent()) {
                    FieldAttributeNode expr = exprOpt.get();
                    Optional<ExpressionAttributeNode> parsedExpr = field.expressionClause();
                    if (parsedExpr.isEmpty()) {
                        diagnostics.add(Diagnostic.error(
                                "Expression directive @expression on field '" + field.name() + "' must specify a non-empty expression",
                                expr.position()
                        ));
                    }
                }
            }

            // Strategy check
            if (vm.isDatabaseBacked()) {
                // Strategy A: database-backed view (marked with @@view)
                if (vm.fromAttribute().isPresent()) {
                    diagnostics.add(Diagnostic.error(
                            "Database-backed view '" + vm.name() + "' with @@view cannot declare @@from",
                            vm.fromAttribute().get().position()
                    ));
                }
                if (vm.queryAttribute().isPresent()) {
                    diagnostics.add(Diagnostic.error(
                            "Database-backed view '" + vm.name() + "' with @@query cannot declare @@query",
                            vm.queryAttribute().get().position()
                    ));
                }
                if (!vm.joinAttributes().isEmpty()) {
                    diagnostics.add(Diagnostic.error(
                            "Database-backed view '" + vm.name() + "' with @@view cannot declare join attributes",
                            vm.joinAttributes().get(0).position()
                    ));
                }
                if (vm.groupByAttribute().isPresent()) {
                    diagnostics.add(Diagnostic.error(
                            "Database-backed view '" + vm.name() + "' with @@view cannot declare @@groupBy",
                            vm.groupByAttribute().get().position()
                    ));
                }
            } else if (vm.isRawQuery()) {
                // Strategy C: raw SQL query view (marked with @@query)
                if (vm.fromAttribute().isPresent()) {
                    diagnostics.add(Diagnostic.error(
                            "Raw query view '" + vm.name() + "' with @@query cannot declare @@from",
                            vm.fromAttribute().get().position()
                    ));
                }
                if (!vm.joinAttributes().isEmpty()) {
                    diagnostics.add(Diagnostic.error(
                            "Raw query view '" + vm.name() + "' with @@query cannot declare join attributes",
                            vm.joinAttributes().get(0).position()
                    ));
                }
                if (vm.groupByAttribute().isPresent()) {
                    diagnostics.add(Diagnostic.error(
                            "Raw query view '" + vm.name() + "' with @@query cannot declare @@groupBy",
                            vm.groupByAttribute().get().position()
                    ));
                }
                validateQueryAttribute(vm, vm.queryAttribute().get(), diagnostics);
            } else {
                // Strategy B: GraphORM-defined virtual view
                Optional<BlockAttributeNode> fromAttr = vm.fromAttribute();
                if (fromAttr.isEmpty()) {
                    diagnostics.add(Diagnostic.error(
                            "Virtual view '" + vm.name() + "' must declare a root source model using @@from(SourceModel, as: \"alias\") or be marked with @@view for database-backed views",
                            vm.position()
                    ));
                } else {
                    validateFromAttribute(vm, fromAttr.get(), document, diagnostics);
                }

                // Validate joins
                Set<String> aliases = new HashSet<>();
                if (fromAttr.isPresent()) {
                    extractAlias(fromAttr.get()).ifPresent(aliases::add);
                }

                for (BlockAttributeNode join : vm.joinAttributes()) {
                    validateJoinAttribute(vm, join, aliases, document, diagnostics);
                }

                // Validate @@groupBy if present
                if (vm.groupByAttribute().isPresent()) {
                    BlockAttributeNode gb = vm.groupByAttribute().get();
                    Optional<ExpressionNode> argVal = gb.getPositionalArgumentValue(0).or(() -> gb.getArgumentValue("fields"));
                    if (argVal.isEmpty() || !(argVal.get() instanceof ArrayLiteralNode arrayNode)) {
                        diagnostics.add(Diagnostic.error(
                                "Block @@groupBy in view '" + vm.name() + "' must specify an array of field/column names, e.g. @@groupBy([\"s.id\", \"s.name\"])",
                                gb.position()
                        ));
                    } else if (arrayNode.elements().isEmpty()) {
                        diagnostics.add(Diagnostic.error(
                                "Block @@groupBy in view '" + vm.name() + "' cannot be empty",
                                gb.position()
                        ));
                    } else {
                        for (ExpressionNode elem : arrayNode.elements()) {
                            String col = extractIdentifierOrString(elem);
                            if (col == null || col.isBlank()) {
                                diagnostics.add(Diagnostic.error(
                                        "Invalid column reference in @@groupBy in view '" + vm.name() + "'",
                                        elem.position()
                                ));
                            }
                        }
                    }
                }
            }
        }
    }

    private static void validateQueryAttribute(
            ViewModelNode vm,
            BlockAttributeNode queryAttr,
            List<Diagnostic> diagnostics
    ) {
        Optional<ExpressionNode> sqlArg = queryAttr.getPositionalArgumentValue(0)
                .or(() -> queryAttr.getArgumentValue("sql"));

        if (sqlArg.isEmpty()) {
            diagnostics.add(Diagnostic.error(
                    "View '" + vm.name() + "' @@query attribute must specify a SQL query string",
                    queryAttr.position()
            ));
            return;
        }

        ExpressionNode expr = sqlArg.get();
        if (!(expr instanceof StringLiteralNode strNode) || strNode.value().isBlank()) {
            diagnostics.add(Diagnostic.error(
                    "View '" + vm.name() + "' @@query attribute must specify a non-empty SQL query string",
                    expr.position()
            ));
            return;
        }

        String sql = strNode.value().trim();
        String normalized = sql.toUpperCase();
        if (!normalized.startsWith("SELECT") && !normalized.startsWith("WITH")) {
            diagnostics.add(Diagnostic.error(
                    "View '" + vm.name() + "' @@query must be a read-only query starting with SELECT or WITH",
                    expr.position()
            ));
        }

        var matcher = FORBIDDEN_SQL.matcher(sql);
        if (matcher.find()) {
            diagnostics.add(Diagnostic.error(
                    "View '" + vm.name() + "' @@query contains forbidden DDL/DML statement: " + matcher.group(),
                    expr.position()
            ));
        }
    }

    private static void validateFromAttribute(
            ViewModelNode vm,
            BlockAttributeNode fromAttr,
            SchemaDocument document,
            List<Diagnostic> diagnostics
    ) {
        Optional<ExpressionNode> sourceModelArg = fromAttr.getPositionalArgumentValue(0);
        if (sourceModelArg.isEmpty()) {
            sourceModelArg = fromAttr.getArgumentValue("model");
        }

        if (sourceModelArg.isEmpty()) {
            diagnostics.add(Diagnostic.error(
                    "View '" + vm.name() + "' @@from attribute must specify a source model",
                    fromAttr.position()
            ));
        } else {
            String sourceModelName = extractIdentifierOrString(sourceModelArg.get());
            if (sourceModelName == null || (!document.containsModel(sourceModelName) && !document.containsViewModel(sourceModelName))) {
                diagnostics.add(Diagnostic.error(
                        "View '" + vm.name() + "' references unknown source model '" + sourceModelName + "' in @@from",
                        sourceModelArg.get().position()
                ));
            }
        }

        Optional<String> aliasOpt = extractAlias(fromAttr);
        if (aliasOpt.isEmpty()) {
            diagnostics.add(Diagnostic.error(
                    "View '" + vm.name() + "' @@from attribute must specify an alias (e.g. as: \"alias\")",
                    fromAttr.position()
            ));
        }
    }

    private static void validateJoinAttribute(
            ViewModelNode vm,
            BlockAttributeNode join,
            Set<String> existingAliases,
            SchemaDocument document,
            List<Diagnostic> diagnostics
    ) {
        Optional<ExpressionNode> targetModelArg = join.getPositionalArgumentValue(0);
        if (targetModelArg.isEmpty()) {
            targetModelArg = join.getArgumentValue("model");
        }

        if (targetModelArg.isEmpty()) {
            diagnostics.add(Diagnostic.error(
                    "View '" + vm.name() + "' " + join.name() + " attribute must specify a target model",
                    join.position()
            ));
        } else {
            String targetModelName = extractIdentifierOrString(targetModelArg.get());
            if (targetModelName == null || (!document.containsModel(targetModelName) && !document.containsViewModel(targetModelName))) {
                diagnostics.add(Diagnostic.error(
                        "View '" + vm.name() + "' references unknown model '" + targetModelName + "' in @@" + join.name(),
                        targetModelArg.get().position()
                ));
            }
        }

        Optional<String> aliasOpt = extractAlias(join);
        if (aliasOpt.isEmpty()) {
            diagnostics.add(Diagnostic.error(
                    "View '" + vm.name() + "' @@" + join.name() + " attribute must specify an alias (e.g. as: \"alias\")",
                    join.position()
            ));
        } else {
            String alias = aliasOpt.get();
            if (!existingAliases.add(alias)) {
                diagnostics.add(Diagnostic.error(
                        "Duplicate table alias '" + alias + "' in view '" + vm.name() + "'",
                        join.position()
                ));
            }
        }

        // Validate on condition
        Optional<ExpressionNode> onArg = join.getArgumentValue("on");
        if (onArg.isEmpty()) {
            onArg = join.getPositionalArgumentValue(2);
        }
        if (onArg.isEmpty()) {
            diagnostics.add(Diagnostic.error(
                    "View '" + vm.name() + "' @@" + join.name() + " attribute must specify a join condition (e.g. on: \"...\")",
                    join.position()
            ));
        }
    }

    private static Optional<String> extractAlias(BlockAttributeNode attr) {
        Optional<ExpressionNode> asArg = attr.getArgumentValue("as");
        if (asArg.isEmpty()) {
            asArg = attr.getPositionalArgumentValue(1);
        }
        return asArg.map(SchemaValidator::extractIdentifierOrString);
    }

    private static void validateViewCycles(SchemaDocument document, List<Diagnostic> diagnostics) {
        Optional<List<String>> cycleOpt = CycleDetector.findCycle(document);
        if (cycleOpt.isPresent()) {
            List<String> cycle = cycleOpt.get();
            String cycleStr = String.join(" -> ", cycle);
            SourcePosition pos = document.findViewModel(cycle.get(0)).map(ViewModelNode::position).orElse(SourcePosition.unknown());
            diagnostics.add(Diagnostic.error(
                    "Circular view dependency detected: " + cycleStr,
                    pos
            ));
        }
    }

    private static void validateFieldList(
            String containerName,
            List<FieldNode> fields,
            SchemaDocument document,
            List<Diagnostic> diagnostics,
            boolean isModel
    ) {
        Set<String> fieldNames = new HashSet<>();
        for (FieldNode field : fields) {
            // Check field name uniqueness
            if (!fieldNames.add(field.name())) {
                diagnostics.add(Diagnostic.error(
                        "Duplicate field '" + field.name() + "' in '" + containerName + "'",
                        field.position()
                ));
            }

            if (isModel) {
                if (field.hasAttribute("aggregate")) {
                    diagnostics.add(Diagnostic.error(
                            "Model '" + containerName + "' cannot declare @aggregate on field '" + field.name() + "'. Aggregations are only permitted on view models.",
                            field.findAttribute("aggregate").get().position()
                    ));
                }
                if (field.hasAttribute("expression")) {
                    diagnostics.add(Diagnostic.error(
                            "Model '" + containerName + "' cannot declare @expression on field '" + field.name() + "'. Computed expressions are only permitted on view models.",
                            field.findAttribute("expression").get().position()
                    ));
                }
            }

            // Validate @auth if present on field
            validateAuthAttribute(containerName + "." + field.name(), field.authAttribute(), diagnostics, false);

            // Validate @injected if present on field
            validateInjectedAttribute(containerName, field, diagnostics);

            // Check field type resolution
            String typeName = field.type().name();
            if (!BUILT_IN_SCALARS.contains(typeName)
                    && !document.containsModel(typeName)
                    && !document.containsViewModel(typeName)
                    && !document.containsCompositeType(typeName)
                    && !document.containsEnum(typeName)) {
                diagnostics.add(Diagnostic.error(
                        "Unknown type '" + typeName + "' for field '" + field.name() + "' in '" + containerName + "'",
                        field.type().position()
                ));
            }
        }
    }

    private static void validateExposeAttribute(
            String targetName,
            Optional<BlockAttributeNode> exposeOpt,
            List<Diagnostic> diagnostics,
            boolean isViewModel
    ) {
        if (exposeOpt.isEmpty()) {
            return;
        }
        BlockAttributeNode node = exposeOpt.get();
        List<ArgumentNode> arguments = node.arguments();
        SourcePosition pos = node.position();

        for (ArgumentNode arg : arguments) {
            if (arg.isNamed()) {
                String name = arg.name();
                if (!"queries".equals(name) && !"mutations".equals(name)) {
                    diagnostics.add(Diagnostic.error(
                            "Unknown argument '" + name + "' in @@expose on '" + targetName + "'. Expected 'queries' or 'mutations'",
                            arg.position()
                    ));
                } else if (!(arg.value() instanceof BooleanLiteralNode)) {
                    diagnostics.add(Diagnostic.error(
                            "Argument '" + name + "' in @@expose on '" + targetName + "' must be a boolean",
                            arg.position()
                    ));
                } else if (isViewModel && "mutations".equals(name)) {
                    BooleanLiteralNode b = (BooleanLiteralNode) arg.value();
                    if (b.value()) {
                        diagnostics.add(Diagnostic.error(
                                "View model '" + targetName + "' cannot expose mutations because view models are strictly read-only",
                                arg.position()
                        ));
                    }
                }
            } else {
                if (!(arg.value() instanceof BooleanLiteralNode)) {
                    diagnostics.add(Diagnostic.error(
                            "Positional argument in @@expose on '" + targetName + "' must be a boolean (e.g. @@expose(false))",
                            arg.position()
                    ));
                }
            }
        }
    }

    private static void validateAuthAttribute(
            String targetName,
            Optional<? extends AstNode> authOpt,
            List<Diagnostic> diagnostics,
            boolean isBlock
    ) {
        if (authOpt.isEmpty()) {
            return;
        }
        AstNode node = authOpt.get();
        List<ArgumentNode> arguments = (node instanceof BlockAttributeNode b)
                ? b.arguments()
                : (node instanceof FieldAttributeNode f) ? f.arguments() : List.of();

        String prefix = isBlock ? "@@auth" : "@auth";
        SourcePosition pos = node.position();

        if (arguments.isEmpty()) {
            diagnostics.add(Diagnostic.error(
                    prefix + " on '" + targetName + "' must specify at least one role, e.g. " + prefix + "(roles: [\"ADMIN\"])",
                    pos
            ));
            return;
        }

        Optional<ExpressionNode> rolesArg = Optional.empty();
        for (ArgumentNode arg : arguments) {
            if (arg.isNamed()) {
                if ("roles".equals(arg.name())) {
                    rolesArg = Optional.of(arg.value());
                } else {
                    diagnostics.add(Diagnostic.error(
                            "Unknown argument '" + arg.name() + "' in " + prefix + " on '" + targetName + "'. Expected 'roles'",
                            arg.position()
                    ));
                    return;
                }
            } else if (rolesArg.isEmpty()) {
                rolesArg = Optional.of(arg.value());
            }
        }

        if (rolesArg.isEmpty()) {
            diagnostics.add(Diagnostic.error(
                    prefix + " on '" + targetName + "' must specify roles, e.g. " + prefix + "(roles: [\"ADMIN\"])",
                    pos
            ));
            return;
        }

        ExpressionNode expr = rolesArg.get();
        if (expr instanceof ArrayLiteralNode array) {
            if (array.elements().isEmpty()) {
                diagnostics.add(Diagnostic.error(
                        prefix + " on '" + targetName + "' roles array cannot be empty",
                        expr.position()
                ));
                return;
            }
            for (ExpressionNode elem : array.elements()) {
                String val = extractIdentifierOrString(elem);
                if (val == null || val.isBlank()) {
                    diagnostics.add(Diagnostic.error(
                            "Invalid role value in " + prefix + " on '" + targetName + "'. Roles must be non-blank strings or identifiers.",
                            elem.position()
                    ));
                }
            }
        } else {
            String val = extractIdentifierOrString(expr);
            if (val == null || val.isBlank()) {
                diagnostics.add(Diagnostic.error(
                        "Invalid role value in " + prefix + " on '" + targetName + "'. Roles must be non-blank strings or identifiers.",
                        expr.position()
                ));
            }
        }
    }

    private static void validateInjectedAttribute(
            String containerName,
            FieldNode field,
            List<Diagnostic> diagnostics
    ) {
        Optional<FieldAttributeNode> injectedOpt = field.injectedAttribute();
        if (injectedOpt.isEmpty()) {
            return;
        }
        FieldAttributeNode injected = injectedOpt.get();
        SourcePosition pos = injected.position();

        if (injected.arguments().isEmpty()) {
            diagnostics.add(Diagnostic.error(
                    "Directive @injected on field '" + field.name() + "' in '" + containerName
                            + "' must specify a non-blank 'from' string, e.g. @injected(from: \"tenantId\")",
                    pos
            ));
            return;
        }

        Optional<ExpressionNode> fromArg = Optional.empty();
        for (ArgumentNode arg : injected.arguments()) {
            if (arg.isNamed()) {
                if ("from".equals(arg.name())) {
                    fromArg = Optional.of(arg.value());
                } else {
                    diagnostics.add(Diagnostic.error(
                            "Unknown argument '" + arg.name() + "' in @injected on field '" + field.name()
                                    + "'. Expected 'from'",
                            arg.position()
                    ));
                    return;
                }
            } else if (fromArg.isEmpty()) {
                fromArg = Optional.of(arg.value());
            }
        }

        if (fromArg.isEmpty()) {
            diagnostics.add(Diagnostic.error(
                    "Directive @injected on field '" + field.name() + "' in '" + containerName
                            + "' must specify a non-blank 'from' string, e.g. @injected(from: \"tenantId\")",
                    pos
            ));
            return;
        }

        String fromVal = extractIdentifierOrString(fromArg.get());
        if (fromVal == null || fromVal.isBlank()) {
            diagnostics.add(Diagnostic.error(
                    "Directive @injected on field '" + field.name() + "' in '" + containerName
                            + "' must specify a non-blank 'from' string (got empty or non-string)",
                    fromArg.get().position()
            ));
        }
    }

    private static String extractIdentifierOrString(ExpressionNode expr) {
        if (expr instanceof IdentifierNode id) {
            return id.name();
        } else if (expr instanceof StringLiteralNode str) {
            return str.value();
        } else if (expr instanceof QualifiedIdentifierNode qid) {
            return qid.asQualifiedString();
        }
        return null;
    }

    private static void validateCachedAttribute(
            String entityName,
            Optional<BlockAttributeNode> cachedOpt,
            SchemaDocument document,
            List<Diagnostic> diagnostics
    ) {
        if (cachedOpt.isEmpty()) {
            return;
        }
        BlockAttributeNode attr = cachedOpt.get();

        // 1. Validate TTL
        Optional<ExpressionNode> ttlExpr = attr.getArgumentValue("ttl")
                .or(() -> attr.getPositionalArgumentValue(0));

        if (ttlExpr.isEmpty()) {
            diagnostics.add(Diagnostic.error(
                    "Cached directive on '" + entityName + "' must specify a 'ttl' duration argument (e.g. \"60s\", \"5m\", \"1h\")",
                    attr.position()
            ));
        } else {
            String ttlStr = extractIdentifierOrString(ttlExpr.get());
            if (ttlStr == null || ttlStr.isBlank() || !com.graphorm.core.util.DurationParser.isValid(ttlStr)) {
                diagnostics.add(Diagnostic.error(
                        "Invalid duration syntax for cache ttl on '" + entityName + "': '" +
                                (ttlStr != null ? ttlStr : ttlExpr.get().toString()) +
                                "'. Expected format: e.g. \"60s\", \"5m\", \"1h\", \"2d\", \"500ms\"",
                        attr.position()
                ));
            }
        }

        // 2. Validate Tags
        Optional<ExpressionNode> tagsExpr = attr.getArgumentValue("tags")
                .or(() -> attr.getPositionalArgumentValue(1));

        if (tagsExpr.isPresent()) {
            List<String> tags = new ArrayList<>();
            ExpressionNode expr = tagsExpr.get();
            if (expr instanceof ArrayLiteralNode arr) {
                for (ExpressionNode elem : arr.elements()) {
                    String tag = extractIdentifierOrString(elem);
                    if (tag != null && !tag.isBlank()) {
                        tags.add(tag.trim());
                    } else {
                        diagnostics.add(Diagnostic.error(
                                "Cache tag on '" + entityName + "' must be a non-empty string or identifier",
                                elem.position()
                        ));
                    }
                }
            } else {
                String tag = extractIdentifierOrString(expr);
                if (tag != null && !tag.isBlank()) {
                    tags.add(tag.trim());
                } else {
                    diagnostics.add(Diagnostic.error(
                            "Cache tag on '" + entityName + "' must be a non-empty string or identifier",
                            expr.position()
                    ));
                }
            }

            for (String tag : tags) {
                if (!document.containsModel(tag) && !document.containsView(tag)) {
                    diagnostics.add(Diagnostic.error(
                            "Cache tag '" + tag + "' on '" + entityName + "' does not reference a valid entity (model or view) in the schema",
                            attr.position()
                    ));
                }
            }
        }
    }

    private static List<String> extractStringList(ExpressionNode expr) {
        if (expr instanceof ArrayLiteralNode array) {
            List<String> result = new ArrayList<>();
            for (ExpressionNode elem : array.elements()) {
                String str = extractIdentifierOrString(elem);
                if (str != null) {
                    result.add(str);
                }
            }
            return result;
        } else if (expr instanceof IdentifierNode id) {
            return List.of(id.name());
        } else if (expr instanceof StringLiteralNode str) {
            return List.of(str.value());
        }
        return List.of();
    }
}
