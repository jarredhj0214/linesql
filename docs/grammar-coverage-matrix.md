# Grammar Coverage Matrix

This document defines the grammar-driven development plan for LineSQL.

LineSQL should not grow only by patching one production SQL case at a time. SQL cases are compatibility assets and regression tests. The implementation roadmap is driven by dialect grammar domains first, then validated by SQL cases.

## Development Rule

For every dialect capability, keep these artifacts aligned:

| Artifact | Role |
| --- | --- |
| ANTLR grammar | Defines the syntax surface LineSQL accepts. |
| Lineage visitor | Maps grammar nodes to table lineage, column lineage, and column usages. |
| SQL case file | Captures a real or representative SQL shape. |
| Manifest entry | Records expected behavior and compatibility contract. |
| Supported scenarios doc | Explains the user-visible capability. |

The preferred implementation order is:

1. Extend or verify the dialect grammar rule.
2. Add or update visitor behavior for the grammar node.
3. Add focused SQL cases under the dialect test resources.
4. Update the manifest expected lineage.
5. Update user-facing support documentation.

## Status Legend

| Status | Meaning |
| --- | --- |
| `COVERED` | Grammar, visitor behavior, and SQL cases exist for common lineage needs. |
| `PARTIAL` | Some common shapes are supported, but grammar or lineage behavior is incomplete. |
| `GRAMMAR_ONLY` | Syntax is recognized, but lineage behavior is limited or missing. |
| `PLANNED` | Targeted for implementation, not yet covered. |
| `N/A` | Not expected for the dialect or not relevant to lineage. |

## Lineage Dimensions

Each grammar domain should be evaluated across these lineage outputs:

| Dimension | Meaning |
| --- | --- |
| Table lineage | Source tables and target or affected tables. |
| Column lineage | Source-to-target column mappings for projections, inserts, CTAS, views, updates, and merge actions. |
| Column usage | Clause-level columns used in `WHERE`, `JOIN`, `GROUP_BY`, `HAVING`, `ORDER_BY`, and DML predicates. |
| Degradation | Partial result and diagnostics when syntax or lineage is incomplete. |

## Grammar Domains

These domains are the shared planning vocabulary across dialects.

| Domain | Representative grammar rules | Expected lineage behavior |
| --- | --- | --- |
| Statement dispatch | `statement`, statement alternatives | Detect statement type and route to the correct visitor path. |
| Query core | `query`, `queryTerm`, `queryPrimary`, `querySpecification` | Resolve SELECT inputs and projection lineage. |
| CTE | `WITH`, named query rules | Propagate table and column lineage through CTE references. |
| Relation | `relation`, `relationPrimary`, table name, alias, derived query | Track visible tables, aliases, derived relations, and scopes. |
| Join | join relation and join criteria rules | Track join source tables and join predicate column usages. |
| Set operation | `UNION`, `INTERSECT`, `EXCEPT` | Merge branch table lineage and column lineage by projection position. |
| Expression | column reference, dereference, function, arithmetic, cast, case | Extract source columns from expressions and build dependency edges. |
| Predicate subquery | `IN`, `EXISTS`, scalar subquery | Track both outer predicate columns and subquery source columns. |
| Aggregation | aggregate functions, `GROUP BY`, `HAVING` | Map aggregate inputs and group/having column usages. |
| Window | window function, over clause | Map function arguments and partition/order column usages. |
| Insert | `INSERT`, target columns, query/values | Track target tables and target-column mappings. |
| Update | `UPDATE`, assignments, optional source relations | Track affected table, assignment lineage, and predicate usages. |
| Delete | `DELETE`, optional source relations | Track affected table and predicate usages. |
| Merge | `MERGE`, source, matched/not-matched actions | Track target/source tables, merge predicates, update assignments, and insert values. |
| CTAS | `CREATE TABLE AS SELECT` | Track target table and output column lineage. |
| Create view | `CREATE VIEW AS SELECT` | Track target view and output column lineage. |
| DDL affected table | `ALTER`, `DROP`, `TRUNCATE`, `RENAME`, comments | Track affected tables without pretending query lineage exists. |
| Dialect extensions | Engine-specific syntax | Preserve dialect-specific anchors and lineage where relevant. |

## Current Dialect Grammar Sources

| Dialect | Grammar source | Notes |
| --- | --- | --- |
| Spark | `linesql-dialect-spark/src/main/antlr4/io/github/linesql/dialect/spark/antlr/SqlBaseParser.g4` | Broad Spark grammar baseline. |
| MySQL | `linesql-dialect-mysql/src/main/antlr4/io/github/linesql/dialect/mysql/antlr/MySqlParser.g4` | Lightweight lineage grammar for common MySQL platform SQL. |
| Hive | `linesql-dialect-hive/src/main/antlr4/io/github/linesql/dialect/hive/antlr/HiveParser.g4` | Lightweight lineage grammar with Hive DDL extensions. |
| Flink | `linesql-dialect-flink/src/main/antlr4/io/github/linesql/dialect/flink/antlr/FlinkParser.g4` | Lightweight lineage grammar with Flink connector DDL extensions. |
| StarRocks | `linesql-dialect-starrocks/src/main/antlr4/io/github/linesql/dialect/starrocks/antlr/StarRocksParser.g4` | Lightweight lineage grammar with StarRocks table model syntax. |
| Oracle | `linesql-dialect-oracle/src/main/antlr4/io/github/linesql/dialect/oracle/antlr/OracleParser.g4` | Lightweight lineage grammar with Oracle query and DML syntax anchors. |
| SQL Server | `linesql-dialect-sqlserver/src/main/antlr4/io/github/linesql/dialect/sqlserver/antlr/SqlServerParser.g4` | Lightweight lineage grammar with SQL Server query and DML syntax anchors. |
| PostgreSQL | Planned | Should become an independent dialect grammar. |
| OceanBase | Planned | Should be modeled by compatibility mode: OceanBase MySQL mode and OceanBase Oracle mode. |

## Dialect Variant Policy

LineSQL keeps one public dialect when syntax differences are mostly connector options, feature flags, or version-gated extensions. It introduces a separate dialect or dialect profile only when grammar routing, identifier rules, statement forms, or lineage semantics materially differ.

| Topic | Decision | Reason |
| --- | --- | --- |
| Flink SQL vs Flink CDC SQL | Keep `FLINK` as the dialect and model CDC as Flink connector/table options. | CDC source connectors are expressed through Flink SQL DDL and `WITH` connector options; lineage should treat them as Flink table definitions plus connector metadata. |
| Flink CDC pipeline YAML | Out of SQL parser scope for now. | It is a declarative integration format, not SQL grammar. It can become a separate parser family later if required. |
| Spark versions | Keep one `SPARK` dialect with a documented version baseline and future version profiles. | Most lineage-critical grammar domains are stable enough to share visitor logic; version differences should be handled as feature gates before splitting modules. |
| OceanBase | Track as OceanBase with two compatibility modes: MySQL mode and Oracle mode. | The grammar surface follows different compatibility families, so reuse should come from MySQL/Oracle lineage domains with OceanBase-specific extensions. |
| PostgreSQL | Add as an independent planned dialect. | PostgreSQL has distinct DML forms, `RETURNING`, CTE semantics, conflict handling, and modern `MERGE` behavior that should not be hidden under generic SQL. |

## Dialect Matrix

### Spark

| Domain | Grammar status | Table lineage | Column lineage | Column usage | Priority |
| --- | --- | --- | --- | --- | --- |
| Statement dispatch | COVERED | COVERED | PARTIAL | PARTIAL | P0 |
| Query core | COVERED | COVERED | COVERED | COVERED | P0 |
| CTE | COVERED | COVERED | COVERED | PARTIAL | P0 |
| Relation and aliases | COVERED | COVERED | COVERED | COVERED | P0 |
| Join | COVERED | COVERED | COVERED | COVERED | P0 |
| Set operation | COVERED | COVERED | COVERED | COVERED | P1 |
| Expression | COVERED | COVERED | COVERED | PARTIAL | P0 |
| Predicate subquery | PARTIAL | COVERED | PARTIAL | COVERED | P0 |
| Aggregation | COVERED | COVERED | COVERED | COVERED | P1 |
| Window | PARTIAL | COVERED | PARTIAL | PARTIAL | P1 |
| Insert | COVERED | COVERED | COVERED | PARTIAL | P0 |
| Update | PARTIAL | COVERED | PARTIAL | PARTIAL | P1 |
| Delete | PARTIAL | COVERED | N/A | PARTIAL | P1 |
| Merge | PARTIAL | COVERED | PARTIAL | PARTIAL | P1 |
| CTAS | COVERED | COVERED | COVERED | PARTIAL | P0 |
| Create view | COVERED | COVERED | COVERED | PARTIAL | P0 |
| DDL affected table | PARTIAL | COVERED | N/A | N/A | P2 |
| Spark extensions | PARTIAL | PARTIAL | PARTIAL | PARTIAL | P1 |

### MySQL

| Domain | Grammar status | Table lineage | Column lineage | Column usage | Priority |
| --- | --- | --- | --- | --- | --- |
| Statement dispatch | PARTIAL | COVERED | PARTIAL | PARTIAL | P0 |
| Query core | PARTIAL | COVERED | COVERED | COVERED | P0 |
| CTE | PARTIAL | COVERED | COVERED | PARTIAL | P1 |
| Relation and aliases | PARTIAL | COVERED | COVERED | COVERED | P0 |
| Join | PARTIAL | COVERED | COVERED | COVERED | P0 |
| Set operation | PARTIAL | COVERED | COVERED | COVERED | P1 |
| Expression | PARTIAL | COVERED | COVERED | PARTIAL | P0 |
| Predicate subquery | PARTIAL | COVERED | PARTIAL | COVERED | P0 |
| Aggregation | PARTIAL | COVERED | COVERED | COVERED | P1 |
| Window | PARTIAL | COVERED | COVERED | COVERED | P1 |
| Insert | PARTIAL | COVERED | COVERED | PARTIAL | P0 |
| Update | PARTIAL | COVERED | PARTIAL | PARTIAL | P0 |
| Delete | PARTIAL | COVERED | N/A | PARTIAL | P0 |
| Merge | N/A | N/A | N/A | N/A | N/A |
| CTAS | PARTIAL | COVERED | COVERED | PARTIAL | P1 |
| Create view | PARTIAL | COVERED | COVERED | PARTIAL | P1 |
| DDL affected table | PARTIAL | COVERED | N/A | N/A | P2 |
| MySQL extensions | PARTIAL | COVERED | PARTIAL | PARTIAL | P0 |

### StarRocks

| Domain | Grammar status | Table lineage | Column lineage | Column usage | Priority |
| --- | --- | --- | --- | --- | --- |
| Statement dispatch | PARTIAL | COVERED | PARTIAL | PARTIAL | P0 |
| Query core | PARTIAL | COVERED | COVERED | COVERED | P0 |
| CTE | PARTIAL | COVERED | COVERED | PARTIAL | P1 |
| Relation and aliases | PARTIAL | COVERED | COVERED | COVERED | P0 |
| Join | PARTIAL | COVERED | COVERED | COVERED | P0 |
| Set operation | PARTIAL | COVERED | COVERED | COVERED | P1 |
| Expression | PARTIAL | COVERED | COVERED | PARTIAL | P0 |
| Predicate subquery | PARTIAL | COVERED | PARTIAL | COVERED | P0 |
| Aggregation | PARTIAL | COVERED | COVERED | COVERED | P1 |
| Window | PARTIAL | COVERED | COVERED | COVERED | P1 |
| Insert | PARTIAL | COVERED | COVERED | PARTIAL | P0 |
| Update | PARTIAL | COVERED | PARTIAL | COVERED | P0 |
| Delete | PARTIAL | COVERED | N/A | COVERED | P0 |
| Merge | PLANNED | PLANNED | PLANNED | PLANNED | P2 |
| CTAS | PARTIAL | COVERED | COVERED | PARTIAL | P1 |
| Create view | PARTIAL | COVERED | COVERED | PARTIAL | P1 |
| DDL affected table | PARTIAL | COVERED | N/A | N/A | P1 |
| StarRocks extensions | PARTIAL | COVERED | N/A | N/A | P1 |

### Hive

| Domain | Grammar status | Table lineage | Column lineage | Column usage | Priority |
| --- | --- | --- | --- | --- | --- |
| Statement dispatch | PARTIAL | COVERED | PARTIAL | PARTIAL | P0 |
| Query core | PARTIAL | COVERED | COVERED | COVERED | P0 |
| CTE | PARTIAL | COVERED | COVERED | PARTIAL | P1 |
| Relation and aliases | PARTIAL | COVERED | COVERED | COVERED | P0 |
| Join | PARTIAL | COVERED | COVERED | COVERED | P0 |
| Set operation | PARTIAL | COVERED | COVERED | COVERED | P1 |
| Expression | PARTIAL | COVERED | COVERED | PARTIAL | P0 |
| Predicate subquery | PARTIAL | COVERED | PARTIAL | COVERED | P0 |
| Aggregation | PARTIAL | COVERED | COVERED | COVERED | P1 |
| Window | PARTIAL | COVERED | COVERED | COVERED | P1 |
| Insert | PARTIAL | COVERED | COVERED | PARTIAL | P0 |
| Update | PARTIAL | COVERED | PARTIAL | COVERED | P1 |
| Delete | PARTIAL | COVERED | N/A | COVERED | P1 |
| Merge | PLANNED | PLANNED | PLANNED | PLANNED | P2 |
| CTAS | PARTIAL | COVERED | COVERED | PARTIAL | P1 |
| Create view | PARTIAL | COVERED | COVERED | PARTIAL | P1 |
| DDL affected table | PARTIAL | COVERED | N/A | N/A | P1 |
| Hive extensions | PARTIAL | COVERED | N/A | N/A | P1 |

### Flink

| Domain | Grammar status | Table lineage | Column lineage | Column usage | Priority |
| --- | --- | --- | --- | --- | --- |
| Statement dispatch | PARTIAL | COVERED | PARTIAL | PARTIAL | P0 |
| Query core | PARTIAL | COVERED | COVERED | COVERED | P0 |
| CTE | PARTIAL | COVERED | COVERED | PARTIAL | P1 |
| Relation and aliases | PARTIAL | COVERED | COVERED | COVERED | P0 |
| Join | PARTIAL | COVERED | COVERED | COVERED | P0 |
| Set operation | PARTIAL | COVERED | COVERED | COVERED | P1 |
| Expression | PARTIAL | COVERED | COVERED | PARTIAL | P0 |
| Predicate subquery | PARTIAL | COVERED | PARTIAL | COVERED | P0 |
| Aggregation | PARTIAL | COVERED | COVERED | COVERED | P1 |
| Window | PARTIAL | COVERED | COVERED | COVERED | P1 |
| Insert | PARTIAL | COVERED | COVERED | PARTIAL | P0 |
| Update | PARTIAL | COVERED | PARTIAL | COVERED | P1 |
| Delete | PARTIAL | COVERED | N/A | COVERED | P1 |
| Merge | PARTIAL | COVERED | PARTIAL | PARTIAL | P2 |
| CTAS | PARTIAL | COVERED | COVERED | PARTIAL | P1 |
| Create view | PARTIAL | COVERED | COVERED | PARTIAL | P1 |
| DDL affected table | PARTIAL | COVERED | N/A | N/A | P1 |
| Flink extensions | PARTIAL | COVERED | N/A | N/A | P1 |

### Oracle

| Domain | Grammar status | Table lineage | Column lineage | Column usage | Priority |
| --- | --- | --- | --- | --- | --- |
| Statement dispatch | PARTIAL | COVERED | PARTIAL | PARTIAL | P0 |
| Query core | PARTIAL | COVERED | COVERED | COVERED | P0 |
| CTE | PARTIAL | COVERED | COVERED | PARTIAL | P1 |
| Relation and aliases | PARTIAL | COVERED | COVERED | COVERED | P0 |
| Join | PARTIAL | COVERED | COVERED | COVERED | P0 |
| Set operation | PARTIAL | COVERED | COVERED | COVERED | P1 |
| Expression | PARTIAL | COVERED | COVERED | PARTIAL | P0 |
| Predicate subquery | PARTIAL | COVERED | PARTIAL | COVERED | P0 |
| Aggregation | PARTIAL | COVERED | COVERED | COVERED | P1 |
| Window | PARTIAL | COVERED | COVERED | COVERED | P1 |
| Insert | PARTIAL | COVERED | COVERED | PARTIAL | P0 |
| Update | PARTIAL | COVERED | PARTIAL | COVERED | P1 |
| Delete | PARTIAL | COVERED | N/A | COVERED | P1 |
| Merge | PARTIAL | COVERED | PARTIAL | COVERED | P1 |
| CTAS | PARTIAL | COVERED | COVERED | PARTIAL | P1 |
| Create view | PARTIAL | COVERED | COVERED | PARTIAL | P1 |
| DDL affected table | PARTIAL | COVERED | N/A | N/A | P1 |
| Oracle extensions | PARTIAL | COVERED | PARTIAL | PARTIAL | P1 |

### SQL Server

| Domain | Grammar status | Table lineage | Column lineage | Column usage | Priority |
| --- | --- | --- | --- | --- | --- |
| Statement dispatch | PARTIAL | COVERED | PARTIAL | PARTIAL | P0 |
| Query core | PARTIAL | COVERED | COVERED | COVERED | P0 |
| CTE | PARTIAL | COVERED | COVERED | PARTIAL | P1 |
| Relation and aliases | PARTIAL | COVERED | COVERED | COVERED | P0 |
| Join | PARTIAL | COVERED | COVERED | COVERED | P0 |
| Set operation | PARTIAL | COVERED | COVERED | COVERED | P1 |
| Expression | PARTIAL | COVERED | COVERED | PARTIAL | P0 |
| Predicate subquery | PARTIAL | COVERED | PARTIAL | COVERED | P0 |
| Aggregation | PARTIAL | COVERED | COVERED | COVERED | P1 |
| Window | PARTIAL | COVERED | COVERED | COVERED | P1 |
| Insert | PARTIAL | COVERED | COVERED | PARTIAL | P0 |
| Update | PARTIAL | COVERED | PARTIAL | COVERED | P0 |
| Delete | PARTIAL | COVERED | N/A | COVERED | P0 |
| Merge | PARTIAL | COVERED | PARTIAL | COVERED | P1 |
| CTAS | PARTIAL | COVERED | COVERED | PARTIAL | P1 |
| Create view | PARTIAL | COVERED | COVERED | PARTIAL | P1 |
| DDL affected table | PARTIAL | COVERED | N/A | N/A | P1 |
| SQL Server extensions | PARTIAL | COVERED | PARTIAL | PARTIAL | P1 |

### PostgreSQL

| Domain | Grammar status | Table lineage | Column lineage | Column usage | Priority |
| --- | --- | --- | --- | --- | --- |
| Statement dispatch | PLANNED | PLANNED | PLANNED | PLANNED | P0 |
| Query core | PLANNED | PLANNED | PLANNED | PLANNED | P0 |
| CTE | PLANNED | PLANNED | PLANNED | PLANNED | P0 |
| Relation and aliases | PLANNED | PLANNED | PLANNED | PLANNED | P0 |
| Join | PLANNED | PLANNED | PLANNED | PLANNED | P0 |
| Set operation | PLANNED | PLANNED | PLANNED | PLANNED | P1 |
| Expression | PLANNED | PLANNED | PLANNED | PLANNED | P0 |
| Predicate subquery | PLANNED | PLANNED | PLANNED | PLANNED | P0 |
| Aggregation | PLANNED | PLANNED | PLANNED | PLANNED | P1 |
| Window | PLANNED | PLANNED | PLANNED | PLANNED | P2 |
| Insert | PLANNED | PLANNED | PLANNED | PLANNED | P0 |
| Update | PLANNED | PLANNED | PLANNED | PLANNED | P0 |
| Delete | PLANNED | PLANNED | N/A | PLANNED | P0 |
| Merge | PLANNED | PLANNED | PLANNED | PLANNED | P1 |
| CTAS | PLANNED | PLANNED | PLANNED | PLANNED | P1 |
| Create view | PLANNED | PLANNED | PLANNED | PLANNED | P1 |
| DDL affected table | PLANNED | PLANNED | N/A | N/A | P1 |
| PostgreSQL extensions | PLANNED | PLANNED | PLANNED | PLANNED | P0 |

Initial PostgreSQL extension focus:

- `INSERT ... ON CONFLICT ... DO UPDATE`
- `RETURNING` on `INSERT`, `UPDATE`, and `DELETE`
- data-modifying CTEs
- `UPDATE ... FROM`
- `DELETE ... USING`
- `CREATE TABLE ... AS`
- PostgreSQL `MERGE`

### OceanBase

OceanBase should be tracked by compatibility mode rather than as a single flat grammar.

| Domain | Grammar status | Table lineage | Column lineage | Column usage | Priority |
| --- | --- | --- | --- | --- | --- |
| Statement dispatch | PLANNED | PLANNED | PLANNED | PLANNED | P0 |
| Query core | PLANNED | PLANNED | PLANNED | PLANNED | P0 |
| CTE | PLANNED | PLANNED | PLANNED | PLANNED | P1 |
| Relation and aliases | PLANNED | PLANNED | PLANNED | PLANNED | P0 |
| Join | PLANNED | PLANNED | PLANNED | PLANNED | P0 |
| Set operation | PLANNED | PLANNED | PLANNED | PLANNED | P1 |
| Expression | PLANNED | PLANNED | PLANNED | PLANNED | P0 |
| Predicate subquery | PLANNED | PLANNED | PLANNED | PLANNED | P0 |
| Aggregation | PLANNED | PLANNED | PLANNED | PLANNED | P1 |
| Window | PLANNED | PLANNED | PLANNED | PLANNED | P2 |
| Insert | PLANNED | PLANNED | PLANNED | PLANNED | P0 |
| Update | PLANNED | PLANNED | PLANNED | PLANNED | P0 |
| Delete | PLANNED | PLANNED | N/A | PLANNED | P0 |
| Merge | PLANNED | PLANNED | PLANNED | PLANNED | P1 |
| CTAS | PLANNED | PLANNED | PLANNED | PLANNED | P1 |
| Create view | PLANNED | PLANNED | PLANNED | PLANNED | P1 |
| DDL affected table | PLANNED | PLANNED | N/A | N/A | P1 |
| OceanBase MySQL mode extensions | PLANNED | PLANNED | PLANNED | PLANNED | P0 |
| OceanBase Oracle mode extensions | PLANNED | PLANNED | PLANNED | PLANNED | P1 |

Initial OceanBase focus:

- reuse MySQL grammar domains for OceanBase MySQL mode
- reuse Oracle grammar domains for OceanBase Oracle mode
- add OceanBase-specific DDL, hints, partition syntax, and compatibility-mode detection anchors
- keep public output dialect explicit enough for downstream systems to distinguish MySQL-compatible OceanBase from native MySQL

## Near-Term Priorities

The next development batches should follow this order:

| Priority | Scope | Reason |
| --- | --- | --- |
| P0 | Query core, relation scope, join predicates, insert mappings, MySQL/StarRocks/SQL Server DML, PostgreSQL basic DML, OceanBase MySQL-mode baseline | These paths protect common catalog and governance use cases. |
| P1 | CTE propagation, predicate subqueries, merge actions, CTAS/view output columns, dialect DDL anchors, OceanBase Oracle-mode baseline | These make the parser useful for production scripts and warehouse jobs. |
| P2 | Window functions, richer DDL metadata, less common dialect extensions | Important, but less likely to break basic lineage adoption. |

## Acceptance Checklist

A grammar domain is not considered done until:

- The parser grammar accepts representative syntax for the dialect.
- The visitor returns table lineage for all common source and target relations.
- Column lineage is returned where target columns exist.
- Clause-level column usages are returned for predicates and grouping/sorting clauses.
- SQL cases cover direct table, alias, CTE, derived table, and subquery variants when applicable.
- Manifest assertions document the expected result.
- `docs/supported-scenarios.md` links the capability to case ids.
- `./scripts/mvn-jdk11 -B clean install` passes.

## Open Tracking Notes

- Spark has the broadest grammar baseline and should be used to validate the full domain model.
- The lightweight dialect grammars should be expanded by domain, not by isolated SQL examples.
- MySQL, StarRocks, and SQL Server DML should stay near the front of the queue because they are common in platform metadata services.
- Hive and Flink DDL extensions should remain table-lineage focused unless they expose real data flow.
- Oracle and SQL Server merge behavior should be strengthened after DML assignment lineage stabilizes.
