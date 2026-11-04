package com.graphorm.schema.ast;

/**
 * Root interface for all Abstract Syntax Tree (AST) nodes in GraphORM.
 */
public interface AstNode {

    /**
     * Source coordinates where this AST node was declared.
     *
     * @return source position
     */
    SourcePosition position();

    /**
     * Accepts a visitor dispatching on the concrete AST node type.
     *
     * @param visitor the AST visitor
     * @param <R>     return type
     * @return visitor evaluation result
     */
    <R> R accept(AstVisitor<R> visitor);
}
