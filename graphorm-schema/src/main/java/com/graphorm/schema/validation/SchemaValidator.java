package com.graphorm.schema.validation;

import com.graphorm.schema.ast.*;

import java.util.*;

/**
 * Semantic validation engine enforcing relational, primary key, view model,
 * and read-only invariants across the parsed schema document.
 */
public final class SchemaValidator {

    private static final Set<String> BUILT_IN_SCALARS = Set.of(
            "Int",
            "BigInt",
            "String",
            "Boolean",
            "Float",
            "DateTime",
            "Json",
            "Bytes"
    );

    private SchemaValidator() {}

    /**
     * Validates the schema document and returns a {@link ValidationResult}.
     *
     * @param document the parsed AST schema document
     * @return validation result containing all detected errors and warnings
     */
    public static ValidationResult validate(SchemaDocument document) {
        if (document == null) {
            return ValidationResult.of(List.of(Diagnostic.error("Schema document is null", SourcePosition.unknown())));
        }

        List<Diagnostic> diagnostics = new ArrayList<>();

        // 1. Entity name uniqueness
        validateEntityUniqueness(document, diagnostics);

        // 2. Enum validations
        validateEnums(document, diagnostics);

        // 3. Composite type validations
        validateCompositeTypes(document, diagnostics);

        // 4. Model validations (PK, fields, relations)
        validateModels(document, diagnostics);

        // 5. View model validations (read-only, @@from, joins, groupBy)
        validateViewModels(document, diagnostics);

        // 6. Cyclic view reference detection
        validateViewCycles(document, diagnostics);

        return ValidationResult.of(diagnostics);
    }

    private static void validateEntityUniqueness(SchemaDocument document, List<Diagnostic> diagnostics) {
        Map<String, SourcePosition> declared = new HashMap<>();

        for (ModelNode m : document.models()) {
            checkDuplicate(m.name(), m.position(), declared, diagnostics);
        }
        for (ViewModelNode vm : document.viewModels()) {
            checkDuplicate(vm.name(), vm.position(), declared, diagnostics);
        }
        for (CompositeTypeNode ct : document.compositeTypes()) {
            checkDuplicate(ct.name(), ct.position(), declared, diagnostics);
        }
        for (EnumNode e : document.enums()) {
            checkDuplicate(e.name(), e.position(), declared, diagnostics);
        }
    }

    private static void checkDuplicate(
            String name,
            SourcePosition pos,
            Map<String, SourcePosition> declared,
            List<Diagnostic> diagnostics
    ) {
        if (declared.containsKey(name)) {
            SourcePosition prev = declared.get(name);
            diagnostics.add(Diagnostic.error(
                    "Duplicate declaration: entity '" + name + "' is already defined at line " + prev.line(),
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
                            for (String ref : refFields) {
                                if (!targetModel.hasField(ref)) {
                                    diagnostics.add(Diagnostic.error(
                                            "Referenced field '" + ref + "' declared in @relation does not exist on target model '" + targetModel.name() + "'",
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

    private static void validateViewModels(SchemaDocument document, List<Diagnostic> diagnostics) {
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

            // Strategy check
            if (vm.isDatabaseBacked()) {
                // Strategy A: database-backed view
                // @@view is present
            } else {
                // Strategy B: GraphORM-defined virtual view
                Optional<BlockAttributeNode> fromAttr = vm.fromAttribute();
                if (fromAttr.isEmpty()) {
                    diagnostics.add(Diagnostic.error(
                            "Virtual view '" + vm.name() + "' must declare a root source model using @@from(SourceModel, as: \"alias\")",
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
            }
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
