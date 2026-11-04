package com.graphorm.schema.ast;

/**
 * Visitor interface for traversing the GraphORM AST node hierarchy.
 *
 * @param <R> return type of visitor operations
 */
public interface AstVisitor<R> {

    default R visitSchemaDocument(SchemaDocument document) {
        return null;
    }

    default R visitDatasource(DatasourceNode datasource) {
        return null;
    }

    default R visitGenerator(GeneratorNode generator) {
        return null;
    }

    default R visitModel(ModelNode model) {
        return null;
    }

    default R visitViewModel(ViewModelNode viewModel) {
        return null;
    }

    default R visitCompositeType(CompositeTypeNode compositeType) {
        return null;
    }

    default R visitEnum(EnumNode enumNode) {
        return null;
    }

    default R visitEnumValue(EnumValueNode enumValue) {
        return null;
    }

    default R visitField(FieldNode field) {
        return null;
    }

    default R visitType(TypeNode type) {
        return null;
    }

    default R visitFieldAttribute(FieldAttributeNode attribute) {
        return null;
    }

    default R visitBlockAttribute(BlockAttributeNode attribute) {
        return null;
    }

    default R visitArgument(ArgumentNode argument) {
        return null;
    }

    default R visitStringLiteral(StringLiteralNode literal) {
        return null;
    }

    default R visitIntLiteral(IntLiteralNode literal) {
        return null;
    }

    default R visitFloatLiteral(FloatLiteralNode literal) {
        return null;
    }

    default R visitBooleanLiteral(BooleanLiteralNode literal) {
        return null;
    }

    default R visitIdentifier(IdentifierNode identifier) {
        return null;
    }

    default R visitQualifiedIdentifier(QualifiedIdentifierNode qualifiedIdentifier) {
        return null;
    }

    default R visitArrayLiteral(ArrayLiteralNode arrayLiteral) {
        return null;
    }

    default R visitFunctionCall(FunctionCallNode functionCall) {
        return null;
    }
}
