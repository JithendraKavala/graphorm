package com.graphorm.graphql.fetcher;

import com.graphorm.core.event.EntityEvent;
import com.graphorm.core.event.EntityEventBroker;
import com.graphorm.core.event.NotifyEventType;
import com.graphorm.core.execution.QueryExecutionEngine;
import com.graphorm.core.exception.AccessDeniedException;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.core.tenant.TenantContext;
import com.graphorm.graphql.security.FieldAuthorizer;
import com.graphorm.graphql.security.FieldAuthorizerContext;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Flow;

/**
 * Generic GraphQL {@link DataFetcher} for real-time model subscriptions (e.g. {@code Subscription.studentCreated},
 * {@code Subscription.studentUpdated(id: ID)}, {@code Subscription.studentDeleted}).
 * Returns a reactive stream {@link Publisher} filtered by tenant and optional ID, dynamically re-querying
 * entities via {@link QueryExecutionEngine} to satisfy GraphQL selection sets.
 */
public class ModelSubscriptionDataFetcher implements DataFetcher<Publisher<Object>> {

    private static final Logger log = LoggerFactory.getLogger(ModelSubscriptionDataFetcher.class);

    private final ModelMetadata model;
    private final ModelRegistry registry;
    private final QueryExecutionEngine executionEngine;
    private final NotifyEventType eventType;
    private final String idArgumentName;

    public ModelSubscriptionDataFetcher(
            ModelMetadata model,
            ModelRegistry registry,
            QueryExecutionEngine executionEngine,
            NotifyEventType eventType,
            String idArgumentName
    ) {
        this.model = Objects.requireNonNull(model, "ModelMetadata must not be null");
        this.registry = registry;
        this.executionEngine = Objects.requireNonNull(executionEngine, "QueryExecutionEngine must not be null");
        this.eventType = Objects.requireNonNull(eventType, "NotifyEventType must not be null");
        this.idArgumentName = idArgumentName != null ? idArgumentName : "id";
    }

    public ModelSubscriptionDataFetcher(
            ModelMetadata model,
            ModelRegistry registry,
            QueryExecutionEngine executionEngine,
            NotifyEventType eventType
    ) {
        this(model, registry, executionEngine, eventType, "id");
    }

    @Override
    public Publisher<Object> get(DataFetchingEnvironment env) {
        log.debug("Subscribing to {}.{} with event type {}", model.name(), env.getField().getName(), eventType);

        if (model.hasAuthRoles()) {
            FieldAuthorizer authorizer = FieldAuthorizerContext.resolve(env);
            if (!authorizer.isAuthorized(model.authRoles())) {
                log.warn("Access denied to model '{}'. Required roles: {}, user roles: {}",
                        model.name(), model.authRoles(), authorizer.getCurrentRoles());
                throw new AccessDeniedException(
                        "ACCESS_DENIED",
                        "Access denied to model '" + model.name() + "'. Required roles: " + model.authRoles()
                );
            }
        }

        String pkField = model.primaryKeyFields().isEmpty() ? "id" : model.primaryKeyFields().getFirst();
        FieldMetadata pkFieldMeta = model.findField(pkField).orElse(null);

        Object rawFilterId = null;
        if (env.containsArgument(idArgumentName) && env.getArgument(idArgumentName) != null) {
            rawFilterId = env.getArgument(idArgumentName);
        } else if (env.containsArgument("id") && env.getArgument("id") != null) {
            rawFilterId = env.getArgument("id");
        }
        final Object filterId = (rawFilterId != null && pkFieldMeta != null)
                ? EntityRowNormalizer.coerceToFieldType(rawFilterId, pkFieldMeta)
                : rawFilterId;

        final String activeTenantId = resolveActiveTenantId(env);

        EntityEventBroker broker = executionEngine.eventBroker()
                .orElseThrow(() -> new IllegalStateException("EntityEventBroker is not available on execution engine for subscriptions"));

        Flow.Publisher<EntityEvent> sourceFlowPublisher = broker.publisher(model.name(), eventType);

        Flow.Publisher<Object> transformedFlowPublisher = new Flow.Publisher<>() {
            @Override
            public void subscribe(Flow.Subscriber<? super Object> downstream) {
                sourceFlowPublisher.subscribe(new Flow.Subscriber<>() {
                    private Flow.Subscription subscription;

                    @Override
                    public void onSubscribe(Flow.Subscription sub) {
                        this.subscription = sub;
                        downstream.onSubscribe(sub);
                    }

                    @Override
                    public void onNext(EntityEvent event) {
                        try {
                            if (!matchesFilter(event, filterId, activeTenantId)) {
                                subscription.request(1);
                                return;
                            }

                            Object mapped = resolveEventPayload(event, activeTenantId);
                            if (mapped == null) {
                                subscription.request(1);
                                return;
                            }
                            downstream.onNext(mapped);
                        } catch (Throwable t) {
                            log.error("Error processing subscription event for {}: {}", model.name(), t.getMessage(), t);
                            downstream.onError(t);
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        downstream.onError(throwable);
                    }

                    @Override
                    public void onComplete() {
                        downstream.onComplete();
                    }
                });
            }
        };

        return FlowAdapters.toPublisher(transformedFlowPublisher);
    }

    private boolean matchesFilter(EntityEvent event, Object filterId, String activeTenantId) {
        if (event == null) {
            return false;
        }

        // 1. Zero cross-tenant event leak check
        if (model.isMultiTenant()) {
            if (activeTenantId != null && !activeTenantId.equalsIgnoreCase(event.tenantId())) {
                return false;
            }
            if (activeTenantId == null && event.tenantId() != null) {
                return false;
            }
        }

        // 2. ID filter check
        if (filterId != null) {
            if (event.entityId() == null) {
                return false;
            }
            String filterStr = String.valueOf(filterId);
            String eventIdStr = String.valueOf(event.entityId());
            if (!filterStr.equals(eventIdStr)) {
                return false;
            }
        }

        return true;
    }

    private Object resolveEventPayload(EntityEvent event, String activeTenantId) {
        if (eventType == NotifyEventType.DELETE) {
            return event.entityId();
        }

        String pkField = model.primaryKeyFields().isEmpty() ? "id" : model.primaryKeyFields().getFirst();
        String pkColumn = model.findField(pkField).map(FieldMetadata::dbColumnName).orElse(pkField);

        if (event.entityId() != null) {
            WherePredicate where = WherePredicate.eq(pkColumn, event.entityId());
            if (model.isMultiTenant() && activeTenantId != null) {
                where = where.and(WherePredicate.eq(model.tenantColumn(), activeTenantId));
            }

            SelectQuery selectQuery = SelectQuery.from(model.tableName())
                    .column("*")
                    .where(where)
                    .limit(1)
                    .build();

            try {
                Optional<Map<String, Object>> entityRow;
                if (model.isMultiTenant() && activeTenantId != null) {
                    entityRow = TenantContext.supplyAsTenant(activeTenantId, () -> executionEngine.executeSelectOne(selectQuery));
                } else {
                    entityRow = executionEngine.executeSelectOne(selectQuery);
                }

                if (entityRow.isPresent()) {
                    return EntityRowNormalizer.normalize(entityRow.get(), model);
                }
            } catch (Exception e) {
                log.warn("Failed to re-query entity {} with ID {}: {}. Falling back to event payload.",
                        model.name(), event.entityId(), e.getMessage());
            }
        }

        if (event.payload() != null && !event.payload().isEmpty()) {
            return EntityRowNormalizer.normalize(event.payload(), model);
        }

        return null;
    }

    private String resolveActiveTenantId(DataFetchingEnvironment env) {
        Optional<String> ctxTenant = TenantContext.getCurrentTenantId();
        if (ctxTenant.isPresent() && !ctxTenant.get().isBlank()) {
            return ctxTenant.get();
        }

        if (env.getGraphQlContext() != null) {
            Object tenantObj = env.getGraphQlContext().get("tenantId");
            if (tenantObj != null && !tenantObj.toString().isBlank()) {
                return tenantObj.toString().trim();
            }
        }

        if (env.containsArgument("tenantId") && env.getArgument("tenantId") != null) {
            return env.getArgument("tenantId").toString().trim();
        }

        return null;
    }
}
