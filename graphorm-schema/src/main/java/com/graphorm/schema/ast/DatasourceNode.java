package com.graphorm.schema.ast;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Datasource declaration block (e.g. datasource db { provider = "postgresql" }).
 *
 * @param position   source coordinate
 * @param name       datasource name (e.g. "db")
 * @param properties declared configuration properties
 */
public record DatasourceNode(
        SourcePosition position,
        String name,
        Map<String, ExpressionNode> properties
) implements AstNode {

    public DatasourceNode {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(name, "name must not be null");
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }

    public Optional<ExpressionNode> getProperty(String propertyName) {
        if (propertyName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(properties.get(propertyName));
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitDatasource(this);
    }
}
