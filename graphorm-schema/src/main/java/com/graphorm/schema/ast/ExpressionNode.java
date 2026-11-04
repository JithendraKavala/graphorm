package com.graphorm.schema.ast;

/**
 * Marker interface for AST nodes that can act as expression values in attribute arguments or configs.
 */
public sealed interface ExpressionNode extends AstNode
        permits StringLiteralNode,
                IntLiteralNode,
                FloatLiteralNode,
                BooleanLiteralNode,
                IdentifierNode,
                QualifiedIdentifierNode,
                ArrayLiteralNode,
                FunctionCallNode {
}
