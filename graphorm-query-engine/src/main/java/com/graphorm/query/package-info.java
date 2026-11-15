/**
 * GraphORM Query Engine module.
 *
 * <p>This module owns the query representation, filters, sorting, pagination,
 * SQL compilation, and execution orchestration. Both the GraphQL interface and
 * the Java client API share the same underlying query engine provided by this module.
 *
 * <p>This module depends on {@code graphorm-core} for metadata abstractions and
 * delegates SQL execution to {@code graphorm-jdbc}. It must not import JDBC classes directly.
 *
 * <p>Implementation begins at Milestone M4.
 */
package com.graphorm.query;
