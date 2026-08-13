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
| Table-producing DDL | Covered | CTAS, CREATE OR REPLACE TABLE AS SELECT, CREATE MATERIALIZED VIEW AS SELECT, CREATE STREAMING TABLE AS SELECT, CREATE TABLE LIKE. |
| Views | Covered | CREATE VIEW AS SELECT, CREATE VIEW column list, ALTER VIEW AS SELECT, temporary view using provider. |
| DML | Covered | MERGE, MERGE USING subquery, UPDATE with subqueries, DELETE with subqueries. |
| Table lifecycle and maintenance | Covered | DROP, TRUNCATE, LOAD DATA, CACHE/UNCACHE, ALTER TABLE maintenance, RENAME TABLE, REPAIR TABLE, index maintenance, COMMENT ON TABLE/COLUMN. |
| Metadata reads | Covered | ANALYZE, DESCRIBE TABLE, SHOW CREATE TABLE, SHOW COLUMNS, SHOW PARTITIONS, REFRESH TABLE. |
| Script handling | Covered | Multi-statement splitting, bad SQL isolation, temporary view/cache propagation and cleanup. |
| Production SQL tolerance | Covered | Scheduler placeholders, quoted non-ASCII identifiers, semicolon in strings. |
| PIVOT / UNPIVOT | Partial | Source table lineage only; generated columns are not propagated yet. |
| TRANSFORM | Partial | Source table lineage only; external script output semantics are not propagated. |
| STREAM and change relations | Covered | `STREAM(table)` and `CHANGES` source lineage plus direct projection column lineage. |
| Pipe query | Partial | SELECT, WHERE, DROP, EXTEND, AGGREGATE, JOIN, and set pipe operators are covered for source lineage; direct projection is covered for SELECT/WHERE/DROP/JOIN. |
| UNNEST / JSON_TABLE | Partial | Generated columns can map to input expressions when the relation has explicit output column names. |
| Table-valued functions | Partial | `TABLE identifier` and `TABLE(query)` arguments are extracted as source table lineage; function output columns are degraded. |

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
| DML | `merge`, `update`, `delete` | Target table lineage; subquery source tables extracted. | `merge_into`, `merge_using_subquery`, `update_with_subquery`, `delete_with_subquery` |
| Table maintenance | `alter table`, `rename`, `drop`, `truncate`, `repair` | Affected table lineage; no column lineage. | `alter_table_add_columns`, `rename_table`, `repair_table` |
| Metadata reads | `analyze`, `describe`, `show create`, `show columns`, `refresh` | Read table recorded as input table; no column lineage. | `analyze_table`, `describe_table`, `show_create_table`, `refresh_table` |
| EXPLAIN | `explain formatted select ... from t` | Visits the wrapped statement and preserves lineage. | `explain_select` |
| Comments | `comment on table`, `comment on column` | Affected table lineage. | `comment_table`, `comment_column` |
| Cache lifecycle | `cache table as select`, `uncache table` | Cache target and source lineage; script-local propagation. | `cache_table_as_select`, `script_cache_table_lineage`, `script_uncache_table` |
| Load data | `load data inpath ... into table t` | Target table lineage. | `load_data_into_table` |

## Query And Relation Families

| Grammar family | Status | Current behavior | Next step |
| --- | --- | --- | --- |
| Regular SELECT | Covered | Direct projections, expressions, aggregate/window arguments, nested fields. | Broaden expression edge cases. |
| JOIN | Covered | Table lineage and qualified projection lineage. | Support more unqualified multi-table inference only with schema metadata. |
| CTE | Covered | Single-level, chained direct projection, alias list propagation. | Complex CTE joins and nested chains. |
| Subquery | Covered | Single-level aliased direct projection propagation. | Nested subquery chains and complex joins. |
| Set operations | Covered | UNION, EXCEPT, and INTERSECT column inputs merged by position. | Consider a semantic mode that separates projection sources from filtering inputs. |
| LATERAL VIEW | Partial | Simple generated columns from UDTF input expressions. | More UDTF output semantics. |
| PIVOT / UNPIVOT | Partial | Source table lineage only. | Generated column mapping and source aggregation propagation. |
| TRANSFORM | Partial | Source table lineage only. | Keep degraded unless script schema semantics are modeled. |
| STREAM table | Covered | Source lineage and direct projection mapping. | Stream table-valued function cases. |
| Changelog relation | Covered | Source lineage and qualified direct projection mapping. | Add additional version/timestamp variants if needed. |
| Table-valued functions | Partial | `TABLE` identifier and query arguments contribute source table lineage. | Decide function-specific output column semantics. |
| UNNEST / JSON_TABLE | Partial | Generated columns map to input array/JSON expressions when explicit output column names are available. | Broaden alias-qualified generated column projection and complex nested JSON semantics. |
| Inline table | Not lineage-bearing | No source table. | Add case only if diagnostics behavior needs locking. |
| `TABLE identifier` query primary | Covered | Source table lineage, with column lineage degraded without schema. | Add schema-aware expansion later. |
| Operator pipe queries | Partial | SELECT, WHERE, DROP, EXTEND, AGGREGATE, JOIN, UNION, INTERSECT, and EXCEPT have executable cases; generated EXTEND/AGGREGATE and set output columns are degraded. | Add richer set column policy if needed. |

## Parse-Only Or Not Yet Explicitly Modeled Statements

These grammar branches should be triaged before claiming broad Spark completion:

| Branch | Suggested handling |
| --- | --- |
| `USE`, `SET CATALOG`, `SET`, `RESET` | Not lineage-bearing; may eventually update parse context. |
| Namespace DDL and SHOW namespace/catalog commands | Not table lineage-bearing; may be modeled as metadata operations later. |
| CREATE/DROP FUNCTION, REFRESH FUNCTION | Not table lineage-bearing unless function bodies contain queries. |
| SQL variables, cursors, execute immediate | Parse-only for now; dynamic SQL should be diagnostics/degraded unless literal SQL can be safely extracted. |
| EXPLAIN statement | Covered for wrapped SQL statements; SET/RESET explanation is not lineage-bearing. |
| COMMENT ON TABLE/COLUMN | Covered as affected table lineage. |
| CALL procedure | Parse-only; procedure lineage is catalog/procedure-specific. |
| CREATE METRIC VIEW / code literal view | Parse-only; code literal lineage cannot be extracted safely yet. |
| CREATE FLOW / AUTO CDC | Parse-only; requires CDC source/target semantics. |
| Table-valued functions | TABLE arguments are covered; output columns still require function-specific rules. |
| `TABLE t` query primary | Covered as source table lineage with column lineage degraded without schema. |
| EXCEPT / INTERSECT | Covered; current column lineage records both inputs by position. |
| Operator pipe statements | Partial coverage for SELECT, WHERE, DROP, EXTEND, AGGREGATE, JOIN, UNION, INTERSECT, and EXCEPT. |

## Recommended Next Implementation Order

1. Broaden UNNEST / JSON_TABLE generated column support for alias-qualified projections.
2. Add function-specific output column semantics for selected table-valued functions.
3. Improve column lineage for PIVOT/UNPIVOT and pipe EXTEND/AGGREGATE only after target/source column semantics are clear.
4. Decide whether dynamic SQL features such as EXECUTE IMMEDIATE should ever inspect literal SQL strings, or always return degraded diagnostics.

## Documentation Rule

Every item moved from `Parse-only` or `Partial` to `Covered` must update:

- `linesql-dialect-spark/src/test/resources/sql/spark/manifest.json`
- `linesql-dialect-spark/src/test/resources/sql/spark/cases/*.sql`
- `docs/supported-scenarios.md`
- this audit document
