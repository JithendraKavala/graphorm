package com.graphorm.schema.validation;

import com.graphorm.schema.ast.AggregateAttributeNode;
import com.graphorm.schema.ast.ArrayLiteralNode;
import com.graphorm.schema.ast.BlockAttributeNode;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Semantic validation engine for GraphORM schema documents.
 * Verifies naming uniqueness, primary keys, relationships, circular dependencies,
 * and view model read-only invariants.
 */
public final class SchemaValidator {

    private static final Set<String> BUILT_IN_SCALARS = Set.of(
            "String", "Int", "BigInt", "Float", "Decimal",
            "Boolean", "DateTime", "Date", "Time", "Json", "UUID", "Bytes"
    );

    private static final Set<String> SUPPORTED_AGGREGATES = Set.of(
            "count", "sum", "avg", "min", "max"
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

        // 4. Validate Models (PK, unique, relations, @id, @unique)
        validateModels(document, diagnostics);

        // 5. Validate Views (read-only invariant, strategy exclusivity, dependencies)
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

            // Field uniqueness and type resolution
            validateFieldList(model.name(), model.fields(), document, diagnostics, true);

            // Primary key validation
            validateModelPrimaryKey(model, diagnostics);

            // Relation validation
            validateModelRelations(model, document, diagnostics);
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
                }
            }
        }
    }

    private static void validateViews(SchemaDocument document, List<Diagnostic> diagnostics) {
        for (ViewModelNode vm : document.viewModels()) {
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
                            "Database-backed view '" + vm.name() + "' with @@view cannot declare @@query",
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
