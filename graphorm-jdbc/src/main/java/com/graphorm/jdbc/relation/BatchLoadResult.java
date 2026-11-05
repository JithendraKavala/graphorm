package com.graphorm.jdbc.relation;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable container holding batched relationship resolution results, providing
 * association lookups for both to-one (1:1, N:1) and to-many (1:N, N:M) cardinalities.
 *
 * @param <P> parent entity type
 * @param <C> child/related entity type
 * @param oneToOne  map associating each parent entity to its single related child (if to-one)
 * @param oneToMany map associating each parent entity to its list of related children (if to-many)
 * @param byKey     map associating each normalized join key to its matching children
 */
public record BatchLoadResult<P, C>(
        Map<P, C> oneToOne,
        Map<P, List<C>> oneToMany,
        Map<Object, List<C>> byKey
) {

    public BatchLoadResult {
        oneToOne = oneToOne == null ? Map.of() : Map.copyOf(oneToOne);
        oneToMany = oneToMany == null ? Map.of() : Map.copyOf(oneToMany);
        byKey = byKey == null ? Map.of() : Map.copyOf(byKey);
    }

    /**
     * Returns an empty batch load result.
     *
     * @param <P> parent type
     * @param <C> child type
     * @return empty BatchLoadResult
     */
    public static <P, C> BatchLoadResult<P, C> empty() {
        return new BatchLoadResult<>(Map.of(), Map.of(), Map.of());
    }

    /**
     * Looks up the single related entity associated with the given parent.
     *
     * @param parent the parent entity
     * @return optional containing the child entity if present, or empty
     */
    public Optional<C> getOne(P parent) {
        return Optional.ofNullable(oneToOne.get(parent));
    }

    /**
     * Looks up the list of related entities associated with the given parent.
     *
     * @param parent the parent entity
     * @return list of child entities (never null, empty if none)
     */
    public List<C> getMany(P parent) {
        return oneToMany.getOrDefault(parent, List.of());
    }
}
