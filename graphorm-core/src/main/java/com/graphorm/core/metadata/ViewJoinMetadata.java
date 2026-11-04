package com.graphorm.core.metadata;

import com.graphorm.core.query.JoinType;

import java.util.Objects;

/**
 * Immutable metadata record defining a joined model in a GraphORM-defined virtual view.
 *
 * @param joinType    the type of join (INNER, LEFT, RIGHT, FULL)
 * @param targetModel the target model being joined
 * @param alias       the table/model alias within the view query
 * @param onCondition the join condition expression (e.g. "s.departmentId == d.id")
 */
public record ViewJoinMetadata(
        JoinType joinType,
        String targetModel,
        String alias,
        String onCondition
) {

    /**
     * Compact constructor enforcing immutability and argument validation.
     */
    public ViewJoinMetadata {
        Objects.requireNonNull(joinType, "Join type must not be null");
        Objects.requireNonNull(targetModel, "Target model must not be null");
        if (targetModel.isBlank()) {
            throw new IllegalArgumentException("Target model must not be blank");
        }
        Objects.requireNonNull(alias, "Alias must not be null");
        if (alias.isBlank()) {
            throw new IllegalArgumentException("Alias must not be blank");
        }
        Objects.requireNonNull(onCondition, "Join condition must not be null");
        if (onCondition.isBlank()) {
            throw new IllegalArgumentException("Join condition must not be blank");
        }
    }

    /**
     * Creates a new builder for {@link ViewJoinMetadata}.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link ViewJoinMetadata}.
     */
    public static final class Builder {
        private JoinType joinType = JoinType.LEFT;
        private String targetModel;
        private String alias;
        private String onCondition;

        public Builder joinType(JoinType joinType) {
            this.joinType = joinType;
            return this;
        }

        public Builder targetModel(String targetModel) {
            this.targetModel = targetModel;
            return this;
        }

        public Builder alias(String alias) {
            this.alias = alias;
            return this;
        }

        public Builder onCondition(String onCondition) {
            this.onCondition = onCondition;
            return this;
        }

        public ViewJoinMetadata build() {
            return new ViewJoinMetadata(joinType, targetModel, alias, onCondition);
        }
    }
}
