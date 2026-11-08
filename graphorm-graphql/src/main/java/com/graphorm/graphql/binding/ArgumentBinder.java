package com.graphorm.graphql.binding;

import com.graphorm.core.metadata.EntityMetadata;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.query.NullsOrder;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.OrderDirection;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.graphql.fetcher.EntityRowNormalizer;
import graphql.schema.DataFetchingEnvironment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Translates GraphQL operation arguments (e.g. {@code where}, {@code orderBy}, {@code limit}, {@code offset})
 * into strongly typed GraphORM Query AST nodes ({@link WherePredicate}, {@link OrderByClause}).
 */
public final class ArgumentBinder {

    private static final String ARG_WHERE = "where";
    private static final String ARG_ORDER_BY = "orderBy";
    private static final String ARG_LIMIT = "limit";
    private static final String ARG_FIRST = "first";
    private static final String ARG_TAKE = "take";
    private static final String ARG_OFFSET = "offset";
    private static final String ARG_SKIP = "skip";

    public ArgumentBinder() {}

    /**
     * Extracts and binds the {@code where} argument from the execution environment.
     *
     * @param env    the GraphQL data fetching environment
     * @param entity the target entity metadata
     * @return the bound {@link WherePredicate}, or null if no where argument is present
     */
    public WherePredicate bindWhere(DataFetchingEnvironment env, EntityMetadata entity) {
        if (env == null) {
            return null;
        }
        Object whereArg = env.getArgument(ARG_WHERE);
        return bindWhere(whereArg, entity);
    }

    /**
     * Binds a where input object (typically a {@link Map}) into a {@link WherePredicate}.
     *
     * @param whereArg the where argument value
     * @param entity   the target entity metadata (used to resolve physical column names)
     * @return the bound {@link WherePredicate}, or null if whereArg is empty or null
     */
    @SuppressWarnings("unchecked")
    public WherePredicate bindWhere(Object whereArg, EntityMetadata entity) {
        if (whereArg == null) {
            return null;
        }
        if (!(whereArg instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException("Expected 'where' argument to be a Map, but got: " + whereArg.getClass());
        }
        if (rawMap.isEmpty()) {
            return null;
        }

        List<WherePredicate> predicates = new ArrayList<>();

        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            String key = Objects.toString(entry.getKey(), "").trim();
            Object val = entry.getValue();

            if (key.isBlank()) {
                continue;
            }

            if ("AND".equalsIgnoreCase(key)) {
                if (val instanceof Collection<?> list) {
                    List<WherePredicate> andChildren = new ArrayList<>();
                    for (Object child : list) {
                        WherePredicate p = bindWhere(child, entity);
                        if (p != null) {
                            andChildren.add(p);
                        }
                    }
                    if (!andChildren.isEmpty()) {
                        predicates.add(WherePredicate.and(andChildren));
                    }
                }
            } else if ("OR".equalsIgnoreCase(key)) {
                if (val instanceof Collection<?> list) {
                    List<WherePredicate> orChildren = new ArrayList<>();
                    for (Object child : list) {
                        WherePredicate p = bindWhere(child, entity);
                        if (p != null) {
                            orChildren.add(p);
                        }
                    }
                    if (!orChildren.isEmpty()) {
                        predicates.add(WherePredicate.or(orChildren));
                    }
                }
            } else if ("NOT".equalsIgnoreCase(key)) {
                WherePredicate child = bindWhere(val, entity);
                if (child != null) {
                    predicates.add(WherePredicate.not(child));
                }
            } else {
                // Key is a field name
                FieldMetadata field = entity != null ? entity.findField(key).orElse(null) : null;
                String column = resolveColumnName(key, entity);
                WherePredicate fieldPredicate = bindFieldPredicate(column, val, field);
                if (fieldPredicate != null) {
                    predicates.add(fieldPredicate);
                }
            }
        }

        if (predicates.isEmpty()) {
            return null;
        }
        if (predicates.size() == 1) {
            return predicates.getFirst();
        }
        return WherePredicate.and(predicates);
    }

    /**
     * Binds a field comparison predicate, supporting scalar equality or nested operator objects.
     */
    @SuppressWarnings("unchecked")
    private WherePredicate bindFieldPredicate(String column, Object value, FieldMetadata field) {
        if (value == null) {
            return WherePredicate.isNull(column);
        }

        if (value instanceof Map<?, ?> opMap) {
            List<WherePredicate> opPredicates = new ArrayList<>();
            for (Map.Entry<?, ?> opEntry : opMap.entrySet()) {
                String op = Objects.toString(opEntry.getKey(), "").trim().toLowerCase(Locale.ROOT);
                Object opVal = opEntry.getValue();
                WherePredicate p = bindOperator(column, op, opVal, field);
                if (p != null) {
                    opPredicates.add(p);
                }
            }
            if (opPredicates.isEmpty()) {
                return null;
            }
            return opPredicates.size() == 1 ? opPredicates.getFirst() : WherePredicate.and(opPredicates);
        }

        // Direct scalar equality
        Object coerced = EntityRowNormalizer.coerceToFieldType(value, field);
        return WherePredicate.eq(column, coerced);
    }

    @SuppressWarnings("unchecked")
    private WherePredicate bindOperator(String column, String op, Object opVal, FieldMetadata field) {
        Object coercedVal = EntityRowNormalizer.coerceToFieldType(opVal, field);

        return switch (op) {
            case "eq", "equals" -> WherePredicate.eq(column, coercedVal);
            case "neq", "ne", "notequals" -> WherePredicate.neq(column, coercedVal);
            case "gt" -> WherePredicate.gt(column, coercedVal);
            case "gte", "ge" -> WherePredicate.gte(column, coercedVal);
            case "lt" -> WherePredicate.lt(column, coercedVal);
            case "lte", "le" -> WherePredicate.lte(column, coercedVal);
            case "like" -> WherePredicate.like(column, Objects.toString(opVal, ""));
            case "ilike" -> WherePredicate.ilike(column, Objects.toString(opVal, ""));
            case "contains" -> WherePredicate.contains(column, Objects.toString(opVal, ""));
            case "icontains", "containsignorecase" -> WherePredicate.iContains(column, Objects.toString(opVal, ""));
            case "startswith" -> WherePredicate.startsWith(column, Objects.toString(opVal, ""));
            case "istartswith", "startswithignorecase" -> WherePredicate.iStartsWith(column, Objects.toString(opVal, ""));
            case "endswith" -> WherePredicate.endsWith(column, Objects.toString(opVal, ""));
            case "iendswith", "endswithignorecase" -> WherePredicate.iEndsWith(column, Objects.toString(opVal, ""));
            case "in" -> {
                if (opVal instanceof Collection<?> col) {
                    List<Object> coercedList = col.stream()
                            .map(item -> EntityRowNormalizer.coerceToFieldType(item, field))
                            .toList();
                    yield WherePredicate.in(column, coercedList);
                }
                yield WherePredicate.in(column, Collections.singletonList(coercedVal));
            }
            case "notin" -> {
                if (opVal instanceof Collection<?> col) {
                    List<Object> coercedList = col.stream()
                            .map(item -> EntityRowNormalizer.coerceToFieldType(item, field))
                            .toList();
                    yield WherePredicate.notIn(column, coercedList);
                }
                yield WherePredicate.notIn(column, Collections.singletonList(coercedVal));
            }
            case "isnull" -> {
                boolean isNull = Boolean.parseBoolean(Objects.toString(opVal, "true"));
                yield isNull ? WherePredicate.isNull(column) : WherePredicate.isNotNull(column);
            }
            case "isnotnull" -> {
                boolean isNotNull = Boolean.parseBoolean(Objects.toString(opVal, "true"));
                yield isNotNull ? WherePredicate.isNotNull(column) : WherePredicate.isNull(column);
            }
            case "between" -> bindBetween(column, opVal, field, false);
            case "notbetween" -> bindBetween(column, opVal, field, true);
            default -> throw new IllegalArgumentException("Unsupported filter operator: '" + op + "' on column '" + column + "'");
        };
    }

    private WherePredicate bindBetween(String column, Object opVal, FieldMetadata field, boolean negate) {
        Object lower;
        Object upper;
        if (opVal instanceof List<?> list && list.size() >= 2) {
            lower = EntityRowNormalizer.coerceToFieldType(list.get(0), field);
            upper = EntityRowNormalizer.coerceToFieldType(list.get(1), field);
        } else if (opVal instanceof Map<?, ?> map) {
            lower = EntityRowNormalizer.coerceToFieldType(map.get("lower"), field);
            upper = EntityRowNormalizer.coerceToFieldType(map.get("upper"), field);
        } else {
            throw new IllegalArgumentException("Expected 'between' operand to be a 2-element list or map with lower/upper, but got: " + opVal);
        }
        WherePredicate p = WherePredicate.between(column, lower, upper);
        return negate ? WherePredicate.not(p) : p;
    }

    /**
     * Extracts and binds the {@code orderBy} argument from the execution environment.
     *
     * @param env    the GraphQL data fetching environment
     * @param entity the target entity metadata
     * @return immutable list of {@link OrderByClause} nodes
     */
    public List<OrderByClause> bindOrderBy(DataFetchingEnvironment env, EntityMetadata entity) {
        if (env == null) {
            return List.of();
        }
        Object orderByArg = env.getArgument(ARG_ORDER_BY);
        return bindOrderBy(orderByArg, entity);
    }

    /**
     * Binds an orderBy input into a list of {@link OrderByClause} nodes.
     *
     * @param orderByArg the orderBy argument value (Map, List, or String)
     * @param entity     the target entity metadata
     * @return immutable list of OrderByClause
     */
    @SuppressWarnings("unchecked")
    public List<OrderByClause> bindOrderBy(Object orderByArg, EntityMetadata entity) {
        if (orderByArg == null) {
            return List.of();
        }

        List<OrderByClause> clauses = new ArrayList<>();

        if (orderByArg instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String fieldName = Objects.toString(entry.getKey(), "").trim();
                String column = resolveColumnName(fieldName, entity);
                OrderDirection direction = parseDirection(entry.getValue());
                clauses.add(new OrderByClause(column, direction, NullsOrder.DEFAULT));
            }
        } else if (orderByArg instanceof Collection<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> itemMap) {
                    for (Map.Entry<?, ?> entry : itemMap.entrySet()) {
                        String fieldName = Objects.toString(entry.getKey(), "").trim();
                        String column = resolveColumnName(fieldName, entity);
                        OrderDirection direction = parseDirection(entry.getValue());
                        clauses.add(new OrderByClause(column, direction, NullsOrder.DEFAULT));
                    }
                } else if (item != null) {
                    clauses.add(parseStringOrderBy(item.toString(), entity));
                }
            }
        } else if (orderByArg instanceof String str) {
            clauses.add(parseStringOrderBy(str, entity));
        }

        return List.copyOf(clauses);
    }

    private OrderByClause parseStringOrderBy(String str, EntityMetadata entity) {
        String trimmed = str.trim();
        if (trimmed.contains("_")) {
            String[] parts = trimmed.split("_");
            String column = resolveColumnName(parts[0], entity);
            OrderDirection dir = parseDirection(parts[1]);
            return new OrderByClause(column, dir, NullsOrder.DEFAULT);
        }
        return OrderByClause.asc(resolveColumnName(trimmed, entity));
    }

    private OrderDirection parseDirection(Object value) {
        if (value == null) {
            return OrderDirection.ASC;
        }
        String str = value.toString().trim().toUpperCase(Locale.ROOT);
        if (str.startsWith("DESC")) {
            return OrderDirection.DESC;
        }
        return OrderDirection.ASC;
    }

    /**
     * Extracts the {@code limit} or {@code first} pagination argument.
     *
     * @param env the GraphQL data fetching environment
     * @return positive limit, or null if not provided
     */
    public Integer bindLimit(DataFetchingEnvironment env) {
        if (env == null) {
            return null;
        }
        Object limitVal = env.getArgument(ARG_LIMIT);
        if (limitVal == null) {
            limitVal = env.getArgument(ARG_FIRST);
        }
        if (limitVal == null) {
            limitVal = env.getArgument(ARG_TAKE);
        }
        return parseInteger(limitVal);
    }

    /**
     * Extracts the {@code offset} or {@code skip} pagination argument.
     *
     * @param env the GraphQL data fetching environment
     * @return non-negative offset, or null if not provided
     */
    public String bindAfter(DataFetchingEnvironment env) {
        if (env == null) return null;
        Object val = env.getArgument("after");
        return val != null ? val.toString() : null;
    }

    public String bindBefore(DataFetchingEnvironment env) {
        if (env == null) return null;
        Object val = env.getArgument("before");
        return val != null ? val.toString() : null;
    }

    public Integer bindFirst(DataFetchingEnvironment env) {
        if (env == null) return null;
        return parseInteger(env.getArgument("first"));
    }

    public Integer bindLast(DataFetchingEnvironment env) {
        if (env == null) return null;
        return parseInteger(env.getArgument("last"));
    }

    public Integer bindOffset(DataFetchingEnvironment env) {
        if (env == null) {
            return null;
        }
        Object offsetVal = env.getArgument(ARG_OFFSET);
        if (offsetVal == null) {
            offsetVal = env.getArgument(ARG_SKIP);
        }
        return parseInteger(offsetVal);
    }

    private Integer parseInteger(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof Number num) {
            return num.intValue();
        }
        try {
            return Integer.parseInt(val.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer parameter: " + val, e);
        }
    }

    private String resolveColumnName(String fieldName, EntityMetadata entity) {
        if (entity == null) {
            return fieldName;
        }
        Optional<FieldMetadata> field = entity.findField(fieldName);
        if (field.isPresent()) {
            if (entity instanceof ModelMetadata) {
                return field.get().dbColumnName();
            } else {
                return field.get().name();
            }
        }
        return fieldName;
    }
}
