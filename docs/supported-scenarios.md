# Supported Scenarios

This document records implemented behavior as LineSQL evolves. Every new parser capability should update this file and add or update SQL cases under the related dialect test resources.

## Dialect Detection

Dialect detection is intentionally conservative: LineSQL uses clear syntax anchors to rank dialect candidates, records confidence and reason metadata, and keeps Spark as the current generic fallback for dialect-neutral SQL.

Current detector case assets:

```text
linesql-core/src/test/java/io/github/linesql/core/internal/SimpleDialectDetectorTest.java
```

Implemented detection anchors:

| Dialect | Anchor examples |
| --- | --- |
| MySQL | `REPLACE INTO`, `ON DUPLICATE KEY`, `LIMIT offset, size`, `UPDATE ... JOIN ... SET` |
| Hive | `ROW FORMAT`, `STORED AS`, `SERDEPROPERTIES`, `CLUSTERED BY` |
| Flink | connector options, `WATERMARK FOR` |
| StarRocks | `DUPLICATE KEY`, `AGGREGATE KEY`, `DISTRIBUTED BY HASH`, replication properties |
| Oracle | `FROM DUAL`, `CONNECT BY`, `START WITH` |
| SQL Server | `SELECT TOP n`, bracketed identifiers, `WITH (NOLOCK)` |
| Spark | `INSERT OVERWRITE`, `LATERAL VIEW`, `CREATE TEMPORARY VIEW`, `USING`, fallback |

Known conflict guards:

| Guard | Reason |
| --- | --- |
| Spark `MERGE INTO` is not classified as Oracle | `MERGE INTO` is shared across engines and is not a safe Oracle-only anchor. |
| JSON path array wildcard `[*]` is not classified as SQL Server | Brackets inside strings are not SQL Server identifiers. |

## SQL Server

SQL Server is an active MVP dialect path. The current implementation uses an ANTLR4 lexer with a lightweight lineage walker for common SQL Server query and write shapes.

Current SQL Server SQL case assets:

```text
linesql-dialect-sqlserver/src/test/resources/sql/sqlserver/manifest.json
linesql-dialect-sqlserver/src/test/resources/sql/sqlserver/cases/*.sql
```

Implemented SQL Server table-level lineage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Basic SELECT source table | `select ... from ods.users` | `select_basic` |
| JOIN source tables | `select ... from ods.users u join dwd.orders o ...` | `join_projection` |
| INSERT INTO target and source | `insert into ads.t select ... from ods.s` | `insert_into` |
| CREATE TABLE AS SELECT | `create table ads.t as select ... from ods.s` | `create_table_as_select` |
| CREATE VIEW AS SELECT | `create view ads.v as select ... from ods.s join dwd.o` | `create_view` |
| Bracketed non-ASCII identifiers | `select [用户ID] from [业务库].[用户表]` | `bracket_identifiers` |
| SELECT TOP and table hint | `select top 10 ... from dbo.users with (nolock)` | `top_with_nolock` |

Implemented SQL Server column-level lineage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Direct single-table projection | `select id as user_id, name from ods.users` | `select_basic` |
| Alias-qualified JOIN projection | `select u.id, o.amount from users u join orders o` | `join_projection` |
| INSERT SELECT target mapping | `insert into ads.t select a as c1 from ods.s` | `insert_into` |
| CTAS output column targets | `create table ads.t as select id as c1 from ods.s` | `create_table_as_select` |
| CREATE VIEW output column targets | `create view ads.v as select u.id from ods.users u` | `create_view` |
| Bracketed identifier column mapping | `select [用户ID] as [用户标识] from [业务库].[用户表]` | `bracket_identifiers` |
| SELECT TOP projection mapping | `select top (10) u.id as user_id from dbo.users u` | `top_parenthesized` |

Current SQL Server diagnostics:

| Code | Meaning |
| --- | --- |
| `SQLSERVER_PARSE_ERROR` | SQL Server SQL could not be tokenized or walked by the current MVP parser. |
| `SQLSERVER_STATEMENT_NOT_SUPPORTED` | The statement was recognized as SQL Server but is not in the current MVP statement set. |
| `SQLSERVER_COLUMN_LINEAGE_NOT_IMPLEMENTED` | No column lineage was produced for a statement shape where table lineage may still be available. |

Known SQL Server gaps:

| Gap | Current behavior |
| --- | --- |
| Full SQL Server grammar | The MVP uses ANTLR tokenization plus a lineage walker; full parser grammar will be expanded incrementally. |
| `select *` expansion | Not expanded without schema metadata. |
| T-SQL specific DML and procedural syntax | `MERGE`, `OUTPUT`, table variables, temp tables, and stored-procedure bodies are not yet covered. |
| CTEs and subqueries | Not yet covered in the SQL Server MVP path. |

## Oracle

Oracle is an active MVP dialect path. The current implementation uses an ANTLR4 lexer with a lightweight lineage walker for common Oracle query and write shapes.

Current Oracle SQL case assets:

```text
linesql-dialect-oracle/src/test/resources/sql/oracle/manifest.json
linesql-dialect-oracle/src/test/resources/sql/oracle/cases/*.sql
```

Implemented Oracle table-level lineage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Basic SELECT source table | `select ... from ods.users` | `select_basic` |
| JOIN source tables | `select ... from ods.users u join dwd.orders o ...` | `join_projection` |
| INSERT INTO target and source | `insert into ads.t select ... from ods.s` | `insert_into` |
| CREATE TABLE AS SELECT | `create table ads.t as select ... from ods.s` | `create_table_as_select` |
| CREATE VIEW AS SELECT | `create view ads.v as select ... from ods.s join dwd.o` | `create_view` |
| Double-quoted non-ASCII identifiers | `select "用户ID" from "业务库"."用户表"` | `quoted_identifiers` |
| DUAL pseudo table | `select sysdate from dual` | `dual_pseudo_table` |
| Hierarchical query clauses | `select ... from app.org start with ... connect by ...` | `hierarchical_query` |

Implemented Oracle column-level lineage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Direct single-table projection | `select id as user_id, name from ods.users` | `select_basic` |
| Alias-qualified JOIN projection | `select u.id, o.amount from users u join orders o` | `join_projection` |
| INSERT SELECT target mapping | `insert into ads.t select a as c1 from ods.s` | `insert_into` |
| CTAS output column targets | `create table ads.t as select id as c1 from ods.s` | `create_table_as_select` |
| CREATE VIEW output column targets | `create view ads.v as select u.id from ods.users u` | `create_view` |
| Double-quoted identifier column mapping | `select "用户ID" as "用户标识" from "业务库"."用户表"` | `quoted_identifiers` |
| Hierarchical query projection mapping | `select id as org_id from app.org start with ... connect by ...` | `hierarchical_query` |

Current Oracle diagnostics:

| Code | Meaning |
| --- | --- |
| `ORACLE_PARSE_ERROR` | Oracle SQL could not be tokenized or walked by the current MVP parser. |
| `ORACLE_STATEMENT_NOT_SUPPORTED` | The statement was recognized as Oracle but is not in the current MVP statement set. |
| `ORACLE_COLUMN_LINEAGE_NOT_IMPLEMENTED` | No column lineage was produced for a statement shape where table lineage may still be available. |

Known Oracle gaps:

| Gap | Current behavior |
| --- | --- |
| Full Oracle grammar | The MVP uses ANTLR tokenization plus a lineage walker; full parser grammar will be expanded incrementally. |
| `select *` expansion | Not expanded without schema metadata. |
| Oracle-specific query syntax | `MODEL`, `PIVOT`, `MERGE`, packages, and PL/SQL blocks are not yet covered. `START WITH` and `CONNECT BY` are recognized as lineage clause boundaries. |
| CTEs and subqueries | Not yet covered in the Oracle MVP path. |

## StarRocks

StarRocks is an active MVP dialect path. The current implementation uses an ANTLR4 lexer with a lightweight lineage walker for common StarRocks query and write shapes.

Current StarRocks SQL case assets:

```text
linesql-dialect-starrocks/src/test/resources/sql/starrocks/manifest.json
linesql-dialect-starrocks/src/test/resources/sql/starrocks/cases/*.sql
```

Implemented StarRocks table-level lineage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Basic SELECT source table | `select ... from ods.users` | `select_basic` |
| JOIN source tables | `select ... from ods.users u join dwd.orders o ...` | `join_projection` |
| INSERT INTO target and source | `insert into ads.t select ... from ods.s` | `insert_into` |
| CREATE TABLE AS SELECT | `create table ads.t as select ... from ods.s` | `create_table_as_select` |
| CREATE VIEW AS SELECT | `create view ads.v as select ... from ods.s join dwd.o` | `create_view` |

Implemented StarRocks column-level lineage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Direct single-table projection | `select id as user_id, name from ods.users` | `select_basic` |
| Alias-qualified JOIN projection | `select u.id, o.amount from users u join orders o` | `join_projection` |
| INSERT SELECT target mapping | `insert into ads.t select a as c1 from ods.s` | `insert_into` |
| CTAS output column targets | `create table ads.t as select id as c1 from ods.s` | `create_table_as_select` |
| CREATE VIEW output column targets | `create view ads.v as select u.id from ods.users u` | `create_view` |

Current StarRocks diagnostics:

| Code | Meaning |
| --- | --- |
| `STARROCKS_PARSE_ERROR` | StarRocks SQL could not be tokenized or walked by the current MVP parser. |
| `STARROCKS_STATEMENT_NOT_SUPPORTED` | The statement was recognized as StarRocks but is not in the current MVP statement set. |
| `STARROCKS_COLUMN_LINEAGE_NOT_IMPLEMENTED` | No column lineage was produced for a statement shape where table lineage may still be available. |

Known StarRocks gaps:

| Gap | Current behavior |
| --- | --- |
| Full StarRocks grammar | The MVP uses ANTLR tokenization plus a lineage walker; full parser grammar will be expanded incrementally. |
| `select *` expansion | Not expanded without schema metadata. |
| StarRocks table model DDL | `DUPLICATE KEY`, `AGGREGATE KEY`, and distribution clauses are used for dialect detection, not yet modeled as lineage. |
| CTEs, subqueries, materialized views, and routine-load syntax | Not yet covered in the StarRocks MVP path. |

## Flink

Flink is an active MVP dialect path. The current implementation uses an ANTLR4 lexer with a lightweight lineage walker for common Flink query and write shapes.

Current Flink SQL case assets:

```text
linesql-dialect-flink/src/test/resources/sql/flink/manifest.json
linesql-dialect-flink/src/test/resources/sql/flink/cases/*.sql
```

Implemented Flink table-level lineage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Basic SELECT source table | `select ... from ods_users` | `select_basic` |
| JOIN source tables | `select ... from ods_users u join dwd_orders o ...` | `join_projection` |
| INSERT INTO target and source | `insert into ads_t select ... from ods_s` | `insert_into` |
| CREATE VIEW AS SELECT | `create view v as select ... from ods_s join dwd_o` | `create_view` |

Implemented Flink column-level lineage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Direct single-table projection | `select id as user_id, name from ods_users` | `select_basic` |
| Alias-qualified JOIN projection | `select u.id, o.amount from users u join orders o` | `join_projection` |
| INSERT SELECT target mapping | `insert into ads_t select a as c1 from ods_s` | `insert_into` |
| CREATE VIEW output column targets | `create view v as select u.id from ods_users u` | `create_view` |

Current Flink diagnostics:

| Code | Meaning |
| --- | --- |
| `FLINK_PARSE_ERROR` | Flink SQL could not be tokenized or walked by the current MVP parser. |
| `FLINK_STATEMENT_NOT_SUPPORTED` | The statement was recognized as Flink but is not in the current MVP statement set. |
| `FLINK_COLUMN_LINEAGE_NOT_IMPLEMENTED` | No column lineage was produced for a statement shape where table lineage may still be available. |

Known Flink gaps:

| Gap | Current behavior |
| --- | --- |
| Full Flink grammar | The MVP uses ANTLR tokenization plus a lineage walker; full parser grammar will be expanded incrementally. |
| `select *` expansion | Not expanded without schema metadata. |
| Flink DDL connector options | Used for dialect detection, not yet modeled as lineage. |
| CTEs, subqueries, temporal joins, and window TVFs | Not yet covered in the Flink MVP path. |

## Hive

Hive is an active MVP dialect path. The current implementation uses an ANTLR4 lexer with a lightweight lineage walker for common Hive query and write shapes.

Current Hive SQL case assets:

```text
linesql-dialect-hive/src/test/resources/sql/hive/manifest.json
linesql-dialect-hive/src/test/resources/sql/hive/cases/*.sql
```

Implemented Hive table-level lineage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Basic SELECT source table | `select ... from ods.users` | `select_basic` |
| JOIN source tables | `select ... from ods.users u join dwd.orders o ...` | `join_projection` |
| INSERT OVERWRITE TABLE target and source | `insert overwrite table ads.t select ... from ods.s` | `insert_overwrite` |
| CREATE TABLE AS SELECT | `create table ads.t as select ... from ods.s` | `create_table_as_select` |
| CREATE VIEW AS SELECT | `create view ads.v as select ... from ods.s join dwd.o` | `create_view` |

Implemented Hive column-level lineage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Direct single-table projection | `select id as user_id, name from ods.users` | `select_basic` |
| Alias-qualified JOIN projection | `select u.id, o.amount from users u join orders o` | `join_projection` |
| INSERT SELECT target mapping | `insert overwrite table ads.t select a as c1 from ods.s` | `insert_overwrite` |
| CTAS output column targets | `create table ads.t as select id as c1 from ods.s` | `create_table_as_select` |
| CREATE VIEW output column targets | `create view ads.v as select u.id from ods.users u` | `create_view` |

Current Hive diagnostics:

| Code | Meaning |
| --- | --- |
| `HIVE_PARSE_ERROR` | Hive SQL could not be tokenized or walked by the current MVP parser. |
| `HIVE_STATEMENT_NOT_SUPPORTED` | The statement was recognized as Hive but is not in the current MVP statement set. |
| `HIVE_COLUMN_LINEAGE_NOT_IMPLEMENTED` | No column lineage was produced for a statement shape where table lineage may still be available. |

Known Hive gaps:

| Gap | Current behavior |
| --- | --- |
| Full Hive grammar | The MVP uses ANTLR tokenization plus a lineage walker; full parser grammar will be expanded incrementally. |
| `select *` expansion | Not expanded without schema metadata. |
| Complex expressions, CTEs, subqueries, and lateral view | Not yet covered in the Hive MVP path. |

## MySQL

MySQL is the second implemented dialect path. The current MVP uses an ANTLR4 lexer with a lightweight lineage walker for common MySQL statement shapes. It is intentionally narrower than Spark coverage while the multi-dialect SPI is being validated.

Current MySQL SQL case assets:

```text
linesql-dialect-mysql/src/test/resources/sql/mysql/manifest.json
linesql-dialect-mysql/src/test/resources/sql/mysql/cases/*.sql
```

Implemented MySQL table-level lineage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Basic SELECT source table | `select ... from app.users` | `select_basic` |
| JOIN source tables | `select ... from app.users u join app.orders o ...` | `join_projection` |
| Subquery source table propagation | `select q.c from (select a as c from app.s) q` | `subquery_column_projection` |
| CTE source table propagation | `with q as (select a as c from app.s) select c from q` | `cte_column_projection` |
| UNION source table propagation | `select a from app.s1 union all select b from app.s2` | `union_column_projection` |
| INSERT INTO SELECT target and source | `insert into mart.t(c1) select a from app.s` | `insert_select` |
| INSERT INTO VALUES target lineage | `insert into mart.t(c1) values (...)` | `insert_values` |
| INSERT SELECT with duplicate-key update | `insert into mart.t(c1) select a from app.s on duplicate key update ...` | `insert_select_on_duplicate` |
| REPLACE INTO SELECT target and source | `replace into mart.t(c1) select a from app.s` | `replace_select` |
| REPLACE INTO VALUES target lineage | `replace into mart.t(c1) values (...)` | `replace_values` |
| CREATE TABLE AS SELECT | `create table mart.t as select ... from app.s` | `create_table_as_select` |
| CREATE VIEW AS SELECT | `create view mart.v as select ... from app.s join app.o` | `create_view` |
| CREATE OR REPLACE VIEW AS SELECT | `create or replace view mart.v as select ... from app.s` | `create_or_replace_view` |
| CREATE TEMPORARY TABLE AS SELECT | `create temporary table if not exists mart.t as select ...` | `create_temporary_table_as_select` |
| UPDATE JOIN table lineage | `update mart.t join app.s on ... set ...` | `update_join` |
| DELETE USING table lineage | `delete from mart.t using mart.t join app.s ...` | `delete_using` |
| DELETE alias FROM JOIN table lineage | `delete t from mart.t t join app.s s ...` | `delete_join` |
| Backquoted non-ASCII identifiers | `` select `用户ID` from `业务库`.`用户表` `` | `backquoted_identifiers` |

Implemented MySQL column-level lineage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Direct single-table projection | `select id as user_id, name from app.users` | `select_basic` |
| Alias-qualified JOIN projection | `select u.id, o.amount from users u join orders o` | `join_projection` |
| Single-level aliased subquery direct propagation | `select q.c from (select a as c from app.s) q` | `subquery_column_projection` |
| Single CTE direct propagation | `with q as (select a as c from app.s) select c from q` | `cte_column_projection` |
| UNION column sources merged by position | `select a as c1 from s1 union all select b from s2` | `union_column_projection` |
| INSERT target column list mapping | `insert into mart.t(c1, c2) select a, b from app.s` | `insert_select` |
| INSERT duplicate-key SELECT mapping | `insert into mart.t(c1) select a from app.s on duplicate key update ...` | `insert_select_on_duplicate` |
| REPLACE SELECT target column list mapping | `replace into mart.t(c1, c2) select a, b from app.s` | `replace_select` |
| CTAS output column targets | `create table mart.t as select id as c1 from app.s` | `create_table_as_select` |
| CREATE VIEW output column targets | `create view mart.v as select u.id from app.users u` | `create_view` |
| CREATE OR REPLACE VIEW output column targets | `create or replace view mart.v as select id as c1 from app.s` | `create_or_replace_view` |
| CREATE TEMPORARY TABLE output column targets | `create temporary table mart.t as select id as c1 from app.s` | `create_temporary_table_as_select` |
| UPDATE SET direct assignment mapping | `update mart.t t join app.s s ... set t.c = s.c` | `update_join` |
| UPDATE SET constant assignment target | `update mart.t set status = 'active'` | `update_join` |
| Backquoted non-ASCII column identifiers | `` select `用户ID` as `用户标识` from `业务库`.`用户表` `` | `backquoted_identifiers` |

Current MySQL diagnostics:

| Code | Meaning |
| --- | --- |
| `MYSQL_PARSE_ERROR` | MySQL SQL could not be tokenized or walked by the current MVP parser. |
| `MYSQL_STATEMENT_NOT_SUPPORTED` | The statement was recognized as MySQL but is not in the current MVP statement set. |
| `MYSQL_COLUMN_LINEAGE_NOT_IMPLEMENTED` | No column lineage was produced for a statement shape where table lineage may still be available. |

Known MySQL gaps:

| Gap | Current behavior |
| --- | --- |
| Full MySQL grammar | The MVP uses ANTLR tokenization plus a lineage walker; full parser grammar will be expanded incrementally. |
| `select *` expansion | Not expanded without schema metadata. |
| Ambiguous plain SQL dialect detection | Explicit MySQL features are auto-detected; dialect-neutral `SELECT` remains default-detected by the current detector. |
| Complex expressions and subqueries | Direct projections, common expressions, joins, UNION, CTAS/view/insert mappings, single-level subquery propagation, and single CTE propagation are covered; nested query propagation is still limited. |
| DML column lineage | `UPDATE SET` direct assignments are covered; `DELETE USING` currently emits table-level lineage. |

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
| TABLE query primary source table | `table ods.users` | `table_query` |
| SELECT with scheduler placeholders | `select ... from ods.s where dt = ${bizdate} and region = {{ region }}` | `select_with_placeholders` |
| Backquoted Chinese identifiers | `` select `用户ID` as `用户标识` from `ods层`.`用户表` `` | `quoted_chinese_identifiers` |
| INSERT OVERWRITE target and source | `insert overwrite table ads.t select ... from ods.s` | `insert_overwrite` |
| INSERT OVERWRITE with static partition and target columns | `insert overwrite table ads.t partition (...) (c1) select ...` | `insert_overwrite_partition_column_list` |
| INSERT REPLACE WHERE target and source | `insert into table ads.t target by name replace where ... select ... from ods.s` | `insert_replace_where` |
| INSERT REPLACE USING target and source | `insert into table ads.t target by name replace using (...) select ... from ods.s` | `insert_replace_using` |
| FROM-first multi-insert targets | `from ods.s insert overwrite table t1 select ... insert overwrite table t2 select ...` | `multi_insert` |
| INSERT OVERWRITE DIRECTORY export source | `insert overwrite directory '/path' using parquet select ... from ods.s` | `insert_overwrite_directory` |
| LOAD DATA target table | `load data inpath '/path' into table ods.t` | `load_data_into_table` |
| DROP TABLE affected table | `drop table if exists mart.t purge` | `drop_table` |
| TRUNCATE TABLE affected table | `truncate table ads.t partition (...)` | `truncate_table` |
| ALTER TABLE RENAME TO old and new tables | `alter table mart.old rename to mart.new` | `rename_table` |
| ALTER TABLE column maintenance | `alter table mart.t add columns (...)` | `alter_table_add_columns` |
| ALTER TABLE property maintenance | `alter table mart.t set tblproperties (...)` | `alter_table_set_properties` |
| ALTER TABLE partition maintenance | `alter table mart.t drop partition (...)` | `alter_table_drop_partition` |
| ALTER TABLE recover partitions | `alter table mart.t recover partitions` | `recover_partitions` |
| ANALYZE TABLE metadata reads | `analyze table mart.t compute statistics` | `analyze_table` |
| DESCRIBE TABLE metadata reads | `describe table formatted mart.t` | `describe_table` |
| SHOW CREATE TABLE metadata reads | `show create table mart.t` | `show_create_table` |
| SHOW COLUMNS metadata reads | `show columns in mart.t` | `show_columns` |
| SHOW PARTITIONS metadata reads | `show partitions mart.t` | `show_partitions` |
| REFRESH TABLE metadata reads | `refresh table mart.t` | `refresh_table` |
| MSCK REPAIR TABLE maintenance | `msck repair table mart.t sync partitions` | `repair_table` |
| CREATE INDEX affected table | `create index idx on table mart.t (...)` | `create_index` |
| DROP INDEX affected table | `drop index idx on table mart.t` | `drop_index` |
| COMMENT ON TABLE affected table | `comment on table mart.t is '...'` | `comment_table` |
| COMMENT ON COLUMN affected table | `comment on column mart.t.c is '...'` | `comment_column` |
| EXPLAIN wrapped statement lineage | `explain formatted select ... from ods.s` | `explain_select` |
| Multi-statement scripts | `select ...; create table ... as select ...` | `script_semicolon` |
| Script-local temporary view source propagation | `create temporary view v as select ...; insert ... select ... from v` | `script_temp_view_lineage` |
| Bad SQL recovery in scripts | `bad sql; select ... from ods.s` | `script_bad_sql_recovery` |
| Temporary view drop lifecycle | `create temporary view v as ...; drop view v; select ... from v` | `script_drop_temp_view` |
| Dynamic SQL graceful degradation | `execute immediate 'select ... from ods.s'` | `execute_immediate_dynamic_sql` |
| Non-lineage session statements | `use db`, `set catalog c`, `reset key` | `use_database`, `set_catalog`, `reset_configuration` |
| Namespace DDL without table lineage | `create namespace mart`, `drop namespace mart` | `create_namespace`, `drop_namespace` |
| Table-free metadata reads | `show namespaces`, `show catalogs`, `analyze tables` | `show_namespaces`, `show_catalogs`, `analyze_tables` |
| Additional metadata reads | `show tables`, `show views`, `show collations`, `describe namespace`, `describe query` | `show_tables`, `show_views`, `show_collations`, `describe_namespace`, `describe_query` |
| Resource and cache control statements | `refresh 'path'`, `clear cache`, `add jar ...` | `refresh_resource`, `clear_cache`, `add_jar_resource` |
| Function and procedure statements | `create function`, `drop function`, `call proc`, `show/describe function` | `create_function`, `create_udf_return_query`, `drop_function`, `call_procedure`, `show_functions`, `describe_function` |
| Variable and cursor control statements | `declare variable`, `declare cursor for select ...` | `create_variable`, `declare_cursor` |
| JOIN source tables | `from ods.users join ods.orders` | `join_basic` |
| CREATE VIEW AS SELECT | `create view mart.v as select ... from ods.s` | `create_view` |
| CREATE TEMPORARY VIEW USING provider | `create temporary view v using csv options (...)` | `create_temp_view_using` |
| ALTER VIEW AS SELECT | `alter view mart.v as select ... from ods.s` | `alter_view_as_select` |
| MERGE source and target tables | `merge into ads.t using ods.s ...` | `merge_into` |
| MERGE source subquery tables | `merge into ads.t using (select ... from ods.s) q ...` | `merge_using_subquery` |
| UPDATE with subquery sources | `update ads.t set c = (select ... from ods.s1) where id in (select ... from ods.s2)` | `update_with_subquery` |
| DELETE with subquery sources | `delete from ads.t where id in (select ... from ods.s)` | `delete_with_subquery` |
| CACHE TABLE AS SELECT source and cached target | `cache table cached as select ... from ods.s` | `cache_table_as_select` |
| Script-local CACHE TABLE propagation | `cache table c as select ... from ods.s; insert ... select ... from c` | `script_cache_table_lineage` |
| Cache table uncache lifecycle | `cache table c as ...; uncache table c; select ... from c` | `script_uncache_table` |
| CTE source table, excluding CTE alias as table | `with base as (...) select ... from base` | `cte_basic` |
| UNION input tables | `select ... from a union all select ... from b` | `union_basic` |
| Subquery input tables | `select ... from (select ... from ods.s)` | `subquery_basic` |
| PIVOT source table lineage | `select * from (...) pivot (...)` | `pivot_table_lineage` |
| PIVOT generated aggregate column lineage | `select small_total from (...) pivot (sum(amount) as total for category in ('small' as small))` | `pivot_column_lineage` |
| UNPIVOT source table lineage | `select * from mart.t unpivot (...)` | `unpivot_table_lineage` |
| Single-value UNPIVOT generated column lineage | `select metric, value from t unpivot (value for metric in (...))` | `unpivot_column_lineage` |
| Multi-value UNPIVOT generated column lineage | `select metric, v1, v2 from t unpivot ((v1, v2) for metric in ((c1, c2), ...))` | `unpivot_multi_value_column_lineage` |
| TRANSFORM source table lineage | `select transform (...) using 'script' as (...) from ods.s` | `transform_table_lineage` |
| STREAM table source lineage | `select ... from stream(ods.events) s` | `stream_table_lineage` |
| CHANGES relation source lineage | `select c.id from ods.users changes from version 1 c` | `changelog_column_projection` |
| UNNEST source table lineage | `select item from ods.orders o, unnest(o.items) u(item)` | `unnest_column_lineage` |
| JSON_TABLE source table lineage | `select name from ods.events e, json_table(e.payload, ... ) jt` | `json_table_column_lineage` |
| Alias-qualified UNNEST generated column lineage | `select u.item from ods.orders o, unnest(o.items) u(item)` | `unnest_qualified_column_lineage` |
| Alias-qualified JSON_TABLE generated column lineage | `select jt.name from ods.events e, json_table(e.payload, ... ) jt` | `json_table_qualified_column_lineage` |
| Table-valued function TABLE identifier argument | `select * from custom_tvf(table ods.users)` | `table_valued_function_table_arg` |
| Table-valued function TABLE query argument | `select * from custom_tvf(table(select ... from ods.users))` | `table_valued_function_query_arg` |
| Spark range table-valued function generated column | `select id from range(10)` | `table_valued_function_range` |
| Pipe SELECT source lineage | `from ods.users |> select id` | `pipe_select_column_projection` |
| Pipe WHERE and SELECT source lineage | `from ods.users |> where ... |> select id` | `pipe_where_select_lineage` |
| Pipe DROP and SELECT source lineage | `from ods.users |> drop name |> select id` | `pipe_drop_column_projection` |
| Pipe EXTEND source lineage | `from ods.users |> extend upper(name) as name_upper` | `pipe_extend_table_lineage` |
| Pipe EXTEND generated column lineage | `from ods.users |> extend upper(name) as name_upper |> select name_upper` | `pipe_extend_column_lineage` |
| Standalone Pipe AGGREGATE column lineage | `from ods.orders |> aggregate count(order_id) as order_cnt group by user_id` | `pipe_aggregate_table_lineage` |
| Pipe AGGREGATE generated column lineage | `from ods.orders |> aggregate count(order_id) as order_cnt group by user_id |> select order_cnt` | `pipe_aggregate_column_lineage` |
| Pipe JOIN source lineage | `from ods.users u |> join ods.orders o on ...` | `pipe_join_column_projection` |
| Pipe UNION source and column lineage | `from ods.users |> select id |> union select id from ods.admins` | `pipe_union_column_projection` |
| Pipe INTERSECT source and column lineage | `from ods.users |> select id |> intersect select id from ods.active_users` | `pipe_intersect_table_lineage` |
| Pipe EXCEPT source and column lineage | `from ods.users |> select id |> except select id from ods.deleted_users` | `pipe_except_table_lineage` |
| CTAS output and source tables | `create table mart.t as select ... from ods.s` | `ctas_column_projection` |
| CTAS with provider and partition clauses | `create table mart.t using parquet partitioned by (...) as select ...` | `ctas_using_partitioned` |
| CREATE OR REPLACE TABLE AS SELECT | `create or replace table mart.t using delta as select ... from ods.s` | `replace_table_as_select` |
| CREATE MATERIALIZED VIEW AS SELECT | `create materialized view mart.v as select ... from ods.s` | `create_materialized_view_as_select` |
| CREATE metric view code literal degradation | `create view mart.v language sql as $$...$$` | `create_metric_view_code_literal` |
| CREATE STREAMING TABLE AS SELECT | `create streaming table mart.t as select ... from stream(ods.s)` | `create_streaming_table_as_select` |
| CREATE FLOW AS INSERT lineage | `create flow f as insert into t select ... from s` | `create_flow_insert` |
| CREATE FLOW AUTO CDC degraded lineage | `create flow f as auto cdc into t from s keys (...)` | `create_flow_auto_cdc` |
| CREATE TABLE LIKE structure lineage | `create table mart.t like ods.s` | `create_table_like` |

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
| GROUP BY aggregate expression sources | `select user_id, count(order_id), sum(amount) from ods.orders group by user_id` | `aggregate_column_projection` |
| Window function argument and spec sources | `select row_number() over (partition by user_id order by created_at) from ods.orders` | `window_column_projection` |
| Single-table nested struct field sources | `select profile.city as city from ods.users` | `nested_field_projection` |
| Qualified nested struct field sources | `select u.profile.city from ods.users u join ods.orders o ...` | `qualified_nested_field_projection` |
| STREAM table direct projection | `select s.id as event_id from stream(ods.events) s` | `stream_table_lineage` |
| CHANGES relation direct projection | `select c.id as user_id from ods.users changes from version 1 c` | `changelog_column_projection` |
| UNNEST generated column propagation | `select item from ods.orders o, unnest(o.items) u(item)` | `unnest_column_lineage` |
| JSON_TABLE generated column propagation | `select name from ods.events e, json_table(e.payload, ... columns(name ...)) jt` | `json_table_column_lineage` |
| Alias-qualified generated column propagation | `select u.item, jt.name from unnest/json_table aliases` | `unnest_qualified_column_lineage`, `json_table_qualified_column_lineage` |
| Spark range generated column | `select id from range(10)` | `table_valued_function_range` |
| PIVOT generated aggregate columns | `select small_total from (...) pivot (sum(amount) as total for category in ('small' as small))` | `pivot_column_lineage` |
| Single-value UNPIVOT name/value propagation | `select metric, value from t unpivot (value for metric in (c1, c2))` | `unpivot_column_lineage` |
| Multi-value UNPIVOT value propagation | `select metric, v1, v2 from t unpivot ((v1, v2) for metric in ((c1, c2), ...))` | `unpivot_multi_value_column_lineage` |
| Pipe SELECT direct projection | `from ods.users |> select id as user_id` | `pipe_select_column_projection` |
| Pipe WHERE then SELECT direct projection | `from ods.users |> where id > 0 |> select id as user_id` | `pipe_where_select_lineage` |
| Pipe DROP then SELECT direct projection | `from ods.users |> drop name |> select id as user_id` | `pipe_drop_column_projection` |
| Pipe EXTEND generated projection | `from ods.users |> extend upper(name) as name_upper |> select name_upper` | `pipe_extend_column_lineage` |
| Standalone Pipe AGGREGATE output projection | `from ods.orders |> aggregate count(order_id) as order_cnt group by user_id` | `pipe_aggregate_table_lineage` |
| Pipe AGGREGATE generated projection | `from ods.orders |> aggregate count(order_id) as order_cnt group by user_id |> select order_cnt` | `pipe_aggregate_column_lineage` |
| Pipe JOIN direct projection | `from ods.users u |> join ods.orders o on ... |> select u.id, o.amount` | `pipe_join_column_projection` |
| LATERAL VIEW explode generated column lineage | `select item from t lateral view explode(items) e as item` | `lateral_view_explode` |
| INSERT target column list mapping | `insert into ads.t(c1, c2) select a, b from ods.s` | `insert_column_list` |
| INSERT BY NAME projection target mapping | `insert into ads.t by name select a as c1 from ods.s` | `insert_by_name` |
| INSERT REPLACE WHERE BY NAME mapping | `insert into ads.t target by name replace where ... select a as c1 from ods.s` | `insert_replace_where` |
| INSERT REPLACE USING BY NAME mapping | `insert into ads.t target by name replace using (...) select a as c1 from ods.s` | `insert_replace_using` |
| INSERT target column list over CTE propagation | `insert into ads.t(c1) with q as (...) select c1 from q` | `insert_from_cte` |
| INSERT target column list over subquery propagation | `insert into ads.t(c1) select c1 from (select a as c1 from ods.s) q` | `insert_from_subquery` |
| INSERT over script-local temporary view propagation | `create temporary view v as select a as c1 from ods.s; insert into ads.t(c1) select c1 from v` | `script_temp_view_lineage` |
| INSERT over script-local cache table propagation | `cache table c as select a from ods.s; insert into ads.t(c1) select a from c` | `script_cache_table_lineage` |
| UNION column sources merged by position | `select a as c1 from s1 union all select b from s2` | `union_column_projection` |
| EXCEPT column inputs by position | `select a as c1 from s1 except select b from s2` | `except_column_projection` |
| INTERSECT column inputs by position | `select a as c1 from s1 intersect select b from s2` | `intersect_column_projection` |
| Pipe set operator column inputs by position | `from s1 |> select a as c1 |> union/intersect/except select b from s2` | `pipe_union_column_projection`, `pipe_intersect_table_lineage`, `pipe_except_table_lineage` |
| EXPLAIN wrapped SELECT columns | `explain select id from ods.s` | `explain_select` |
| CREATE VIEW output column targets | `create view mart.v as select id from ods.s` | `create_view` |
| CREATE VIEW column list target names | `create view mart.v(c1, c2) as select a, b from ods.s` | `create_view_column_list` |
| ALTER VIEW output column targets | `alter view mart.v as select id as c1 from ods.s` | `alter_view_as_select` |
| CTAS output column targets | `create table mart.t as select id as c1 from ods.s` | `ctas_column_projection` |
| CTAS provider and partition clause output targets | `create table mart.t using parquet partitioned by (...) as select id from ods.s` | `ctas_using_partitioned` |
| CREATE OR REPLACE TABLE output column targets | `create or replace table mart.t as select id as c1 from ods.s` | `replace_table_as_select` |
| CREATE MATERIALIZED VIEW output column targets | `create materialized view mart.v as select id as c1 from ods.s` | `create_materialized_view_as_select` |
| CREATE STREAMING TABLE output column targets | `create streaming table mart.t as select id as c1 from stream(ods.s)` | `create_streaming_table_as_select` |
| Single-level CTE direct column propagation | `with base as (select id as c1 from ods.s) select c1 from base` | `cte_column_projection` |
| Chained CTE direct column propagation | `with a as (...), b as (select c1 from a) select c1 from b` | `chained_cte_column_projection` |
| CTE column alias list propagation | `with q(c1, c2) as (select a, b from ods.s) select c1 from q` | `cte_column_aliases` |
| Single-level aliased subquery direct column propagation | `select c1 from (select id as c1 from ods.s) q` | `subquery_column_projection` |

### Diagnostics

Current Spark diagnostics:

| Code | Meaning |
| --- | --- |
| `SPARK_PARSE_ERROR` | Spark SQL could not be parsed by the current grammar entry point. |
| `DYNAMIC_SQL_NOT_EXPANDED` | Dynamic SQL was parsed but intentionally not expanded for lineage extraction. |
| `CODE_LITERAL_NOT_EXPANDED` | Code literal SQL was parsed as a statement shape but not expanded for lineage extraction. |
| `CDC_LINEAGE_DEGRADED` | AUTO CDC target/source tables were extracted, but CDC-specific column semantics were not expanded. |
| `COLUMN_LINEAGE_NOT_IMPLEMENTED` | No column lineage was produced for the statement. Table lineage may still be available. |
| `COLUMN_LINEAGE_PARTIAL` | Some column lineage was produced, but at least one projection could not be resolved safely. |

### Production SQL Tolerance

Implemented Spark tolerance scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Unquoted scheduler placeholders | `${bizdate}`, `{{ region }}` in expressions | `select_with_placeholders` |
| Backquoted non-ASCII identifiers | Chinese table and column identifiers | `quoted_chinese_identifiers` |
| Bad SQL isolation in scripts | one invalid statement does not block later statements | `script_bad_sql_recovery` |
| Dynamic SQL degradation | `EXECUTE IMMEDIATE` returns diagnostics instead of guessing embedded SQL lineage | `execute_immediate_dynamic_sql` |
| Non-lineage session statements | `USE`, `SET CATALOG`, and `RESET` parse without lineage diagnostics | `use_database`, `set_catalog`, `reset_configuration` |
| Namespace and catalog statements | Namespace DDL and table-free metadata reads parse without table/column diagnostics | `create_namespace`, `drop_namespace`, `show_namespaces`, `show_catalogs`, `analyze_tables` |
| Function/procedure/variable/cursor statements | Function DDL, CALL, variable, and cursor control statements parse without table/column diagnostics | `create_function`, `call_procedure`, `create_variable`, `declare_cursor` |
| Resource/cache/metadata statements | Resource commands, cache clearing, and table-free metadata reads parse without table/column diagnostics | `refresh_resource`, `clear_cache`, `add_jar_resource`, `show_tables`, `describe_namespace` |

### Known Gaps

The following Spark lineage features are intentionally not complete yet:

| Gap | Current behavior |
| --- | --- |
| `select *` expansion | Not expanded without schema metadata. |
| Complex CTE column propagation | Chained direct CTE projection and CTE column aliases are supported; recursive CTEs and complex CTE joins are not complete yet. |
| Complex subquery column propagation | Single-level aliased direct subquery projection is supported; nested subquery chains and complex subquery joins are not complete yet. |
| Temporary view scope | Temporary view lineage is maintained inside one `parseScript` call only; persistent catalog view expansion is not implemented yet. |
| Unqualified columns in multi-table queries | Not guessed when they cannot be safely mapped to one table. |
| Complex UDTF and lateral view column propagation | Simple generated columns from UDTF input expressions and Spark `range` output are supported, including alias-qualified generated column references; broader UDTF output semantics are not complete yet. |
| Pipe set operator with schema-free TABLE right side | Pipe UNION/INTERSECT/EXCEPT column lineage is supported when the right side has explicit projections; schema-free `TABLE t` right sides are not expanded without metadata. |
| PIVOT and complex UNPIVOT column lineage | PIVOT aggregate output columns, single-value UNPIVOT, and positional multi-value UNPIVOT columns are supported; richer PIVOT grouping/value naming and UNPIVOT alias/null semantics are not complete yet. |
| TRANSFORM column lineage | Table-level lineage is supported; script output semantics are not propagated yet. |
| Pipe AGGREGATE complex grouping semantics | Simple standalone aggregate outputs and following SELECT propagation are supported; grouping analytics and complex grouping sets are not complete yet. |
| Dynamic SQL expansion | `EXECUTE IMMEDIATE` is parsed and diagnosed, but embedded SQL text is not recursively parsed. |
| Code literal expansion | Metric view code literals are parsed and diagnosed, but embedded code text is not recursively parsed. |
| CDC column semantics | AUTO CDC source/target tables are extracted; CDC-specific field propagation is not complete yet. |
| Multi-insert column lineage | Table-level lineage is supported; per-target column lineage is not emitted yet. |
| Complex nested fields and structs | Basic nested field paths are preserved; schema-aware struct expansion is not implemented yet. |
