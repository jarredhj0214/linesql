# Spark Coverage Audit

This document maps Spark grammar families to the current LineSQL lineage behavior.
It is a working checklist for implementation, tests, and documentation.

Status legend:

- `Covered`: executable SQL cases exist in `linesql-dialect-spark/src/test/resources/sql/spark/manifest.json`.
- `Partial`: table lineage is available, but column lineage or some variants are intentionally degraded.
- `Parse-only`: the grammar can parse the family, but LineSQL has no explicit lineage behavior yet.
- `Not lineage-bearing`: the statement usually does not reference a table-level data lineage object.

## Current Coverage Summary

| Area | Status | Notes |
| --- | --- | --- |
| Basic queries | Covered | SELECT, TABLE, JOIN, CTE, subquery, UNION, EXCEPT, INTERSECT, aggregation, window functions, nested fields. |
| INSERT writes | Covered | INSERT INTO, INSERT OVERWRITE, partition target columns, BY NAME, REPLACE WHERE/USING, directory export, multi-insert table lineage. |
| Table-producing DDL | Covered | CTAS, CREATE OR REPLACE TABLE AS SELECT, CREATE MATERIALIZED VIEW AS SELECT, CREATE STREAMING TABLE AS SELECT, CREATE TABLE LIKE, metric view code literal degradation, CREATE FLOW INSERT/AUTO CDC. |
| Views | Covered | CREATE VIEW AS SELECT, CREATE VIEW column list, ALTER VIEW AS SELECT, temporary view using provider. |
| DML | Covered | MERGE, MERGE USING subquery, UPDATE with subqueries, DELETE with subqueries. |
| Table lifecycle and maintenance | Covered | DROP, TRUNCATE, LOAD DATA, CACHE/UNCACHE, ALTER TABLE maintenance, RENAME TABLE, REPAIR TABLE, index maintenance, COMMENT ON TABLE/COLUMN. |
| Metadata reads | Covered | ANALYZE, DESCRIBE TABLE/QUERY/NAMESPACE, SHOW CREATE TABLE, SHOW TABLES/COLUMNS/VIEWS/PARTITIONS/NAMESPACES/CATALOGS/COLLATIONS, REFRESH TABLE/resource, ANALYZE TABLES. |
| Script handling | Covered | Multi-statement splitting, bad SQL isolation, temporary view/cache propagation and cleanup. |
| Production SQL tolerance | Covered | Scheduler placeholders, quoted non-ASCII identifiers, semicolon in strings, dynamic SQL degradation, non-lineage session/namespace/catalog/function/procedure/resource/cache/control statements. |
| PIVOT / UNPIVOT | Partial | PIVOT source table lineage plus alias-based aggregate output columns are covered; single-value and positional multi-value UNPIVOT generated column lineage is covered. |
| TRANSFORM | Partial | Source table lineage only; external script output semantics are not propagated. |
| STREAM and change relations | Covered | `STREAM(table)` and `CHANGES` source lineage plus direct projection column lineage. |
| Pipe query | Partial | SELECT, WHERE, DROP, EXTEND, AGGREGATE, JOIN, and set pipe operators are covered for source lineage; direct/generative projection is covered for SELECT/WHERE/DROP/EXTEND/AGGREGATE/JOIN and explicit-projection set operators. |
| UNNEST / JSON_TABLE | Partial | Generated columns can map to input expressions when the relation has explicit output column names, including alias-qualified generated column references. |
| Table-valued functions | Partial | `TABLE identifier` and `TABLE(query)` arguments are extracted as source table lineage; Spark `range` generated output and function-style multi-column aliases are covered. Function-specific output semantics are still degraded. |

## Statement Families

| Grammar family | Examples | Current behavior | Case ids |
| --- | --- | --- | --- |
| Query default | `select ... from t`, `table t` | Table lineage and focused column lineage. | `select_basic`, `table_query`, `column_direct_projection`, `join_basic` |
| INSERT | `insert into/overwrite table t select ...` | Source/target table lineage, target column mapping, BY NAME mapping. | `insert_overwrite`, `insert_column_list`, `insert_by_name` |
| INSERT REPLACE | `insert into table t alias by name replace where ...` | Source/target table lineage and BY NAME column mapping. | `insert_replace_where`, `insert_replace_using` |
| INSERT DIRECTORY | `insert overwrite directory ... select ...` | Source table lineage; no target table. | `insert_overwrite_directory` |
| Multi-insert | `from s insert ... insert ...` | Per-target table lineage; column lineage degraded. | `multi_insert` |
| CTAS / RTAS | `create table t as select ...`, `create or replace table ...` | Source/target table lineage and projection column lineage. | `ctas_column_projection`, `replace_table_as_select` |
| Pipeline datasets | `create materialized view ... as select ...`, `create streaming table ... as select ...` | Treated as table-producing query lineage. | `create_materialized_view_as_select`, `create_streaming_table_as_select` |
| CREATE TABLE LIKE | `create table t like s` | Structural source/target table lineage; no column lineage. | `create_table_like` |
| Views | `create view`, `alter view` | View target table lineage and projection column lineage. | `create_view`, `create_view_column_list`, `alter_view_as_select` |
| Metric views | `create view ... as $$...$$` | Output view is recorded; code literal text is diagnosed and not expanded. | `create_metric_view_code_literal` |
| DML | `merge`, `update`, `delete` | Target table lineage; subquery source tables extracted. | `merge_into`, `merge_using_subquery`, `update_with_subquery`, `delete_with_subquery` |
| Table maintenance | `alter table`, `rename`, `drop`, `truncate`, `repair` | Affected table lineage; no column lineage. | `alter_table_add_columns`, `rename_table`, `repair_table` |
| Metadata reads | `analyze`, `describe`, `show create`, `show columns`, `refresh`, `show namespaces`, `show catalogs`, `show tables`, `describe query` | Read table recorded as input table when a table/query is present; table-free metadata reads return no table lineage. | `analyze_table`, `describe_table`, `show_create_table`, `refresh_table`, `show_namespaces`, `show_catalogs`, `analyze_tables`, `show_tables`, `describe_query` |
| EXPLAIN | `explain formatted select ... from t` | Visits the wrapped statement and preserves lineage. | `explain_select` |
| Comments | `comment on table`, `comment on column` | Affected table lineage. | `comment_table`, `comment_column` |
| Cache lifecycle | `cache table as select`, `uncache table` | Cache target and source lineage; script-local propagation. | `cache_table_as_select`, `script_cache_table_lineage`, `script_uncache_table` |
| Load data | `load data inpath ... into table t` | Target table lineage. | `load_data_into_table` |
| Flow definitions | `create flow ... as insert ... select`, `create flow ... as auto cdc ...` | INSERT flow reuses insert lineage; AUTO CDC records source/target and degrades CDC column semantics. | `create_flow_insert`, `create_flow_auto_cdc` |

## Query And Relation Families

| Grammar family | Status | Current behavior | Next step |
| --- | --- | --- | --- |
| Regular SELECT | Covered | Direct projections, expressions, aggregate/window arguments, nested fields. | Broaden expression edge cases. |
| JOIN | Covered | Table lineage and qualified projection lineage. | Support more unqualified multi-table inference only with schema metadata. |
| CTE | Covered | Single-level, chained direct projection, alias list propagation. | Complex CTE joins and nested chains. |
| Subquery | Covered | Single-level aliased direct projection propagation. | Nested subquery chains and complex joins. |
| Set operations | Covered | UNION, EXCEPT, and INTERSECT column inputs merged by position. | Consider a semantic mode that separates projection sources from filtering inputs. |
| LATERAL VIEW / UDTF aliases | Partial | Simple generated columns from UDTF input expressions and `expr AS (c1, c2)` multi-column aliases. | More function-specific UDTF output semantics. |
| PIVOT / UNPIVOT | Partial | PIVOT aggregate output columns are covered for pivot value aliases and aggregate aliases; UNPIVOT generated columns are covered for single-value and positional multi-value forms. | Richer PIVOT grouping/value naming and UNPIVOT alias/null semantics. |
| TRANSFORM | Partial | Source table lineage only. | Keep degraded unless script schema semantics are modeled. |
| STREAM table | Covered | Source lineage and direct projection mapping. | Stream table-valued function cases. |
| Changelog relation | Covered | Source lineage and qualified direct projection mapping. | Add additional version/timestamp variants if needed. |
| Table-valued functions | Partial | `TABLE` identifier and query arguments contribute source table lineage; Spark `range` contributes a generated `id` column with no sources. | Add more function-specific output column semantics. |
| UNNEST / JSON_TABLE | Partial | Generated columns map to input array/JSON expressions when explicit output column names are available, including alias-qualified generated column references. | Broaden complex nested JSON semantics. |
| Inline table | Not lineage-bearing | No source table. | Add case only if diagnostics behavior needs locking. |
| `TABLE identifier` query primary | Covered | Source table lineage, with column lineage degraded without schema. | Add schema-aware expansion later. |
| Operator pipe queries | Partial | SELECT, WHERE, DROP, EXTEND, AGGREGATE, JOIN, UNION, INTERSECT, and EXCEPT have executable cases; EXTEND and AGGREGATE generated columns can propagate into following SELECT, simple standalone AGGREGATE output is covered, and explicit-projection set outputs are merged by position. | Add schema-aware TABLE right-side expansion and richer aggregate grouping policy if needed. |

## Parse-Only Or Not Yet Explicitly Modeled Statements

These grammar branches should be triaged before claiming broad Spark completion:

| Branch | Suggested handling |
| --- | --- |
| `USE`, `SET CATALOG`, `SET`, `RESET` | `USE`, `SET CATALOG`, and `RESET` have explicit non-lineage cases; additional `SET` variants may eventually update parse context. |
| Namespace DDL and SHOW namespace/catalog commands | Namespace DDL is explicit non-lineage; SHOW namespace/catalog commands are table-free metadata reads. |
| CREATE/DROP FUNCTION, REFRESH FUNCTION | Function DDL is explicit non-lineage; SHOW/DESCRIBE/REFRESH FUNCTION are table-free metadata reads. |
| SQL variables, cursors, execute immediate | EXECUTE IMMEDIATE returns `DYNAMIC_SQL_NOT_EXPANDED`; variables and cursors are explicit non-lineage control statements. |
| EXPLAIN statement | Covered for wrapped SQL statements; SET/RESET explanation is not lineage-bearing. |
| COMMENT ON TABLE/COLUMN | Covered as affected table lineage. |
| COMMENT ON NAMESPACE | Explicit non-lineage. |
| CALL procedure | Explicit non-lineage; procedure lineage is catalog/procedure-specific. |
| Resource/cache control | `REFRESH 'path'`, `ADD/LIST resource`, and `CLEAR CACHE` have explicit non-table behavior. |
| CREATE METRIC VIEW / code literal view | Output view is recorded and `CODE_LITERAL_NOT_EXPANDED` is emitted. |
| CREATE FLOW / AUTO CDC | INSERT flow lineage is covered; AUTO CDC source/target lineage is covered with `CDC_LINEAGE_DEGRADED`. |
| Table-valued functions | TABLE arguments, Spark `range` output, and function-style multi-column aliases are covered; richer output columns still require function-specific rules. |
| `TABLE t` query primary | Covered as source table lineage with column lineage degraded without schema. |
| EXCEPT / INTERSECT | Covered; current column lineage records both inputs by position. |
| Operator pipe statements | Partial coverage for SELECT, WHERE, DROP, EXTEND, AGGREGATE, JOIN, UNION, INTERSECT, and EXCEPT. |

## Recommended Next Implementation Order

1. Add function-specific output column semantics for more selected table-valued functions.
2. Improve column lineage for richer PIVOT grouping/value naming, richer UNPIVOT alias/null semantics, pipe AGGREGATE grouping analytics, and AUTO CDC only after target/source column semantics are clear.

## Documentation Rule

Every item moved from `Parse-only` or `Partial` to `Covered` must update:

- `linesql-dialect-spark/src/test/resources/sql/spark/manifest.json`
- `linesql-dialect-spark/src/test/resources/sql/spark/cases/*.sql`
- `docs/supported-scenarios.md`
- this audit document
