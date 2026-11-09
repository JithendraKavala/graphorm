package com.graphorm.graphql.schema;

import com.graphorm.core.metadata.EntityMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import graphql.language.FieldDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Inspects GraphQL SDL definitions (via {@link TypeDefinitionRegistry}) and automatically matches
 * root Query and Mutation fields to models and views in the {@link ModelRegistry}.
 */
public class GraphQlTypeInspector {

    private static final Logger log = LoggerFactory.getLogger(GraphQlTypeInspector.class);

    private final TypeDefinitionRegistry typeRegistry;
    private final ModelRegistry modelRegistry;
    private final List<GraphQlOperationBinding> bindings = new ArrayList<>();
    private final List<GraphQlOperationBinding> rejectedViewMutations = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();
    private boolean inspected = false;

    public GraphQlTypeInspector(TypeDefinitionRegistry typeRegistry, ModelRegistry modelRegistry) {
        this.typeRegistry = Objects.requireNonNull(typeRegistry, "TypeDefinitionRegistry must not be null");
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "ModelRegistry must not be null");
    }

    public static GraphQlTypeInspector parse(String sdl, ModelRegistry modelRegistry) {
        Objects.requireNonNull(sdl, "GraphQL SDL must not be null");
        SchemaParser parser = new SchemaParser();
        TypeDefinitionRegistry registry = parser.parse(new StringReader(sdl));
        return new GraphQlTypeInspector(registry, modelRegistry);
    }

    public static GraphQlTypeInspector parse(Reader reader, ModelRegistry modelRegistry) {
        Objects.requireNonNull(reader, "Reader must not be null");
        SchemaParser parser = new SchemaParser();
        TypeDefinitionRegistry registry = parser.parse(reader);
        return new GraphQlTypeInspector(registry, modelRegistry);
    }

    /**
     * Inspects the schema and produces all discovered operation bindings.
     *
     * @return immutable list of operation bindings
     */
    public List<GraphQlOperationBinding> inspect() {
        lock.lock();
        try {
            if (inspected) {
                return Collections.unmodifiableList(bindings);
            }

            inspectQueryOperations();
            inspectMutationOperations();
            inspectSubscriptionOperations();

            inspected = true;
            return Collections.unmodifiableList(bindings);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns any attempted mutation operations targeting read-only views that were rejected.
     */
    public List<GraphQlOperationBinding> getRejectedViewMutations() {
        lock.lock();
        try {
            if (!inspected) {
                inspect();
            }
            return Collections.unmodifiableList(rejectedViewMutations);
        } finally {
            lock.unlock();
        }
    }

    public Optional<GraphQlOperationBinding> findBinding(String parentType, String fieldName) {
        if (!inspected) {
            inspect();
        }
        return bindings.stream()
                .filter(b -> b.parentType().equalsIgnoreCase(parentType) && b.fieldName().equals(fieldName))
                .findFirst();
    }

    public Optional<GraphQlOperationBinding> findQueryBinding(String fieldName) {
        return findBinding("Query", fieldName);
    }

    public Optional<GraphQlOperationBinding> findMutationBinding(String fieldName) {
        return findBinding("Mutation", fieldName);
    }

    public Optional<GraphQlOperationBinding> findSubscriptionBinding(String fieldName) {
        return findBinding("Subscription", fieldName);
    }

    private void inspectQueryOperations() {
        Optional<ObjectTypeDefinition> queryTypeDef = typeRegistry.getType("Query", ObjectTypeDefinition.class);
        if (queryTypeDef.isEmpty()) {
            return;
        }

        for (FieldDefinition field : queryTypeDef.get().getFieldDefinitions()) {
            Optional<GraphQlOperationBinding> binding = matchQueryField(field);
            binding.ifPresent(bindings::add);
        }
    }

    private Optional<GraphQlOperationBinding> matchQueryField(FieldDefinition field) {
        String fieldName = field.getName();
        Type<?> fieldType = field.getType();
        String returnTypeName = unwrapTypeName(fieldType);
        boolean isList = isListType(fieldType);

        boolean isConnection = returnTypeName.endsWith("Connection") || fieldName.endsWith("Connection");
        EntityMetadata entity = null;
        if (isConnection) {
            String base = returnTypeName.endsWith("Connection")
                    ? returnTypeName.substring(0, returnTypeName.length() - "Connection".length())
                    : (fieldName.endsWith("Connection") ? fieldName.substring(0, fieldName.length() - "Connection".length()) : fieldName);
            entity = matchEntity(base);
            if (entity == null) {
                entity = matchEntityByFieldName(base);
            }
        }

        if (entity == null) {
            // 1. Try matching entity by return type
            entity = matchEntity(returnTypeName);
        }

        // 2. Fallback: try matching by field name convention
        if (entity == null) {
            entity = matchEntityByFieldName(fieldName);
            // If field name ends with 's', assume list if not otherwise determined
            if (entity != null && !isList && fieldName.endsWith("s") && !entity.name().toLowerCase(Locale.ROOT).endsWith("s")) {
                isList = true;
            }
        }

        if (entity == null) {
            log.debug("Field Query.{} does not match any entity in ModelRegistry; skipping auto-wiring", fieldName);
            return Optional.empty();
        }

        String idArgName = resolveIdArgumentName(field, entity);

        GraphQlOperationKind kind;
        if (isConnection) {
            kind = entity instanceof com.graphorm.core.metadata.ViewModelMetadata
                    ? GraphQlOperationKind.VIEW_CONNECTION_QUERY
                    : GraphQlOperationKind.MODEL_CONNECTION_QUERY;
        } else if (entity instanceof com.graphorm.core.metadata.ViewModelMetadata) {
            kind = isList ? GraphQlOperationKind.VIEW_LIST_QUERY : GraphQlOperationKind.VIEW_SINGLE_QUERY;
        } else {
            kind = isList ? GraphQlOperationKind.MODEL_LIST_QUERY : GraphQlOperationKind.MODEL_SINGLE_QUERY;
        }

        log.info("Discovered Query binding: Query.{} -> {} ({})", fieldName, entity.name(), kind);
        return Optional.of(new GraphQlOperationBinding("Query", fieldName, entity, kind, idArgName, "input"));
    }

    private void inspectSubscriptionOperations() {
        Optional<ObjectTypeDefinition> subTypeDef = typeRegistry.getType("Subscription", ObjectTypeDefinition.class);
        if (subTypeDef.isEmpty()) {
            return;
        }

        for (FieldDefinition field : subTypeDef.get().getFieldDefinitions()) {
            matchSubscriptionField(field).ifPresent(bindings::add);
        }
    }

    private Optional<GraphQlOperationBinding> matchSubscriptionField(FieldDefinition field) {
        String fieldName = field.getName();
        String lowerField = fieldName.toLowerCase(Locale.ROOT);

        String entityCandidateName;
        GraphQlOperationKind kind;

        if (lowerField.endsWith("created")) {
            kind = GraphQlOperationKind.MODEL_CREATED_SUBSCRIPTION;
            entityCandidateName = fieldName.substring(0, fieldName.length() - 7);
        } else if (lowerField.endsWith("updated")) {
            kind = GraphQlOperationKind.MODEL_UPDATED_SUBSCRIPTION;
            entityCandidateName = fieldName.substring(0, fieldName.length() - 7);
        } else if (lowerField.endsWith("deleted")) {
            kind = GraphQlOperationKind.MODEL_DELETED_SUBSCRIPTION;
            entityCandidateName = fieldName.substring(0, fieldName.length() - 7);
        } else if (lowerField.startsWith("oncreate")) {
            kind = GraphQlOperationKind.MODEL_CREATED_SUBSCRIPTION;
            entityCandidateName = fieldName.substring(8);
        } else if (lowerField.startsWith("onupdate")) {
            kind = GraphQlOperationKind.MODEL_UPDATED_SUBSCRIPTION;
            entityCandidateName = fieldName.substring(8);
        } else if (lowerField.startsWith("ondelete")) {
            kind = GraphQlOperationKind.MODEL_DELETED_SUBSCRIPTION;
            entityCandidateName = fieldName.substring(8);
        } else {
            return Optional.empty();
        }

        EntityMetadata entity = matchEntity(entityCandidateName);
        if (entity == null) {
            String returnTypeName = unwrapTypeName(field.getType());
            entity = matchEntity(returnTypeName);
        }

        if (entity == null) {
            log.debug("Subscription field Subscription.{} did not match any entity in ModelRegistry", fieldName);
            return Optional.empty();
        }

        if (entity.isReadOnly()) {
            log.warn("Subscription on read-only view model '{}' is not supported.", entity.name());
            return Optional.empty();
        }

        String idArgName = resolveIdArgumentName(field, entity);
        log.info("Discovered Subscription binding: Subscription.{} -> {} ({})", fieldName, entity.name(), kind);
        return Optional.of(new GraphQlOperationBinding("Subscription", fieldName, entity, kind, idArgName, "input"));
    }

    private void inspectMutationOperations() {
        Optional<ObjectTypeDefinition> mutationTypeDef = typeRegistry.getType("Mutation", ObjectTypeDefinition.class);
        if (mutationTypeDef.isEmpty()) {
            return;
        }

        for (FieldDefinition field : mutationTypeDef.get().getFieldDefinitions()) {
            matchMutationField(field).ifPresent(b -> {
                if (b.isRejectedViewMutation()) {
                    rejectedViewMutations.add(b);
                } else {
                    bindings.add(b);
                }
            });
        }
    }

    private Optional<GraphQlOperationBinding> matchMutationField(FieldDefinition field) {
        String fieldName = field.getName();
        String lowerField = fieldName.toLowerCase(Locale.ROOT);

        String prefix;
        String entityCandidateName;

        if (lowerField.startsWith("create")) {
            prefix = "create";
            entityCandidateName = fieldName.substring(6);
        } else if (lowerField.startsWith("update")) {
            prefix = "update";
            entityCandidateName = fieldName.substring(6);
        } else if (lowerField.startsWith("delete")) {
            prefix = "delete";
            entityCandidateName = fieldName.substring(6);
        } else {
            // Not a standard CRUD mutation prefix
            return Optional.empty();
        }

        // Try candidate name or field return type
        EntityMetadata entity = matchEntity(entityCandidateName);
        if (entity == null) {
            String returnTypeName = unwrapTypeName(field.getType());
            entity = matchEntity(returnTypeName);
        }

        if (entity == null) {
            log.debug("Mutation field Mutation.{} did not match any entity in ModelRegistry", fieldName);
            return Optional.empty();
        }

        // Invariant check: View models can NEVER be targets of mutations!
        if (entity.isReadOnly()) {
            log.warn("STRICT READ-ONLY INVARIANT: Attempted mutation '{}' on read-only view '{}' will be strictly rejected.",
                    fieldName, entity.name());
            GraphQlOperationBinding rejected = new GraphQlOperationBinding(
                    "Mutation", fieldName, entity, GraphQlOperationKind.VIEW_MUTATION_REJECTED, "id", "input"
            );
            return Optional.of(rejected);
        }

        String idArgName = resolveIdArgumentName(field, entity);
        String inputArgName = resolveInputArgumentName(field);

        GraphQlOperationKind kind = switch (prefix) {
            case "create" -> GraphQlOperationKind.MODEL_CREATE_MUTATION;
            case "update" -> GraphQlOperationKind.MODEL_UPDATE_MUTATION;
            case "delete" -> GraphQlOperationKind.MODEL_DELETE_MUTATION;
            default -> throw new IllegalStateException("Unexpected prefix: " + prefix);
        };

        log.info("Discovered Mutation binding: Mutation.{} -> {} ({})", fieldName, entity.name(), kind);
        return Optional.of(new GraphQlOperationBinding("Mutation", fieldName, entity, kind, idArgName, inputArgName));
    }

    private String resolveIdArgumentName(FieldDefinition field, EntityMetadata entity) {
        // 1. First check explicit "id"
        for (InputValueDefinition arg : field.getInputValueDefinitions()) {
            String argName = arg.getName();
            if ("id".equalsIgnoreCase(argName)) {
                return argName;
            }
        }
        // 2. Check if argument matches a primary key on model
        for (InputValueDefinition arg : field.getInputValueDefinitions()) {
            String argName = arg.getName();
            if (entity instanceof ModelMetadata m && m.isPrimaryKey(argName)) {
                return argName;
            }
        }
        // 3. Check if argument matches a field on the entity
        for (InputValueDefinition arg : field.getInputValueDefinitions()) {
            String argName = arg.getName();
            if (entity.hasField(argName)) {
                return argName;
            }
            if ((entity.name() + "Id").equalsIgnoreCase(argName)) {
                return argName;
            }
        }
        // 4. If single argument exists on field, use it
        if (field.getInputValueDefinitions().size() == 1) {
            return field.getInputValueDefinitions().getFirst().getName();
        }
        return "id";
    }

    private String resolveInputArgumentName(FieldDefinition field) {
        for (InputValueDefinition arg : field.getInputValueDefinitions()) {
            String argName = arg.getName();
            if ("input".equalsIgnoreCase(argName) || "data".equalsIgnoreCase(argName)) {
                return argName;
            }
        }
        return "input";
    }

    private EntityMetadata matchEntity(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Optional<EntityMetadata> direct = modelRegistry.findEntity(name);
        if (direct.isPresent()) {
            return direct.get();
        }
        // Case-insensitive match
        for (EntityMetadata e : modelRegistry.getAllEntities()) {
            if (e.name().equalsIgnoreCase(name)) {
                return e;
            }
        }
        return null;
    }

    private EntityMetadata matchEntityByFieldName(String fieldName) {
        String lower = fieldName.toLowerCase(Locale.ROOT);
        for (EntityMetadata e : modelRegistry.getAllEntities()) {
            String eLower = e.name().toLowerCase(Locale.ROOT);
            if (lower.equals(eLower)
                    || lower.equals(eLower + "s")
                    || (eLower.endsWith("y") && lower.equals(eLower.substring(0, eLower.length() - 1) + "ies"))) {
                return e;
            }
        }
        return null;
    }

    private static String unwrapTypeName(Type<?> type) {
        if (type instanceof TypeName tn) {
            return tn.getName();
        }
        if (type instanceof NonNullType nnt) {
            return unwrapTypeName(nnt.getType());
        }
        if (type instanceof ListType lt) {
            return unwrapTypeName(lt.getType());
        }
        return "";
    }

    private static boolean isListType(Type<?> type) {
        if (type instanceof ListType) {
            return true;
        }
        if (type instanceof NonNullType nnt) {
            return isListType(nnt.getType());
        }
        return false;
    }

    public TypeDefinitionRegistry typeRegistry() {
        return typeRegistry;
    }

    public ModelRegistry modelRegistry() {
        return modelRegistry;
    }
}
