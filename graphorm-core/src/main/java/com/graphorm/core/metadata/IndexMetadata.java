package com.graphorm.core.metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable metadata record representing a database index.
 *
 * @param name     the index name (e.g. "idx_student_email")
 * @param fields   the list of indexed field/column names
 * @param isUnique whether this index enforces uniqueness
 * @param type     the index access method/type (e.g. BTREE, GIN)
 */
public record IndexMetadata(
        String name,
        List<String> fields,
        boolean isUnique,
        IndexType type
) {

    public IndexMetadata {
        Objects.requireNonNull(name, "Index name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Index name must not be blank");
        }
        Objects.requireNonNull(fields, "Index fields must not be null");
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("Index must specify at least one field");
        }
        fields = List.copyOf(fields);
        type = type != null ? type : IndexType.BTREE;
    }

    public IndexMetadata(String name, List<String> fields) {
        this(name, fields, false, IndexType.BTREE);
    }

    public IndexMetadata(String name, List<String> fields, boolean isUnique) {
        this(name, fields, isUnique, IndexType.BTREE);
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private final List<String> fields = new ArrayList<>();
        private boolean isUnique = false;
        private IndexType type = IndexType.BTREE;

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "Index name must not be null");
        }

        public Builder addField(String field) {
            this.fields.add(Objects.requireNonNull(field, "Field must not be null"));
            return this;
        }

        public Builder fields(List<String> fields) {
            this.fields.clear();
            if (fields != null) {
                this.fields.addAll(fields);
            }
            return this;
        }

        public Builder fields(String... fields) {
            this.fields.clear();
            if (fields != null) {
                this.fields.addAll(List.of(fields));
            }
            return this;
        }

        public Builder unique(boolean isUnique) {
            this.isUnique = isUnique;
            return this;
        }

        public Builder type(IndexType type) {
            this.type = type != null ? type : IndexType.BTREE;
            return this;
        }

        public IndexMetadata build() {
            return new IndexMetadata(name, fields, isUnique, type);
        }
    }
}
