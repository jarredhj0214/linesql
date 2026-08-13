# Supported Scenarios

This document records implemented behavior as LineSQL evolves. Every new parser capability should update this file and add or update SQL cases under the related dialect test resources.

## Spark

Spark is the first implemented dialect. It uses Apache Spark's official ANTLR grammar as the parse baseline, while lineage extraction is implemented by LineSQL.

Current Spark SQL case assets:

```text
linesql-dialect-spark/src/test/resources/sql/spark/manifest.json
linesql-dialect-spark/src/test/resources/sql/spark/cases/*.sql
```

The manifest records executable expectations for statement type, input tables, output tables, column lineage, and expected diagnostics.

### Table Lineage

Implemented Spark table-level lineage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Basic SELECT source table | `select ... from ods.users` | `select_basic` |
| INSERT OVERWRITE target and source | `insert overwrite table ads.t select ... from ods.s` | `insert_overwrite` |
| Multi-statement scripts | `select ...; create table ... as select ...` | `script_semicolon` |
| JOIN source tables | `from ods.users join ods.orders` | `join_basic` |
| CREATE VIEW AS SELECT | `create view mart.v as select ... from ods.s` | `create_view` |
| MERGE source and target tables | `merge into ads.t using ods.s ...` | `merge_into` |
| CACHE TABLE AS SELECT source table | `cache table cached as select ... from ods.s` | `cache_table_as_select` |
| CTE source table, excluding CTE alias as table | `with base as (...) select ... from base` | `cte_basic` |
| UNION input tables | `select ... from a union all select ... from b` | `union_basic` |
| Subquery input tables | `select ... from (select ... from ods.s)` | `subquery_basic` |
| CTAS output and source tables | `create table mart.t as select ... from ods.s` | `ctas_column_projection` |

Invalid SQL returns a diagnostic instead of throwing for the whole parse result. See `parse_error`.

### Column Lineage

Implemented Spark column-level lineage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Direct single-table projection | `select id as user_id, name from ods.users` | `column_direct_projection` |
| Function expression source columns | `select lower(name) as name_lower from ods.orders` | `column_expression_projection` |
| Arithmetic expression source columns | `select price * quantity as amount from ods.orders` | `column_expression_projection` |
| Constant projection with no sources | `select 1 as flag from ods.orders` | `column_expression_projection` |
| Partial extraction diagnostics | `select id, lower(name) from ods.users` | `column_partial_projection` |
| Qualified JOIN projection | `select u.id, o.amount from users u join orders o ...` | `column_join_projection` |
| INSERT target column list mapping | `insert into ads.t(c1, c2) select a, b from ods.s` | `insert_column_list` |
| INSERT target column list over CTE propagation | `insert into ads.t(c1) with q as (...) select c1 from q` | `insert_from_cte` |
| INSERT target column list over subquery propagation | `insert into ads.t(c1) select c1 from (select a as c1 from ods.s) q` | `insert_from_subquery` |
| CREATE VIEW output column targets | `create view mart.v as select id from ods.s` | `create_view` |
| CTAS output column targets | `create table mart.t as select id as c1 from ods.s` | `ctas_column_projection` |
| Single-level CTE direct column propagation | `with base as (select id as c1 from ods.s) select c1 from base` | `cte_column_projection` |
| Single-level aliased subquery direct column propagation | `select c1 from (select id as c1 from ods.s) q` | `subquery_column_projection` |

### Diagnostics

Current Spark diagnostics:

| Code | Meaning |
| --- | --- |
| `SPARK_PARSE_ERROR` | Spark SQL could not be parsed by the current grammar entry point. |
| `COLUMN_LINEAGE_NOT_IMPLEMENTED` | No column lineage was produced for the statement. Table lineage may still be available. |
| `COLUMN_LINEAGE_PARTIAL` | Some column lineage was produced, but at least one projection could not be resolved safely. |

### Known Gaps

The following Spark lineage features are intentionally not complete yet:

| Gap | Current behavior |
| --- | --- |
| `select *` expansion | Not expanded without schema metadata. |
| Complex CTE column propagation | Single-level direct CTE projection is supported; recursive CTEs, multi-CTE dependency chains, and complex CTE joins are not complete yet. |
| Complex subquery column propagation | Single-level aliased direct subquery projection is supported; nested subquery chains and complex subquery joins are not complete yet. |
| Unqualified columns in multi-table queries | Not guessed when they cannot be safely mapped to one table. |
| UDTF and lateral view column propagation | Not implemented yet. |
| Complex nested fields and structs | Basic dereference source collection exists, but full struct-field semantics are not modeled yet. |
