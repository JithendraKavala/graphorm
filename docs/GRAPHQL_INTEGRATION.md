# GraphORM: GraphQL Integration & Runtime Architecture

## 1. Integration Strategy

GraphORM integrates directly with **GraphQL Java** and **Spring for GraphQL**. It acts as an automatic data layer binding both physical database models (`model`) and declarative view models (`view`) to GraphQL schema definitions without requiring handwritten DTOs, DAOs, or DataFetchers.

## 2. Dynamic DataFetcher Registration

During application startup, `GraphOrmGraphQlRegistrar` inspects both the GraphQL Schema and the `ModelRegistry`:

```text
For each Type in GraphQL Schema:
    If Type matches a Regular Model in ModelRegistry:
        1. Register Query.<model>s          -> GenericListFetcher (filtering, sorting, pagination)
        2. Register Query.<model>           -> GenericSingleFetcher
        3. Register Mutation.create<Model>  -> GenericCreateFetcher
        4. Register Mutation.update<Model>  -> GenericUpdateFetcher
        5. Register Mutation.delete<Model>  -> GenericDeleteFetcher
        
        For each RelationField on Model:
            Register Type.<relationField>   -> GenericRelationFetcher (via DataLoader)

    If Type matches a View Model in ModelRegistry:
        1. Register Query.<view>s           -> GenericViewListFetcher (filtering, sorting, pagination)
        2. Register Query.<view>            -> GenericViewSingleFetcher
        3. STRICT INVARIANT: DO NOT register any Mutation.<view> fetchers (read-only enforced)
        
        For each RelationField or Nested View on View Model:
            Register Type.<relationField>   -> GenericViewRelationFetcher (via DataLoader)
```

## 3. View Model GraphQL Exposure

View models are automatically exposed as read-only GraphQL object types and queries:

```graphql
type StudentDashboard {
    studentId: Int!
    studentName: String!
    departmentName: String
    courseCount: Int!
    gpa: Float
    primaryAddress: Address
}

type Query {
    # Regular Model Queries
    students(where: StudentFilter, orderBy: StudentOrderBy, limit: Int, offset: Int): [Student!]!
    student(id: ID!): Student

    # View Model Queries (Read-Only)
    studentDashboards(where: StudentDashboardFilter, orderBy: StudentDashboardOrderBy, limit: Int, offset: Int): [StudentDashboard!]!
    studentDashboard(studentId: Int!): StudentDashboard
}

# Mutations are strictly limited to regular models.
type Mutation {
    createStudent(input: StudentCreateInput!): Student!
    updateStudent(id: ID!, input: StudentUpdateInput!): Student!
    deleteStudent(id: ID!): Boolean!
    # No createStudentDashboard, updateStudentDashboard, or deleteStudentDashboard exists!
}
```

## 4. Selection-Set Projection & DataLoader Batching for Views

When resolving view models:
1. **Selection Set Analysis**: If a client requests only `{ studentName courseCount }`, the query planner plans SQL that selects only `s.name` and `COUNT(e.id)`, omitting unrequested fields (like `departmentName` or `gpa`).
2. **Nested Relationships on Views**: If a view exposes relationships to regular models (or other views), the query planner auto-injects hidden foreign key identifiers.
3. **N+1 Elimination**: When traversing nested collections from views, GraphORM queues keys into a `DataLoader` batch lookup, preventing N+1 queries.

## 5. Coexistence with Custom Resolvers

GraphORM never overrides custom resolvers:
- If a Spring controller declares `@QueryMapping public StudentDashboard studentDashboard(@Argument Integer studentId)`, Spring for GraphQL registers the custom controller method first.
- GraphORM checks if a DataFetcher is already registered for coordinates (`Query`, `studentDashboard`). If so, it skips automatic registration.
- Custom resolvers can inject `GraphOrmClient` (e.g. `db.studentDashboard().findUnique(...)`) to execute view queries with full type safety and selection awareness.
