# Supported Scenarios

This document records implemented behavior as LineSQL evolves. Every new parser capability should update this file and add or update SQL cases under the related dialect test resources.

## Dialect Detection

Dialect detection is intentionally conservative: LineSQL uses clear syntax anchors to rank dialect candidates, records confidence and reason metadata, and keeps Spark as the current generic fallback for dialect-neutral SQL.

Current detector case assets:

```text
linesql-core/src/test/java/io/github/linesql/core/internal/SimpleDialectDetectorTest.java
linesql-cli/src/test/java/io/github/linesql/cli/LineSqlAutoDetectionIntegrationTest.java
```

Implemented detection anchors:

| Dialect | Anchor examples |
| --- | --- |
| MySQL | `REPLACE INTO`, `ON DUPLICATE KEY`, `LIMIT offset, size`, `UPDATE ... JOIN ... SET`, `LOW_PRIORITY`/`QUICK` DML modifiers, `RENAME TABLE`, `LOCK TABLES`, `INTO OUTFILE`, `JSON_TABLE`, `LOAD XML`, JSON arrow operators |
| Hive | `ROW FORMAT`, `STORED AS`, `SERDEPROPERTIES`, `CLUSTERED BY` |
| Flink | connector options, `WATERMARK FOR` |
| StarRocks | `CREATE TABLE ... DUPLICATE KEY`, `CREATE TABLE ... AGGREGATE KEY`, `CREATE TABLE ... DISTRIBUTED BY HASH`, replication properties |
| Oracle | `FROM DUAL`, `CONNECT BY`, `START WITH`, `AS OF TIMESTAMP`, `AS OF SCN`, `CREATE SEQUENCE`, `CREATE SYNONYM`, `CREATE DATABASE LINK` |
| SQL Server | `SELECT TOP n`, bracketed identifiers, `WITH (NOLOCK)`, bracketed DML identifiers |
| Spark | `INSERT OVERWRITE`, `LATERAL VIEW`, `CREATE TEMPORARY VIEW`, `USING`, fallback |

Known conflict guards:

| Guard | Reason |
| --- | --- |
| Spark `MERGE INTO` is not classified as Oracle | `MERGE INTO` is shared across engines and is not a safe Oracle-only anchor. |
| JSON path array wildcard `[*]` is not classified as SQL Server | Brackets inside strings are not SQL Server identifiers; `JSON_TABLE(...)` is treated as a MySQL anchor. |
| MySQL `ON DUPLICATE KEY` is not classified as StarRocks | StarRocks key anchors are scoped to `CREATE TABLE` statements. |
| MySQL executable version comments are not dropped as ordinary comments | `/*!80000 ... */` carries MySQL SQL and is treated as a MySQL dialect anchor. |

Ambiguous DML such as bare `UPDATE ... FROM`, `DELETE ... USING`, or `DELETE ... JOIN` can be valid in more than one engine. Automatic detection should rely on additional anchors when available; callers can pass an explicit dialect hint when the execution engine is known.

Implemented script-level public API scenarios:

| Scenario | Example shape | Test asset |
| --- | --- | --- |
| Per-statement auto detection in scripts | Hive DDL; Flink DDL; SQL Server `TOP` query | `LineSqlAutoDetectionIntegrationTest.parseScriptAutoDetectsEachStatementIndependently` |
| Partial results after bad SQL | valid SELECT; invalid statement; valid Flink DDL | `LineSqlAutoDetectionIntegrationTest.parseScriptKeepsPartialResultsAfterBadStatement` |

Implemented CLI scenarios:

| Scenario | Example shape | Test asset |
| --- | --- | --- |
| Explicit dialect option | `linesql --dialect MYSQL "select id from ods.users"` | `MainTest.acceptsExplicitDialectOption` |
| Equals-style dialect option | `linesql --dialect=SQLSERVER "select top 10 id from dbo.users"` | `MainTest.acceptsDialectEqualsOption` |
| Common dialect aliases | `linesql --dialect postgres "select id from public.users"` | `MainTest.acceptsCommonDialectAliases` |
| STDIN with explicit dialect | `cat query.sql \| linesql --dialect ORACLE` | `MainTest.readsSqlFromStdinWhenNoSqlArgsProvided` |
| Unsupported dialect rejection | `linesql --dialect db2 "select 1"` | `MainTest.rejectsUnknownDialect` |

## PostgreSQL

PostgreSQL is a baseline parser dialect path. The current implementation uses a dedicated lightweight ANTLR grammar for common PostgreSQL lineage SQL.

Current PostgreSQL SQL case assets:

```text
linesql-dialect-postgresql/src/test/resources/sql/postgresql/manifest.json
linesql-dialect-postgresql/src/test/resources/sql/postgresql/cases/*.sql
```

Implemented PostgreSQL scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Basic SELECT source and columns | `select id as user_id, name from public.users` | `select_basic` |
| JOIN source tables and projections | `select u.id, o.amount from users u join orders o ...` | `join_projection` |
| INSERT SELECT with RETURNING | `insert into mart.t select ... returning ...` | `insert_select_returning` |
| INSERT SELECT with ON CONFLICT | `insert into mart.t select ... on conflict (...) do update ...` | `insert_on_conflict` |
| PostgreSQL INSERT variants | `insert ... overriding system value select ...`, `insert ... on conflict on constraint ... do update ... where ...`, `insert ... default values`, `insert ... values (...)` | `insert_overriding_system_select`, `insert_on_conflict_constraint_where`, `insert_default_values`, `insert_values_target_columns` |
| WITH before INSERT SELECT | `with q as (...) insert into mart.t select ... from q` | `with_insert_select` |
| WITH RECURSIVE SELECT | `with recursive q(...) as (...) select ... from q` | `with_recursive_select` |
| COPY FROM target table and column list | `copy mart.t(c1, c2) from '/tmp/file.csv' with (...)` | `copy_from_csv` |
| COPY table TO keeps exported column lineage | `copy mart.t(c1, c2) to '/tmp/file.csv' with (...)` | `copy_table_to_file` |
| COPY query TO keeps exported query lineage | `copy (select ... from mart.s) to stdout with csv header` | `copy_query_to_stdout` |
| CREATE TABLE schema DDL | `create table mart.t (...)` | `create_table_schema` |
| PostgreSQL table inheritance and partition DDL | `create unlogged table ... partition by ...`, `create table child partition of parent ...`, `create table child (...) inherits(parent)`, `alter table parent attach/detach partition child ...` | `create_unlogged_partitioned_table`, `create_partition_of_table`, `create_table_inherits`, `alter_table_attach_partition`, `alter_table_detach_partition` |
| Schema, sequence, and session control | `create schema ...`, `drop schema ...`, `create/alter/drop sequence ...`, `set search_path to ...` | `create_schema`, `drop_schema`, `create_sequence`, `alter_sequence`, `drop_sequence`, `set_search_path` |
| Function lifecycle DDL | `create or replace function ... returns ... language ... as ...`, `drop function ...` | `create_function`, `drop_function` |
| Extension lifecycle control | `create extension if not exists ...`, `drop extension if exists ... cascade` | `create_extension`, `drop_extension` |
| Table privilege statements | `grant select on table mart.t to role`, `revoke update on table mart.t from role` | `grant_table_privilege`, `revoke_table_privilege` |
| Row-level security policy lifecycle | `create policy ... on mart.t using (...)`, `create policy ... with check (...)`, `drop policy ... on mart.t`; policy predicates are returned as `WHERE` usages | `create_policy_using`, `create_policy_with_check`, `drop_policy` |
| Row-level security table switches | `alter table mart.t enable row level security`, `alter table mart.t force row level security` | `alter_table_enable_row_security`, `alter_table_force_row_security` |
| Logical replication publication lifecycle | `create publication ... for table t1, t2`, `alter publication ... add table t`, `create publication ... for all tables`, `drop publication ...`; table-scoped publications return published tables as metadata inputs | `create_publication_for_tables`, `alter_publication_add_table`, `create_publication_all_tables`, `drop_publication` |
| Logical replication subscription lifecycle | `create subscription ... connection ... publication ...`, `alter subscription ... set publication ...`, `drop subscription ...` | `create_subscription`, `alter_subscription_set_publication`, `drop_subscription` |
| CREATE TABLE AS SELECT | `create table mart.t as select ... from public.s`, `create table mart.t(c1, c2) as select ...`, `create table mart.t as select ... with no data` | `create_table_as_select`, `create_table_as_select_column_list`, `create_table_as_select_with_no_data` |
| SELECT INTO created table lineage | `select ... into mart.t from public.s`; `select ... into temporary table tmp_t from public.s` | `select_into_table`, `select_into_temporary_table` |
| CREATE TABLE LIKE INCLUDING source table | `create table mart.t (like mart.s including all)` | `create_table_like_including` |
| CREATE VIEW AS SELECT | `create view mart.v as select ... from public.s` | `create_view` |
| CREATE MATERIALIZED VIEW AS SELECT | `create materialized view mart.mv as select ... from mart.s`, `create materialized view mart.mv as select ... with no data` | `create_materialized_view`, `create_materialized_view_with_no_data` |
| VALUES and set-returning function relations | `values (...)`; `from (values (...)) as v(c)`; `cross join lateral jsonb_array_elements_text(t.tags) as e(value)` | `values_query`, `derived_values_table`, `lateral_jsonb_array_elements` |
| REFRESH MATERIALIZED VIEW affected view | `refresh materialized view concurrently mart.mv` | `refresh_materialized_view` |
| ALTER MATERIALIZED VIEW affected view | `alter materialized view mart.mv rename to mv2`, `alter materialized view mart.mv set schema archive` | `alter_materialized_view_rename`, `alter_materialized_view_set_schema` |
| COMMENT ON TABLE affected table | `comment on table mart.t is '...'` | `comment_table` |
| COMMENT ON COLUMN affected table | `comment on column mart.t.c is '...'` | `comment_column` |
| CREATE INDEX affected table, index keys, included columns, and partial predicate columns | `create index concurrently idx on mart.t using btree(c) where flag = true`; `create index ... on mart.t (lower(c), ts desc) include (c2) where flag = true` | `create_index_partial`, `create_index_include_partial` |
| ANALYZE table metadata read | `analyze verbose mart.t(c1, c2)`; selected columns are returned as `READ_METADATA` usages | `analyze_table` |
| VACUUM affected table | `vacuum (full, analyze) mart.t(c1, c2)`; selected columns are returned as `READ_METADATA` usages | `vacuum_table` |
| REINDEX TABLE affected table | `reindex table concurrently mart.t` | `reindex_table` |
| DROP TABLE affected table | `drop table if exists mart.t`, `drop table if exists mart.t restrict` | `drop_table`, `drop_table_restrict` |
| DROP VIEW affected view | `drop view if exists mart.v`, `drop view if exists mart.v cascade` | `drop_view`, `drop_view_cascade` |
| DROP MATERIALIZED VIEW affected view | `drop materialized view if exists mart.mv` | `drop_materialized_view` |
| TRUNCATE TABLE affected table | `truncate table mart.t` | `truncate_table` |
| ALTER TABLE column maintenance | `alter table mart.t add column c timestamp` | `alter_table_add_column` |
| CTE column propagation | `with q as (...) select q.c1 from q` | `cte_column_projection` |
| UPDATE FROM with RETURNING | `update mart.t set ... from staging.s where ... returning ...` | `update_from_returning` |
| DELETE with subquery and RETURNING | `delete from mart.t where id in (...) returning ...` | `delete_using_returning` |
| WITH before UPDATE FROM | `with q as (...) update mart.t set c = q.c from q where ...` | `with_update_from` |
| UPDATE ONLY FROM | `update only mart.t set c = s.c from staging.s where ...` | `update_only_from` |
| WITH before DELETE USING | `with q as (...) delete from mart.t using q where ...` | `with_delete_using` |
| DELETE FROM ONLY USING | `delete from only mart.t using staging.s where ...` | `delete_only_using` |
| MERGE target and source tables | `merge into mart.t using staging.s on ... when matched then update ...` | `merge_into` |
| MERGE source subquery tables | `merge into mart.t using (select ... from staging.s) q on ...` | `merge_using_subquery` |
| WITH before MERGE | `with q as (...) merge into mart.t using q on ...` | `with_merge` |
| JOIN, WHERE, GROUP BY, HAVING, ORDER BY usages | `select ... from u join o ... where ... group by ...` | `clause_column_usage` |
| PostgreSQL cast operator and ILIKE usage | `select id::text ... where email ilike ...` | `postgres_cast_ilike` |
| DISTINCT ON and null ordering | `select distinct on (k) ... order by k, ts desc nulls last` | `distinct_on_order_nulls` |
| Aggregate FILTER and ordered-set aggregate | `count(*) filter (where status = ...)`, `percentile_cont(...) within group (order by c)` | `aggregate_filter_within_group` |
| Window frame projection and usage | `sum(v) over(partition by k order by ts rows between ... and current row)` | `window_frame_projection` |
| JSON operators and array subscripts | `payload ->> 'name'`, `payload ? 'name'`, `attrs @> ...`, `tags[1]`, `metrics[1:2]` | `json_array_operator_projection` |
| Double-quoted non-ASCII identifiers | `select "用户ID" from "业务库"."用户表"` | `quoted_identifiers` |

Known PostgreSQL gaps:

| Gap | Current behavior |
| --- | --- |
| Full PostgreSQL grammar | A dedicated lightweight ANTLR grammar exists for baseline cases; broad PostgreSQL syntax is still being expanded. |
| RETURNING row lineage | Write lineage is preserved, but returned-row lineage is not modeled as a separate output stream yet. |
| Full ON CONFLICT action lineage | Source-to-target insert lineage is preserved; conflict update action lineage is not fully expanded yet. |
| Broader PostgreSQL-specific DDL | Core table/view/index/comment/maintenance DDL is covered; advanced storage, partition, policy, publication/subscription, and procedural bodies are still being expanded. |

## OceanBase

OceanBase is a compatibility-mode dialect path. The current implementation models OceanBase as a two-mode SQL surface and delegates common lineage to either MySQL-mode or Oracle-mode parsing while keeping the public dialect as `OCEANBASE`.

Current OceanBase SQL case assets:

```text
linesql-dialect-oceanbase/src/test/resources/sql/oceanbase/manifest.json
linesql-dialect-oceanbase/src/test/resources/sql/oceanbase/cases/*.sql
```

Implemented OceanBase scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| MySQL mode SELECT source and columns | `select id as user_id, name from app.users` | `mysql_mode_select` |
| MySQL mode INSERT SELECT | `insert into mart.t select ... from app.s join app.o ...` | `mysql_mode_insert_select` |
| MySQL mode CREATE TABLE AS SELECT | `create table mart.t as select ... from app.s` | `mysql_mode_create_table_as_select` |
| MySQL mode CREATE TABLE schema DDL | `create table mart.t (...)` | `mysql_mode_create_table_schema` |
| MySQL mode CREATE TABLE constraints | `primary key`, `unique key`, `constraint ... unique key`, table/column `foreign key ... references ... on delete/on update` | `mysql_mode_create_table_constraints`, `mysql_mode_create_table_named_unique_constraint`, `mysql_mode_create_table_foreign_key_reference`, `mysql_mode_create_table_foreign_key_actions`, `mysql_mode_create_table_named_foreign_key_index` |
| MySQL mode UPDATE JOIN | `update mart.t join staging.s ... set ...` | `mysql_mode_update_join` |
| MySQL mode DELETE USING | `delete from mart.t using mart.t join staging.s ...` | `mysql_mode_delete_using` |
| MySQL mode LOAD DATA | `load data local infile ... into table mart.t (...) set ...`, `low_priority ... replace into table ... partition (...) character set ...`, `concurrent local ... ignore`, `columns terminated by ...` | `mysql_mode_load_data`, `mysql_mode_load_data_replace_partition_charset`, `mysql_mode_load_data_concurrent_ignore`, `mysql_mode_load_data_columns_options` |
| MySQL mode INSERT target partition | `insert into mart.t partition(p202601) (c1, c2) select ...` | `mysql_mode_insert_partition_select` |
| MySQL mode REPLACE target partition | `replace into mart.t partition(p202601) (c1, c2) select ...` | `mysql_mode_replace_partition_select` |
| MySQL mode EXPLAIN FORMAT JSON | `explain format = json select ... from app.t` | `mysql_mode_explain_format_json_select` |
| MySQL mode EXPLAIN BASIC | `explain basic select ... from app.t` | `mysql_mode_explain_basic_select` |
| MySQL mode EXPLAIN PARTITIONS | `explain partitions select ... from app.t partition (...)` | `mysql_mode_explain_partitions_select` |
| MySQL mode EXPLAIN OUTLINE INSERT | `explain outline insert into mart.t (...) select ...` | `mysql_mode_explain_outline_insert` |
| MySQL mode subpartition DDL | `create table mart.t (...) partition by range columns(...) subpartition by hash(...)` | `mysql_mode_create_table_subpartition` |
| MySQL mode CREATE TABLE with TABLEGROUP | `create table mart.t (...) tablegroup = tg partition by ...` | `mysql_mode_create_table_with_tablegroup` |
| MySQL mode server and routine metadata reads | `show variables`, `show global variables like ...`, `show session status where ...`, `show grants`, `show processlist`, `show warnings`, `show full tables from ...`, `show open tables from ...`, `show table status from ...`, `show triggers/events from ...`, `show procedure status where ...` | `mysql_mode_show_variables`, `mysql_mode_show_global_variables_like`, `mysql_mode_show_session_status_where`, `mysql_mode_show_grants`, `mysql_mode_show_processlist`, `mysql_mode_show_warnings`, `mysql_mode_show_full_tables_from_schema`, `mysql_mode_show_open_tables_like`, `mysql_mode_show_table_status_from_schema`, `mysql_mode_show_triggers_from_schema`, `mysql_mode_show_events_from_schema`, `mysql_mode_show_procedure_status` |
| MySQL mode generated/check/invisible DDL | `generated always as (...)`, `on update ...`, `check (...)`, invisible column/index attributes | `mysql_mode_create_table_generated_columns`, `mysql_mode_create_table_check_invisible` |
| MySQL mode CREATE TABLE LIKE | `create table mart.t like app.s` | `mysql_mode_create_table_like` |
| MySQL mode CREATE TABLE options and data types | `engine`, row/storage/table options, `enum`, `set`, unsigned/zerofill, charset/collation, column storage attributes | `mysql_mode_create_table_advanced_options`, `mysql_mode_create_table_storage_options`, `mysql_mode_create_table_enum_set_types`, `mysql_mode_create_table_mysql_type_attributes`, `mysql_mode_create_table_column_storage_attributes` |
| MySQL mode CREATE TABLE index definitions | primary/unique key options, `fulltext index`, `spatial index` | `mysql_mode_create_table_index_options`, `mysql_mode_create_table_fulltext_spatial_index` |
| MySQL mode CREATE TABLE partitioning | `partition by hash`, `partition by key`, `partition by range`, `partition by range columns`, `partition by list columns` | `mysql_mode_create_table_hash_partition`, `mysql_mode_create_table_key_partition`, `mysql_mode_create_table_range_partition`, `mysql_mode_create_table_range_columns_partition`, `mysql_mode_create_table_list_columns_partition` |
| MySQL mode CREATE LOCAL INDEX | `create index idx on mart.t (c1) local` | `mysql_mode_create_local_index` |
| MySQL mode CREATE GLOBAL INDEX with partitions | `create unique index idx on mart.t (c1) global partition by (c1) partitions 8` | `mysql_mode_create_global_index_partition` |
| MySQL mode ALTER TABLE index lifecycle | `alter table mart.t add/drop/rename/alter index ...` | `mysql_mode_alter_table_add_index`, `mysql_mode_alter_table_drop_index`, `mysql_mode_alter_table_rename_index`, `mysql_mode_alter_table_alter_index_visibility` |
| MySQL mode ALTER TABLE constraints | `add/drop primary key`, `add/drop/alter check`, `add/drop foreign key ... references ...` | `mysql_mode_alter_table_add_primary_key`, `mysql_mode_alter_table_drop_primary_key`, `mysql_mode_alter_table_add_check_enforced`, `mysql_mode_alter_table_drop_check`, `mysql_mode_alter_table_alter_check_not_enforced`, `mysql_mode_alter_table_add_foreign_key`, `mysql_mode_alter_table_add_foreign_key_actions`, `mysql_mode_alter_table_add_named_foreign_key_index`, `mysql_mode_alter_table_drop_foreign_key` |
| MySQL mode ALTER TABLE partition maintenance | `alter table mart.t exchange partition p with table staging.t without validation`, `rebuild/optimize/analyze/repair partition p`, `coalesce partition n`, `reorganize partition p into (...)`, `remove partitioning` | `mysql_mode_alter_table_exchange_partition`, `mysql_mode_alter_table_rebuild_partition`, `mysql_mode_alter_table_optimize_partition`, `mysql_mode_alter_table_analyze_partition`, `mysql_mode_alter_table_repair_partition`, `mysql_mode_alter_table_coalesce_partition`, `mysql_mode_alter_table_reorganize_partition`, `mysql_mode_alter_table_remove_partitioning` |
| MySQL mode ALTER TABLE options | `alter table mart.t tablegroup = tg`, `alter table mart.t set (tablegroup = tg)`, `comment = '...'`, `convert to character set ... collate ...` | `mysql_mode_alter_table_tablegroup`, `mysql_mode_alter_table_set_tablegroup`, `mysql_mode_alter_table_comment_charset` |
| MySQL mode RENAME TABLE | `rename table app.old to app.new` | `mysql_mode_rename_table` |
| MySQL mode multi-table RENAME TABLE | `rename table app.old to app.new, mart.old to mart.new` | `mysql_mode_rename_multiple_tables` |
| MySQL mode table metadata reads | `show create table mart.t`, `show full columns from mart.t like ...`, `show columns from t from mart`, `show index from mart.t`, `show keys in t in mart`, `show full tables from mart`, `describe mart.t` | `mysql_mode_show_create_table`, `mysql_mode_show_columns_from_table`, `mysql_mode_show_columns_from_schema_table`, `mysql_mode_show_index_from_table`, `mysql_mode_show_keys_from_schema_table`, `mysql_mode_show_full_tables_from_schema`, `mysql_mode_describe_table` |
| MySQL mode table maintenance reads | `analyze table mart.t update histogram on c`, `check table mart.t`, `optimize table mart.t`, `repair table mart.t` | `mysql_mode_analyze_table`, `mysql_mode_check_table`, `mysql_mode_optimize_table`, `mysql_mode_repair_table` |
| OceanBase outline optimizer control | `create outline ... on ...`, `create or replace outline ... on ... to ...`, `create outline ... on sql_id using hint ...`, `create format outline ...`, `drop outline ...`, `drop format outline ...` | `control_create_outline_sql_text`, `control_create_or_replace_outline_to_target`, `control_create_outline_sql_id_using_hint`, `control_create_format_outline`, `control_drop_outline`, `control_drop_format_outline` |
| MySQL mode CREATE TRIGGER | `create trigger trg after insert on mart.t for each row ...` | `mysql_mode_create_trigger` |
| MySQL mode DROP TRIGGER | `drop trigger if exists mart.trg` | `mysql_mode_drop_trigger` |
| MySQL mode CREATE/ALTER/DROP EVENT | `create event e on schedule every 1 day do insert into ... select ...`, `alter event e on schedule ...`, `drop event if exists mart.e` | `mysql_mode_create_event_insert_select`, `mysql_mode_alter_event`, `mysql_mode_drop_event` |
| MySQL mode CREATE/ALTER/DROP PROCEDURE | `create procedure mart.p() begin ... end`, `alter procedure mart.p comment ...`, `drop procedure if exists mart.p` | `mysql_mode_create_procedure`, `mysql_mode_alter_procedure`, `mysql_mode_drop_procedure` |
| MySQL mode INSERT/REPLACE SET lineage | `insert into mart.t set c = ...`, `replace into mart.t set c = ...`, scalar subqueries and `on duplicate key update` row aliases | `mysql_mode_insert_set`, `mysql_mode_insert_set_scalar_subquery`, `mysql_mode_insert_set_on_duplicate`, `mysql_mode_insert_set_row_alias_on_duplicate`, `mysql_mode_replace_set`, `mysql_mode_replace_set_scalar_subquery` |
| MySQL mode INSERT VALUES variants | `values row(...)`, `on duplicate key update c = default(c)` | `mysql_mode_insert_values_row_constructor`, `mysql_mode_insert_values_on_duplicate_default` |
| MySQL mode partitioned CTAS | `create table mart.t (...) partition by hash(...) as select ...` | `mysql_mode_create_table_partition_as_select` |
| MySQL mode optimizer hints | `select /*+ leading(...) index(...) */ ... from app.t join ...` | `mysql_mode_select_optimizer_hint_lineage` |
| MySQL mode INTERSECT DISTINCT | `select c from app.a intersect distinct select c from app.b` | `mysql_mode_intersect_distinct_column_projection` |
| MySQL mode MINUS DISTINCT | `select c from app.a minus distinct select c from app.b` | `mysql_mode_minus_distinct_column_projection` |
| MySQL mode TRUNCATE TABLE | `truncate table mart.t` | `mysql_mode_truncate_table` |
| MySQL mode multi-table DELETE | `delete t1, t2 from t1 join t2 ... where ...` | `mysql_mode_delete_multi_table` |
| MySQL mode INSERT SELECT ON DUPLICATE KEY UPDATE | `insert into mart.t (...) select ... from app.s on duplicate key update ...` | `mysql_mode_insert_select_on_duplicate` |
| MySQL mode UPDATE/DELETE WHERE EXISTS | `update mart.t ... where exists (...)`, `delete from mart.t where exists (...)` | `mysql_mode_update_where_exists_subquery`, `mysql_mode_delete_where_exists_subquery` |
| MySQL mode WITH before UPDATE/DELETE EXISTS | `with q as (...) update/delete ... where exists (...)` | `mysql_mode_with_update_exists_subquery`, `mysql_mode_with_delete_exists_subquery` |
| MySQL mode REPLACE SELECT | `replace into mart.t (...) select ... from app.s` | `mysql_mode_replace_select` |
| MySQL mode aggregate expressions | `select k, count(v), sum(v) from app.t group by k` | `mysql_mode_aggregate_expression_projection` |
| MySQL mode window expressions | `row_number() over(partition by ... order by ...), sum(...) over(...)` | `mysql_mode_window_function_projection` |
| MySQL mode CTE propagation | `with q as (select ... from app.t) select ... from q` | `mysql_mode_cte_column_projection` |
| MySQL mode IN subquery predicates | `where id in (select user_id from app.s)`, `where (c1, c2) in (select x, y from app.s)` | `mysql_mode_in_subquery_column_usage`, `mysql_mode_tuple_in_subquery_column_usage` |
| MySQL mode quantified and CASE/EXISTS subqueries | `where c = any (select ...)`, `case when exists (select ...) then ... end` | `mysql_mode_quantified_any_some_subquery_predicate`, `mysql_mode_case_exists_subquery_projection` |
| MySQL mode JSON_TABLE | `json_table(payload, '$.items[*]' columns (...))`, nested `path` columns | `mysql_mode_json_table_projection`, `mysql_mode_json_table_nested_columns` |
| MySQL mode JSON expressions | `payload->>'$.id'`, `json_value(payload, '$.vin' returning char(...))` | `mysql_mode_json_extract_expression`, `mysql_mode_json_value_returning_lineage` |
| MySQL mode JSON predicate and mutation functions | `json_contains(...)`, `member of (...)`, `json_overlaps(...)`, `json_search(...)`, `json_schema_valid(...)`, `json_length(...)`, `json_set(...)`, `json_merge_patch(...)` | `mysql_mode_json_contains_predicate_usage`, `mysql_mode_json_member_of_predicate`, `mysql_mode_json_overlaps_predicate_usage`, `mysql_mode_json_search_schema_predicate_usage`, `mysql_mode_json_mutation_function_lineage` |
| MySQL mode READ_CONSISTENCY hint | `select /*+ read_consistency(weak) */ ... from app.t` | `mysql_mode_read_consistency_hint` |
| MySQL mode QUERY_TIMEOUT hint | `select /*+ query_timeout(1000000) */ ... from app.t` | `mysql_mode_query_timeout_hint` |
| MySQL mode OceanBase system view | `select tenant_id from oceanbase.__all_tenant where ...` | `mysql_mode_oceanbase_system_view` |
| MySQL mode SELECT FOR UPDATE | `select ... from mart.t where ... for update nowait` | `mysql_mode_select_for_update_nowait` |
| MySQL mode SELECT FOR SHARE | `select ... from mart.t where ... for share skip locked` | `mysql_mode_select_for_share_skip_locked` |
| MySQL mode flashback query | `select ... from app.t as of snapshot 1582807800000000` | `mysql_mode_flashback_snapshot` |
| MySQL mode SELECT INTO OUTFILE | `select ... into outfile '/tmp/x.csv' fields terminated by ',' from app.t` | `mysql_mode_select_into_outfile` |
| MySQL mode trailing SELECT INTO OUTFILE | `select ... from app.t where ... into outfile '/tmp/x.csv'` | `mysql_mode_select_trailing_into_outfile` |
| MySQL mode LOAD XML | `load xml infile ... into table ods.t rows identified by ...`, `load xml ... set c = ...` | `mysql_mode_load_xml_rows_identified`, `mysql_mode_load_xml_set_assignments` |
| MySQL mode transaction start and isolation control | `start transaction with consistent snapshot, read write`, `start transaction read write, with consistent snapshot`, `set [global/session] transaction isolation level ...` | `mysql_mode_start_transaction_snapshot`, `mysql_mode_start_transaction_read_write`, `mysql_mode_set_transaction_read_only`, `mysql_mode_set_transaction_isolation_repeatable_read`, `mysql_mode_set_transaction_isolation_read_committed` |
| MySQL mode XA transaction control | `xa start`, `xa end ... suspend for migrate`, `xa prepare`, `xa commit ... one phase`, `xa rollback`, `xa recover convert xid` | `mysql_mode_xa_start`, `mysql_mode_xa_end_suspend`, `mysql_mode_xa_prepare`, `mysql_mode_xa_commit_one_phase`, `mysql_mode_xa_rollback`, `mysql_mode_xa_recover` |
| MySQL mode transaction commit | `commit and chain` | `mysql_mode_commit_chain` |
| MySQL mode savepoint lifecycle | `savepoint sp`, `rollback to savepoint sp`, `release savepoint sp` | `mysql_mode_savepoint`, `mysql_mode_rollback_to_savepoint`, `mysql_mode_release_savepoint` |
| MySQL mode session and variable settings | `set global undo_retention = 900`, `set names ... collate ...`, `set character set ...` | `mysql_mode_set_global_undo_retention`, `mysql_mode_set_names_collate`, `mysql_mode_set_character_set` |
| MySQL mode account and privilege control | `create/alter/drop user ...`, `show create user ...`, `create/drop/set role ...`, `grant ... on ... to ...`, `revoke ... on ... from ...` | `mysql_mode_create_user`, `mysql_mode_alter_user`, `mysql_mode_drop_user`, `mysql_mode_show_create_user`, `mysql_mode_create_role`, `mysql_mode_drop_role`, `mysql_mode_set_role`, `mysql_mode_grant_privileges`, `mysql_mode_revoke_privileges` |
| MySQL mode admin and replication metadata/control | `flush privileges`, `kill query ...`, `lock/unlock instance`, `clone local`, `clone instance from donor`, `show binary logs`, `show master status`, `show replica status`, `change replication source to ...`, `change master to ...`, `start replica`, `stop slave`, `reset replica all` | `mysql_mode_flush_privileges`, `mysql_mode_kill_query`, `mysql_mode_lock_instance_for_backup`, `mysql_mode_unlock_instance`, `mysql_mode_clone_local_data_directory`, `mysql_mode_clone_instance_from_donor`, `mysql_mode_show_binary_logs`, `mysql_mode_show_master_status`, `mysql_mode_show_replica_status`, `mysql_mode_change_replication_source`, `mysql_mode_change_master_to`, `mysql_mode_start_replica`, `mysql_mode_stop_slave`, `mysql_mode_reset_replica_all` |
| MySQL mode dynamic SQL and routine calls | `do ...`, `call proc(...)`, `prepare stmt from ...`, `execute stmt`, `deallocate prepare stmt` | `mysql_mode_do_statement`, `mysql_mode_call_statement`, `mysql_mode_prepare_statement`, `mysql_mode_execute_statement`, `mysql_mode_deallocate_prepare` |
| MySQL mode LOCK TABLES | `lock tables mart.t read local, mart.u write` | `mysql_mode_lock_tables` |
| Oracle mode DUAL pseudo table query | `select sysdate from dual` | `oracle_mode_dual` |
| Oracle mode statistics maintenance | `analyze table ... compute/estimate/delete statistics`, `analyze index ... validate structure` | `oracle_mode_analyze_table_all_columns`, `oracle_mode_analyze_table_columns_sample`, `oracle_mode_analyze_table_delete_statistics`, `oracle_mode_analyze_index_validate_structure` |
| Oracle mode EXPLAIN PLAN | `explain plan for select ...`, `explain plan into plan_table for insert ... select ...`, `explain plan for update/delete/merge ...` | `oracle_mode_explain_plan_select`, `oracle_mode_explain_plan_insert_select`, `oracle_mode_explain_plan_update`, `oracle_mode_explain_plan_delete`, `oracle_mode_explain_plan_merge` |
| Oracle mode MERGE | `merge into mart.t using staging.s on (...) when matched ...` | `oracle_mode_merge` |
| Oracle mode CREATE VIEW | `create view mart.v as select ... from app.s`, `create view ... bequeath definer/current_user as select ...` | `oracle_mode_create_view`, `oracle_mode_create_view_bequeath_definer`, `oracle_mode_create_view_bequeath_current_user` |
| Oracle mode CREATE VIEW WITH READ ONLY | `create view mart.v as select ... from app.s with read only` | `oracle_mode_create_view_read_only` |
| Oracle mode CREATE VIEW WITH CHECK OPTION | `create view mart.v as select ... from app.s with check option`, `with check option constraint name` | `oracle_mode_create_view_check_option`, `oracle_mode_create_view_check_option_constraint` |
| Oracle mode FORCE VIEW | `create or replace force view mart.v as select ... from ods.s` | `oracle_mode_create_force_view` |
| Oracle mode NO FORCE VIEW | `create no force view mart.v as select ... from ods.s` | `oracle_mode_create_no_force_view` |
| Oracle mode CREATE TABLE schema DDL | `create table mart.t (id number primary key, name varchar2(...))`, `create table ... organization external (...)` | `oracle_mode_create_table_schema`, `oracle_mode_create_external_table` |
| Oracle mode global/private temporary table | `create global temporary table t (...) on commit preserve rows`, `create private temporary table ora$ptt_t (...) on commit drop definition`, `create private temporary table ora$ptt_t on commit preserve definition as select ...` | `oracle_mode_create_global_temporary_table`, `oracle_mode_create_global_temporary_table_as_select`, `oracle_mode_create_private_temporary_table`, `oracle_mode_create_private_temporary_table_as_select` |
| Oracle mode table/view maintenance DDL | `drop table mart.t`, `drop table mart.t cascade constraints purge`, `drop view mart.v`, `alter table mart.t rename to ...`, `alter table mart.t add c number` | `oracle_mode_drop_table`, `oracle_mode_drop_table_cascade_purge`, `oracle_mode_drop_view`, `oracle_mode_rename_table`, `oracle_mode_alter_table_add_column` |
| Oracle mode ALTER TABLE maintenance | `alter table t add partition ...`, `alter table t drop partition ...`, `alter table t truncate partition ... update indexes`, `alter table t modify/drop/rename column ...` | `oracle_mode_alter_table_add_partition`, `oracle_mode_alter_table_drop_partition`, `oracle_mode_alter_table_truncate_partition`, `oracle_mode_alter_table_modify_column`, `oracle_mode_alter_table_drop_column`, `oracle_mode_alter_table_rename_column` |
| Oracle mode COMMENT ON TABLE | `comment on table mart.t is '...'` | `oracle_mode_comment_on_table` |
| Oracle mode COMMENT ON COLUMN | `comment on column mart.t.c is '...'` | `oracle_mode_comment_on_column` |
| Oracle mode CREATE INDEX | `create index idx on mart.t(c)`, `create unique index idx on mart.t(c)`, `create bitmap index idx on mart.t(c)`, `create index ... local`, `create unique index ... global partition by hash (...)`, `create index ... tablespace ... online nologging parallel ... compress ...`; explicit index and partition keys are returned as `INDEX` usages | `oracle_mode_create_index`, `oracle_mode_create_unique_index`, `oracle_mode_create_bitmap_index`, `oracle_mode_create_index_local`, `oracle_mode_create_index_global_hash_partition`, `oracle_mode_create_index_tablespace_online` |
| Oracle mode CREATE/DROP TRIGGER | `create or replace trigger trg before insert on mart.t for each row ...`, `drop trigger mart.trg` | `oracle_mode_create_trigger_before_insert`, `oracle_mode_drop_trigger` |
| Oracle mode package and anonymous block | `create or replace package ...`, `create or replace package body ...`, `begin ... end`, `declare ... begin ... end` | `oracle_mode_create_package`, `oracle_mode_create_package_body`, `oracle_mode_anonymous_begin_block`, `oracle_mode_anonymous_declare_block` |
| OceanBase transaction control | `commit`, `rollback`, `savepoint sp`, `set transaction read only/read write/isolation level ...` | `oracle_mode_commit_statement`, `oracle_mode_rollback_statement`, `oracle_mode_savepoint_statement`, `oracle_mode_set_transaction_read_only`, `oracle_mode_set_transaction_read_write`, `oracle_mode_set_transaction_isolation` |
| Oracle mode ALTER INDEX | `alter index mart.idx rebuild`, `alter index mart.idx unusable` | `oracle_mode_alter_index_rebuild`, `oracle_mode_alter_index_unusable` |
| Oracle mode DROP INDEX | `drop index mart.idx_t_c` | `oracle_mode_drop_index` |
| Oracle mode hierarchical query | `select ... from app.org start with ... connect by prior ...`, `connect by nocycle prior ...`, `connect_by_root col` | `oracle_mode_hierarchical_query`, `oracle_mode_hierarchical_query_nocycle`, `oracle_mode_hierarchical_connect_by_root` |
| Oracle mode legacy outer join marker | `where a.id = b.user_id(+)` | `oracle_mode_legacy_outer_join_marker` |
| Oracle mode hierarchical sibling order | `select ... from app.org start with ... connect by prior ... order siblings by ...` | `oracle_mode_order_siblings_by` |
| Oracle mode INTERSECT | `select c from ods.a intersect select c from ods.b` | `oracle_mode_intersect_column_projection` |
| Oracle mode MINUS | `select c from ods.a minus select c from ods.b` | `oracle_mode_minus_column_projection` |
| Oracle mode TRUNCATE TABLE | `truncate table ads.t`, `truncate table ads.t drop storage`, `truncate table ads.t reuse storage` | `oracle_mode_truncate_table`, `oracle_mode_truncate_table_drop_storage`, `oracle_mode_truncate_table_reuse_storage` |
| Oracle mode CTAS | `create table ads.t as select ... from ods.s` | `oracle_mode_create_table_as_select` |
| Oracle mode materialized view lifecycle | `create materialized view mv build immediate refresh fast/force on demand as select ...`, `build deferred refresh complete on commit as select ...`, `alter materialized view mv refresh fast on demand`, `alter materialized view mv compile`, `drop materialized view mv preserve table`, `drop materialized view mv purge` | `oracle_mode_create_materialized_view_build_refresh`, `oracle_mode_create_materialized_view_deferred_complete`, `oracle_mode_create_materialized_view_refresh_force`, `oracle_mode_alter_materialized_view_refresh_fast`, `oracle_mode_alter_materialized_view_compile`, `oracle_mode_drop_materialized_view_preserve_table`, `oracle_mode_drop_materialized_view_purge` |
| Oracle mode CTAS table properties and MODEL sources | `create table t nologging parallel 8 as select ...`, `create table t compress as select ...`, `create table t as select ... from s model ...` | `oracle_mode_ctas_nologging_parallel`, `oracle_mode_ctas_compress`, `oracle_mode_ctas_model_measure_alias` |
| Oracle mode partitioned CREATE TABLE/CTAS | `partition by range (...)`, `partition by hash (...) partitions n`, `partition by list (...) as select ...` | `oracle_mode_create_table_range_partition`, `oracle_mode_create_table_hash_partition`, `oracle_mode_create_table_list_partition_as_select` |
| Oracle mode INSERT from derived subquery | `insert into ads.t (...) select ... from (select ... from ods.s) q` | `oracle_mode_insert_from_subquery` |
| Oracle mode INSERT RETURNING | `insert into ods.t (...) values (...) returning id into v_id` | `oracle_mode_insert_values_returning` |
| Oracle mode INSERT ALL | `insert all into mart.a (...) values (...) into mart.b (...) values (...) select ...`, `when condition then into ... values (...)` | `oracle_mode_insert_all`, `oracle_mode_insert_all_when` |
| Oracle mode INSERT FIRST | `insert first into mart.a (...) values (...) into mart.b (...) values (...) select ...`, `when condition then into ... values (...)` | `oracle_mode_insert_first`, `oracle_mode_insert_first_when` |
| Oracle mode SELECT BULK COLLECT INTO | `select ... bulk collect into vars from ...` | `oracle_mode_select_bulk_collect_into` |
| Oracle mode date and timestamp literals | `where ts >= date '2026-09-01' and ts < timestamp '2026-09-02 00:00:00'` | `oracle_mode_date_timestamp_literal_predicate` |
| Oracle mode DECODE/NVL2 expressions | `select decode(status, 'VIP', vip_score, base_score), nvl2(nickname, nickname, name) from t` | `oracle_mode_decode_nvl2_projection` |
| Oracle mode UPDATE expression assignment | `update ads.t set c = upper(x), score = a + b where ...` | `oracle_mode_update_expression_assignment` |
| Oracle mode UPDATE RETURNING | `update ods.t set c = ... where ... returning c into v_c` | `oracle_mode_update_returning` |
| Oracle mode UPDATE WHERE EXISTS | `update ads.t set c = ... where exists (select ... from ods.s ...)` | `oracle_mode_update_where_exists` |
| Oracle mode DELETE WHERE EXISTS | `delete from ads.t where exists (select ... from ods.s ...)` | `oracle_mode_delete_where_exists` |
| Oracle mode DELETE RETURNING | `delete from ods.t where ... returning id into v_id` | `oracle_mode_delete_returning` |
| Oracle mode MERGE USING subquery and conditional actions | `merge into ads.t using (select ... from ods.s) q on (...) ...`, `when matched then update ... where ...`, `when matched then update ... delete where ...`, `when not matched then insert ... where ...` | `oracle_mode_merge_using_subquery`, `oracle_mode_merge_update_where`, `oracle_mode_merge_update_delete_where`, `oracle_mode_merge_insert_where` |
| Oracle mode GROUP BY ROLLUP, CUBE, GROUPING SETS, and GROUPING_ID | `group by rollup(region, product)`, `grouping_id(region, product)`, `group by cube(region, product)`, `group by grouping sets ((region, product), (region), ())` | `oracle_mode_group_by_rollup_projection`, `oracle_mode_group_by_cube_projection`, `oracle_mode_group_by_grouping_sets_projection`, `oracle_mode_grouping_id_projection` |
| Oracle mode window and ordered aggregate expressions | `row_number() over(partition by ... order by ...)`, analytic window frames, `keep (dense_rank ... order by ...)`, `listagg(c, ',' on overflow truncate '...' with count) within group (order by ts)`, `percentile_cont(0.5) within group (order by c) over (...)` | `oracle_mode_window_function_projection`, `oracle_mode_window_frame_projection`, `oracle_mode_keep_dense_rank_projection`, `oracle_mode_listagg_within_group_projection`, `oracle_mode_listagg_overflow_projection`, `oracle_mode_percentile_cont_within_group_over` |
| Oracle mode CREATE SEQUENCE | `create sequence ods.seq_order_id start with 1 increment by 1` | `oracle_mode_create_sequence` |
| Oracle mode ALTER SEQUENCE | `alter sequence ods.seq_order_id increment by 10` | `oracle_mode_alter_sequence` |
| Oracle mode DROP SEQUENCE | `drop sequence ods.seq_order_id` | `oracle_mode_drop_sequence` |
| Oracle mode CREATE SYNONYM | `create or replace synonym app.orders_syn for ods.orders`, `create synonym app.s for ods.t@link`; returns the referenced table as an input and the synonym as an output | `oracle_mode_create_synonym`, `oracle_mode_create_synonym_for_dblink` |
| Oracle mode CREATE PUBLIC SYNONYM | `create public synonym orders_syn for ods.orders`; returns the referenced table as an input and the public synonym as an output | `oracle_mode_create_public_synonym` |
| Oracle mode DROP SYNONYM | `drop synonym app.orders_syn`; returns the affected synonym object as an output | `oracle_mode_drop_synonym` |
| Oracle mode DROP PUBLIC SYNONYM | `drop public synonym orders_syn`; returns the affected public synonym as an output | `oracle_mode_drop_public_synonym` |
| Oracle mode CREATE DATABASE LINK | `create database link remote_dw connect to ... using ...` | `oracle_mode_create_database_link` |
| Oracle mode CREATE PUBLIC DATABASE LINK | `create public database link remote_dw connect to ... using ...` | `oracle_mode_create_public_database_link` |
| Oracle mode DROP DATABASE LINK | `drop database link remote_dw` | `oracle_mode_drop_database_link` |
| Oracle mode SELECT through DBLink | `select c1 from ods.remote_orders@remote_dw where ...` | `oracle_mode_select_dblink` |
| Oracle mode flashback query by timestamp | `select ... from ods.t as of timestamp to_timestamp(...)` | `oracle_mode_flashback_timestamp` |
| Oracle mode flashback query by SCN | `select ... from ods.t as of scn 1582807800000000` | `oracle_mode_flashback_scn` |
| Oracle mode SELECT FOR UPDATE | `select ... from ods.t where ... for update of c nowait`, `select ... for update of c skip locked` | `oracle_mode_select_for_update_nowait`, `oracle_mode_select_for_update_skip_locked` |
| Oracle mode LOCK TABLE | `lock table ods.t in share mode nowait`, `lock table ods.t, mart.u in row exclusive mode wait 5`, `lock table ods.t in share row exclusive mode wait 10`, `lock table ods.t in exclusive mode nowait` | `oracle_mode_lock_table_share_mode`, `oracle_mode_lock_table_row_exclusive_wait`, `oracle_mode_lock_table_share_row_exclusive`, `oracle_mode_lock_table_exclusive_nowait` |
| Oracle mode FETCH WITH TIES | `select ... from ods.t order by c fetch first 10 rows with ties` | `oracle_mode_fetch_with_ties` |
| Oracle mode FETCH PERCENT | `select ... from ods.t order by c fetch first 10 percent rows only` | `oracle_mode_fetch_percent_only` |
| Oracle mode OFFSET FETCH NEXT | `select ... from ods.t order by c offset 20 rows fetch next 10 rows only` | `oracle_mode_offset_fetch_next` |
| Oracle mode ORDER BY NULLS | `select ... from ods.t order by c desc nulls last` | `oracle_mode_order_by_nulls` |
| Oracle mode PIVOT source table lineage | `select * from (...) pivot (...)` | `oracle_mode_pivot_table_source` |
| Oracle mode UNPIVOT source table lineage | `select ... from t unpivot (...)` | `oracle_mode_unpivot_table_source` |
| Oracle mode PIVOT generated column lineage | `select north_amt from (...) pivot (sum(amount) as amt for region in (...))` | `oracle_mode_pivot_generated_column_lineage` |
| Oracle mode multi-column PIVOT lineage | `select north_app_total from (...) pivot (sum(amount) as total for (region, channel) in (...))` | `oracle_mode_pivot_multi_column_generated_lineage` |
| Oracle mode multi-value UNPIVOT lineage | `select metric, v1, v2 from t unpivot ((v1, v2) for metric in ((c1, c2), ...))` | `oracle_mode_unpivot_multi_value_column_lineage` |
| Oracle mode MATCH_RECOGNIZE relation lineage and usages | `from t match_recognize (partition by ... order by ... measures ... pattern (...) define ...)`, including `+` and `{m,n}` pattern quantifiers | `oracle_mode_match_recognize_basic`, `oracle_mode_match_recognize_quantifier` |
| Oracle mode MODEL clause lineage and usages | `from t model return updated/all rows partition by (...) dimension by (...) measures (...) rules (...)`, `model ignore/keep nav ...`, including measure aliases | `oracle_mode_model_clause_basic`, `oracle_mode_model_measure_alias_lineage`, `oracle_mode_model_ignore_nav_alias_lineage`, `oracle_mode_model_keep_nav_alias_lineage` |
| Oracle mode SAMPLE table clause | `select ... from t sample (...)`, `sample block (...) seed (...)` | `oracle_mode_sample_table_source`, `oracle_mode_sample_block_seed_table_source` |
| Oracle mode table function and lateral relation | `from table(app.fn(...)) f`, `join table(app.fn(t.c)) f`, `cross/outer apply table(app.fn(t.c)) f`, `from xmltable(... passing t.c columns (...)) x`, `cross join lateral (select ... from t where ...) q` | `oracle_mode_table_function_relation`, `oracle_mode_xml_table_relation`, `oracle_mode_lateral_inline_view`, `oracle_mode_join_table_function_relation`, `oracle_mode_cross_apply_table_function`, `oracle_mode_outer_apply_table_function` |
| Oracle mode partition/subpartition table extension | `select ... from t partition(...)`, `select ... from t subpartition(...)` | `oracle_mode_partition_table_source`, `oracle_mode_subpartition_table_source` |
| Oracle mode INSERT through DBLink | `insert into ods.remote_orders@remote_dw (...) values (...)` | `oracle_mode_insert_dblink_values` |
| Oracle mode INSERT SELECT through DBLink | `insert into ods.remote_orders@remote_dw (...) select ... from ods.s` | `oracle_mode_insert_dblink_select` |
| Oracle mode sequence pseudocolumn | `select seq.nextval as next_id from dual` | `oracle_mode_sequence_nextval_insert` |
| Oracle mode row pseudocolumns | `where rownum <= 10`, `select rowid`, `select ora_rowscn`, `select level ... connect by ...` | `oracle_mode_rownum_filter`, `oracle_mode_rowid_projection`, `oracle_mode_ora_rowscn_projection`, `oracle_mode_hierarchical_level` |
| Oracle mode UPDATE through DBLink | `update ods.remote_orders@remote_dw set ... where ...` | `oracle_mode_update_dblink` |
| Oracle mode MERGE through DBLink | `merge into ods.remote_orders@remote_dw t using ods.s ...` | `oracle_mode_merge_dblink_target` |
| Oracle mode DELETE through DBLink | `delete from ods.remote_orders@remote_dw where ...` | `oracle_mode_delete_dblink` |
| ODP read-consistency proxy config | `alter proxyconfig set obproxy_read_consistency = 1` | `control_alter_proxyconfig_read_consistency` |
| Read-only/read-write database DDL | `create database if not exists test_ro_db read only`, `alter database app_db read only`, `alter database app_db read write` | `create_database_read_only`, `alter_database_read_only`, `alter_database_read_write` |
| Resource unit control | `create resource unit unit1 ...` | `control_create_resource_unit` |
| Resource unit alter/drop control | `alter resource unit unit1 ...`, `drop resource unit unit1` | `control_alter_resource_unit`, `control_drop_resource_unit` |
| Resource pool control | `create resource pool pool1 ...` | `control_create_resource_pool` |
| Resource pool alter/drop control | `alter resource pool pool1 ...`, `drop resource pool pool1` | `control_alter_resource_pool`, `control_drop_resource_pool` |
| Tablespace control | `create undo tablespace ...`, `drop undo tablespace ...` | `control_create_undo_tablespace`, `control_drop_undo_tablespace` |
| Tenant creation control | `create tenant tenant_a resource_pool_list = (...) set ...` | `control_create_tenant` |
| Tenant alteration control | `alter tenant tenant_a set variables ...` | `control_alter_tenant` |
| Tenant switch control | `change tenant tenant_a` | `control_change_tenant` |
| Tenant drop control | `drop tenant tenant_a force` | `control_drop_tenant` |
| Tenant lifecycle recovery/rename | `flashback tenant ... to before drop`, `flashback tenant ... rename to ...`, `rename tenant ... to ...` | `control_flashback_tenant_before_drop`, `control_flashback_tenant_rename`, `control_rename_tenant` |
| Tenant metadata read | `show tenant`, `show tenant like ...`, `show create tenant ...`, `show tenant status` | `control_show_tenant`, `control_show_tenant_like`, `control_show_create_tenant`, `control_show_tenant_status` |
| Cluster server add control | `alter system add server '127.0.0.1:2882' zone 'zone1'` | `control_alter_system_add_server` |
| Cluster server delete control | `alter system delete server '127.0.0.1:2882' zone 'zone1'` | `control_alter_system_delete_server` |
| Cluster server stop control | `stop server '127.0.0.1:2882'` | `control_stop_server` |
| Compaction freeze control | `alter system major freeze`, `alter system major freeze tenant = all_user`, `alter system minor freeze` | `control_alter_system_major_freeze`, `control_alter_system_major_freeze_tenant`, `control_alter_system_minor_freeze` |
| System parameter control | `alter system set ...`, `alter system set ... tenant = ...`, `alter system reset ...` | `control_alter_system_set_parameter`, `control_alter_system_set_tenant_parameter`, `control_alter_system_reset_parameter` |
| Resource metadata read | `show resource unit`, `show resource pool`, `show resource pool like ...` | `control_show_resource_unit`, `control_show_resource_pool`, `control_show_resource_pool_like` |
| Tablegroup creation control | `create tablegroup tg_orders sharding = 'partition'` | `control_create_tablegroup` |
| Tablegroup alteration control | `alter tablegroup tg_orders sharding = 'adaptive'` | `control_alter_tablegroup` |
| Tablegroup drop control | `drop tablegroup tg_orders` | `control_drop_tablegroup` |
| Tablegroup metadata read | `show tablegroups`, `show tablegroup ...` | `control_show_tablegroups`, `control_show_tablegroup` |
| Database default tablegroup DDL | `create database app_db default tablegroup = tg`, `alter database app_db default tablegroup tg` | `create_database_default_tablegroup`, `alter_database_default_tablegroup` |
| Recyclebin metadata read | `show recyclebin`, `show recyclebin where object_name like ...` | `control_show_recyclebin`, `control_show_recyclebin_where` |
| Parameter metadata read | `show parameters like ...`, `show parameters tenant = all like ...` | `control_show_parameters_like`, `control_show_parameters_tenant_like` |
| Purge recyclebin objects | `purge recyclebin`, `purge table app.t`, `purge index app.idx`, `purge database db`, `purge tenant tenant_a` | `control_purge_recyclebin`, `control_purge_table`, `control_purge_index`, `control_purge_database`, `control_purge_tenant` |
| Flashback dropped table | `flashback table app.t to before drop`, `flashback table app.t to before drop rename to app.t_restored` | `control_flashback_table_before_drop`, `control_flashback_table_rename` |

Known OceanBase gaps:

| Gap | Current behavior |
| --- | --- |
| OceanBase-specific tenant syntax and compatibility-only divergences | Planned after the compatibility-mode table/column lineage surface is broader. |
| Divergent MySQL-mode and Oracle-mode semantics | Common lineage is reused; OceanBase-specific semantics need dedicated cases. |

## SQL Server

SQL Server is an active parser dialect path. The current implementation uses an ANTLR4 lexer with a lightweight lineage walker for common SQL Server query and write shapes.

Current SQL Server SQL case assets:

```text
linesql-dialect-sqlserver/src/test/resources/sql/sqlserver/manifest.json
linesql-dialect-sqlserver/src/test/resources/sql/sqlserver/cases/*.sql
```

Implemented SQL Server table-level lineage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Basic SELECT source table | `select ... from ods.users` | `select_basic` |
| Local/global temporary tables, table variables, and scalar variables | `select ... from #t`, `create table #t (...)`, `insert into ##t select ...`, `declare @t table (...)`, `declare @d date = ...`, `set @v = (select ... from t)`, `select ... from @t`, `insert into @t select ...` | `select_local_temp_table`, `create_local_temp_table`, `insert_global_temp_table`, `declare_table_variable`, `declare_scalar_variables`, `set_variable_scalar_subquery`, `select_table_variable`, `insert_table_variable` |
| JOIN source tables | `select ... from ods.users u join dwd.orders o ...` | `join_projection` |
| APPLY subquery and table-valued function sources | `cross apply (...) q`, `outer apply (...) q`, `from dbo.fn(...) f`, `cross/outer apply dbo.fn(t.c) f`, `from openquery(link, '...') q`, `from openrowset(provider, conn, query) r`, `cross apply openjson(t.payload) with (...) j` | `cross_apply_subquery`, `outer_apply_subquery`, `select_table_valued_function`, `cross_apply_table_valued_function`, `outer_apply_table_valued_function`, `openquery_relation`, `openrowset_relation`, `cross_apply_openjson_with_schema` |
| VALUES derived tables | `from (values (...), (...)) as v(c1, c2)`, `cross apply (values (...)) as v(c)` | `values_derived_table`, `cross_apply_values_lineage` |
| PIVOT/UNPIVOT source tables | `from (...) s pivot (...) p`, `from mart.t unpivot (...) u` | `pivot_source_table`, `unpivot_source_table` |
| StarRocks SEMI/ANTI/ASOF joins | `left semi join ...`, `left anti join ...`, `asof join ... on ...` | `semi_anti_asof_join_column_usage` |
| INSERT INTO target and source | `insert into ads.t select ... from ods.s` | `insert_into` |
| INSERT OVERWRITE target and source | `insert overwrite table ads.t select ... from ods.s` | `insert_overwrite` |
| INSERT EXEC target table | `insert into audit.t (...) exec dbo.proc ...` | `insert_exec_procedure` |
| INSERT INTO VALUES target lineage | `insert into ads.t(c1) values (...)`, `insert into mart.t(c1, c2) values (..., default)` | `insert_values`, `insert_values_default` |
| CREATE TABLE AS SELECT | `create table ads.t as select ... from ods.s`, declared target column names before `AS SELECT`, optional bitmap index in CTAS column list | `create_table_as_select`, `create_table_declared_columns_as_select`, `create_table_declared_columns_bitmap_index_as_select` |
| SELECT INTO created table lineage | `select ... into dbo.t from dbo.s`, `select ... into #temp from dbo.s`, `with q as (...) select ... into #temp from q` | `select_into`, `select_into_temp_table`, `with_select_into_temp_table` |
| CREATE TABLE model AS SELECT | `create table ads.t duplicate key(...) distributed by hash(...) properties(...) as select ...` | `create_table_model_as_select` |
| CREATE VIEW AS SELECT | `create view ads.v as select ... from ods.s join dwd.o`, `create view ... with schemabinding as select ...` | `create_view`, `create_view_with_schemabinding` |
| CREATE OR ALTER VIEW AS SELECT | `create or alter view dbo.v as select ... from dbo.s` | `create_or_alter_view` |
| CREATE OR REPLACE VIEW AS SELECT | `create or replace view ads.v as select ... from ods.s` | `create_or_replace_view` |
| ALTER VIEW AS SELECT | `alter view ads.v(c1, c2) as select ... from ods.s where ...` | `alter_view` |
| INSERT SELECT over CTE | `insert into ads.t with q as (...) select ... from q` | `insert_from_cte` |
| DML OUTPUT INTO audit tables | `insert/update/delete ... output inserted/deleted.c into audit.t(...)`, `output inserted.* into audit.t`, `output deleted.* into audit.t`, `delete alias output ... from ... join ...`, `output ... into @table_variable`, `output ... into #temp`, `with q as (...) update ... output ... into audit.t from q`, `output deleted.*, inserted.* into audit.t` | `insert_output_into`, `insert_output_wildcard_into`, `insert_output_table_variable`, `update_output_into`, `update_output_wildcard_into`, `update_output_table_variable`, `with_update_output_from_cte`, `delete_alias_output_from_join`, `delete_output_into`, `delete_output_wildcard_into`, `delete_output_table_variable`, `delete_output_temp_table` |
| WITH before INSERT SELECT | `with q as (...) insert into ads.t select ... from q` | `with_insert_select` |
| CREATE VIEW over CTE | `create view ads.v as with q as (...) select ... from q` | `create_view_with_cte` |
| UNION source table propagation | `select a from dbo.s1 union all select b from dbo.s2` | `union_column_projection` |
| Bracketed non-ASCII identifiers | `select [用户ID] from [业务库].[用户表]` | `bracket_identifiers` |
| SELECT TOP and table hint | `select top 10 ... from dbo.users with (nolock)`, `with (nolock, index(...))`, `select top (...) percent with ties ... order by ...` | `top_with_nolock`, `table_hint_index`, `top_percent_with_ties` |
| Temporal table query | `from dbo.t for system_time as of ...`, `from ... to ...`, `between ... and ...`, `contained in (...)`, `all` | `select_for_system_time_as_of`, `select_for_system_time_from_to`, `select_for_system_time_between`, `select_for_system_time_contained_in`, `select_for_system_time_all` |
| OFFSET FETCH pagination | `order by c offset n rows fetch next m rows only` | `offset_fetch_pagination` |
| Query hints | `option (recompile, maxdop 4)`, `option (optimize for (...))` | `select_option_recompile_maxdop`, `select_option_optimize_for` |
| Optimizer statistics maintenance | `create statistics ... on t(c) where ...`, `update statistics t stat with fullscan`, `drop statistics t.stat` | `create_statistics_filtered`, `update_statistics_fullscan`, `drop_statistics` |
| Single CTE source table propagation | `with q as (...) select ... from q` | `cte_column_projection` |
| Single derived subquery source table propagation | `select ... from (select ... from ods.s) q` | `subquery_column_projection` |
| GROUP BY ROLLUP/GROUPING SETS source columns | `group by rollup(a, b)`, `group by grouping sets ((a), (b))` | `group_by_rollup`, `group_by_grouping_sets` |
| QUALIFY window filter source columns | `qualify row_number() over(partition by ... order by ...) = 1` | `qualify_window_filter` |
| StarRocks bitmap/HLL function projection lineage | `bitmap_union(to_bitmap(c))`, `hll_union(hll_hash(c))` | `starrocks_bitmap_hll_functions` |
| UPDATE FROM target and source tables | `update ads.t set c = s.c from ods.s s`, `update u set ... from ads.t u join ...`, `update top (...) alias set ... from ads.t alias join ...` | `update_from`, `update_alias_target_from_join`, `update_top`, `update_top_alias_target_from_join` |
| WITH before UPDATE FROM | `with q as (...) update ads.t set c = q.c from q where ...`, with `OUTPUT INTO` audit capture | `with_update_from`, `with_update_output_from_cte` |
| DELETE FROM JOIN target and source tables | `delete top (...) from ads.t where ...`, `delete t from ads.t t join ods.s s ...`, `delete t output ... into audit.t from ads.t t join ...` | `delete_top`, `delete_from_join`, `delete_alias_output_from_join` |
| WITH before DELETE FROM JOIN | `with q as (...) delete t from ads.t t join q ...` | `with_delete_join` |
| MERGE target and source tables | `merge into ads.t using ods.s on ... when matched then update ...`, `merge into t with (holdlock) as a using ...`, `with cte as (...) merge into ... using cte`, `when matched and ... then update ...`, `when not matched by source then delete`, `when not matched by target and ... then insert values(...)`, `merge ... output ... into audit.t` | `merge_into`, `merge_target_holdlock`, `merge_with_cte_source`, `merge_matched_and_update`, `merge_not_matched_by_source_delete`, `merge_not_matched_by_target_and_insert`, `merge_output_into` |
| MERGE source subquery and CTE tables | `merge into ads.t using (select ... from ods.s) q on ...`, `with cte as (...) merge into ... using cte` | `merge_using_subquery`, `merge_with_cte_source` |
| UPDATE with subquery sources | `update ads.t set c = (select ... from ods.s1) where id in (select ... from ods.s2)` | `update_with_subquery` |
| DELETE with subquery sources | `delete from ads.t where id in (select ... from ods.s)` | `delete_with_subquery` |
| CREATE TABLE schema DDL | `create table dbo.t (...)`, `create external table ext.t (...) with (...)`, `identity(seed, increment)`, computed column `as expression persisted` | `create_table_schema`, `create_external_table`, `create_table_identity_column`, `create_table_computed_column` |
| Schema/session/routine/trigger lifecycle control | `use db`, `create schema ...`, `drop schema ...`, `create or alter procedure ...`, `create or alter trigger ... on table`, `drop trigger if exists ...`, `set nocount on`, `set identity_insert dbo.t on`, `exec dbo.proc ...`, `begin/commit/rollback/save transaction` | `use_database`, `create_schema`, `drop_schema`, `create_or_alter_procedure`, `drop_procedure`, `create_or_alter_trigger`, `drop_trigger_if_exists`, `set_nocount`, `set_identity_insert`, `execute_procedure`, `begin_transaction`, `commit_transaction`, `rollback_transaction`, `save_transaction` |
| Table privilege grants | `grant select on object::dbo.t to role`, `revoke update on dbo.t from role` | `grant_select_on_object`, `revoke_update_on_table` |
| DROP TABLE affected table | `drop table if exists dbo.t`, `drop table if exists dbo.t1, dbo.t2` | `drop_table`, `drop_multiple_tables` |
| DROP VIEW affected view | `drop view if exists dbo.v`, `drop view if exists dbo.v1, dbo.v2` | `drop_view`, `drop_multiple_views` |
| TRUNCATE TABLE affected table | `truncate table dbo.t`, `truncate table dbo.t with (partitions (...))` | `truncate_table`, `truncate_table_partitions` |
| ALTER TABLE column maintenance | `alter table dbo.t add c int` | `alter_table_add_column` |
| CREATE INDEX affected table, index columns, include columns, and filtered predicate columns | `create nonclustered index ix on dbo.t(c) include(c2) where flag = 1`, `create nonclustered index ix on dbo.t(c) with (online = on, data_compression = page)` | `create_filtered_index`, `create_index_with_options` |
| SYNONYM object lifecycle | `create synonym dbo.s for ods.t`, `drop synonym if exists dbo.s`; `CREATE SYNONYM` returns the referenced table as an input and the synonym as an output | `create_synonym`, `drop_synonym_if_exists` |
| DROP INDEX affected table | `drop index ix on dbo.t`, `drop index if exists ix on dbo.t`, `drop index ix on dbo.t with (online = on)` | `drop_index`, `drop_index_if_exists`, `drop_index_with_options` |
| ALTER INDEX affected table | `alter index ix on dbo.t rebuild` | `alter_index_rebuild` |

Implemented SQL Server column-level lineage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Direct single-table projection | `select id as user_id, name from ods.users` | `select_basic` |
| SELECT INTO variables | `select id, name into v_id, v_name from app.users`, `select ... bulk collect into vars from ...` | `select_into_variables`, `select_bulk_collect_into` |
| DATE and TIMESTAMP literals | `where ts >= date '2026-09-01' and ts < timestamp '2026-09-02 00:00:00'` | `date_timestamp_literal_predicate` |
| Alias-qualified JOIN projection | `select u.id, o.amount from users u join orders o` | `join_projection` |
| APPLY subquery and table-valued function derived columns | `select q.c from t cross/outer apply (select ... from s) q`, `select f.c from dbo.fn(...) f`, `select q.c from openquery(link, '...') q`, `select r.c from openrowset(provider, conn, query) r`, `select j.c from openjson(t.payload) with (...) j` | `cross_apply_subquery`, `outer_apply_subquery`, `select_table_valued_function`, `cross_apply_table_valued_function`, `openquery_relation`, `openrowset_relation`, `cross_apply_openjson_with_schema` |
| Backtick-qualified direct projections | ``select u.`department_id` from ods.users u`` | `backtick_qualified_direct_projection` |
| Case-insensitive derived column propagation | `select user_id from (select id as User_ID from ods.users) u` | `derived_case_insensitive_column_projection` |
| INSERT SELECT target mapping | `insert into ads.t select a as c1 from ods.s` | `insert_into` |
| INSERT OVERWRITE target column mapping | `insert overwrite table ads.t(c1, c2) select a, b from ods.s` | `insert_overwrite` |
| INSERT target column list mapping | `insert into ads.t(c1, c2) select a, b from ods.s`, `insert into ads.t temporary partition(...) with label ... (c1, c2) select ...` | `insert_column_list`, `insert_temporary_partition_with_label` |
| Temporary table and table-variable column mapping | `select c from #t`, `insert into ##t(c) select c from s`, `select c from @t`, `insert into @t(c) select c from s` | `select_local_temp_table`, `insert_global_temp_table`, `select_table_variable`, `insert_table_variable` |
| INSERT over UNION ALL target column lineage | `insert into t(c1) select a from s1 union all select b from s2` | `insert_union_column_lineage` |
| INSERT over INTERSECT target column lineage | `insert into t(c1) select a from s1 intersect select b from s2` | `insert_intersect_column_lineage` |
| INSERT over EXCEPT target column lineage | `insert into t(c1) select a from s1 except select b from s2` | `insert_except_column_lineage` |
| CTAS output column targets | `create table ads.t as select id as c1 from ods.s` | `create_table_as_select` |
| CTAS over aliased/expression/aggregate projections | `create table ads.t as select a as c1, upper(b), count(c) ...` | `ctas_expression_projection` |
| CTAS with StarRocks table model options | `create table ads.t duplicate key(...) distributed by hash(...) as select ...` | `create_table_model_as_select` |
| CREATE VIEW output column targets | `create view ads.v as select u.id from ods.users u`, `create view ... with schemabinding as select ...` | `create_view`, `create_view_with_schemabinding` |
| CREATE VIEW over aliased/expression/aggregate projections | `create view ads.v as select a as c1, upper(b), count(c) ...` | `create_view_expression_projection` |
| CREATE VIEW column list target names | `create view ads.v(c1, c2) as select a, b from ods.s` | `create_view_column_list` |
| ALTER VIEW output column targets | `alter view ads.v(c1, c2) as select a, b from ods.s` | `alter_view` |
| INSERT SELECT target mapping over CTE | `insert into ads.t with q as (...) select q.c1 from q` | `insert_from_cte` |
| WITH before INSERT SELECT target mapping | `with q as (...) insert into ads.t(c1) select q.c1 from q` | `with_insert_select` |
| INSERT target column list over subquery propagation | `insert into ads.t(c1) select c1 from (select a as c1 from ods.s) q` | `insert_from_subquery` |
| INSERT target column list over aliased/expression projections | `insert into t(c1,c2,c3) select a as x, upper(b), count(c) ...` | `insert_column_list_expression_projection` |
| INSERT OUTPUT INTO keeps primary insert lineage | `insert into ads.t(c1, c2) output inserted.c1 into audit.t(c1) select a, b from ods.s`, `output inserted.* into audit.t`, `output inserted.c into @table_variable` | `insert_output_into`, `insert_output_wildcard_into`, `insert_output_table_variable` |
| CREATE VIEW output columns over CTE | `create view ads.v as with q as (...) select q.c1 from q` | `create_view_with_cte` |
| Bracketed identifier column mapping | `select [用户ID] as [用户标识] from [业务库].[用户表]` | `bracket_identifiers` |
| SELECT TOP/table-hint projection mapping | `select top (10) u.id as user_id from dbo.users u`, `select ... from dbo.users with (nolock, index(...)) u`, `select top (...) percent with ties c from t order by c` | `top_parenthesized`, `table_hint_index`, `top_percent_with_ties` |
| OFFSET FETCH projection mapping | `select c from t order by ts offset n rows fetch next m rows only` | `offset_fetch_pagination` |
| CASE expression dependencies | `select case when status = 'A' then score else 0 end as c from t` | `case_expression` |
| Multi-branch CASE expression dependencies | `select case when status = 'A' then score when status = 'P' then pending_score else default_score end from t` | `complex_case_expression` |
| StarRocks function expression lineage | `select ifnull(nickname, name), date_trunc('day', ts) from ods.s` | `starrocks_function_expression_projection` |
| CAST, conversion, function, ordered aggregate, collation, and arithmetic expression dependencies | `select cast(id as varchar), try_cast(txt as decimal), convert(date, txt, 120), dateadd(day, 7, created_at), name collate Latin1_General_CI_AS, coalesce(name, nickname), iif(status='VIP', vip_score, base_score), choose(level_no, level1_name, level2_name), string_agg(name, ',') within group (order by ts), price * quantity from t` | `common_expression_projection`, `try_cast_projection`, `convert_try_convert_projection`, `datepart_function_projection`, `collate_expression_projection`, `iif_choose_projection`, `string_agg_within_group_projection` |
| Nested function expression dependencies | `select coalesce(lower(name), upper(nickname), cast(id as varchar)) from t` | `nested_function_projection` |
| Scalar subquery projection dependencies | `select (select max(amount) from orders) as max_amount from users` | `scalar_subquery_projection` |
| IN subquery predicate column usage | `where id in (select user_id from sessions)` | `in_subquery_column_usage` |
| WHERE subquery scope isolation | `select id from users where id in (select user_id from sessions)` | `SparkDialectParserTest.keepsOuterProjectionLineageWhenWhereContainsSubquery` |
| Fully qualified column references | `select db.table.col from db.table` | `SparkDialectParserTest.resolvesFullyQualifiedColumnReferences` |
| ORDER BY projection alias/null ordering column usage | `select c as alias from t order by alias`; `order by c desc nulls last` | `projection_alias_order_usage`, `order_by_nulls_first_last` |
| ORDER BY expression column usage | `select id from t order by coalesce(updated_at, created_at)` | `order_by_expression_column_usage` |
| LIMIT offset,size query organization | `select id from t where ... order by ... limit 10, 100` | `limit_offset_comma` |
| GROUP BY aggregate expression dependencies | `select user_id, count(order_id), sum(amount) from t group by user_id` | `aggregate_expression_projection` |
| DISTINCT aggregate dependencies and HAVING usage | `select count(distinct user_id) ...`, `count(distinct user_id, product_id) ...` | `distinct_aggregate_column_usage`, `count_distinct_multi_column` |
| GROUP BY expression column usage | `select lower(region), count(order_id) from t group by lower(region)` | `group_by_expression_column_usage` |
| GROUP BY ROLLUP, CUBE, GROUPING SETS, and GROUPING_ID | `group by rollup(region, product)`, `grouping_id(region, product)`, `group by cube(region, product)`, `group by grouping sets ((region, product), (region), ())`, `group by region, product with rollup` | `group_by_rollup_projection`, `group_by_cube_projection`, `group_by_grouping_sets_projection`, `group_by_with_rollup_projection`, `grouping_id_projection` |
| Window function and ordered aggregate dependencies | `select row_number() over (partition by k order by ts), sum(v) over (...) from t`, window frames, `keep (dense_rank ... order by ...)`, `listagg(c, ',' on overflow truncate '...' with count) within group (order by ts)`, `percentile_cont(0.5) within group (order by c) over (...)` | `window_function_projection`, `window_frame_projection`, `keep_dense_rank_projection`, `listagg_within_group_projection`, `listagg_overflow_projection`, `percentile_cont_within_group_over` |
| JSON/XML output query suffix | `select ... from dbo.t for json path`, `select ... from dbo.t for xml path(...)` | `select_for_json_path`, `select_for_xml_path` |
| Single CTE direct column propagation | `with q as (select id as user_id from ods.s) select q.user_id from q` | `cte_column_projection` |
| Chained CTE direct column propagation | `with a as (...), b as (select c1 from a) select c1 from b` | `chained_cte_column_projection` |
| CTE column alias list propagation | `with q(c1, c2) as (select a, b from ods.s) select c1 from q` | `cte_column_aliases` |
| Recursive CTE source propagation | `with recursive q(...) as (select ... union all select ... from q) select ... from q` | `recursive_cte_column_projection` |
| Single derived subquery direct column propagation | `select q.user_id from (select id as user_id from ods.s) q` | `subquery_column_projection` |
| Same-name columns in joined subqueries stay scoped | `select t1.id, t2.id from (...) t1 join (...) t2 on t1.id = t2.id` | `joined_subquery_scope` |
| UNION column sources merged by position | `select a as c1 from s1 union all select b from s2` | `union_column_projection` |
| INTERSECT column sources merged by position | `select a as c1 from s1 intersect select b from s2` | `intersect_column_projection` |
| EXCEPT column sources merged by position | `select a as c1 from s1 except select b from s2` | `except_column_projection` |
| UPDATE assignment mapping | `update ads.t set c = s.c from ods.s s` | `update_from` |
| UPDATE SET expression dependencies | `update ads.t set c1 = upper(s.c2), c3 = s.c4 + t.c5 from ods.s s`, `update top (...) ads.t set c = c + 1 where ...` | `update_expression_assignment`, `update_top` |
| UPDATE FROM derived query assignment dependencies | `update ads.t set c1 = q.c2 from (select c2 from ods.s) q where ...` | `update_from_derived_assignment` |
| UPDATE OUTPUT INTO keeps primary update lineage | `update ads.t set c = s.c output deleted.c, inserted.c into audit.t(...) from ods.s s where ...`, `output deleted.*, inserted.* into audit.t`, `output ... into @table_variable` | `update_output_into`, `update_output_wildcard_into`, `update_output_table_variable` |
| WITH before UPDATE FROM assignment dependencies | `with q as (...) update ads.t set c1 = q.c2 from q where ...` | `with_update_from` |
| MERGE update assignments and insert values | `merge into ads.t using ods.s on ... when matched then update set c = s.c when not matched by target then insert (...) values (...)`, `merge into t with (holdlock) as a using ...`, `with cte as (...) merge into ... using cte`, `merge ... output ... into audit.t` | `merge_into`, `merge_target_holdlock`, `merge_with_cte_source`, `merge_output_into` |
| MERGE source subquery field propagation | `merge into ads.t using (select a as c from ods.s) q on ...` | `merge_using_subquery` |

Implemented SQL Server clause-level column usage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| WHERE, GROUP BY, HAVING, and ORDER BY source columns | `select u.id, count(o.id) from users u join orders o where ... group by u.id having ... order by ...` | `clause_column_usage` |
| Basic predicate operators in WHERE | `where c between ... and ... and name like ... and deleted_at is null` | `predicate_operator_column_usage` |
| IN expression-list predicate usage | `where status in (...) and region in (home_region, ...)` | `in_list_predicate_column_usage` |
| Tuple IN subquery predicate usage | `where (user_id, product_id) [not] in (select user_id, product_id from ...)` | `tuple_in_subquery_column_usage`, `tuple_not_in_subquery_column_usage` |
| Negated predicate operators in WHERE | `where c not between ... and name not like ... and status not in (...)` | `negated_predicate_column_usage` |
| Pattern and regex predicates | `where name like ... escape ... and email regexp ... and phone not rlike ...` | `like_escape_regexp_usage` |
| Boolean predicates | `where is_active is true and deleted is not false` | `is_true_false_predicate` |
| Quantified subquery predicates | `where amount > all (select ...) and status = any (select ...)` | `quantified_subquery_predicates` |
| Logical NOT over grouped predicates | `where not (status = ... or name like ...) and score > ...` | `logical_not_group_column_usage` |
| Self-join aliases | `select e.id, m.name from employees e left join employees m on e.manager_id = m.id` | `self_join_column_usage` |
| JOIN USING source columns | `select u.id from users u join orders o using (id)` | `join_using_column_usage` |
| Chained JOIN USING source columns | `select u.id from users u join orders o using (id) join payments p using (id)` | `join_using_multi_table_scope` |
| JOIN USING scoped inside comma-separated relations | `select b.id from audit a, users b join orders o using (id)` | `join_using_comma_scope` |
| JOIN USING over CTE references | `with u as (...), o as (...) select ... from u join o using (id)` | `join_using_derived_scope` |
| JOIN USING over derived subqueries | `select ... from (select ...) u join (select ...) o using (id)` | `join_using_subquery_scope` |
| JOIN ON over CTE references | `with u as (...), o as (...) select ... from u join o on u.id = o.user_id` | `join_on_derived_scope` |
| JOIN ON over derived subqueries | `select ... from (select ...) u join (select ...) o on u.id = o.user_id` | `join_on_subquery_scope` |
| DELETE FROM JOIN predicate columns | `delete t from ads.t t join ods.s s on t.id = s.id` | `delete_from_join` |
| DELETE FROM derived JOIN predicate columns | `delete t from ads.t t join (select id from ods.s) q on t.id = q.id` | `delete_from_join_derived` |
| WITH before DELETE JOIN predicate columns | `with q as (...) delete t from ads.t t join q on t.id = q.id` | `with_delete_join` |
| MERGE ON source columns | `merge into ads.t using ods.s on t.id = s.id ...` | `merge_into` |
| MERGE ON and WHEN predicates | `merge into ads.t using (select id from ods.s) q on t.id = q.id`, `when matched and s.flag = ... then ...` | `merge_using_subquery`, `merge_matched_and_update` |
| UNION branch WHERE source columns | `select id from dbo.s1 where ... union all select id from dbo.s2 where ...` | `set_operation_clause_column_usage` |
| EXISTS subquery predicate column usage | `where exists (select 1 from dbo.orders o where o.user_id = u.id)` | `exists_subquery_column_usage` |
| UPDATE/DELETE WHERE subquery predicate columns | `update/delete dbo.t where id in (select user_id from ods.s)` | `update_with_subquery`, `delete_with_subquery` |

Current SQL Server diagnostics:

| Code | Meaning |
| --- | --- |
| `SQLSERVER_PARSE_ERROR` | SQL Server SQL could not be tokenized or walked by the current parser. |
| `SQLSERVER_STATEMENT_NOT_SUPPORTED` | The statement was recognized as SQL Server but is not in the current statement set. |
| `SQLSERVER_COLUMN_LINEAGE_NOT_IMPLEMENTED` | No column lineage was produced for a statement shape where table lineage may still be available. |

Known SQL Server gaps:

| Gap | Current behavior |
| --- | --- |
| Full SQL Server grammar | The parser uses ANTLR tokenization plus a lineage walker; full parser grammar will be expanded incrementally. |
| `select *` expansion | Not expanded without schema metadata. |
| Advanced T-SQL DML and procedural syntax | Basic `MERGE`, `UPDATE FROM`, `DELETE FROM JOIN`, DML `OUTPUT INTO`, temporary table names, table-variable declaration/references, and procedure execution are covered. Stored-procedure bodies are still parsed conservatively. |
| Complex CTEs and subqueries | Single CTE, chained CTE direct projection, CTE column aliases, and single derived subquery direct projection propagation are covered. Recursive CTEs and complex nested subqueries are not complete yet. |

## Oracle

Oracle is an active parser dialect path. The current implementation uses an ANTLR4 lexer with a lightweight lineage walker for common Oracle query and write shapes.

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
| SELECT wildcard EXCLUDE | `select * exclude (...) from t`, `select alias.* exclude (...) from t alias` | `select_star_exclude`, `select_qualified_star_exclude` |
| SELECT PIVOT source table lineage | `select * from (...) pivot(sum(v) for k in (...))` | `pivot_table_source` |
| SELECT UNPIVOT source table lineage | `select ... from t unpivot(v for k in (...))` | `unpivot_table_source` |
| SELECT PIVOT generated aggregate columns | `select north_amt from (...) pivot(sum(amount) as amt for region in (...))` | `pivot_generated_column_lineage` |
| SELECT multi-column PIVOT generated aggregate columns | `select north_app_total from (...) pivot(sum(amount) as total for (region, channel) in (...))` | `pivot_multi_column_generated_lineage` |
| SELECT multi-value UNPIVOT generated columns | `select metric, v1, v2 from t unpivot((v1, v2) for metric in ((c1, c2), ...))` | `unpivot_multi_value_column_lineage` |
| SELECT MATCH_RECOGNIZE relation lineage and usages | `from t match_recognize (partition by ... order by ... measures ... pattern (...) define ...)`, including `+` and `{m,n}` pattern quantifiers | `match_recognize_basic`, `match_recognize_quantifier` |
| SELECT MODEL clause lineage and usages | `from t model return updated/all rows partition by (...) dimension by (...) measures (...) rules (...)`, `model ignore/keep nav ...`, including measure aliases | `model_clause_basic`, `model_measure_alias_lineage`, `model_ignore_nav_alias_lineage`, `model_keep_nav_alias_lineage` |
| SELECT SAMPLE table clause | `select ... from t sample (...)`, `sample block (...) seed (...)` | `sample_table_source`, `sample_block_seed_table_source` |
| TABLE(function), APPLY, JSON_TABLE, XMLTABLE, and LATERAL relation source | `from table(app.fn(...)) f`, `join table(app.fn(t.c)) f`, `cross/outer apply table(app.fn(t.c)) f`, `join json_table(t.payload, '$' columns (...)) jt`, `from xmltable(... passing t.c columns (...)) x`, `cross join lateral (select ... from t where ...) q` | `table_function_relation`, `join_table_function_relation`, `cross_apply_table_function`, `outer_apply_table_function`, `json_table_relation`, `xml_table_relation`, `lateral_inline_view` |
| SELECT partition/subpartition table extension | `select ... from t partition(...)`, `select ... from t subpartition(...)` | `partition_table_source`, `subpartition_table_source` |
| SELECT EXCEPT/MINUS set operation | `select c from t1 minus distinct select c from t2` | `minus_distinct_column_projection` |
| Aggregate FILTER clause | `sum(v) filter (where flag = ...)`, `count(*) filter (where ...)` | `aggregate_filter_where_lineage` |
| SQL typed string literals | `date '2024-01-01'`, `timestamp '2024-01-01 00:00:00'` in predicates | `typed_string_literals_where` |
| Dictionary/complex result access | `dictionary_get(...)[1]`, `dictionary_get(...).field` | `dictionary_get_subscript_lineage`, `dictionary_get_field_lineage` |
| INSERT INTO target and source | `insert into ads.t select ... from ods.s` | `insert_into` |
| INSERT INTO VALUES target lineage | `insert into ads.t(c1) values (...)`, `insert into mart.t(c1, c2) values (..., default)` | `insert_values`, `insert_values_default` |
| INSERT ALL multi-table target lineage | `insert all into t1 (...) values (...) into t2 (...) values (...) select ...`, `when condition then into ... values (...)` | `insert_all`, `insert_all_when` |
| INSERT FIRST multi-table target lineage | `insert first into t1 (...) values (...) into t2 (...) values (...) select ...`, `when condition then into ... values (...)` | `insert_first`, `insert_first_when` |
| CREATE TABLE schema DDL | `create table mart.t (...)`, `create table ... organization external (...)` | `create_table_schema`, `create_external_table` |
| CREATE GLOBAL/PRIVATE TEMPORARY TABLE | `create global temporary table t (...) on commit preserve rows`, `create private temporary table ora$ptt_t (...) on commit drop definition`, `create private temporary table ora$ptt_t on commit preserve definition as select ...` | `create_global_temporary_table`, `create_global_temporary_table_as_select`, `create_private_temporary_table`, `create_private_temporary_table_as_select` |
| Session, transaction, routine, and sequence lifecycle control | `alter session set current_schema = ...`, `commit`, `rollback`, `savepoint sp`, `set transaction read only/read write/isolation level ...`, `create procedure ...`, `create function ...`, `create package ...`, `create package body ...`, `drop procedure ...`, `create/alter/drop sequence ...`, anonymous `begin/end` blocks | `alter_session_current_schema`, `commit_statement`, `rollback_statement`, `savepoint_statement`, `set_transaction_read_only`, `set_transaction_read_write`, `set_transaction_isolation`, `create_procedure`, `create_function`, `create_package`, `create_package_body`, `drop_procedure`, `create_sequence`, `alter_sequence`, `drop_sequence`, `anonymous_begin_block`, `anonymous_declare_block` |
| Trigger lifecycle control | `create or replace trigger trg before insert on mart.t for each row ...`, `drop trigger mart.trg` | `create_trigger_before_insert`, `drop_trigger` |
| CREATE TABLE AS SELECT | `create table ads.t as select ... from ods.s` | `create_table_as_select` |
| CTAS table properties and MODEL sources | `create table t nologging parallel 8 as select ...`, `create table t compress as select ...`, `create table t as select ... from s model ...` | `ctas_nologging_parallel`, `ctas_compress`, `ctas_model_measure_alias` |
| Partitioned CREATE TABLE/CTAS | `partition by range (...)`, `partition by hash (...) partitions n`, `partition by list (...) as select ...` | `create_table_range_partition`, `create_table_hash_partition`, `create_table_list_partition_as_select` |
| CREATE VIEW AS SELECT | `create view ads.v as select ... from ods.s join dwd.o`, `bequeath definer/current_user`, `with check option constraint name` | `create_view`, `create_view_bequeath_definer`, `create_view_bequeath_current_user`, `create_view_check_option_constraint` |
| CREATE MATERIALIZED VIEW AS SELECT | `create materialized view mart.mv as select ... from mart.s`, `build immediate refresh fast/force on demand as select ...`, `build deferred refresh complete on commit as select ...` | `create_materialized_view`, `create_materialized_view_build_refresh`, `create_materialized_view_deferred_complete`, `create_materialized_view_refresh_force` |
| ALTER MATERIALIZED VIEW affected view | `alter materialized view mv refresh fast on demand`, `alter materialized view mv compile` | `alter_materialized_view_refresh_fast`, `alter_materialized_view_compile` |
| CREATE INDEX affected table and index columns | `create bitmap index idx on mart.t(c)`, `create index ... local`, `create unique index ... global partition by hash (...)`, `create index ... tablespace ... online nologging parallel ... compress ...`; explicit index and partition keys are returned as `INDEX` usages | `create_bitmap_index`, `create_index_local`, `create_index_global_hash_partition`, `create_index_tablespace_online` |
| ANALYZE TABLE/INDEX metadata read | `analyze table mart.t compute statistics`, `analyze table ... estimate statistics for columns ... sample ...`, `analyze table ... delete statistics`, `analyze index ... validate structure` | `analyze_table`, `analyze_table_all_columns`, `analyze_table_columns_sample`, `analyze_table_delete_statistics`, `analyze_index_validate_structure` |
| DESCRIBE table metadata read | `describe table hr.t`, `desc hr.t c` | `describe_table`, `describe_column` |
| EXPLAIN PLAN metadata read | `explain plan for select ...`, `explain plan into plan_table for insert ... select ...`, `explain plan for update/delete/merge ...` | `explain_plan_select`, `explain_plan_insert_select`, `explain_plan_update`, `explain_plan_delete`, `explain_plan_merge` |
| LOCK TABLE control | `lock table ods.t in share mode nowait`, `lock table ods.t, mart.u in row exclusive mode wait 5`, `lock table ods.t in share row exclusive mode wait 10`, `lock table ods.t in exclusive mode nowait` | `lock_table_share_mode`, `lock_table_row_exclusive_wait`, `lock_table_share_row_exclusive`, `lock_table_exclusive_nowait` |
| Table privilege grants | `grant select on ods.t to role`, `revoke update on ods.t from role` | `grant_select_on_table`, `revoke_update_on_table` |
| INSERT SELECT over CTE | `insert into ads.t with q as (...) select ... from q` | `insert_from_cte` |
| CREATE VIEW over CTE | `create view ads.v as with q as (...) select ... from q` | `create_view_with_cte` |
| INSERT OVERWRITE dynamic partition VALUES | `insert overwrite t partition(dt = '2026-08-28') with label ... values (...)` | `insert_overwrite_dynamic_partition_values` |
| UNION source table propagation | `select a from ods.s1 union all select b from ods.s2` | `union_column_projection` |
| Double-quoted non-ASCII identifiers | `select "用户ID" from "业务库"."用户表"` | `quoted_identifiers` |
| DUAL pseudo table | `select sysdate from dual` | `dual_pseudo_table` |
| Hierarchical query clauses | `select ... from app.org start with ... connect by ...`, `connect by nocycle prior ...`, `connect_by_root col` | `hierarchical_query`, `hierarchical_query_nocycle`, `hierarchical_connect_by_root` |
| Legacy outer join marker | `where a.id = b.user_id(+)` | `legacy_outer_join_marker` |
| Row and hierarchy pseudocolumns | `where rownum <= 10`, `select rowid`, `select ora_rowscn`, `select level ... connect by ...` | `rownum_filter`, `rowid_projection`, `ora_rowscn_projection`, `hierarchical_level` |
| SELECT FOR UPDATE locking read | `select ... for update of c nowait`, `select ... for update of c wait n`, `select ... for update of c skip locked` | `select_for_update_nowait`, `select_for_update_wait`, `select_for_update_skip_locked`, `oracle_mode_select_for_update_wait` |
| OFFSET/FETCH pagination | `select ... from mart.t order by c offset 10 rows fetch next 20 rows only`, `fetch first ... rows with ties`, `fetch first ... percent rows only` | `fetch_first_pagination`, `fetch_with_ties`, `fetch_percent_only` |
| Single CTE source table propagation | `with q as (...) select ... from q` | `cte_column_projection` |
| Single derived subquery source table propagation | `select ... from (select ... from ods.s) q` | `subquery_column_projection` |
| UPDATE target table lineage | `update ads.t set c = c2 where ...` | `update_set` |
| DELETE target table lineage | `delete from ads.t where ...` | `delete_where` |
| MERGE target and source tables | `merge into ads.t using ods.s on (...) when matched then update ...`, `when matched then update ... where ...`, `when matched then update ... delete where ...`, `when not matched then insert ... where ...` | `merge_into`, `merge_update_where`, `merge_update_delete_where`, `merge_insert_where` |
| MERGE source subquery tables | `merge into ads.t using (select ... from ods.s) q on (...)` | `merge_using_subquery` |
| UPDATE with subquery sources | `update ads.t set c = (select ... from ods.s1) where id in (select ... from ods.s2)` | `update_with_subquery` |
| DELETE with subquery sources | `delete from ads.t where id in (select ... from ods.s)` | `delete_with_subquery` |
| DROP TABLE affected table | `drop table mart.t`, `drop table mart.t cascade constraints purge` | `drop_table`, `drop_table_cascade_purge` |
| DROP VIEW affected view | `drop view mart.v` | `drop_view` |
| DROP MATERIALIZED VIEW affected view | `drop materialized view mart.mv`, `drop materialized view mart.mv preserve table`, `drop materialized view mart.mv purge` | `drop_materialized_view`, `drop_materialized_view_preserve_table`, `drop_materialized_view_purge` |
| TRUNCATE TABLE affected table | `truncate table ads.t`, `truncate table ads.t drop storage`, `truncate table ads.t reuse storage` | `truncate_table`, `truncate_table_drop_storage`, `truncate_table_reuse_storage` |
| SYNONYM object lifecycle | `create synonym app.s for ods.t`, `create synonym app.s for ods.t@link`, `drop public synonym s`; `CREATE SYNONYM` returns the referenced table as an input and the synonym as an output | `create_synonym`, `create_public_synonym`, `create_synonym_for_dblink`, `drop_synonym`, `drop_public_synonym` |
| DATABASE LINK lifecycle and table references | `create database link ...`, `drop database link ...`, `select ... from ods.t@link`, `insert into ods.t@link select ...`; DBLink suffixes are accepted while table identity remains `schema.table` | `create_database_link`, `drop_database_link`, `select_dblink`, `insert_dblink_select` |
| ALTER TABLE RENAME TO old and new tables | `alter table mart.old rename to new_name` | `rename_table` |
| ALTER TABLE column maintenance | `alter table mart.t add c number`, `alter table mart.t modify c varchar2(...)`, `alter table mart.t drop column c`, `alter table mart.t rename column old to new` | `alter_table_add_column`, `alter_table_modify_column`, `alter_table_drop_column`, `alter_table_rename_column` |
| ALTER TABLE partition maintenance | `alter table t add partition ...`, `alter table t drop partition ...`, `alter table t truncate partition ... update indexes` | `alter_table_add_partition`, `alter_table_drop_partition`, `alter_table_truncate_partition` |
| COMMENT ON TABLE affected table | `comment on table mart.t is '...'` | `comment_table` |
| COMMENT ON COLUMN affected table | `comment on column mart.t.c is '...'` | `comment_column` |

Implemented Oracle column-level lineage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Direct single-table projection | `select id as user_id, name from ods.users` | `select_basic` |
| Alias-qualified JOIN projection | `select u.id, o.amount from users u join orders o` | `join_projection` |
| INSERT SELECT target mapping | `insert into ads.t select a as c1 from ods.s` | `insert_into` |
| INSERT target column list mapping | `insert into ads.t(c1, c2) select a, b from ods.s` | `insert_column_list` |
| INSERT over UNION ALL target column lineage | `insert into t(c1) select a from s1 union all select b from s2` | `insert_union_column_lineage` |
| INSERT over INTERSECT target column lineage | `insert into t(c1) select a from s1 intersect select b from s2` | `insert_intersect_column_lineage` |
| INSERT over EXCEPT target column lineage | `insert into t(c1) select a from s1 except select b from s2` | `insert_except_column_lineage` |
| INSERT ALL target column mapping | `insert all into t1(c1) values (a) into t2(c1) values (a) select a from s`, `when condition then into ... values (...)` | `insert_all`, `insert_all_when` |
| INSERT FIRST target column mapping | `insert first into t1(c1) values (a) into t2(c1) values (a) select a from s`, `when condition then into ... values (...)` | `insert_first`, `insert_first_when` |
| CTAS output column targets | `create table ads.t as select id as c1 from ods.s` | `create_table_as_select` |
| CTAS over aliased/expression/aggregate projections | `create table ads.t as select a as c1, upper(b), count(c) ...` | `ctas_expression_projection` |
| CREATE VIEW output column targets | `create view ads.v as select u.id from ods.users u`, `create view ... bequeath definer/current_user as select ...`, `create view ... with check option constraint name` | `create_view`, `create_view_bequeath_definer`, `create_view_bequeath_current_user`, `create_view_check_option_constraint` |
| CREATE VIEW over aliased/expression/aggregate projections | `create view ads.v as select a as c1, upper(b), count(c) ...` | `create_view_expression_projection` |
| CREATE VIEW column list target names | `create view ads.v(c1, c2) as select a, b from ods.s` | `create_view_column_list` |
| INSERT SELECT target mapping over CTE | `insert into ads.t with q as (...) select q.c1 from q` | `insert_from_cte` |
| INSERT target column list over subquery propagation | `insert into ads.t(c1) select c1 from (select a as c1 from ods.s) q` | `insert_from_subquery` |
| INSERT target column list over aliased/expression projections | `insert into t(c1,c2,c3) select a as x, upper(b), count(c) ...` | `insert_column_list_expression_projection` |
| CREATE VIEW output columns over CTE | `create view ads.v as with q as (...) select q.c1 from q` | `create_view_with_cte` |
| Double-quoted identifier column mapping | `select "用户ID" as "用户标识" from "业务库"."用户表"` | `quoted_identifiers` |
| Hierarchical query projection mapping | `select id as org_id from app.org start with ... connect by ...` | `hierarchical_query` |
| CASE expression dependencies | `select case when status = 'A' then score else 0 end as c from t` | `case_expression` |
| Multi-branch CASE expression dependencies | `select case when status = 'A' then score when status = 'P' then pending_score else default_score end from t` | `complex_case_expression` |
| CAST, function, and arithmetic expression dependencies | `select cast(id as varchar), coalesce(name, nickname), decode(status, 'VIP', vip_score, base_score), nvl2(nickname, nickname, name), price * quantity from t` | `common_expression_projection`, `decode_nvl2_projection` |
| Nested function expression dependencies | `select coalesce(lower(name), upper(nickname), cast(id as varchar)) from t` | `nested_function_projection` |
| Scalar subquery projection dependencies | `select (select max(amount) from orders) as max_amount from users` | `scalar_subquery_projection` |
| IN subquery predicate column usage | `where id in (select user_id from sessions)` | `in_subquery_column_usage` |
| GIN inverted-index MATCH predicate usage | `where c match 'x' and c2 match_any 'a b' and c3 match_all 'a b'` | `gin_match_predicate_usage` |
| ORDER BY projection alias column usage | `select c as alias from t order by alias` | `projection_alias_order_usage` |
| ORDER BY expression column usage | `select id from t order by coalesce(updated_at, created_at)` | `order_by_expression_column_usage` |
| GROUP BY aggregate expression dependencies | `select user_id, count(order_id), sum(amount) from t group by user_id` | `aggregate_expression_projection` |
| DISTINCT aggregate dependencies and HAVING usage | `select count(distinct user_id) ... group by region having count(distinct order_id) > ...` | `distinct_aggregate_column_usage` |
| HAVING projection alias usage | `select sum(amount) as total_amount from app.orders group by user_id having total_amount > ...` | `having_projection_alias_usage` |
| GROUP BY expression column usage | `select lower(region), count(order_id) from t group by lower(region)` | `group_by_expression_column_usage` |
| GROUP BY ROLLUP, CUBE, GROUPING SETS, and GROUPING_ID | `group by rollup(region, product)`, `grouping_id(region, product)`, `group by cube(region, product)`, `group by grouping sets ((region, product), (region), ())` | `group_by_rollup_projection`, `group_by_cube_projection`, `group_by_grouping_sets_projection`, `grouping_id_projection` |
| Window function expression dependencies and window clause usages | `select row_number() over (partition by k order by ts), sum(v) over (...) from t` | `window_function_projection` |
| Hive query organization usages | `sort by c`, `distribute by k sort by ts`, `cluster by k`, `limit offset, rows` | `sort_by_column_usage`, `distribute_sort_by_column_usage`, `cluster_by_column_usage`, `limit_offset_rows` |
| Single CTE direct column propagation | `with q as (select id as user_id from ods.s) select q.user_id from q` | `cte_column_projection` |
| Chained CTE direct column propagation | `with a as (...), b as (select c1 from a) select c1 from b` | `chained_cte_column_projection` |
| CTE column alias list propagation | `with q(c1, c2) as (select a, b from ods.s) select c1 from q` | `cte_column_aliases` |
| Recursive CTE deterministic base-field propagation | `with recursive org(...) as (anchor union all recursive) select base fields from org`; recursively derived fields are intentionally conservative | `recursive_cte_hierarchy_lineage` |
| Single derived subquery direct column propagation | `select q.user_id from (select id as user_id from ods.s) q` | `subquery_column_projection` |
| Same-name columns in joined subqueries stay scoped | `select t1.id, t2.id from (...) t1 join (...) t2 on t1.id = t2.id` | `joined_subquery_scope` |
| UNION column sources merged by position | `select a as c1 from s1 union all select b from s2` | `union_column_projection` |
| INTERSECT column sources merged by position | `select a as c1 from s1 intersect select b from s2` | `intersect_column_projection` |
| EXCEPT column sources merged by position | `select a as c1 from s1 except select b from s2` | `except_column_projection` |
| MINUS column sources merged by position | `select a as c1 from s1 minus select b from s2` | `minus_column_projection` |
| UPDATE assignment mapping | `update ads.t set c = c2 where ...` | `update_set` |
| UPDATE SET expression dependencies | `update ads.t set c1 = upper(c2), c3 = c4 + c5 where ...` | `update_expression_assignment` |
| MERGE update assignments and insert values | `merge into ads.t using ods.s on (...) when matched then update set c = s.c when not matched then insert (...) values (...)` | `merge_into` |
| MERGE source subquery field propagation | `merge into ads.t using (select a as c from ods.s) q on (...)` | `merge_using_subquery` |

Implemented Oracle clause-level column usage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| WHERE, GROUP BY, HAVING, and ORDER BY source columns | `select u.id, count(o.id) from users u join orders o where ... group by u.id having ... order by ...` | `clause_column_usage` |
| Basic predicate operators in WHERE | `where c between ... and ... and name like ... and deleted_at is null` | `predicate_operator_column_usage` |
| IN expression-list predicate usage | `where status in (...) and region in (home_region, ...)` | `in_list_predicate_column_usage` |
| Negated predicate operators in WHERE | `where c not between ... and name not like ... and status not in (...)` | `negated_predicate_column_usage` |
| Logical NOT over grouped predicates | `where not (status = ... or name like ...) and score > ...` | `logical_not_group_column_usage` |
| JOIN ON source columns | `select u.id, o.amount from users u join orders o on ...` | `join_on_column_usage` |
| Self-join aliases | `select e.id, m.name from employees e left join employees m on e.manager_id = m.id` | `self_join_column_usage` |
| JOIN USING source columns | `select u.id from users u join orders o using (id)` | `join_using_column_usage` |
| Chained JOIN USING source columns | `select u.id from users u join orders o using (id) join payments p using (id)` | `join_using_multi_table_scope` |
| JOIN USING scoped inside comma-separated relations | `select b.id from audit a, users b join orders o using (id)` | `join_using_comma_scope` |
| JOIN USING over CTE references | `with u as (...), o as (...) select ... from u join o using (id)` | `join_using_derived_scope` |
| JOIN USING over derived subqueries | `select ... from (select ...) u join (select ...) o using (id)` | `join_using_subquery_scope` |
| JOIN ON over CTE references | `with u as (...), o as (...) select ... from u join o on u.id = o.user_id` | `join_on_derived_scope` |
| JOIN ON over derived subqueries | `select ... from (select ...) u join (select ...) o on u.id = o.user_id` | `join_on_subquery_scope` |
| UPDATE WHERE source columns | `update ads.t set c = s.c from ods.s s where ...` | `dml_where_column_usage` |
| EXISTS subquery predicate column usage | `where exists (select 1 from ods.orders o where o.user_id = u.id)` | `exists_subquery_column_usage` |
| UPDATE WHERE subquery predicate columns | `update ads.t set c = (...) where id in (select user_id from ods.s)` | `update_with_subquery` |
| DELETE WHERE subquery predicate columns | `delete from ads.t where id in (select user_id from ods.s)` | `delete_with_subquery` |
| MERGE ON source columns | `merge into ads.t using ods.s on (t.id = s.id) ...` | `merge_into` |
| MERGE ON over source subquery columns | `merge into ads.t using (select id from ods.s) q on (t.id = q.id)` | `merge_using_subquery` |
| UNION branch WHERE source columns | `select id from ods.s1 where ... union all select id from ods.s2 where ...` | `set_operation_clause_column_usage` |

Current Oracle diagnostics:

| Code | Meaning |
| --- | --- |
| `ORACLE_PARSE_ERROR` | Oracle SQL could not be tokenized or walked by the current parser. |
| `ORACLE_STATEMENT_NOT_SUPPORTED` | The statement was recognized as Oracle but is not in the current statement set. |
| `ORACLE_COLUMN_LINEAGE_NOT_IMPLEMENTED` | No column lineage was produced for a statement shape where table lineage may still be available. |

Known Oracle gaps:

| Gap | Current behavior |
| --- | --- |
| Full Oracle grammar | The parser uses ANTLR tokenization plus a lineage walker; full parser grammar will be expanded incrementally. |
| `select *` expansion | Not expanded without schema metadata. |
| Oracle-specific query syntax | Package DDL and anonymous PL/SQL block envelopes are recognized, while package/procedure body internals are parsed conservatively. Basic `MERGE INTO`, hierarchical `START WITH` / `CONNECT BY`, PIVOT/UNPIVOT source table lineage, MATCH_RECOGNIZE relation usage, and MODEL clause usages are covered. |
| Complex CTEs and subqueries | Single CTE, chained CTE direct projection, CTE column aliases, and single derived subquery direct projection propagation are covered. Recursive CTEs and complex nested subqueries are not complete yet. |

## StarRocks

StarRocks is an active parser dialect path. The current implementation uses an ANTLR4 lexer with a lightweight lineage walker for common StarRocks query and write shapes.

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
| INSERT INTO VALUES target lineage | `insert into ads.t(c1) values (...)` | `insert_values` |
| CREATE TABLE AS SELECT | `create table ads.t as select ... from ods.s` | `create_table_as_select` |
| CREATE TABLE AS SELECT over CTE | `create table ads.t as with q as (...) select ... from q` | `create_table_as_with_select` |
| CREATE TABLE schema reuse in scripts | `create table ods.t (...); insert into ads.t select * from ods.t` | `parseScriptExpandsWildcardFromPriorCreateTableSchema` |
| CTAS lineage reuse in scripts | `create table dwd.t as select ... from ods.s; select t.* from dwd.t t` | `parseScriptPropagatesPriorCtasLineage` |
| CREATE VIEW AS SELECT | `create view ads.v as select ... from ods.s join dwd.o` | `create_view` |
| CREATE VIEW lineage reuse in scripts | `create view dwd.v as select ... from ods.s; select v.* from dwd.v v` | `propagatesCreateViewLineageInSharedContext` |
| INSERT SELECT over CTE | `insert into ads.t with q as (...) select ... from q` | `insert_from_cte` |
| CREATE VIEW over CTE | `create view ads.v as with q as (...) select ... from q` | `create_view_with_cte` |
| Secure CREATE VIEW with comments | `create view ads.v(c1 comment '...') comment '...' security invoker as select ...` | `create_secure_view_column_comments` |
| ALTER VIEW AS SELECT | `alter view ads.v(c1 comment "...", c2) as select ...`, aggregate view rewrites | `alter_view_declared_columns`, `alter_view_expression_lineage` |
| UNION source table propagation | `select a from ods.s1 union all select b from ods.s2` | `union_column_projection` |
| Single CTE source table propagation | `with q as (...) select ... from q` | `cte_column_projection` |
| Single derived subquery source table propagation | `select ... from (select ... from ods.s) q` | `subquery_column_projection` |
| UPDATE FROM target and source tables | `update ads.t set c = s.c from ods.s s` | `update_from` |
| UPDATE FROM derived query source tables | `update ads.t set c = q.c from (select ... from ods.s) q where ...` | `update_from_derived_assignment` |
| WITH before UPDATE FROM | `with q as (...) update ads.t set c = q.c from q where ...` | `with_update_from` |
| DELETE USING target and source tables | `delete from ads.t using ods.s s where ...` | `delete_using` |
| DELETE USING derived query source tables | `delete from ads.t using (select ... from ods.s) q where ...` | `delete_using_derived` |
| DELETE named partition with predicate usage | `delete from mart.t partition(p1) where c = ...`, `delete from mart.t partition p1 where c = ...` | `delete_partition_where`, `delete_bare_partition_where` |
| WITH before DELETE USING | `with q as (...) delete from ads.t using q where ...` | `with_delete_using` |
| UPDATE with subquery sources | `update ads.t set c = (select ... from ods.s1) where id in (select ... from ods.s2)` | `update_with_subquery` |
| UPDATE with scalar subquery predicate | `update ads.t set c = c + 1 where c < (select avg(c) from ods.s)` | `update_where_scalar_subquery_usage` |
| UPDATE with EXISTS predicate | `update ads.t set c = c + 1 where exists (select 1 from ods.s where ...)` | `update_where_exists_subquery` |
| UPDATE with constant/default or omitted predicate | `update ads.t set c = c + 1 where true`, `update t set c = default where ...`, partial update `update t set c = c + 1` | `update_where_true_assignment`, `update_set_default`, `update_partial_without_where` |
| DELETE with subquery sources | `delete from ads.t where id in (select ... from ods.s)` | `delete_with_subquery` |
| DELETE with EXISTS predicate | `delete from ads.t where exists (select 1 from ods.s where ...)` | `delete_where_exists_subquery` |
| DELETE with constant predicate | `delete from ads.t where true` | `delete_where_true` |
| INSERT WITH LABEL SELECT | `insert into ads.t with label job (...) select ... from ods.s` | `insert_with_label_select` |
| INSERT BY NAME and write properties | `insert into ads.t by name select ...`, `insert into ads.t by name properties(...) select ...`, `insert into ads.t properties(...) select ...` | `insert_by_name_lineage`, `insert_by_name_properties`, `insert_properties_lineage` |
| INSERT OVERWRITE TEMPORARY PARTITION | `insert overwrite table ads.t temporary partition(p1) (...) properties(...) select ...` | `insert_overwrite_temporary_partition` |
| INSERT INTO FILES unload | `insert into files(...) select ... from dwd.orders`, remote FILES unload properties such as `partition_by` and compression, `insert into files(...) with label job values (...)` | `insert_into_files_select`, `insert_into_files_partitioned_unload`, `insert_into_files_with_label_values` |
| Broker Load multiple data_desc entries | one `load label` statement loading multiple `data infile ... into table ...` entries | `load_label_multi_table_data_infile` |
| Load job metadata | `show load from db where label like ... and state = ... order by ... limit ...`, `show all routine load for db.job ...`, `show routine load task from db where jobname = ...` | `show_load_order_limit`, `show_all_routine_load_for_job`, `show_routine_load_task_from_db` |
| FILES table function | `select * from files(...)`, `files(...) as f(c1, c2)`, joins between physical tables and `files(...)` | `select_from_files_table_function`, `files_table_function_alias_columns`, `join_files_table_function` |
| Pipe loading lifecycle | `create or replace pipe ... as insert into ... from files(...)`, `alter pipe ... resume if suspended`, `alter pipe ... retry all` | `create_or_replace_pipe_files_load`, `alter_pipe_resume_if_suspended`, `alter_pipe_retry_all` |
| Native query table function | `select * from native_query(...)`, `native_query(...) as q(c1, c2)`, joins between physical tables and `native_query(...)` | `native_query_table_function`, `native_query_alias_columns`, `join_native_query_table_function` |
| SELECT INTO OUTFILE unload | `select ... from t ... into outfile ... format as ... properties(...)` | `select_into_outfile` |
| StarRocks query hints | `select /*+ set_var(...) */ ...`, `insert /*+ set_var(...) */ overwrite ... select ...` | `select_set_var_hint_lineage`, `insert_overwrite_set_var_hint_lineage` |
| Iceberg time-travel table references | `select ... from iceberg.db.t version as of ...`, `timestamp as of ...`, `insert into iceberg.db.t for version as of ... select ...` | `select_version_as_of_lineage`, `select_timestamp_as_of_lineage`, `insert_for_version_as_of_lineage` |
| Synchronous materialized view table hint | `select ... from mv [_sync_mv_]` | `select_sync_mv_hint_lineage` |
| CREATE TABLE LIKE structure lineage | `create table mart.t like ods.s`, `create table mart.t partition by ... distributed by ... properties(...) like ods.s`, `create external table if not exists mart.t like ext.db.s` | `create_table_like`, `create_table_like_with_options`, `create_external_table_like_if_not_exists` |
| Deprecated external table DDL | `create external table t (...) engine = iceberg properties (...)`, `engine = hive properties (...)`; target table schema is retained for script-local lineage expansion | `create_external_table_iceberg`, `create_external_table_hive` |
| CTAS with table model options | `create table t primary key(...) distributed by hash(...) order by (...) properties(...) as select ...`, range batch partitions with `start/end/every`; key, distribution, and order columns are returned as `TABLE_MODEL` column usages | `create_table_model_as_select`, `create_table_ctas_range_batch_partition`, `create_table_ctas_order_by` |
| CREATE TABLE table model DDL | `create table t (...) duplicate/aggregate/unique/primary key (...) distributed by ...`, StarRocks 4.1 range-based distribution semantic without explicit `distributed by`, `create temporary table ... engine=olap ...`, `properties ("bloom_filter_columns" = "c1,c2")`; key, order, and distribution columns are returned as `TABLE_MODEL` usages, and bloom filter columns are returned as `INDEX` usages | `create_table_duplicate_key`, `create_table_range_distribution_semantic`, `create_temporary_table_schema`, `create_table_aggregate_key`, `create_table_unique_key`, `create_table_primary_key_random`, `create_table_bloom_filter_properties` |
| CREATE TABLE ENGINE=OLAP DDL | `create table t (...) engine=olap duplicate key(...) distributed by ...` | `create_table_engine_olap` |
| CREATE TABLE rollup index clause | `create table t (...) duplicate key(...) distributed by ... rollup (r1(...), r2(...) from base properties(...))`, official `rollup (...) order by (...)` order; rollup columns are returned as `TABLE_MODEL` column usages | `create_table_rollup_clause`, `create_table_rollup_order_by_clause` |
| CREATE TABLE bitmap/GIN/VECTOR index definition | `create table t (..., index idx (c) using bitmap comment '...')`, `index idx (body) using gin ("parser" = "english") comment '...'`, `index idx (embedding) using vector (...)`; inline index columns are returned as `INDEX` column usages | `create_table_bitmap_index_definition`, `create_table_gin_index_properties`, `create_table_vector_index_definition` |
| CREATE TABLE aggregate column types | `pv bigint sum`, `uv hll hll_union`, `tags bitmap bitmap_union` | `create_table_aggregate_types` |
| CREATE TABLE generated columns, AUTO_INCREMENT, and complex defaults | `create table t (..., c string as json_string(...))` with same-table column lineage, `id bigint auto_increment`, array/map/struct default values | `create_table_generated_columns`, `create_table_auto_increment`, `create_table_complex_type_defaults` |
| CREATE TABLE range partition DDL | `create table t (...) duplicate key (...) partition by range (...) distributed by ...` | `create_table_duplicate_key` |
| CREATE TABLE expression partition DDL | `create table t (...) partition by date_trunc('day', event_time) distributed by hash(...)` | `create_table_expression_partition` |
| CREATE TABLE multi-column list partition DDL | `create table t (...) partition by (dt, city) distributed by hash(...)` | `create_table_list_partition` |
| DROP TABLE affected table | `drop table if exists mart.t`, `drop temporary table if exists tmp.t`, `drop table mart.t force` | `drop_table`, `drop_temporary_table`, `drop_table_force` |
| DROP VIEW affected view | `drop view if exists ads.v` | `drop_view` |
| DROP MATERIALIZED VIEW affected view | `drop materialized view if exists mart.mv` | `drop_materialized_view` |
| TRUNCATE TABLE affected table | `truncate table ads.t`, `truncate table ads.t partition(p1, p2)` | `truncate_table`, `truncate_table_partition` |
| REFRESH MATERIALIZED VIEW affected view | `refresh materialized view mart.mv with sync mode`, `refresh materialized view mart.mv partition start (...) end (...) force with async mode` | `refresh_materialized_view`, `refresh_materialized_view_partition_force` |
| REFRESH EXTERNAL TABLE affected table | `refresh external table catalog.db.t`, `refresh external table catalog.db.t partition(...)` | `refresh_external_table`, `refresh_external_table_partition` |
| CANCEL REFRESH MATERIALIZED VIEW control | `cancel refresh materialized view mart.mv force` | `cancel_refresh_materialized_view_force` |
| CREATE MATERIALIZED VIEW with distribution/build/refresh | `create materialized view ... comment '...' distributed by ... refresh deferred manual as select ...`, `build immediate refresh async every(...) as select ...`, `build deferred refresh manual as select ...`; partition, distribution, and sort-key columns are returned as target `TABLE_MODEL` usages, and `bloom_filter_columns` properties are returned as `INDEX` usages | `create_materialized_view_distributed_refresh`, `create_materialized_view_comment_deferred_manual`, `create_materialized_view_build_immediate`, `create_materialized_view_build_deferred`, `create_materialized_view_bloom_filter_properties` |
| CREATE MATERIALIZED VIEW expression partition, ORDER BY, and refresh schedule | `create materialized view ... partition by date_trunc(...) refresh async start(...) every (...) as select ...`, MV sort key `order by (...)`; MV model columns are returned as target `TABLE_MODEL` usages while ORDER BY source usage is also preserved | `create_materialized_view_expression_partition_refresh`, `create_materialized_view_order_by_refresh_schedule` |
| Dictionary object and column lineage | `create dictionary d using dim.t (k key, v value)`, original objects can be base tables, views, or asynchronous materialized views; `refresh dictionary d`, `cancel refresh dictionary d`, `drop dictionary d cache`, `show dictionary d` | `create_dictionary`, `create_dictionary_from_view`, `create_dictionary_from_materialized_view`, `refresh_dictionary`, `cancel_refresh_dictionary`, `drop_dictionary_cache`, `show_dictionary` |
| ALTER TABLE RENAME TO old and new tables | `alter table ads.old rename to ads.new`, `rename rollup ...`, `rename partition ...` | `rename_table`, `alter_table_rename_rollup`, `alter_table_rename_partition` |
| ALTER TABLE column maintenance | `alter table mart.t add column c int`, `alter table mart.t add column c varchar(...) not null default ... after other`, `alter table mart.t add columns (...)`, multiple alter clauses in one statement, `add/modify column ... as expression`, `modify column ...`, `rename column ...`, `order by (...)`; generated-column expressions return same-table column lineage and order keys return `TABLE_MODEL` usages | `alter_table_add_column`, `alter_table_add_column_options`, `alter_table_add_columns_group`, `alter_table_add_generated_column`, `alter_table_multiple_clauses_generated_index`, `alter_table_modify_generated_column`, `alter_table_modify_column`, `alter_table_rename_column`, `alter_table_order_by` |
| ALTER TABLE rollup-scoped columns and ordering | `add column ... to rollup`, `drop column ... from rollup`, `modify column ... from rollup`, `order by (...) from rollup properties (...)`; rollup ordering keys return `TABLE_MODEL` usages | `alter_table_add_column_to_rollup`, `alter_table_drop_column_from_rollup`, `alter_table_modify_column_from_rollup`, `alter_table_order_by_from_rollup` |
| ALTER TABLE STRUCT nested field maintenance | `modify column profile add field contact.email varchar(...)`, `modify column profiles drop field items.[*].legacy_code` | `alter_table_modify_struct_add_field`, `alter_table_modify_struct_array_drop_field` |
| ALTER TABLE drop column maintenance | `alter table mart.t drop column c`, `alter table mart.t drop column if exists c` | `alter_table_drop_column`, `alter_table_drop_column_if_exists` |
| ALTER TABLE partition maintenance | `alter table mart.t add/drop partition ...`, `drop partition if exists ... force`, `drop partitions (...) force`, `drop partitions start/end/every`, `drop partitions where ...`, `values less than (...)`, `values in (...)`, `values [(...), (...))`, table/partition-level distribution including `default buckets`; hash distribution and partition predicate columns return `TABLE_MODEL` usages | `alter_table_add_partition`, `alter_table_add_partition_range_bracket_distribution`, `alter_table_add_partition_values_in`, `alter_table_drop_partition`, `alter_table_drop_partition_force`, `alter_table_drop_partitions_batch_force`, `alter_table_drop_partitions_range_batch`, `alter_table_drop_partitions_where`, `alter_table_partitions_distributed_random`, `alter_table_distributed_hash_default_buckets` |
| ALTER TABLE temporary/replace partition maintenance | `alter table mart.t add/drop temporary partition ...`, `add temporary partitions start(...) end(...) every(...)`, `replace partition ... with temporary partition ...` | `alter_table_add_temporary_partition`, `alter_table_add_temporary_partitions_every`, `alter_table_drop_temporary_partition`, `alter_table_replace_partition` |
| ALTER TABLE partition properties and recovery | `alter table mart.t modify partition (...) set (...)`, `modify partition p set (...)`, `modify partition (*) set (...)`, `recover partition ...` | `alter_table_modify_partition_properties`, `alter_table_modify_partition_single`, `alter_table_modify_all_partitions`, `alter_table_recover_partition` |
| ALTER TABLE Iceberg branch/tag lifecycle | `alter table iceberg.db.t create tag ... as of version ... retain ...`, `alter table iceberg.db.t drop branch ...` | `alter_table_create_tag_as_of`, `alter_table_drop_branch` |
| ALTER TABLE compaction, tablet resize, and persistent index maintenance | `alter table mart.t compact`, `cumulative/base compact (...)`, `split/merge tablets ... properties(...)`, `drop persistent index on tablets (...)` | `alter_table_compact`, `alter_table_cumulative_compact_partitions`, `alter_table_split_tablets_partition`, `alter_table_merge_tablets`, `alter_table_drop_persistent_index` |
| ALTER TABLE rollup maintenance | `alter table mart.t add/drop rollup ...`, rollups with `from base_index`, batch add/drop rollups; `ADD ROLLUP` columns are returned as `TABLE_MODEL` column usages | `alter_table_add_rollup`, `alter_table_add_rollup_from_base`, `alter_table_add_rollup_batch`, `alter_table_drop_rollup`, `alter_table_drop_rollup_batch` |
| ALTER TABLE index maintenance | `alter table mart.t add index ... using bitmap/vector`, `alter table mart.t drop index ...`; `ADD INDEX` columns are returned as `INDEX` column usages | `alter_table_add_bitmap_index`, `alter_table_add_vector_index`, `alter_table_drop_index` |
| ALTER TABLE property/comment maintenance | `alter table mart.t set (...)`, `alter table mart.t comment = "..."`; `bloom_filter_columns` values are returned as `INDEX` usages | `alter_table_set_properties`, `alter_table_comment` |
| ALTER TABLE swap affected tables | `alter table mart.t swap with table mart.t_shadow` | `alter_table_swap` |
| MERGE INTO table, subquery, CTE, and matched delete sources | `merge into target using source on ... when matched then update set ... when not matched then insert (...) values (...)`, CTE-backed source, and `when matched ... then delete`; target/source tables, ON/WHEN usages, UPDATE and INSERT value lineage are returned | `merge_update_insert_table_source`, `merge_update_insert_subquery_source`, `merge_delete_when_matched`, `merge_with_cte_source` |
| CANCEL ALTER TABLE control | `cancel alter table column/optimize/rollup from mart.t`, optional rollup job ids | `cancel_alter_table_column`, `cancel_alter_table_rollup_jobs` |
| CREATE INDEX affected table and index columns | `create index idx on ads.t(c)`, `create index ... using ngrambf (...)`, `create index ... using gin (...) comment '...'`, `create index ... using vector (...)`; explicit index columns are returned as `INDEX` column usages | `create_index`, `create_index_ngrambf_properties`, `create_index_gin_properties_comment`, `create_index_vector_properties` |
| DROP INDEX affected table | `drop index idx on ads.t` | `drop_index` |
| COMMENT ON TABLE affected table | `comment on table mart.t is '...'` | `comment_on_table` |
| COMMENT ON COLUMN affected table | `comment on column mart.t.c is '...'` | `comment_on_column` |
| SHOW CREATE TABLE metadata read | `show create table ads.t`, `show create table ads.t\G` | `show_create_table`, `show_create_table_vertical` |
| SHOW CREATE VIEW metadata read | `show create view ads.v`, `show create view ads.v\G` | `show_create_view`, `show_create_view_vertical` |
| SHOW CREATE MATERIALIZED VIEW metadata read | `show create materialized view mart.mv`, `show create materialized view mart.mv\G` | `show_create_materialized_view`, `show_create_materialized_view_vertical` |
| ALTER MATERIALIZED VIEW affected view | `alter materialized view mart.mv active/inactive`, `alter materialized view mart.mv rename to mart.mv2`, `alter materialized view mart.mv swap with mart.mv_shadow`, `alter materialized view mart.mv refresh schedule every(...)`, `alter materialized view mart.mv set ("bloom_filter_columns" = "c1,c2")`; bloom filter columns are returned as `INDEX` usages | `alter_materialized_view_active`, `alter_materialized_view_inactive`, `alter_materialized_view_rename`, `alter_materialized_view_swap`, `alter_materialized_view_refresh_schedule`, `alter_materialized_view_set_properties` |
| DROP MATERIALIZED VIEW affected view | `drop materialized view mart.mv`, `drop materialized view if exists mart.mv force` | `drop_materialized_view`, `drop_materialized_view_force` |
| SHOW ALTER MATERIALIZED VIEW metadata read | `show alter materialized view from db`, `show alter materialized view in db` | `show_alter_materialized_view`, `show_alter_materialized_view_in` |
| SHOW PARTITIONS metadata read | `show partitions from ads.t`, `show temporary partitions from ads.t where ... order by ... limit ...`; filters/order keys are returned as table-scoped `READ_METADATA` usages | `show_partitions`, `show_temporary_partitions_filter` |
| SELECT from temporary partitions | `select ... from mart.t temporary partition(tp1, tp2)` | `select_temporary_partition` |
| SHOW COLUMNS and INDEX metadata read | `show columns from ads.t`, `show columns from t from db`, `show full columns from t from db`, `show indexes from ads.t`, `show index from t from db`, `show keys from t from db` | `show_columns_from_table`, `show_columns_from_table_from_database`, `show_full_columns_from_table`, `show_full_columns_from_table_from_database`, `show_indexes_from_table`, `show_index_from_table_from_database`, `show_keys_from_table_from_database` |
| SHOW TABLE STATUS metadata read | `show table status`, `show table status from mart like 'ads_%'` | `show_table_status`, `show_table_status_like` |
| SHOW TABLET metadata read | `show tablet from mart.t`, `show tablet from mart.t partition(...) where ... order by ... limit ...`, `show tablet 10010`; table-scoped filters/order keys are returned as `READ_METADATA` usages | `show_tablet_from_table`, `show_tablet_partition_filter`, `show_tablet_by_id` |
| Cluster/process metadata reads | `show proc '/dbs'`, `show proc '/dbs/db'`, `show proc '/dbs/db/table'`, `show full processlist`, `show backends`, `show compute nodes`, `show broker`, `show running queries`, `show backend blacklist`, `show sqlblacklist` | `show_proc`, `show_proc_database`, `show_proc_table`, `show_processlist_full`, `show_cluster_nodes`, `show_compute_nodes`, `show_broker`, `show_running_queries`, `show_backend_blacklist`, `show_sqlblacklist` |
| Tablet and replica admin metadata reads | `admin show replica status/distribution from mart.t partition(...)`, `admin show tablet status from mart.t where ... properties (...)` | `admin_show_replica_status`, `admin_show_replica_distribution`, `admin_show_tablet_status` |
| Tablet and node maintenance control | `admin repair table mart.t partition(...) properties (...)`, `admin cancel repair table mart.t`, `admin check tablet (...) properties (...)`, `admin set table mart.t partition(...) version to ...`, `admin skip committed transaction ... reason ...`, `alter system add/drop/decommission FE/BE/CN/Broker ...`, `alter system create image`, `add/delete backend or compute node blacklist ...`, `add/delete sqlblacklist ...`, `kill connection/query ...`, `cancel decommission backend ...`, `sync` | `admin_repair_table_partition`, `admin_cancel_repair_table`, `admin_check_tablet`, `admin_set_partition_version`, `admin_skip_committed_transaction`, `alter_system_add_backend`, `alter_system_add_follower`, `alter_system_drop_observer`, `alter_system_decommission_backend`, `alter_system_add_compute_node`, `alter_system_drop_compute_node`, `alter_system_add_broker`, `alter_system_drop_broker`, `alter_system_drop_all_broker`, `alter_system_create_image`, `add_backend_blacklist`, `delete_compute_node_blacklist`, `add_sqlblacklist_insert_values`, `delete_sqlblacklist_multi`, `kill_connection`, `kill_query`, `kill_process`, `cancel_decommission_backend`, `sync_statement` |
| ANALYZE TABLE metadata read | `analyze table ads.t`, `analyze sample table ads.t(c1, c2) properties (...)`, `analyze table ads.t update/drop histogram on c with async mode with 32 buckets`; explicit analyze columns are returned as `READ_METADATA` column usages | `analyze_table`, `analyze_sample_table_columns`, `analyze_table_update_histogram`, `analyze_table_drop_histogram` |
| Analyze job lifecycle | `create analyze sample table ads.t(c1) properties (...)`, `create analyze sample if not exists table ads.t(c1, c2) properties (...)`, `create analyze full database ads`, `create analyze full all properties (...)`, `drop analyze 10001`, `kill analyze 10001`; explicit analyze job columns are returned as `READ_METADATA` column usages | `create_analyze_table_columns`, `create_analyze_if_not_exists_table`, `create_analyze_database`, `create_analyze_all_properties`, `drop_analyze_job`, `kill_analyze_job` |
| Analyze and function/variable metadata reads | `show analyze job where ... order by ... limit ...`, `show analyze status where ... limit ...`, `show full builtin functions from db like ...`, `show global/session variables ...`; filters/order keys are returned as `READ_METADATA` usages | `show_analyze_job`, `show_analyze_status`, `show_full_builtin_functions`, `show_global_variables_like`, `show_session_variables_where` |
| Statistics metadata lifecycle | `drop stats ads.t`, `drop stats ads.t(c1, c2)`, `drop multiple columns stats ads.t`, `show stats meta where ...`, `show histogram meta where ...`; filters are returned as `READ_METADATA` usages | `drop_stats_table`, `drop_stats_columns`, `drop_multiple_columns_stats`, `show_stats_meta_where`, `show_histogram_meta_where` |
| DESCRIBE TABLE metadata read | `desc table ads.t`, `desc ads.t`, `describe mart.t all` | `describe_table`, `describe_table_short`, `describe_table_all` |
| DESC FILES schema inspection | `desc files("path" = "...", "format" = "parquet")` | `desc_files_schema` |
| EXPLAIN query/write metadata reads | `explain verbose select ...`, `explain analyze select ...`, `explain insert into ... select ...`, `explain analyze insert into ... select ...` | `explain_verbose_select`, `explain_analyze_select`, `explain_insert_select`, `explain_analyze_insert` |
| Schema/database control DDL | `create database ...`, `drop database ...`, `drop database ... force`, `alter database ... set data quota ...`, `alter database ... rename ...`, `alter database ... set properties (...)` | `create_database`, `drop_database`, `drop_database_force`, `alter_database_data_quota`, `alter_database_rename`, `alter_database_storage_volume` |
| External catalog lifecycle and session switch | `create external catalog ... properties (...)` for Hive, JDBC, and Iceberg property sets, `create external catalog ... comment ... properties (...)`, `drop catalog ...`, `drop catalog if exists ...`, `show create catalog ...`, `show catalogs`, `show catalogs\G`, `show catalogs like ...`, `set catalog ...` | `create_external_catalog_hive`, `create_external_catalog_comment`, `create_external_catalog_jdbc_mysql`, `create_external_catalog_iceberg`, `drop_catalog_basic`, `drop_catalog`, `show_create_catalog`, `show_catalogs`, `show_catalogs_vertical`, `show_catalogs_like`, `set_catalog` |
| Storage volume lifecycle control and metadata reads | `create storage volume ... type = s3 locations = (...) properties (...)`, `alter storage volume ... set properties (...)`, `alter storage volume ... enable/disable`, `drop storage volume ...`, `show storage volumes`, `show storage volumes like ...` | `create_storage_volume_s3`, `alter_storage_volume_set`, `alter_storage_volume_enable`, `alter_storage_volume_disable`, `drop_storage_volume`, `show_storage_volumes`, `show_storage_volumes_like` |
| Warehouse lifecycle control and metadata reads | `create warehouse ... properties (...)`, `alter warehouse ... set properties (...)`, `suspend warehouse ...`, `resume warehouse ...`, `drop warehouse ...`, `show warehouses where ... order by ...` | `create_warehouse`, `alter_warehouse_set`, `suspend_warehouse`, `resume_warehouse`, `drop_warehouse`, `show_warehouses` |
| External resource lifecycle control and metadata reads | `create external resource ... properties (...)`, `create external resource "name" properties (...)`, `create resource ... properties (...)`, `alter resource ... set properties (...)`, `alter resource 'name' set properties (...)`, `drop resource ...`, `drop resource 'name'`, `show resources`, `show resources where ... order by ... limit ...` | `create_external_resource`, `create_external_resource_quoted_name`, `create_resource_jdbc`, `alter_resource_set`, `alter_resource_quoted_name`, `drop_resource`, `drop_resource_quoted_name`, `show_resources_basic`, `show_resources` |
| Resource group lifecycle and metadata reads | `create resource group ... to (...) with (...)`, `alter resource group ... add/drop/with ...`, `alter resource group ... drop all`, `drop resource group ...`, `show resource groups`, `show resource groups all`, `show resource group name`, `show usage resource groups` | `create_resource_group_classifier`, `alter_resource_group_add_classifier`, `alter_resource_group_drop_classifiers`, `alter_resource_group_drop_all`, `alter_resource_group_limits`, `drop_resource_group`, `show_resource_groups`, `show_resource_groups_all`, `show_resource_group_name`, `show_usage_resource_groups` |
| Schema/session/routine lifecycle control | `use db`, `set query_timeout = ...`, `set @id1 = 1, @'batch-id' = ...`, Java UDF `create function ... returns ... properties (...)`, `create global aggregate function ...`, `create table function ...`, inline Python UDF `returns ... type = ... as $$...$$`, SQL UDF `create or replace global function ... returns expression`, `drop global function ...` | `use_database`, `set_session_variable`, `set_user_variables`, `set_quoted_user_variables`, `create_function`, `create_aggregate_function`, `create_table_function`, `create_python_udf_inline`, `create_sql_udf_expression`, `create_or_replace_global_sql_udf`, `drop_function`, `drop_global_function_signature` |
| Transaction and prepared statement control | `begin`, `start transaction`, `commit`, `rollback`, `prepare stmt from 'select ...'`, `execute stmt using @v`, `deallocate prepare stmt` | `begin_transaction_control`, `start_transaction_control`, `commit_control`, `rollback_control`, `prepare_select_statement`, `execute_prepared_using`, `deallocate_prepare` |
| Account and privilege control | `create/alter/drop user`, `create/drop/set role`, `set default role all/none/... to user`, `set password [for user] = ...`, `execute as ... with no revert`, `grant/revoke ... on table/view/materialized view ...`, MySQL-compatible `grant/revoke ... on db.table ...`, `grant ... on all tables in database ...`; object grants/revokes return the affected table-like object, while wildcard database-scope grants are recognized without synthetic table output | `create_user_default_role_properties`, `alter_user_default_role_properties`, `drop_user_if_exists`, `create_role`, `drop_role`, `set_role`, `set_default_role_all`, `set_password_current_user`, `set_password_for_user`, `execute_as_with_no_revert`, `grant_select_on_table_to_user`, `revoke_select_on_table_from_user`, `grant_select_on_view_to_role`, `revoke_select_on_view_from_role`, `grant_select_on_materialized_view_to_user`, `revoke_select_on_materialized_view_from_user`, `grant_select_on_all_tables_in_database`, `grant_select_on_qualified_table_compat`, `revoke_select_on_qualified_table_compat` |
| Account metadata reads | `show grants`, `show grants for current_user()`, `show grants for user`, `show grants for role`, `show roles`, `show users`, `show authentication for user`, `show all authentication`, `show property for user like ...` | `show_grants_current`, `show_grants_current_user`, `show_grants_for_user`, `show_grants_for_role`, `show_roles`, `show_users`, `show_authentication_for_user`, `show_all_authentication`, `show_property_for_user_like` |
| Backup and restore repository control | `create read only repository ... with broker on location ...`, `drop repository ...`, `cancel backup/restore from db`, `cancel backup/restore for external catalog`, `recover database/table/partition ...` | `create_repository_broker`, `drop_repository`, `cancel_backup_from_db`, `cancel_backup_external_catalog`, `cancel_restore_from_db`, `cancel_restore_external_catalog`, `recover_database`, `recover_table`, `recover_partition_from_table` |
| Backup and restore object lineage | `backup database ... on (table t1, table t2)`, `backup snapshot db.snapshot ... on (t)`, `backup all external catalogs ...`, `backup external catalogs (...) ...`, `backup ... on (all tables)`, `backup/restore ... on (view v, materialized view mv, function f)`, `restore snapshot ... external catalog c as alias`, `restore snapshot ... database src as target on (table t as t2)`, `restore snapshot db.snapshot ... on (t as t2)` | `backup_database_tables`, `backup_snapshot_qualified_table`, `backup_external_catalogs`, `backup_external_catalog_list`, `backup_all_tables`, `backup_views_and_functions`, `restore_external_catalog_alias`, `restore_tables`, `restore_database_table_alias`, `restore_snapshot_qualified_table_alias`, `restore_views_and_functions` |
| Backup and restore metadata reads | `show backup`, `show backup from db`, `show restore`, `show restore from db`, `show repositories`, `show snapshot on repo`, `show snapshot on repo where ...` | `show_backup`, `show_backup_from_db`, `show_restore`, `show_restore_from_db`, `show_repositories`, `show_snapshot_on_repo_basic`, `show_snapshot_on_repo` |
| Database metadata reads | `show create database db`, `show databases`, `show databases from catalog`, `show data`, `show data from db.table` | `show_create_database`, `show_databases`, `show_databases_from_catalog`, `show_data`, `show_data_from_table` |
| File lifecycle control and metadata reads | `create file ... properties (...)`, `create file ... in db properties (...)`, `drop file ... properties (...)`, `drop file ... from db properties (...)`, `show file`, `show file from db` | `create_file_without_database`, `create_file_properties`, `drop_file_without_database`, `drop_file_properties`, `show_file`, `show_file_from_database` |
| Table and materialized-view maintenance metadata reads | `show delete`, `show delete from db`, `show dynamic partition tables from db`, `show alter table column ...`, `show alter materialized view from db`, `show materialized views from db where ...`, legacy `show materialized view from db where ...`, `show full columns from db.table`, `show columns from t from db`, `show full columns from t from db`, `show tables`, `show tables from catalog.db`, `show full tables from db like ...`, `show views in db where ...` | `show_delete`, `show_delete_from_db`, `show_dynamic_partition_tables`, `show_alter_table_column`, `show_alter_materialized_view`, `show_materialized_views`, `show_materialized_views_where`, `show_materialized_views_like`, `show_materialized_view_legacy_singular`, `show_full_columns_from_table`, `show_columns_from_table_from_database`, `show_full_columns_from_table_from_database`, `show_tables`, `show_tables_from_catalog_db`, `show_full_tables_like`, `show_views_where`, `show_full_views` |
| Routine load target table | `create routine load db.job on t ... from kafka (...)`, `create routine load ... from pulsar (...)`, `create routine load job on ods.t ...`, `temporary partition(...)`, `columns(c1, tmp, c2 = f(tmp))`; unqualified targets can inherit the database from the qualified job name, and direct loaded columns, transformed SET inputs, and WHERE inputs use `$routine_loadN.column` external lineage sources | `create_routine_load_kafka`, `create_routine_load_unqualified_target`, `create_routine_load_column_assignment`, `create_routine_load_pulsar`, `create_routine_load_temporary_partition` |
| Broker load target table and loaded columns | `load label job (data infile (...) into table ods.t) with broker`, `load label job (data infile (...) into table ods.t (...) where ... set (...)) with broker ...`; direct loaded columns, transformed SET inputs, and WHERE inputs use `$loadN.column` external lineage sources | `load_label_basic_with_broker`, `load_label_data_infile` |
| Broker load extended data descriptors | `data infile ("path1", "path2") into table ods.t (...)`, `data infile (...) negative into table ods.t temporary partition (...) rows terminated by ... format options, columns from path as (...) set ...`; path columns, transformed SET inputs, and WHERE inputs use `$loadN.column` external lineage sources | `load_label_multiple_files`, `load_label_extended_data_desc`, `load_label_format_options_assignments` |
| Broker load broker variants and multi-target descriptors | `with broker "broker_name" (...)`, broker-free `with broker (...)`, one load job containing multiple `data infile ... into table ...` descriptors, and mixed file/table descriptors; table and column lineage is accumulated per descriptor | `load_label_quoted_broker`, `load_label_multi_data_desc`, `load_label_mixed_file_and_table` |
| Spark Load resource descriptors | `load label ... with resource ... properties (...)` with `DATA INFILE` file sources or `DATA FROM TABLE hive_ext.t`; file descriptors use `$loadN.column`, while external table descriptors return physical table-to-table and column lineage | `spark_load_data_infile`, `spark_load_data_from_table` |
| Pipe external-file loading lifecycle | `create pipe ... as insert into t select ... from files (...)`, official `select * from files(...)` form, `alter pipe ... set/suspend/retry file`, `drop pipe ...`, `show pipes`, `show pipes ...`, `show pipes ...\G`; direct file columns use `$pipeN.column` external lineage sources when target columns are explicit, and SHOW filters/order keys are returned as `READ_METADATA` usages | `create_pipe_files_load`, `create_pipe_select_star_files`, `alter_pipe_set_property`, `alter_pipe_suspend`, `alter_pipe_retry_file`, `drop_pipe`, `show_pipes_basic`, `show_pipes`, `show_pipes_vertical` |
| Task lifecycle and scheduled SQL wrappers | `submit task ... as insert into ... select ...`, `submit task ... properties (...) as insert into ... select ...`, `submit task schedule every(interval ...) as insert overwrite ... select ...`, `submit task schedule start(...) every(interval ...) as create table ... as select ...`, `submit task ... as cache select ...`, `alter task ... suspend/resume/set(...)`, `drop task ... force` | `submit_task_insert_select`, `submit_task_properties_insert`, `submit_task_schedule_insert`, `submit_task_schedule_start_ctas`, `submit_task_cache_select`, `submit_task_ctas`, `alter_task_suspend`, `alter_task_resume`, `alter_task_set_properties`, `drop_task_force` |
| Load and unload job control statements | `pause/resume/stop routine load for job`, `alter routine load for job ...`, `alter load for label properties (...)`, `cancel load where label = ...`, `cancel load from db where label = ...`, `cancel export where queryid = ...`, `cancel export from db where queryid = ...`; metadata WHERE columns are returned as `READ_METADATA` usages | `pause_routine_load`, `resume_routine_load`, `stop_routine_load`, `alter_routine_load_kafka`, `alter_load_priority`, `cancel_load_current_db`, `cancel_load`, `cancel_export_current_db`, `cancel_export_by_queryid` |
| Load and unload job metadata reads | `show load`, `show load where state = ...`, `show load ...`, `show load\G`, `show load ...\G`, `show routine load`, `show routine load ...`, `show routine load from db where ... order by ... limit ...`, `show routine load task ...`, `show routine load task ...\G`, `show export`, `show export from db`, `show export from db where queryid = ...`, `show export from db where ... order by ... limit ...`, `show transaction where id = ...`, `show transaction from db where id = ...`; filters/order keys are returned as `READ_METADATA` usages | `show_load_basic`, `show_load_state_only`, `show_load`, `show_load_vertical_basic`, `show_load_vertical`, `show_routine_load_basic`, `show_routine_load`, `show_routine_load_from_order_limit`, `show_routine_load_task`, `show_routine_load_task_vertical`, `show_export`, `show_export_from_db`, `show_export_queryid`, `show_export_from_where_order_limit`, `show_transaction_id_current_db`, `show_transaction_from_id` |
| Export source table and column lineage | `export table mart.t [partition(...)] to 'path' properties (...)`, `export table mart.t to 'path' with broker`, `export table mart.t(c1, c2) to 'path' with broker (...)`, `with broker "broker_name" (...)`; selected columns are returned as unload column lineage from the source table | `export_table`, `export_table_partition`, `export_table_with_broker_basic`, `export_table_with_broker_columns`, `export_table_with_named_broker` |
| Admin control statements | `admin set frontend config (...)`, `admin set replica status properties (...)`, `admin show replica distribution from t`, `admin repair table t partition (...) properties (...)` | `admin_set_frontend_config`, `admin_set_replica_status`, `admin_show_replica_distribution`, `admin_repair_table_partition` |

Implemented StarRocks column-level lineage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Direct single-table projection | `select id as user_id, name from ods.users` | `select_basic` |
| Alias-qualified JOIN projection | `select u.id, o.amount from users u join orders o` | `join_projection` |
| INSERT SELECT target mapping | `insert into ads.t select a as c1 from ods.s` | `insert_into` |
| INSERT target column list mapping | `insert into ads.t(c1, c2) select a, b from ods.s` | `insert_column_list` |
| INSERT BY NAME projection target mapping | `insert into ads.t by name select a as c1 from ods.s`, `insert into ads.t by name properties(...) select c1 from ods.s` | `insert_by_name_lineage`, `insert_by_name_properties` |
| INSERT with write properties target mapping | `insert into ads.t properties(...) select a as c1 from ods.s` | `insert_properties_lineage` |
| INSERT OVERWRITE TEMPORARY PARTITION target mapping | `insert overwrite table ads.t temporary partition(p1) (c1, c2) select a, sum(b) from ods.s group by a` | `insert_overwrite_temporary_partition` |
| INSERT over UNION ALL target column lineage | `insert into t(c1) select a from s1 union all select b from s2` | `insert_union_column_lineage` |
| INSERT over INTERSECT target column lineage | `insert into t(c1) select a from s1 intersect select b from s2` | `insert_intersect_column_lineage` |
| INSERT over EXCEPT target column lineage | `insert into t(c1) select a from s1 except select b from s2` | `insert_except_column_lineage` |
| CTAS output column targets | `create table ads.t as select id as c1 from ods.s` | `create_table_as_select` |
| CTAS over aliased/expression/aggregate projections | `create table ads.t as select a as c1, upper(b), count(c) ...` | `ctas_expression_projection` |
| CREATE VIEW output column targets | `create view ads.v as select u.id from ods.users u` | `create_view` |
| CREATE VIEW over aliased/expression/aggregate projections | `create view ads.v as select a as c1, upper(b), count(c) ...` | `create_view_expression_projection` |
| CREATE OR REPLACE VIEW output column targets | `create or replace view ads.v as select id as c1 from ods.s` | `create_or_replace_view` |
| CREATE MATERIALIZED VIEW output column targets | `create materialized view ads.mv as select u.id from ods.users u` | `create_materialized_view` |
| CREATE VIEW column list target names | `create view ads.v(c1, c2) as select a, b from ods.s` | `create_view_column_list` |
| INSERT SELECT target mapping over CTE | `insert into ads.t with q as (...) select q.c1 from q` | `insert_from_cte` |
| WITH before INSERT SELECT target mapping | `with q as (...) insert into ads.t(c1) select q.c1 from q` | `with_insert_select` |
| INSERT target column list over subquery propagation | `insert into ads.t(c1) select c1 from (select a as c1 from ods.s) q` | `insert_from_subquery` |
| INSERT target column list over aliased/expression projections | `insert into t(c1,c2,c3) select a as x, upper(b), count(c) ...` | `insert_column_list_expression_projection` |
| CREATE VIEW output columns over CTE | `create view ads.v as with q as (...) select q.c1 from q` | `create_view_with_cte` |
| Wildcard projection without metadata | `select * from ods.users` | `representsTableStarColumnLineageWithoutMetadata` |
| Qualified wildcard over derived query | `select q.* from (select id as user_id, name from ods.users) q` | `expandsAliasedSubqueryStarColumnLineage` |
| Qualified columns through derived wildcard | `select q.id from (select * from ods.users) q` | `resolvesQualifiedColumnsFromDerivedSelectStar` |
| StarRocks `EXCLUDE` wildcard safety | `select * exclude (email) from ods.users` | `select_star_exclude` |
| StarRocks known-schema `EXCLUDE` expansion | `create table ods.t (...); select t.* exclude (email) from ods.t t` | `parseScriptExpandsKnownQualifiedStarExclude` |
| CASE expression dependencies | `select case when status = 'A' then score else 0 end as c from t` | `case_expression` |
| Multi-branch CASE expression dependencies | `select case when status = 'A' then score when status = 'P' then pending_score else default_score end from t` | `complex_case_expression` |
| CAST, function, and arithmetic expression dependencies | `select cast(id as varchar), coalesce(name, nickname), price * quantity from t` | `common_expression_projection` |
| Nested function expression dependencies | `select coalesce(lower(name), upper(nickname), cast(id as varchar)) from t` | `nested_function_projection` |
| Scalar subquery projection dependencies | `select (select max(amount) from orders) as max_amount from users` | `scalar_subquery_projection` |
| IN subquery predicate column usage | `where id in (select user_id from sessions)` | `in_subquery_column_usage` |
| ORDER BY projection alias column usage | `select c as alias from t order by alias` | `projection_alias_order_usage` |
| ORDER BY expression column usage | `select id from t order by coalesce(updated_at, created_at)` | `order_by_expression_column_usage` |
| ORDER BY NULLS FIRST/LAST usage | `order by event_time desc nulls last, id asc nulls first` | `order_by_nulls_first_last` |
| LIMIT/OFFSET query organization | `limit offset,size`, `limit size offset offset` | `limit_offset_comma`, `limit_offset_keyword` |
| GROUP BY aggregate expression dependencies | `select user_id, count(order_id), sum(amount) from t group by user_id` | `aggregate_expression_projection` |
| DISTINCT aggregate dependencies and HAVING usage | `select count(distinct user_id) ... group by region having count(distinct order_id) > ...` | `distinct_aggregate_column_usage` |
| QUALIFY projection alias dependencies | `select row_number() over(...) as rn from t qualify rn = 1` | `qualify_projection_alias_window_filter` |
| GROUP BY and HAVING projection alias usage | `select date_trunc(...) as d, sum(v) as s from t group by d having s > ...` | `group_having_projection_alias_usage` |
| JSON function expression dependencies | `get_json_string(payload, '$.id')`, `json_query(payload, '$.items')`, `parse_json(payload)` | `json_function_lineage` |
| Generated-column rewrite expression dependencies | `select array_avg(data_array), json_string(json_query(data_json, ...)) from t` | `select_generated_column_rewrite_expression` |
| Dictionary lookup function dependencies | `dict_mapping('dim.dict', key_col, 'value_col', true)` captures fact key and dictionary value columns | `dict_mapping_function_lineage` |
| PIVOT generated aggregate column dependencies | `select sum_c1_1 from t pivot (sum(c1) as sum_c1 for c3 in (1))`, `select * from t pivot (...)`, multi-column pivot values | `pivot_generated_column_lineage`, `pivot_single_column`, `pivot_multi_column`, `pivot_multi_column_generated_lineage` |
| Array function, array literal, and lambda dependencies | `array_length(tags)`, `array_intersect(tags, active_tags)`, `array_contains_all(tags, ['vip'])`, `array_join(tags, ',')`, `arrays_zip(scores, weights)`, `array_map(x -> x + 1, scores)`, `array_map(scores, x -> x + bonus)`, `array_map((x, y) -> x * y, xs, ys)`, `array_sort(scores, (l, r) -> l > r)` | `array_function_lineage`, `array_join_zip_all_match_lineage`, `lambda_array_function_lineage`, `lambda_array_map_last_argument`, `lambda_multi_arg_array_function_lineage`, `array_sort_lambda_comparator` |
| Lambda predicate and complex value dependencies | `any_match(tags, tag -> tag = target_tag)`, `named_struct('id', user_id, 'score', total_score)`, `scores[1]`, `element_at(tags, 1)` | `array_filter_lambda_where_usage`, `named_struct_expression_lineage`, `array_subscript_expression_lineage` |
| Window frame expression dependencies | `sum(v) over(partition by k order by ts range between interval 7 day preceding and current row)`, `rows between unbounded preceding and current row` | `window_frame_range_interval`, `window_frame_rows_unbounded` |
| UNNEST table-function generated columns | `select u.unnest from ods.logs l, unnest(l.tags) u`, `cross join lateral unnest(t.scores) as s(score)`, multiple UNNEST relations, `unnest_bitmap(bitmap_col)` | `unnest_table_function_lineage`, `cross_join_lateral_unnest_lineage`, `multi_lateral_unnest_lineage`, `unnest_bitmap_table_function_lineage` |
| External table-function alias columns | `select f.c1 from files(...) as f(c1, c2)`, `select q.c1 from native_query(...) as q(c1, c2)` | `files_table_function_alias_columns`, `native_query_alias_columns` |
| EXPORT TABLE explicit columns | `export table mart.orders(c1, c2) to ...` | `export_table_with_broker_columns` |
| LATERAL subquery column propagation | `select q.c from lateral (select a as c from ods.s) q`, `lateral (...) as q(c)` | `lateral_query_column_projection`, `lateral_query_column_alias_list_projection` |
| GROUP BY expression column usage | `select lower(region), count(order_id) from t group by lower(region)` | `group_by_expression_column_usage` |
| GROUPING function over extended grouping | `select grouping(region), sum(amount) from t group by rollup(dt, region)`, `group by cube(dt, region)` | `grouping_function_rollup_lineage`, `group_by_cube` |
| Single CTE direct column propagation | `with q as (select id as user_id from ods.s) select q.user_id from q` | `cte_column_projection` |
| Chained CTE direct column propagation | `with a as (...), b as (select c1 from a) select c1 from b` | `chained_cte_column_projection` |
| CTE column alias list propagation | `with q(c1, c2) as (select a, b from ods.s) select c1 from q` | `cte_column_aliases` |
| Parenthesized single relation alias | `select u.id from (ods.users) as u` | `parenthesized_relation_alias_projection` |
| Single derived subquery direct column propagation | `select q.user_id from (select id as user_id from ods.s) q`, `(select id from ods.s) as q(user_id)` | `subquery_column_projection`, `subquery_column_alias_list_projection` |
| Known table alias column list through script context | `create table ods.t(c1 int); select u.alias_c1 from ods.t as u(alias_c1)` | `StarRocksDialectParserTest.parseScriptResolvesKnownTableAliasColumnList` |
| ParseContext default namespace | `defaultCatalog/defaultSchema + select c from t` | `StarRocksDialectParserTest.appliesDefaultNamespaceFromParseContext` |
| Same-name columns in joined subqueries stay scoped | `select t1.id, t2.id from (...) t1 join (...) t2 on t1.id = t2.id` | `joined_subquery_scope` |
| UNION column sources merged by position | `select a as c1 from s1 union all select b from s2` | `union_column_projection` |
| INTERSECT column sources merged by position | `select a as c1 from s1 intersect select b from s2` | `intersect_column_projection` |
| EXCEPT column sources merged by position | `select a as c1 from s1 except select b from s2` | `except_column_projection` |
| UPDATE assignment mapping | `update ads.t set c = s.c from ods.s s` | `update_from` |
| UPDATE SET expression dependencies | `update ads.t set c1 = upper(s.c2), c3 = s.c4 + t.c5 from ods.s s` | `update_expression_assignment` |
| UPDATE FROM derived query assignment dependencies | `update ads.t set c1 = q.c2 from (select c2 from ods.s) q where ...` | `update_from_derived_assignment` |
| WITH before UPDATE FROM assignment dependencies | `with q as (...) update ads.t set c1 = q.c2 from q where ...` | `with_update_from` |
| UPDATE scalar subquery assignment dependencies | `update ads.t set c = (select max(c) from ods.s) where ...` | `update_scalar_subquery_assignment` |

Implemented StarRocks clause-level column usage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Statistics metadata column usage | `analyze sample table ads.t(c1, c2)`, `analyze table ads.t update/drop histogram on c1, c2`, `create analyze sample table ads.t(c1, c2)`, `create analyze sample if not exists table ads.t(c1, c2)` | `analyze_sample_table_columns`, `analyze_table_update_histogram`, `analyze_table_drop_histogram`, `create_analyze_table_columns`, `create_analyze_if_not_exists_table` |
| WHERE, GROUP BY, HAVING, and ORDER BY source columns | `select u.id, count(o.id) from users u join orders o where ... group by u.id having ... order by ...` | `clause_column_usage` |
| Basic predicate operators in WHERE | `where c between ... and ... and name like ... and deleted_at is null` | `predicate_operator_column_usage` |
| IN expression-list predicate usage | `where status in (...) and region in (home_region, ...)` | `in_list_predicate_column_usage` |
| Negated predicate operators in WHERE | `where c not between ... and name not like ... and status not in (...)` | `negated_predicate_column_usage` |
| Logical NOT over grouped predicates | `where not (status = ... or name like ...) and score > ...` | `logical_not_group_column_usage` |
| Self-join aliases | `select e.id, m.name from employees e left join employees m on e.manager_id = m.id` | `self_join_column_usage` |
| JOIN USING source columns | `select u.id from users u join orders o using (id)` | `join_using_column_usage` |
| Chained JOIN USING source columns | `select u.id from users u join orders o using (id) join payments p using (id)` | `join_using_multi_table_scope` |
| JOIN USING scoped inside comma-separated relations | `select b.id from audit a, users b join orders o using (id)` | `join_using_comma_scope` |
| JOIN USING over CTE references | `with u as (...), o as (...) select ... from u join o using (id)` | `join_using_derived_scope` |
| JOIN USING over derived subqueries | `select ... from (select ...) u join (select ...) o using (id)` | `join_using_subquery_scope` |
| JOIN ON over CTE references | `with u as (...), o as (...) select ... from u join o on u.id = o.user_id` | `join_on_derived_scope` |
| JOIN ON over derived subqueries | `select ... from (select ...) u join (select ...) o on u.id = o.user_id` | `join_on_subquery_scope` |
| StarRocks join hints | `join [broadcast] t2 on ...`, `join [bucket] t2 on ...` | `join_broadcast_hint_column_usage`, `join_bucket_hint_group_usage` |
| UNION branch WHERE source columns | `select id from ods.s1 where ... union all select id from ods.s2 where ...` | `set_operation_clause_column_usage` |
| DELETE USING WHERE source columns | `delete from ads.t using ods.s s where t.id = s.id` | `delete_using` |
| DELETE USING derived WHERE columns | `delete from ads.t using (select id from ods.s) q where t.id = q.id` | `delete_using_derived` |
| WITH before DELETE USING predicate columns | `with q as (...) delete from ads.t using q where t.id = q.id` | `with_delete_using` |
| EXISTS subquery predicate column usage | `where exists (select 1 from ods.orders o where o.user_id = u.id)` | `exists_subquery_column_usage` |
| UPDATE/DELETE WHERE subquery predicate columns | `update/delete ads.t where id in (select user_id from ods.s)` | `update_with_subquery`, `delete_with_subquery` |

Current StarRocks diagnostics:

| Code | Meaning |
| --- | --- |
| `STARROCKS_PARSE_ERROR` | StarRocks SQL could not be tokenized or walked by the current parser. |
| `STARROCKS_STATEMENT_NOT_SUPPORTED` | The statement was recognized as StarRocks but is not in the current statement set. |
| `STARROCKS_COLUMN_LINEAGE_NOT_IMPLEMENTED` | No column lineage was produced for a statement shape where table lineage may still be available. |

Known StarRocks gaps:

| Gap | Current behavior |
| --- | --- |
| Full StarRocks grammar | The parser uses ANTLR tokenization plus a lineage walker; full parser grammar will be expanded incrementally. |
| `select *` expansion | Not expanded without schema metadata. |
| StarRocks table model DDL | `DUPLICATE KEY`, `AGGREGATE KEY`, `UNIQUE KEY`, `PRIMARY KEY`, hash/random distribution, order keys, generated columns, and index columns are covered as target table lineage plus table-model/index column usages where deterministic. |
| Complex CTEs, subqueries, and routine-load syntax | Single CTE, chained CTE direct projection, CTE column aliases, single derived subquery direct projection propagation, and materialized-view SELECT lineage are covered. Recursive CTEs and routine-load syntax are not complete yet. |

## Flink

Flink is an active parser dialect path. The current implementation uses an ANTLR4 lexer with a lightweight lineage walker for common Flink query and write shapes.

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
| NATURAL JOIN source tables | `select ... from a natural left join b` | `natural_left_join` |
| INSERT INTO target and source | `insert into ads_t select ... from ods_s` | `insert_into` |
| EXECUTE INSERT target and source | `execute insert into ads.t select ... from dwd.s` | `execute_insert_select` |
| INSERT OVERWRITE static partition | `insert overwrite ads.t partition (...) (c1, c2) select ...` | `insert_partition_select` |
| Hive-compatible INSERT TABLE syntax | `insert into table t select ...`; `insert overwrite table t partition (...) if not exists select ...` | `hive_insert_into_table`, `hive_insert_overwrite_table_partition` |
| Hive-compatible FROM-first multi-insert | `from source insert overwrite table t1 select ... insert overwrite table t2 select ...` | `hive_from_first_multi_insert` |
| Hive-compatible INSERT OVERWRITE DIRECTORY | `insert overwrite directory '/path' using parquet select ... from t`; `insert overwrite local directory ... row format ...`; `stored as inputformat ... outputformat ...` | `hive_insert_overwrite_directory`, `hive_insert_overwrite_local_directory_row_format`, `hive_insert_overwrite_directory_input_output_format` |
| Hive-compatible LOAD DATA | `load data local inpath ... overwrite into table t`; `load data inpath ... into table t partition (...)` | `hive_load_data_local_overwrite`, `hive_load_data_partition` |
| Hive-compatible CREATE DATABASE | `create database if not exists db comment ... location ... with dbproperties (...)` | `hive_create_database_location` |
| Hive-compatible CREATE TABLE storage DDL | `create external table t (...) partitioned by (...) stored as parquet location ... tblproperties (...)` | `hive_create_table_partitioned_stored` |
| Hive-compatible ROW FORMAT DDL | `create table t (...) row format serde ... with serdeproperties (...)`; `row format delimited fields terminated by ...` | `hive_create_table_row_format_serde`, `hive_create_table_input_output_format` |
| Hive-compatible CREATE TABLE AS SELECT | `create table t stored as parquet tblproperties (...) as select ... from s` | `hive_create_table_stored_as_ctas` |
| Hive-compatible ALTER DATABASE | `alter database db set location ...`; `alter database db set dbproperties (...)` | `hive_alter_database_set_location`, `hive_alter_database_set_dbproperties` |
| Hive-compatible ALTER TABLE properties | `alter table t set tblproperties (...)`; `alter table t set serdeproperties (...)` | `hive_alter_table_set_tblproperties`, `hive_alter_table_set_serdeproperties` |
| Hive-compatible ALTER TABLE partitions | `alter table t add if not exists partition (...) location ...`; `partition (...) rename to partition (...)`; `partition (...) set location ...`; `drop if exists partition (...)` | `hive_alter_table_add_partitions_location`, `hive_alter_table_rename_partition`, `hive_alter_table_partition_set_location`, `hive_alter_table_drop_partition_if_exists` |
| Hive-compatible ALTER TABLE columns and file format | `alter table t change column ...`; `add columns (...)`; `replace columns (...)`; `set fileformat ...`; `partition (...) set fileformat ...` | `hive_alter_table_change_column`, `hive_alter_table_add_columns`, `hive_alter_table_replace_columns`, `hive_alter_table_set_fileformat`, `hive_alter_table_partition_set_fileformat` |
| Hive-compatible cleanup DDL | `drop table if exists t purge`; `truncate table t partition (...)` | `hive_drop_table_purge`, `hive_truncate_table_partition` |
| INSERT INTO VALUES target lineage | `insert into ads_t(c1) values (...)` | `insert_values` |
| CREATE TABLE LIKE structure lineage | `create table mart_t like ods_s` | `create_table_like` |
| CREATE VIEW AS SELECT | `create view v as select ... from ods_s join dwd_o` | `create_view` |
| CREATE TEMPORARY VIEW with comment | `create temporary view if not exists v comment '...' as select ...` | `create_temporary_view_comment` |
| INSERT SELECT over CTE | `insert into ads_t with q as (...) select ... from q` | `insert_from_cte` |
| CREATE VIEW over CTE | `create view v as with q as (...) select ... from q` | `create_view_with_cte` |
| UNION source table propagation | `select a from ods_s1 union all select b from ods_s2` | `union_column_projection` |
| Single CTE source table propagation | `with q as (...) select ... from q` | `cte_column_projection` |
| Single derived subquery source table propagation | `select ... from (select ... from ods_s) q` | `subquery_column_projection` |
| Scalar subquery source propagation | `select (select max(v) from ods_s) as c from src_t` | `scalar_subquery` |
| IN subquery predicate column usage | `where id in (select user_id from sessions)` | `in_subquery_column_usage` |
| ORDER BY projection alias column usage | `select c as alias from t order by alias` | `projection_alias_order_usage` |
| ORDER BY expression column usage | `select id from t order by coalesce(updated_at, created_at)` | `order_by_expression_column_usage` |
| IN/EXISTS subquery source propagation | `where id in (select id from ods_s)` / `where exists (...)` | `in_subquery`, `exists_subquery` |
| LATERAL subquery source propagation | `from src_t, lateral (select ... from ods_s)` | `lateral_subquery` |
| CROSS JOIN UNNEST generated column lineage | `from t cross join unnest(t.arr) as u(elem)` | `cross_join_unnest_lineage` |
| LATERAL TABLE function generated column lineage | `from t, lateral table(fn(t.col)) as f(out_col)` | `lateral_table_function_lineage` |
| MATCH_RECOGNIZE pattern relation lineage and usages | `from t match_recognize (... partition by user_id order by proctime measures A.id as aid ... after match skip to first B pattern ((A | B) C{2,3}) within interval '10' minute define A as name = ...) as mr`; MEASURES and partition passthrough produce generated column lineage, while partition/order/define columns are returned as clause usages | `match_recognize_lineage`, `match_recognize_within_interval`, `match_recognize_skip_to_first`, `match_recognize_alternation_quantifier` |
| INSERT ON CONFLICT policies | `insert into sink select ... on conflict do nothing/error/deduplicate` | `insert_on_conflict_do_nothing`, `insert_on_conflict_do_error`, `insert_on_conflict_do_deduplicate` |
| UPDATE target table lineage | `update ads_t set c = c2 where ...`; `update ads_t set c = c * 2` | `update_set`, `update_without_where` |
| DELETE target table lineage | `delete from ads_t where ...`; `delete from ads_t` | `delete_where`, `delete_without_where` |
| UPDATE with subquery sources | `update ads_t set c = (select ... from ods_s1) where id in (select ... from ods_s2)` | `update_with_subquery` |
| DELETE with subquery sources | `delete from ads_t where id in (select ... from ods_s)` | `delete_with_subquery` |
| Statement set write lineage | `execute statement set begin insert into ...; end` | `execute_statement_set` |
| CREATE TABLE connector DDL | `create table ods_t (...) with ('connector' = 'kafka')` | `create_table_connector` |
| CREATE TABLE connector DDL tolerance | `with ('password' = 'secret' 'table-name' = 't')` preserves table lineage when a comma is missing between properties | `create_table_properties_missing_comma_tolerant` |
| CREATE TABLE metadata/computed/watermark/PK/partition DDL | `create table t (... c metadata from 'k' virtual, ts as ..., watermark for ..., primary key ... not enforced) partitioned by (...) with (...)`; computed columns with column inputs produce same-table column lineage, while watermark, primary-key, partition columns are returned as `TABLE_MODEL` usages | `create_table_metadata_computed_partitioned` |
| CREATE TABLE distribution DDL | `create table t (...) distributed by hash(id) into 8 buckets with (...)`; hash/range distribution columns are returned as `TABLE_MODEL` usages | `create_table_distributed_hash` |
| CREATE TABLE LIKE options | `create table t (...) like source_t (including options, excluding generated) with (...)` | `create_table_like_options` |
| CDC connector source DDL | `create table t (...) with ('connector' = 'mysql-cdc'/'postgres-cdc', ...)` | `create_table_mysql_cdc`, `create_table_postgres_cdc` |
| Common Flink connector sink/source DDL | `upsert-kafka`, `datagen`, and partitioned `filesystem` connector table definitions; source-free computed columns such as `proctime()` do not emit column lineage | `create_table_upsert_kafka`, `create_table_datagen`, `create_table_filesystem_partitioned` |
| CDC source to sink aggregate write | `insert into sink select key, count(...), sum(...) from cdc_source group by key` | `insert_cdc_to_sink` |
| MERGE INTO target and source tables | `merge into ads.t using ods.s on ... when matched then update ...` | `merge_into` |
| Temporal join source tables | `join rates for system_time as of o.proc_time` | `temporal_join` |
| TUMBLE table-valued function source table | `from table(tumble(table ods.orders, descriptor(ts), interval '1' hour))`; window descriptor columns are returned as `WINDOW_ORDER_BY` usages | `tumble_window` |
| HOP/CUMULATE/SESSION table-valued function source table | `from table(hop/cumulate/session(table t, descriptor(ts), ...))`; window descriptor columns are returned as `WINDOW_ORDER_BY` usages | `hop_window`, `cumulate_window`, `session_window` |
| ML and vector table functions | `from ml_predict(table t, model m, descriptor(...))`; `lateral table(vector_search(table v, q.embedding, descriptor(...)))` | `ml_predict_table_function`, `vector_search_lateral_table` |
| Changelog conversion PTF | `from from_changelog(input => table raw_orders, op => descriptor(op_type), op_mapping => map[...])` | `from_changelog_ptf` |
| Time travel source table | `from lake.orders for system_time as of timestamp '...'` | `time_travel_timestamp`, `time_travel_timestamp_interval` |
| Flink SQL hints | `table /*+ options(...) */`; `select /*+ broadcast(t) */ ...`; `insert into sink /*+ options(...) */ select ...` | `dynamic_table_options_hint`, `join_query_hint`, `insert_target_options_hint` |
| Top-N and deduplication QUALIFY | `qualify row_number() over(partition by ... order by ...) <= n` | `topn_qualify`, `deduplication_qualify` |
| Window JOIN over windowing TVFs | `from table(tumble(...)) l join/semi join/anti join table(tumble(...)) r on l.window_start = r.window_start ...` | `window_join_tumble`, `window_semi_join`, `window_anti_join` |
| Hive-compatible LEFT SEMI/ANTI JOIN | `left semi join ... on ...`; `left anti join ... on ...` | `hive_left_semi_join`, `hive_left_anti_join` |
| DROP TABLE affected table | `drop table if exists mart_t` | `drop_table` |
| DROP TEMPORARY TABLE affected table | `drop temporary table if exists tmp.t` | `drop_temporary_table` |
| DROP VIEW affected view | `drop view if exists mart.v`; `drop temporary view if exists tmp.v` | `drop_view_if_exists`, `drop_temporary_view` |
| ALTER TABLE RENAME TO old and new tables | `alter table mart_old rename to mart_new`; `alter table if exists mart_old rename to mart_new` | `rename_table`, `alter_table_if_exists_rename` |
| ALTER TABLE column maintenance | `alter table mart_t add c int` | `alter_table_add_column` |
| ALTER TABLE official column/watermark/PK maintenance | `alter table t add (... watermark ...); alter table t modify (...); alter table t drop (...)` | `alter_table_add_elements`, `alter_table_modify_elements`, `alter_table_drop_columns`, `alter_table_drop_watermark`, `alter_table_rename_column` |
| ALTER TABLE partition maintenance | `alter table t add/drop partition (...)` | `alter_table_partitions`, `alter_table_drop_partition` |
| ALTER TABLE distribution and property reset | `alter table t add distribution by hash(...); alter table t reset (...); alter table if exists t reset (...)` | `alter_table_distribution`, `alter_table_reset_properties`, `alter_table_if_exists_reset` |
| DESCRIBE TABLE metadata read | `describe table mart_t`; `describe extended dwd.orders` | `describe_table`, `describe_extended_table` |
| CREATE/DROP CATALOG control | `create catalog c comment '...' with (...); drop catalog if exists c` | `create_catalog_hive`, `create_catalog_comment`, `drop_catalog`, `drop_catalog_if_exists` |
| ALTER CATALOG control | `alter catalog c set (...); alter catalog c reset (...); alter catalog c comment '...'` | `alter_catalog_set_properties`, `alter_catalog_reset`, `alter_catalog_comment` |
| CREATE/ALTER/DROP DATABASE control | `create database if not exists db with (...); alter database db set (...); drop database if exists db cascade/restrict` | `create_database_with_properties`, `alter_database_set`, `drop_database_cascade`, `drop_database_restrict` |
| USE CATALOG/DATABASE control | `use catalog c; use database db` | `use_catalog`, `use_database` |
| CREATE/ALTER/DROP FUNCTION control | `create temporary system function f as 'class' language java`; `alter function f as com.example.Udf language java` | `create_temporary_system_function`, `alter_function_language`, `alter_function_identifier`, `drop_function` |
| CREATE FUNCTION with USING JAR and properties | `create function f as 'class' language java using jar 'file:///...' with (...)`; `create function f as com.example.Udf language java using jar 'file:///...'` | `create_function_using_jar_with`, `create_function_identifier` |
| CREATE MODEL metadata control | `create model m (...) comment '...' with (...); create model m input(...) output(...) with (...); create temporary model if not exists m with (...)` | `create_model_with_columns`, `create_model_input_output`, `create_temporary_model` |
| ALTER MODEL control | `alter model ml.m set (...); alter model ml.m reset (...); alter model if exists ml.m rename to ml.m2` | `alter_model_set_properties`, `alter_model_reset`, `alter_model_rename`, `alter_model_if_exists_rename` |
| Materialized table lifecycle | `create materialized table ... freshness = interval ... as select ...; alter materialized table ...; drop materialized table if exists ...` | `create_materialized_table_as_select`, `create_materialized_table_pk_partition`, `create_materialized_table_range_distribution`, `alter_materialized_table_suspend`, `alter_materialized_table_resume_with`, `alter_materialized_table_refresh_partition`, `alter_materialized_table_as_select`, `alter_materialized_table_add_column`, `alter_materialized_table_drop_column`, `drop_materialized_table`, `drop_materialized_table_if_exists` |
| DROP MODEL control | `drop temporary model if exists ml.m` | `drop_model` |
| Module lifecycle control | `load module hive with (...); unload module hive; use modules hive, core` | `load_module_hive`, `unload_module_hive`, `use_modules_order` |
| JAR lifecycle control | `add jar 'file:///...'; remove jar 'file:///...'` | `add_jar`, `remove_jar` |
| SQL Client session commands | `help`; `quit`; `exit`; `clear` | `help_statement`, `quit_statement`, `exit_statement`, `clear_statement` |
| Session property control | `set`; `reset`; `set 'k' = 'v'; set ('k' = 'v'); reset 'k'; reset ('k')`; `set table.exec.state.ttl=86400000ms` | `set_empty`, `reset_all`, `set_statement`, `set_parenthesized_statement`, `reset_statement`, `reset_parenthesized_statement`, `set_unquoted_configuration`, `set_duration_configuration`, `reset_unquoted_configuration` |
| SHOW metadata utility | `show catalogs; show create table t; show functions` | `show_catalogs`, `show_create_table`, `show_functions` |
| Expanded SHOW metadata utility | `show current catalog/database; show create catalog/table/view/materialized table/model; show columns/partitions/procedures/views/modules/jars/models` | `show_current_catalog`, `show_current_database`, `show_create_catalog`, `show_databases`, `show_tables_like`, `show_columns_from_table`, `show_partitions_table`, `show_create_materialized_table`, `show_create_model`, `show_procedures`, `show_views`, `show_create_view`, `show_materialized_tables`, `show_modules`, `show_jars`, `show_models` |
| SHOW metadata filters and variants | `show catalogs ilike ...`; `show tables in db not ilike ...`; `show databases from catalog like/not ilike ...`; `show views from db not like ...`; `show columns from t not like ...`; `show materialized tables in db like ...`; `show user/functions in db not ilike ...`; `show full modules`; `show models in db not like ...`; `show procedures from db like ...` | `show_catalogs_ilike`, `show_tables_not_ilike`, `show_databases_from_catalog_like`, `show_databases_not_ilike`, `show_views_not_like`, `show_columns_not_like`, `show_materialized_tables_like`, `show_user_functions_in_database_ilike`, `show_functions_not_ilike`, `show_full_modules`, `show_models_in_database_not_like`, `show_procedures_from_database_like` |
| DESCRIBE object metadata variants | `desc catalog extended c`; `describe function extended udf.f`; `desc model extended ml.m` | `describe_catalog_extended`, `describe_function_extended`, `describe_model_extended` |
| ANALYZE TABLE statistics metadata read | `analyze table t compute statistics for all columns`; `analyze table t partition(...) compute statistics for columns c1, c2` | `analyze_table_statistics`, `analyze_table_all_columns`, `analyze_table_partition_columns` |
| CALL and JOB utility statements | `call system.generate_n(4); show jobs; describe job '...'; stop job '...' with savepoint`; `stop job '...' with drain`; `stop job '...' with savepoint with drain` | `call_catalog_procedure`, `show_jobs`, `describe_job`, `stop_job_with_savepoint`, `stop_job_with_drain`, `stop_job_with_savepoint_drain` |
| EXPLAIN wrapped lineage | `explain plan for select ...`; `explain estimated_cost, changelog_mode insert into ... select ...` | `explain_plan_for_select`, `explain_details_insert` |
| Compiled plan utility statements | `compile plan '...' for insert into ... select ...`; `execute plan '...'` | `compile_plan_insert`, `execute_plan_file` |

Implemented Flink column-level lineage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Direct single-table projection | `select id as user_id, name from ods_users` | `select_basic` |
| Alias-qualified JOIN projection | `select u.id, o.amount from users u join orders o` | `join_projection` |
| Schema-free wildcard projection | `insert into ads.t select * from ods.s`; `select o.* from ods.orders o` | `insert_select_star_wildcard`, `select_qualified_star_wildcard` |
| Script-local CREATE TABLE schema wildcard expansion | `create table ods.s(id bigint, amount decimal(...)) with (...); insert into ads.t select * from ods.s` | `FlinkDialectParserTest.expandsWildcardLineageFromScriptCreateTableSchema` |
| Known temporary view wildcard expansion | `create temporary view v2 as select v1.* ...`; `insert into ads.t select * from v2` expands known view columns when available | `FlinkDialectParserTest.expandsQualifiedStarFromKnownTemporaryViewColumns` |
| INSERT SELECT target mapping | `insert into ads_t select a as c1 from ods_s` | `insert_into` |
| INSERT target column list mapping | `insert into ads_t(c1, c2) select a, b from ods_s` | `insert_column_list` |
| INSERT over UNION ALL target column lineage | `insert into t(c1) select a from s1 union all select b from s2` | `insert_union_column_lineage` |
| INSERT over INTERSECT target column lineage | `insert into t(c1) select a from s1 intersect select b from s2` | `insert_intersect_column_lineage` |
| INSERT over EXCEPT target column lineage | `insert into t(c1) select a from s1 except select b from s2` | `insert_except_column_lineage` |
| CTAS output column targets | `create table ads_t as select id as c1 from ods_s` | `create_table_as_select` |
| Hive-compatible CTAS output column targets | `create table ads.t stored as parquet tblproperties (...) as select c1 from dwd.s` | `hive_create_table_stored_as_ctas` |
| CTAS over aliased/expression/aggregate projections | `create table ads_t as select a as c1, upper(b), count(c) ...` | `ctas_expression_projection` |
| CREATE VIEW output column targets | `create view v as select u.id from ods_users u` | `create_view` |
| CREATE VIEW tolerance | `create view if not exist v as select ...` preserves lineage for the common missing-`s` typo | `create_view_if_not_exist_tolerant` |
| CREATE VIEW over aliased/expression/aggregate projections | `create view v as select a as c1, upper(b), count(c) ...` | `create_view_expression_projection` |
| CREATE VIEW column list target names | `create view v(c1, c2) as select a, b from ods_s` | `create_view_column_list` |
| INSERT SELECT target mapping over CTE | `insert into ads_t with q as (...) select q.c1 from q` | `insert_from_cte` |
| INSERT target column list over subquery propagation | `insert into ads_t(c1) select c1 from (select a as c1 from ods_s) q` | `insert_from_subquery` |
| INSERT over script-local temporary view wildcard propagation | `create temporary view v as select id, amount from ods.s; insert into ads.t select * from v` | `FlinkDialectParserTest.propagatesWildcardLineageAcrossTemporaryViewScript` |
| CREATE VIEW output columns over CTE | `create view v as with q as (...) select q.c1 from q` | `create_view_with_cte` |
| CASE expression dependencies | `select case when status = 'A' then score else 0 end as c from t` | `case_expression` |
| Multi-branch CASE expression dependencies | `select case when status = 'A' then score when status = 'P' then pending_score else default_score end from t` | `complex_case_expression` |
| CAST, function, and arithmetic expression dependencies | `select cast(id as string), coalesce(name, nickname), price * quantity from t` | `common_expression_projection` |
| Nested function expression dependencies | `select coalesce(lower(name), upper(nickname), cast(id as string)) from t` | `nested_function_projection` |
| Unaliased single-source expression target inference | `select ifnull(workshop_code, 'N/A') from ods.orders` can infer `workshop_code` for downstream temporary-view propagation | `FlinkDialectParserTest.infersUnaliasedSingleSourceExpressionTarget` |
| Struct/ROW field dereference paths | `select data_json.publish_time as ts from topic_events` preserves the nested source path under the single input table | `FlinkDialectParserTest.resolvesStructFieldDereferenceFromSingleInputTable`, `nested_field_projection` |
| Scalar subquery projection dependencies | `select (select max(amount) from orders) as max_amount from users` | `scalar_subquery_projection` |
| Scalar subquery-only projection dependencies | `with q as (...) select (select max(amount) from q) as max_amount` | `scalar_subquery_only_projection` |
| Scalar subquery predicate isolation | `select round(cnt / (select count(*) from t where c > 0), 2) from q` | `scalar_subquery_predicate_isolation` |
| IN subquery predicate column usage | `where id in (select user_id from sessions)` | `in_subquery_column_usage` |
| HAVING subquery scope isolation | `group by vin having vin in (select vin from dim.vehicles)` | `having_subquery_scope_isolation` |
| ORDER BY projection alias column usage | `select c as alias from t order by alias` | `projection_alias_order_usage` |
| ORDER BY expression column usage | `select id from t order by coalesce(updated_at, created_at)` | `order_by_expression_column_usage` |
| GROUP BY aggregate expression dependencies | `select user_id, count(order_id), sum(amount) from t group by user_id` | `aggregate_expression_projection` |
| DISTINCT aggregate dependencies and HAVING usage | `select count(distinct user_id) ... group by region having count(distinct order_id) > ...` | `distinct_aggregate_column_usage` |
| GROUP BY and HAVING projection alias usage | `select date_trunc(...) as d, sum(v) as s from t group by d having s > ...` | `group_having_projection_alias_usage` |
| GROUP BY expression column usage | `select lower(region), count(order_id) from t group by lower(region)` | `group_by_expression_column_usage` |
| GROUP BY ROLLUP/CUBE/GROUPING SETS | `group by rollup(...); group by cube(...); group by grouping sets (...)` | `group_by_rollup`, `group_by_cube`, `group_by_grouping_sets` |
| Temporal join projection dependencies | `select o.amount * r.rate from orders o join rates for system_time as of ... r` | `temporal_join` |
| Window TVF projection dependencies and time-column usage | `select window_start, user_id, count(order_id) from table(tumble/hop/cumulate/session(... descriptor(ts) ...))` | `tumble_window`, `hop_window`, `cumulate_window`, `session_window` |
| Hive-compatible LATERAL VIEW generated columns | `from t lateral view explode(t.items) lv as item`; chained lateral views | `hive_lateral_view_explode`, `hive_lateral_view_outer_explode`, `hive_multiple_lateral_views` |
| Hive-compatible TABLESAMPLE | `from t tablesample (100 rows)` | `hive_table_sample_rows` |
| Hive-compatible TRANSFORM query | `select transform (a, b) using 'script' as c, d from t` | `hive_transform_select` |
| Hive-compatible directory export column lineage | `insert overwrite local directory ... select c1, c2 from t` | `hive_insert_overwrite_local_directory_row_format`, `hive_insert_overwrite_directory_input_output_format` |
| Window frame aggregate dependencies and window clause usages | `sum(amount) over (partition by user_id order by ts rows between ...)` | `window_frame` |
| Named WINDOW clause | `select sum(v) over w from t window w as (...)` | `named_window_clause` |
| Aggregate FILTER clause | `count(*) filter (where status = 'paid')` | `aggregate_filter_clause` |
| Aggregate null treatment | `array_agg(amount ignore nulls)`; `array_agg(amount respect nulls) over (...)` | `array_agg_ignore_nulls`, `array_agg_respect_nulls_window` |
| JSON constructor and aggregate null behavior | `json_object(key k value v absent on null)`, comma-separated `json_object('k' value v, ...)`, `json_array(... null on null)`, `json_objectagg(...)`, `json_arrayagg(...)` | `json_object_on_null`, `json_object_comma_separated`, `json_array_on_null`, `json_objectagg_on_null`, `json_arrayagg_on_null` |
| JSON query/value functions | `json_value(payload, '$.vin' returning string ... on empty ... on error)`, `json_query(payload, '$.items' returning array<string> with ... wrapper)`, `json_exists(... on error)` | `json_value_returning_on_empty_error`, `json_query_returning_array_wrapper`, `json_exists_on_error_usage`, `unnest_json_query_returning_array` |
| Array subscript expressions | `data[1].id`, `signals[1]` in SELECT and WHERE expressions | `array_subscript_expression` |
| SQL-standard temporal functions | `floor(event_time to minute)`, `extract(epoch from event_time)` | `floor_timestamp_to_minute`, `extract_epoch_from_timestamp` |
| SQL-standard string special forms | `trim(trailing ']' from raw_data)`, `position('error' in reason_text)` | `trim_from_expression`, `position_in_expression` |
| VALUES query | `values (1, 'created'), (2, 'updated')` | `values_query` |
| Query organization with LIMIT/OFFSET/FETCH | `limit all offset 10 rows`; `fetch next row only` | `limit_all_offset_rows`, `fetch_next_row_only` |
| Hive-compatible query organization | `sort by ...`; `distribute by ... sort by ...`; `cluster by ...` | `hive_sort_by`, `hive_distribute_sort_by`, `hive_cluster_by` |
| Hive-compatible LIMIT offset form | `limit 20, 100` | `hive_limit_offset_rows` |
| CREATE OR REPLACE / REPLACE TABLE AS SELECT output mapping | `create or replace table ads.t with (...) as select ... from dwd.s group by ...; replace table ads.t (...) as select ... from dwd.s` | `replace_table_as_select`, `replace_table_standalone_as_select` |
| COMPILE PLAN inner INSERT output mapping | `compile plan '...' for insert into ads.t select c1 from dwd.s` | `compile_plan_insert` |
| CTAS explicit column-list remapping | `create table t (target_a, target_b) as select source_x, source_y from s` | `create_table_ctas_reordered_columns` |
| ALTER VIEW AS query output mapping | `alter view ads.v as select ... from dwd.s group by ...` | `alter_view_as_query` |
| Materialized table query output mapping | `create materialized table ... as select ...; alter materialized table ... as select ...` | `create_materialized_table_as_select`, `create_materialized_table_range_distribution`, `alter_materialized_table_as_select` |
| Single CTE direct column propagation | `with q as (select id as user_id from ods_s) select q.user_id from q` | `cte_column_projection` |
| Chained CTE direct column propagation | `with a as (...), b as (select c1 from a) select c1 from b` | `chained_cte_column_projection` |
| CTE column alias list propagation | `with q(c1, c2) as (select a, b from ods_s) select c1 from q` | `cte_column_aliases` |
| Single derived subquery direct column propagation | `select q.user_id from (select id as user_id from ods_s) q` | `subquery_column_projection` |
| Same-name columns in joined subqueries stay scoped | `select t1.id, t2.id from (...) t1 join (...) t2 on t1.id = t2.id` | `joined_subquery_scope` |
| UNION column sources merged by position | `select a as c1 from s1 union all select b from s2` | `union_column_projection` |
| INTERSECT column sources merged by position | `select a as c1 from s1 intersect select b from s2` | `intersect_column_projection` |
| EXCEPT column sources merged by position | `select a as c1 from s1 except select b from s2` | `except_column_projection` |
| UPDATE assignment mapping | `update ads_t set c = c2 where ...` | `update_set` |
| UPDATE SET expression dependencies | `update ads_t set c1 = upper(c2), c3 = c4 + c5 where ...` | `update_expression_assignment` |

Implemented Flink clause-level column usage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| WHERE, GROUP BY, HAVING, and ORDER BY source columns | `select u.id, count(o.id) from users u join orders o where ... group by u.id having ... order by ...` | `clause_column_usage` |
| Basic predicate operators in WHERE | `where c between ... and ... and name like ... and deleted_at is null` | `predicate_operator_column_usage` |
| IS TRUE/FALSE/UNKNOWN predicate usage | `where flag is true and deleted is not false and risk is unknown` | `is_true_false_unknown_predicate` |
| IN expression-list predicate usage | `where status in (...) and region in (home_region, ...)` | `in_list_predicate_column_usage` |
| Quantified subquery predicates | `where amount > all (select ...) and discount < any (select ...)` | `quantified_subquery_predicates` |
| Negated predicate operators in WHERE | `where c not between ... and name not like ... and status not in (...)` | `negated_predicate_column_usage` |
| Logical NOT over grouped predicates | `where not (status = ... or name like ...) and score > ...` | `logical_not_group_column_usage` |
| Self-join aliases | `select e.id, m.name from employees e left join employees m on e.manager_id = m.id` | `self_join_column_usage` |
| JOIN USING source columns | `select u.id from users u join orders o using (id)` | `join_using_column_usage` |
| Chained JOIN USING source columns | `select u.id from users u join orders o using (id) join payments p using (id)` | `join_using_multi_table_scope` |
| JOIN USING scoped inside comma-separated relations | `select b.id from audit a, users b join orders o using (id)` | `join_using_comma_scope` |
| JOIN USING over CTE references | `with u as (...), o as (...) select ... from u join o using (id)` | `join_using_derived_scope` |
| JOIN USING over derived subqueries | `select ... from (select ...) u join (select ...) o using (id)` | `join_using_subquery_scope` |
| JOIN ON over CTE references | `with u as (...), o as (...) select ... from u join o on u.id = o.user_id` | `join_on_derived_scope` |
| JOIN ON over derived subqueries | `select ... from (select ...) u join (select ...) o on u.id = o.user_id` | `join_on_subquery_scope` |
| UNION branch WHERE source columns | `select id from s1 where ... union all select id from s2 where ...` | `set_operation_clause_column_usage` |
| UPDATE/DELETE WHERE subquery predicate columns | `update/delete ads_t where id in (select user_id from ods_s)` | `update_with_subquery`, `delete_with_subquery` |
| CTAS/ALTER VIEW/EXPLAIN query clause usage | `... as select ... from t group by ...` and `explain insert into ... select ...` | `replace_table_as_select`, `alter_view_as_query`, `explain_insert_select` |
| ORDER BY with FETCH FIRST/NEXT and LIMIT/OFFSET | `select ... from t where ... order by ts fetch first 100 rows only`; `limit all offset 10 rows`; `fetch next row only` | `order_by_fetch_first`, `limit_all_offset_rows`, `fetch_next_row_only` |
| Hive-compatible SORT/DISTRIBUTE/CLUSTER BY source columns | `sort by abs(y)`; `distribute by x sort by y`; `cluster by x` | `hive_sort_by`, `hive_distribute_sort_by`, `hive_cluster_by` |

Current Flink diagnostics:

| Code | Meaning |
| --- | --- |
| `FLINK_PARSE_ERROR` | Flink SQL could not be tokenized or walked by the current parser. |
| `FLINK_STATEMENT_NOT_SUPPORTED` | The statement was recognized as Flink but is not in the current statement set. |
| `FLINK_COLUMN_LINEAGE_NOT_IMPLEMENTED` | No column lineage was produced for a statement shape where table lineage may still be available. |

Known Flink gaps:

| Gap | Current behavior |
| --- | --- |
| Full Flink grammar | The parser uses ANTLR tokenization plus a lineage walker; full parser grammar will be expanded incrementally. |
| `select *` expansion | Expanded when script-local table/view schema is known; otherwise kept as wildcard lineage without external schema metadata. |
| Flink DDL connector options | Connector DDL is covered as affected target table lineage; connector properties are not exposed as a separate lineage model yet. |
| Complex CTEs, subqueries, temporal joins, window TVFs, and row-pattern matching | Single CTE, chained CTE direct projection, CTE column aliases, single derived subquery direct projection propagation, temporal join version-time usage, TUMBLE/HOP/CUMULATE/SESSION descriptor usage, and MATCH_RECOGNIZE MEASURES/partition passthrough are covered. Recursive CTEs, metadata-backed wildcard expansion, and full generated-window-column semantics are not complete yet. |

## Hive

Hive is an active parser dialect path. The current implementation uses an ANTLR4 lexer with a lightweight lineage walker for common Hive query and write shapes.

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
| INSERT OVERWRITE DIRECTORY export source | `insert overwrite directory '/path' stored as parquet select ... from ods.s`, `insert overwrite local directory ... row format ...`, `stored as inputformat ... outputformat ...` | `insert_overwrite_directory`, `insert_overwrite_local_directory_row_format`, `insert_overwrite_directory_input_output_format` |
| INSERT INTO VALUES target lineage | `insert into table ads.t(c1) values (...)` | `insert_values` |
| CREATE TABLE AS SELECT | `create table ads.t as select ... from ods.s` | `create_table_as_select` |
| CREATE TABLE LIKE structure lineage | `create table mart.t like ods.s` | `create_table_like` |
| CREATE TABLE with storage format | `create table ods.t (...) stored as parquet` | `create_table_stored_as` |
| CREATE VIEW AS SELECT | `create view ads.v as select ... from ods.s join dwd.o` | `create_view` |
| INSERT SELECT over CTE | `insert into ads.t with q as (...) select ... from q` | `insert_from_cte` |
| CREATE VIEW over CTE | `create view ads.v as with q as (...) select ... from q` | `create_view_with_cte` |
| UNION source table propagation | `select a from ods.s1 union all select b from ods.s2` | `union_column_projection` |
| Single CTE source table propagation | `with q as (...) select ... from q` | `cte_column_projection` |
| Single derived subquery source table propagation | `select ... from (select ... from ods.s) q` | `subquery_column_projection` |
| UPDATE target table lineage | `update ads.t set c = c2 where ...` | `update_set` |
| DELETE target table lineage | `delete from ads.t where ...` | `delete_where` |
| UPDATE with subquery sources | `update ads.t set c = (select ... from ods.s1) where id in (select ... from ods.s2)` | `update_with_subquery` |
| DELETE with subquery sources | `delete from ads.t where id in (select ... from ods.s)` | `delete_with_subquery` |
| LOAD DATA target table lineage | `load data inpath '...' into table ads.t` | `load_data` |
| DROP TABLE affected table | `drop table if exists mart.t` | `drop_table` |
| DROP TABLE PURGE affected table | `drop table if exists mart.t purge` | `drop_table_purge` |
| TRUNCATE TABLE affected table | `truncate table ads.t partition (...)` | `truncate_table` |
| ALTER TABLE RENAME TO old and new tables | `alter table mart.old rename to mart.new` | `rename_table` |
| ALTER TABLE column maintenance | `alter table mart.t add columns (...)` | `alter_table_add_columns` |
| DESCRIBE TABLE metadata read | `describe table mart.t` | `describe_table` |
| Hive database lifecycle and schema switch | `create database if not exists db comment ... location ... tblproperties (...)`, `drop database if exists db cascade`, `use db` | `create_database_location`, `drop_database_cascade`, `use_database` |
| Hive table repair and statistics metadata | `msck repair table t sync partitions`, `analyze table t partition(...) compute statistics noscan` | `msck_repair_table`, `analyze_table_statistics` |

Implemented Hive column-level lineage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Direct single-table projection | `select id as user_id, name from ods.users` | `select_basic` |
| Alias-qualified JOIN projection | `select u.id, o.amount from users u join orders o` | `join_projection` |
| INSERT SELECT target mapping | `insert overwrite table ads.t select a as c1 from ods.s` | `insert_overwrite` |
| INSERT target column list mapping | `insert into ads.t(c1, c2) select a, b from ods.s` | `insert_column_list` |
| Directory export column lineage | `insert overwrite directory ... select c1, c2 from ods.s` | `insert_overwrite_directory`, `insert_overwrite_local_directory_row_format`, `insert_overwrite_directory_input_output_format` |
| INSERT over UNION ALL target column lineage | `insert into t(c1) select a from s1 union all select b from s2` | `insert_union_column_lineage` |
| INSERT over INTERSECT target column lineage | `insert into t(c1) select a from s1 intersect select b from s2` | `insert_intersect_column_lineage` |
| INSERT over EXCEPT target column lineage | `insert into t(c1) select a from s1 except select b from s2` | `insert_except_column_lineage` |
| CTAS output column targets | `create table ads.t as select id as c1 from ods.s` | `create_table_as_select` |
| CTAS over aliased/expression/aggregate projections | `create table ads.t as select a as c1, upper(b), count(c) ...` | `ctas_expression_projection` |
| CREATE VIEW output column targets | `create view ads.v as select u.id from ods.users u` | `create_view` |
| CREATE VIEW over aliased/expression/aggregate projections | `create view ads.v as select a as c1, upper(b), count(c) ...` | `create_view_expression_projection` |
| CREATE VIEW column list target names | `create view ads.v(c1, c2) as select a, b from ods.s` | `create_view_column_list` |
| INSERT SELECT target mapping over CTE | `insert into ads.t with q as (...) select q.c1 from q` | `insert_from_cte` |
| INSERT target column list over subquery propagation | `insert into ads.t(c1) select c1 from (select a as c1 from ods.s) q` | `insert_from_subquery` |
| INSERT target column list over aliased/expression projections | `insert into t(c1,c2,c3) select a as x, upper(b), count(c) ...` | `insert_column_list_expression_projection` |
| CREATE VIEW output columns over CTE | `create view ads.v as with q as (...) select q.c1 from q` | `create_view_with_cte` |
| CASE expression dependencies | `select case when status = 'A' then score else 0 end as c from t` | `case_expression` |
| Multi-branch CASE expression dependencies | `select case when status = 'A' then score when status = 'P' then pending_score else default_score end from t` | `complex_case_expression` |
| CAST, function, and arithmetic expression dependencies | `select cast(id as string), coalesce(name, nickname), price * quantity from t` | `common_expression_projection` |
| Nested function expression dependencies | `select coalesce(lower(name), upper(nickname), cast(id as string)) from t` | `nested_function_projection` |
| Scalar subquery projection dependencies | `select (select max(amount) from orders) as max_amount from users` | `scalar_subquery_projection` |
| IN subquery predicate column usage | `where id in (select user_id from sessions)` | `in_subquery_column_usage` |
| ORDER BY projection alias column usage | `select c as alias from t order by alias` | `projection_alias_order_usage` |
| ORDER BY expression column usage | `select id from t order by coalesce(updated_at, created_at)` | `order_by_expression_column_usage` |
| GROUP BY aggregate expression dependencies | `select user_id, count(order_id), sum(amount) from t group by user_id` | `aggregate_expression_projection` |
| DISTINCT aggregate dependencies and HAVING usage | `select count(distinct user_id) ... group by region having count(distinct order_id) > ...` | `distinct_aggregate_column_usage` |
| GROUP BY expression column usage | `select lower(region), count(order_id) from t group by lower(region)` | `group_by_expression_column_usage` |
| Window function expression dependencies and window clause usages | `select row_number() over (partition by k order by ts), sum(v) over (...) from t` | `window_function_projection` |
| Single CTE direct column propagation | `with q as (select id as user_id from ods.s) select q.user_id from q` | `cte_column_projection` |
| Chained CTE direct column propagation | `with a as (...), b as (select c1 from a) select c1 from b` | `chained_cte_column_projection` |
| CTE column alias list propagation | `with q(c1, c2) as (select a, b from ods.s) select c1 from q` | `cte_column_aliases` |
| Single derived subquery direct column propagation | `select q.user_id from (select id as user_id from ods.s) q` | `subquery_column_projection` |
| Same-name columns in joined subqueries stay scoped | `select t1.id, t2.id from (...) t1 join (...) t2 on t1.id = t2.id` | `joined_subquery_scope` |
| UNION column sources merged by position | `select a as c1 from s1 union all select b from s2` | `union_column_projection` |
| INTERSECT column sources merged by position | `select a as c1 from s1 intersect select b from s2` | `intersect_column_projection` |
| EXCEPT column sources merged by position | `select a as c1 from s1 except select b from s2` | `except_column_projection` |
| UPDATE assignment mapping | `update ads.t set c = c2 where ...` | `update_set` |
| UPDATE SET expression dependencies | `update ads.t set c1 = upper(c2), c3 = c4 + c5 where ...` | `update_expression_assignment` |

Implemented Hive clause-level column usage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| WHERE, GROUP BY, HAVING, and ORDER BY source columns | `select u.id, count(o.id) from users u join orders o where ... group by u.id having ... order by ...` | `clause_column_usage` |
| Basic predicate operators in WHERE | `where c between ... and ... and name like ... and deleted_at is null` | `predicate_operator_column_usage` |
| IN expression-list predicate usage | `where status in (...) and region in (home_region, ...)` | `in_list_predicate_column_usage` |
| Negated predicate operators in WHERE | `where c not between ... and name not like ... and status not in (...)` | `negated_predicate_column_usage` |
| Logical NOT over grouped predicates | `where not (status = ... or name like ...) and score > ...` | `logical_not_group_column_usage` |
| Self-join aliases | `select e.id, m.name from employees e left join employees m on e.manager_id = m.id` | `self_join_column_usage` |
| JOIN USING source columns | `select u.id from users u join orders o using (id)` | `join_using_column_usage` |
| Chained JOIN USING source columns | `select u.id from users u join orders o using (id) join payments p using (id)` | `join_using_multi_table_scope` |
| JOIN USING scoped inside comma-separated relations | `select b.id from audit a, users b join orders o using (id)` | `join_using_comma_scope` |
| JOIN USING over CTE references | `with u as (...), o as (...) select ... from u join o using (id)` | `join_using_derived_scope` |
| JOIN USING over derived subqueries | `select ... from (select ...) u join (select ...) o using (id)` | `join_using_subquery_scope` |
| JOIN ON over CTE references | `with u as (...), o as (...) select ... from u join o on u.id = o.user_id` | `join_on_derived_scope` |
| JOIN ON over derived subqueries | `select ... from (select ...) u join (select ...) o on u.id = o.user_id` | `join_on_subquery_scope` |
| UNION branch WHERE source columns | `select id from ods.s1 where ... union all select id from dwd.s2 where ...` | `set_operation_clause_column_usage` |
| EXISTS subquery predicate column usage | `where exists (select 1 from ods.orders o where o.user_id = u.id)` | `exists_subquery_column_usage` |
| UPDATE/DELETE WHERE subquery predicate columns | `update/delete ads.t where id in (select user_id from ods.s)` | `update_with_subquery`, `delete_with_subquery` |
| SORT/DISTRIBUTE/CLUSTER BY source columns | `sort by c`, `distribute by k sort by ts`, `cluster by k` | `sort_by_column_usage`, `distribute_sort_by_column_usage`, `cluster_by_column_usage` |

Current Hive diagnostics:

| Code | Meaning |
| --- | --- |
| `HIVE_PARSE_ERROR` | Hive SQL could not be tokenized or walked by the current parser. |
| `HIVE_STATEMENT_NOT_SUPPORTED` | The statement was recognized as Hive but is not in the current statement set. |
| `HIVE_COLUMN_LINEAGE_NOT_IMPLEMENTED` | No column lineage was produced for a statement shape where table lineage may still be available. |

Known Hive gaps:

| Gap | Current behavior |
| --- | --- |
| Full Hive grammar | The parser uses ANTLR tokenization plus a lineage walker; full parser grammar will be expanded incrementally. |
| `select *` expansion | Not expanded without schema metadata. |
| Complex expressions, CTEs, subqueries, and lateral view | Single CTE, chained CTE direct projection, CTE column aliases, and single derived subquery direct projection propagation are covered. Complex expressions, recursive CTEs, nested subqueries, and lateral view are not complete yet. |

## MySQL

MySQL is an active parser dialect path. It uses an ANTLR4 lexer with a lightweight lineage walker for common MySQL statement shapes, including SELECT, DML, DDL, lifecycle statements, metadata reads, control statements, and common expression lineage.

Current MySQL SQL case assets:

```text
linesql-dialect-mysql/src/test/resources/sql/mysql/manifest.json
linesql-dialect-mysql/src/test/resources/sql/mysql/cases/*.sql
```

Implemented MySQL table-level lineage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Basic SELECT source table | `select ... from app.users` | `select_basic` |
| TABLE statement source table | `table app.users order by id limit 10` | `table_statement` |
| HANDLER table access syntax | `handler app.users open`, `handler app.users read idx = (...) where ...`, `handler app.users close` | `handler_open`, `handler_read_index_range`, `handler_close` |
| VALUES statement without table lineage | `values row(1, 'a'), row(2, 'b') order by 1 limit 1` | `values_statement` |
| VALUES derived table without input tables | `select v.c from (values row(...)) as v(c)` | `derived_values_table` |
| DUAL pseudo-table ignored | `select 1 as one from dual` | `select_from_dual` |
| SELECT with MySQL options | `select high_priority sql_calc_found_rows ... from app.users` | `select_with_mysql_options` |
| SELECT with LIMIT/OFFSET organization | `select ... from app.users where ... order by ... limit 100 offset 20`, `limit 20, 100` | `limit_offset_keyword`, `limit_offset_comma` |
| SELECT wildcard projection lineage | `select * from app.users`, `select u.* from app.users u`, `select q.* from (select ...) q` | `select_table_star_lineage`, `select_qualified_star_lineage`, `select_derived_star_lineage` |
| CTAS/VIEW wildcard projection lineage | `create table mart.t as select * from app.s`, `create view mart.v as select * from app.s` | `create_table_as_select_star_lineage`, `create_view_select_star_lineage` |
| JOIN source tables | `select ... from app.users u join app.orders o ...` | `join_projection` |
| NATURAL JOIN source tables | `select ... from app.users natural join app.orders`, `natural left outer join ...`, `natural right outer join ...` | `natural_join_projection`, `natural_left_join_projection`, `natural_right_outer_join_projection` |
| CROSS JOIN source tables | `select ... from app.users u cross join dim.regions r where ...` | `cross_join_projection` |
| STRAIGHT_JOIN source tables | `select straight_join ... from app.users u straight_join app.orders o ...` | `select_straight_join`, `straight_join_operator_lineage` |
| Table index hints | `select ... from app.users u force index for join (...)`, `ignore key for order by (...)`, `ignore key for group by (...)`, `select ... from app.users use index ()` | `select_force_index`, `select_ignore_key_for_order_by`, `select_index_hint_join_group_lineage`, `select_use_index_empty` |
| Optimizer hint comments | `select /*+ SET_VAR(...) */ ... from app.orders` | `select_optimizer_hint_lineage` |
| SELECT locking clauses | `select ... from app.users for update`, `select ... for update nowait`, `select ... for update of u nowait`, `select ... for share of u, o skip locked`, `select ... lock in share mode` | `select_for_update`, `select_for_update_nowait`, `select_for_update_of_alias_nowait`, `select_for_share_skip_locked`, `select_for_share_of_alias_skip_locked`, `select_lock_in_share_mode` |
| SELECT PROCEDURE ANALYSE compatibility | `select ... from app.orders order by amount procedure analyse(...)` | `select_procedure_analyse` |
| DISTINCTROW SELECT projection lineage | `select distinctrow id, name from app.users where ...` | `select_distinctrow` |
| Subquery source table propagation | `select q.c from (select a as c from app.s) q` | `subquery_column_projection` |
| LATERAL derived query propagation | `from app.users u join lateral (select ... from app.orders o where o.user_id = u.id) recent` | `lateral_derived_query_lineage` |
| CTE source table propagation | `with q as (select a as c from app.s) select c from q` | `cte_column_projection` |
| Chained CTE column propagation | `with q1 as (...), q2 as (select ... from q1) select ... from q2` | `cte_chain_column_projection` |
| Recursive CTE base-table propagation | `with recursive q(...) as (... union all ... from q) select ... from q` | `recursive_cte_column_projection` |
| Recursive CTE base-table propagation | `with recursive q as (...) select ... from q` | `with_recursive_select` |
| UNION source table propagation | `select a from app.s1 union all select b from app.s2` | `union_column_projection` |
| SELECT INTO OUTFILE/DUMPFILE source lineage | `select ... into outfile '...' from app.users`, `select ... into dumpfile '...' from app.users`, `select ... from app.users into outfile '...'`, `select ... into outfile ... columns terminated by ...` | `select_into_outfile`, `select_into_dumpfile`, `select_into_outfile_tail`, `select_into_outfile_columns_options` |
| SELECT INTO variables source lineage | `select c1, c2 into @v1, @v2 from app.users`, `select c1 into v1 from app.users` | `select_into_variables`, `select_into_local_variables` |
| JSON_TABLE derived relation source table | `select jt.sku from app.orders u join json_table(u.payload, '$.items[*]' columns (...)) jt ...`, `exists path` and `default ... on empty` columns | `json_table_projection`, `json_table_nested_columns`, `json_table_exists_default_columns` |
| GROUP_CONCAT separator/order syntax | `select group_concat(c order by ts desc separator '、') from app.t group by k` | `group_concat_separator`, `group_concat_order_separator_lineage` |
| GROUP BY WITH ROLLUP | `select region, count(*) from app.orders group by region with rollup` | `group_by_with_rollup` |
| GROUP BY direction with ROLLUP | `select region, count(*) from app.orders group by region desc with rollup` | `group_by_direction_rollup` |
| ORDER BY ordinal position | `select id as user_id from app.users order by 1 desc limit 10` | `order_by_position` |
| Tuple predicates | `where (user_id, product_id) in ((1, 100), ...)`, `where (a, b) = (c, d)` | `tuple_in_predicate_column_usage`, `tuple_comparison_predicate` |
| INSERT INTO SELECT target and source | `insert into mart.t(c1) select a from app.s` | `insert_select` |
| INSERT IGNORE SELECT target and source | `insert ignore into mart.t(c1) select a from app.s` | `insert_ignore_select` |
| INSERT priority modifier target and source | `insert low_priority ignore into mart.t(c1) select a from app.s` | `insert_low_priority_ignore_select` |
| INSERT SELECT with ORDER BY/LIMIT | `insert into mart.t(c1) select a from app.s order by a limit 100` | `insert_select_order_limit` |
| INSERT SELECT with EXISTS predicate | `insert into mart.t select ... from app.s where exists (select 1 from app.d where ...)` | `insert_select_exists_subquery` |
| INSERT SELECT with CTE after target | `insert into mart.t (...) with q as (...) select ... from q` | `insert_select_with_cte_after_target` |
| INSERT target partition and source | `insert into mart.t partition (p1) (c1) select a from app.s` | `insert_partition_select` |
| INSERT delayed VALUES constant column lineage | `insert delayed into mart.t(c1) values (...)` | `insert_delayed_values` |
| INSERT INTO VALUES/VALUE constant column lineage | `insert into mart.t(c1) values (...)`, `insert into mart.t(c1) value (...)` | `insert_values`, `insert_value_single_row` |
| INSERT VALUES scalar subquery column lineage | `insert into mart.t(c1) values ((select max(a) from app.s))` | `insert_values_scalar_subquery` |
| INSERT VALUES row alias with duplicate-key update | `insert into mart.t(c1) values (...) as new on duplicate key update c1 = new.c1`, `values ((select ...)) as new on duplicate key update c1 = new.c1` | `insert_values_alias_on_duplicate`, `insert_values_alias_on_duplicate_scalar_subquery` |
| INSERT VALUES ROW constructor constant column lineage | `insert into mart.t(c1) values row(...), row(...)` | `insert_values_row_constructor` |
| INSERT/REPLACE empty VALUES rows | `insert into mart.t () values ()`, `replace into mart.t values ()` | `insert_empty_values`, `replace_empty_values` |
| INSERT SET target and constant/subquery column lineage | `insert into mart.t set c1 = ...`, `set c1 = (select ... from app.s)`, `set ... as new(c1_alias) on duplicate key update c = new.c1_alias`, `set ... on duplicate key update c = values(c)` | `insert_set`, `insert_set_scalar_subquery`, `insert_set_row_alias_on_duplicate`, `insert_set_on_duplicate` |
| INSERT SELECT with duplicate-key update | `insert into mart.t(c1) select a from app.s on duplicate key update ...` | `insert_select_on_duplicate` |
| INSERT SELECT with duplicate-key aggregate update | `insert into mart.t(c1, c2) select a, sum(b) from app.s group by a on duplicate key update ...` | `insert_select_on_duplicate_key_update` |
| WITH before INSERT SELECT | `with q as (...) insert into mart.t(c1) select q.c1 from q`, `with q as (...) insert ... where exists (...)` | `with_insert_select`, `with_insert_exists_subquery` |
| WITH before REPLACE SELECT | `with q as (...) replace into mart.t(c1) select q.c1 from q` | `with_replace_select` |
| REPLACE INTO SELECT target and source | `replace into mart.t(c1) select a from app.s` | `replace_select` |
| REPLACE priority modifier target and source | `replace low_priority into mart.t(c1) select a from app.s` | `replace_low_priority_select` |
| REPLACE SELECT with ORDER BY/LIMIT | `replace into mart.t(c1) select a from app.s order by a limit 100` | `replace_select_order_limit` |
| REPLACE target partition and source | `replace into mart.t partition (p1) (c1) select a from app.s` | `replace_partition_select` |
| REPLACE INTO VALUES/VALUE constant column lineage | `replace into mart.t(c1) values (...)`, `replace into mart.t(c1) value (...)` | `replace_values`, `replace_value_single_row` |
| REPLACE VALUES scalar subquery column lineage | `replace into mart.t(c1) values ((select max(a) from app.s))` | `replace_values_scalar_subquery` |
| REPLACE SET target and constant/subquery column lineage | `replace into mart.t set c1 = ...`, `set c1 = (select ... from app.s)` | `replace_set`, `replace_set_scalar_subquery` |
| LOAD DATA target table | `load data local infile '...' into table mart.t` | `load_data_local_infile` |
| LOAD DATA options target table | `load data infile '...' into table mart.t fields ... lines ... ignore ...` | `load_data_with_options` |
| LOAD DATA FIELDS/COLUMNS synonym options | `load data infile ... into table mart.t columns terminated by ...` | `load_data_columns_options` |
| LOAD DATA SET target and constant column lineage | `load data infile '...' into table mart.t (...) set loaded_at = now()` | `load_data_set_assignments` |
| LOAD DATA optionally enclosed fields and variables | `load data infile ... fields optionally enclosed by ... (@v, c) set id = cast(@v as unsigned)` | `load_data_optionally_enclosed_variables` |
| LOAD DATA priority/partition/charset options | `load data low_priority infile ... replace into table app.t partition (...) character set ... ignore 1 rows (...) set ...` | `load_data_replace_partition_charset` |
| LOAD DATA concurrent local ignore | `load data concurrent local infile ... ignore into table ods.t (...)` | `load_data_concurrent_ignore` |
| LOAD XML target table and rows tag | `load xml local infile ... into table ods.t rows identified by '<row>' (...)` | `load_xml_rows_identified`, `load_xml_set_assignments` |
| CREATE TABLE AS SELECT | `create table mart.t as select ... from app.s` | `create_table_as_select` |
| CREATE TABLE SELECT without AS | `create table mart.t select ... from app.s` | `create_table_select_without_as` |
| CREATE TABLE IGNORE/REPLACE SELECT | `create table mart.t ignore select ... from app.s`, `create table mart.t replace select ...` | `create_table_ignore_select`, `create_table_replace_select` |
| CREATE TABLE options before AS SELECT | `create table mart.t (...) engine=InnoDB default charset=utf8mb4 as select ...` | `create_table_options_as_select` |
| CREATE TABLE storage/statistics/engine options | `create table mart.t (...) data directory ... index directory ... stats_persistent ...`, `checksum ... connection ... engine_attribute ...` | `create_table_directory_stats_options`, `create_table_engine_attribute_options` |
| CREATE TABLE declared columns AS SELECT | `create table mart.t(c1 ...) as select a as other_name from app.s` | `create_table_declared_columns_as_select` |
| CREATE TABLE schema DDL with constraints | `create table mart.t (id bigint auto_increment, primary key (id), unique key uk_c (c), constraint uk_c unique key (c), index idx_c (c))` | `create_table_constraints`, `create_table_named_unique_constraint` |
| CREATE TABLE foreign key reference | `create table mart.child (..., c bigint references mart.parent(id) on delete ..., foreign key [index_name] (...) references mart.parent (...) on update ...)` | `create_table_foreign_key_reference`, `create_table_foreign_key_actions`, `create_table_named_foreign_key_index` |
| CREATE TABLE generated columns and ON UPDATE constraints | `create table mart.t (full_name varchar(...) generated always as (...), updated_at ... on update ...)` | `create_table_generated_columns` |
| CREATE TABLE advanced table options | `create table mart.t (...) row_format=compressed key_block_size=8 compression='zlib' tablespace ts`, `auto_increment=... avg_row_length=... insert_method=... union=(...)` | `create_table_advanced_options`, `create_table_storage_options` |
| CREATE TABLE MySQL data types and type attributes | `create table mart.t (status enum('A','B'), id bigint unsigned, name varchar(...) character set utf8mb4 collate ..., point_col point srid 4326, score double precision, name national varchar(...))` | `create_table_enum_set_types`, `create_table_mysql_type_attributes`, `create_table_column_storage_attributes`, `create_table_compound_type_names` |
| CREATE TABLE CHECK and invisible attributes | `create table mart.t (c int constraint chk check (...) enforced, index idx(c) invisible)` | `create_table_check_invisible`, `create_table_column_check_enforced` |
| CREATE TABLE index definitions with MySQL options | `create table mart.t (..., fulltext index idx(c) with parser ngram, unique key ... comment ... visible, spatial index idx(g))` | `create_table_fulltext_spatial_index`, `create_table_index_options`, `create_table_fulltext_index_options` |
| CREATE TEMPORARY TABLE schema DDL | `create temporary table if not exists mart.t (...) engine=InnoDB` | `create_temporary_table_schema` |
| CREATE TABLE partition DDL | `create table mart.t (...) partition by hash/range (...)`, `partition by range/list columns (...)`, `partition by linear key algorithm = 2 (...)`, `subpartition by hash (...)` | `create_table_hash_partition`, `create_table_range_partition`, `create_table_range_columns_partition`, `create_table_list_columns_partition`, `create_table_linear_key_algorithm_partition`, `create_table_subpartition` |
| CREATE TABLE partition AS SELECT | `create table mart.t (...) partition by hash (...) as select ...` | `create_table_partition_as_select` |
| CREATE TABLE AS WITH SELECT | `create table mart.t as with q as (...) select ... from q` | `create_table_as_with_select` |
| CREATE TABLE LIKE structure lineage | `create table mart.t like app.s` | `create_table_like` |
| CREATE TEMPORARY TABLE LIKE structure lineage | `create temporary table tmp like app.s` | `create_temporary_table_like` |
| Script-local temporary table source propagation | `create temporary table tmp as select ...; insert ... select ... from tmp` | `script_temp_table_lineage` |
| Temporary table drop lifecycle | `create temporary table tmp as ...; drop table tmp; select ... from tmp` | `script_drop_temp_table` |
| CREATE VIEW AS SELECT | `create view mart.v as select ... from app.s join app.o` | `create_view` |
| CREATE OR REPLACE VIEW AS SELECT | `create or replace view mart.v as select ... from app.s` | `create_or_replace_view` |
| CREATE VIEW declared column list | `create view mart.v(c1, c2) as select a as x, b as y from app.s` | `create_view_declared_columns` |
| CREATE VIEW with MySQL options | `create algorithm=merge sql security invoker view mart.v as select ... with check option` | `create_view_with_options` |
| CREATE VIEW with DEFINER | `create definer='u'@'%' ...`; `create definer=current_user() ...` | `create_view_with_definer`, `create_view_current_user_definer` |
| CREATE VIEW AS WITH SELECT | `create view mart.v as with q as (...) select ... from q` | `create_view_as_with_select` |
| ALTER VIEW AS SELECT | `alter algorithm=merge view mart.v(c1) as select ...` | `alter_view` |
| CREATE TEMPORARY TABLE AS SELECT | `create temporary table if not exists mart.t as select ...` | `create_temporary_table_as_select` |
| UPDATE JOIN table lineage | `update mart.t join app.s on ... set ...` | `update_join` |
| UPDATE LOW_PRIORITY IGNORE | `update low_priority ignore mart.t join app.s ... set ...` | `update_low_priority_ignore` |
| Multi-table UPDATE lineage | `update mart.t, app.s set ... where ...` | `update_multi_table` |
| Multi-table UPDATE target lineage | `update mart.t join app.s on ... set t.c = s.c, s.synced_at = t.updated_at` | `update_multi_table_targets` |
| UPDATE JOIN over derived query | `update mart.t join (select ... from app.s) q on ... set ...` | `update_join_derived_assignment` |
| UPDATE ORDER BY LIMIT | `update mart.t set c = ... where ... order by ... limit ...` | `update_order_by_limit` |
| UPDATE WHERE EXISTS | `update mart.t t set ... where exists (select 1 from app.s where s.id = t.id) order by ... limit ...` | `update_where_exists_subquery` |
| WITH before UPDATE JOIN/EXISTS | `with q as (...) update mart.t join q on ... set ...`, `with q as (...) update ... where exists (...)` | `with_update_join`, `with_update_exists_subquery` |
| DELETE USING table lineage | `delete from mart.t using mart.t join app.s ...`, `delete from t1, t2 using t1 join t2 ...` | `delete_using`, `delete_from_targets_using` |
| DELETE LOW_PRIORITY QUICK IGNORE | `delete low_priority quick ignore from mart.t where ...` | `delete_low_priority_quick_ignore` |
| DELETE ORDER BY LIMIT | `delete from mart.t where ... order by ... limit ...` | `delete_order_by_limit` |
| DELETE WHERE EXISTS | `delete from mart.t t where exists (select 1 from app.s where s.id = t.id) order by ... limit ...` | `delete_where_exists_subquery` |
| DELETE alias FROM JOIN table lineage | `delete t from mart.t t join app.s s ...` | `delete_join` |
| Multi-table DELETE lineage | `delete t1, t2 from mart.t1 join app.t2 ...`, `delete t1.*, t2.* from ...` | `delete_multi_table`, `delete_multi_table_star_targets` |
| DELETE alias FROM derived JOIN table lineage | `delete t from mart.t t join (select ... from app.s) q ...` | `delete_join_derived` |
| WITH before DELETE alias FROM JOIN/EXISTS | `with q as (...) delete t from mart.t t join q ...`, `with q as (...) delete ... where exists (...)` | `with_delete_join`, `with_delete_exists_subquery` |
| Backquoted non-ASCII identifiers | `` select `用户ID` from `业务库`.`用户表` `` | `backquoted_identifiers` |
| Template variable identifiers | `from ${source_schema}.${source_table}` | `template_variable_identifier_lineage` |
| Template variable write targets | `insert into ${target_schema}.${target_table} select ... from ${source_schema}.${source_table}` | `insert_select_template_identifier_lineage` |
| DROP TABLE affected table | `drop table if exists mart.t` | `drop_table` |
| DROP TABLE multiple affected tables | `drop table if exists mart.t1, mart.t2` | `drop_multiple_tables` |
| DROP TEMPORARY TABLE affected table | `drop temporary table if exists tmp_users` | `drop_temporary_table` |
| DROP VIEW affected view | `drop view if exists mart.v` | `drop_view` |
| DROP VIEW multiple affected views | `drop view if exists mart.v1, mart.v2` | `drop_multiple_views` |
| TRUNCATE TABLE affected table | `truncate table ads.t` | `truncate_table` |
| ALTER TABLE RENAME TO old and new tables | `alter table mart.old rename to mart.new` | `rename_table` |
| RENAME TABLE old and new tables | `rename table app.old to app.new`, `rename table a.old to a.new, b.old to b.new` | `rename_table_statement`, `rename_multiple_tables` |
| ALTER TABLE column maintenance | `alter table mart.t add column c int`, `add column parent_id bigint references mart.parent(id) on delete ...` | `alter_table_add_column`, `alter_table_add_column_reference` |
| ALTER TABLE drop column maintenance | `alter table mart.t drop column c` | `alter_table_drop_column` |
| ALTER TABLE multiple comma-separated actions | `alter table mart.t add column c int, modify column d varchar(32), drop column e` | `alter_table_multiple_actions` |
| ALTER TABLE online DDL options | `alter table mart.t add column c int, algorithm=inplace, lock=none` | `alter_table_online_options` |
| ALTER TABLE index maintenance | `alter table mart.t add index idx_c (c)`, `add fulltext index ... with parser ...`, `add spatial index ... invisible` | `alter_table_add_index`, `alter_table_add_fulltext_index_options`, `alter_table_add_spatial_index` |
| ALTER TABLE change column maintenance | `alter table mart.t change column old_c new_c varchar(128)`, `change column old_c new_c varchar(...) null default ... comment ... after c` | `alter_table_change_column`, `alter_table_change_column_attributes` |
| ALTER TABLE modify column maintenance | `alter table mart.t modify column c varchar(256)`, `modify column c varchar(...) not null default ... comment ... after c` | `alter_table_modify_column`, `alter_table_modify_column_attributes` |
| ALTER TABLE rename column maintenance | `alter table mart.t rename column old_c to new_c` | `alter_table_rename_column` |
| ALTER TABLE primary key maintenance | `alter table mart.t add/drop primary key` | `alter_table_add_primary_key`, `alter_table_drop_primary_key` |
| ALTER TABLE index lifecycle maintenance | `alter table mart.t drop index idx_c`, `alter table mart.t rename index idx_old to idx_new`, `alter table mart.t alter index idx invisible` | `alter_table_drop_index`, `alter_table_rename_index`, `alter_table_alter_index_visibility` |
| ALTER TABLE check constraint maintenance | `alter table mart.t add constraint chk check (...) enforced`, `drop check ...`, `alter check ... not enforced` | `alter_table_add_check_enforced`, `alter_table_drop_check`, `alter_table_alter_check_not_enforced` |
| ALTER TABLE foreign key reference | `alter table mart.child add foreign key [index_name] (...) references mart.parent (...) on delete ... on update ...`, `drop foreign key fk_name` | `alter_table_add_foreign_key`, `alter_table_add_foreign_key_actions`, `alter_table_add_named_foreign_key_index`, `alter_table_drop_foreign_key` |
| ALTER TABLE column default maintenance | `alter table mart.t alter column c set default ...`, `alter column c drop default` | `alter_table_alter_column_default` |
| ALTER TABLE charset and key maintenance | `alter table mart.t convert to character set utf8mb4 collate ...`, `alter table mart.t default character set ... collate ...`, `alter table mart.t disable keys` | `alter_table_convert_charset`, `alter_table_default_charset_collate`, `alter_table_disable_keys` |
| ALTER TABLE storage/order maintenance | `alter table mart.t order by c`, `alter table mart.t force`, `alter table mart.t discard/import tablespace` | `alter_table_order_by`, `alter_table_force`, `alter_table_tablespace_import_discard` |
| ALTER TABLE partition maintenance | `alter table app.users add/drop/rebuild/coalesce/reorganize/remove partition...` | `alter_table_add_partition`, `alter_table_drop_partition`, `alter_table_rebuild_partition`, `alter_table_coalesce_partition`, `alter_table_reorganize_partition`, `alter_table_remove_partitioning` |
| ALTER TABLE partition exchange | `alter table app.users exchange partition p with table staging.users_p with/without validation` | `alter_table_exchange_partition`, `alter_table_exchange_partition_with_validation` |
| CREATE INDEX affected table | `create index idx on mart.t(c)`, `create fulltext index idx on mart.t(c)`, `create index idx on mart.t(name(32) desc)`, `create index idx on mart.t((lower(c)))`, `create index idx on mart.t((cast(json_col->'$.ids' as unsigned array)))` | `create_index`, `create_fulltext_index`, `create_index_prefix_order`, `create_index_expression`, `create_index_multivalued_array` |
| CREATE INDEX options | `create index idx using btree on mart.t(c) visible algorithm=inplace lock=none`, `create index idx on mart.t(c) engine_attribute='...'`, `create fulltext index idx on cms.t(body) with parser ngram comment '...' invisible` | `create_index_using_options`, `create_index_engine_attributes`, `create_fulltext_index_with_parser` |
| DROP INDEX affected table | `drop index idx on mart.t`, `drop index idx on mart.t algorithm=inplace lock=none` | `drop_index`, `drop_index_online_options` |
| CREATE TRIGGER affected table | `create trigger trg before insert on mart.t for each row ...` | `create_trigger` |
| EXPLAIN/DESCRIBE wrapped SELECT lineage | `explain select ... from app.s where ...`, `describe select ... from app.s` | `explain_select`, `describe_select_lineage` |
| EXPLAIN FORMAT/ANALYZE wrapped SELECT lineage | `explain format=json select ...`, `explain analyze select ...` | `explain_format_json_select`, `explain_analyze_select` |
| EXPLAIN connection diagnostic metadata read | `explain format=json for connection 12345` | `explain_for_connection` |
| USE database session statement | `use mart` | `use_database` |
| SHOW CREATE TABLE metadata read | `show create table mart.t` | `show_create_table` |
| SHOW CREATE VIEW metadata read | `show create view mart.v` | `show_create_view` |
| SHOW CREATE DATABASE metadata read | `show create database app` | `show_create_database` |
| SHOW CREATE routine, trigger, and event metadata read | `show create procedure app.p`, `show create function app.f`, `show create trigger app.trg`, `show create event app.ev` | `show_create_procedure`, `show_create_function`, `show_create_trigger`, `show_create_event` |
| SHOW COLUMNS metadata read | `show columns from mart.t`, `show extended full columns from t from mart like ...` | `show_columns_from_table`, `show_full_columns_from_schema_table` |
| SHOW INDEX metadata read | `show index from mart.t`, `show keys in t in mart where ...` | `show_index_from_table`, `show_keys_from_schema_table` |
| Schema-level and server metadata reads | `show full tables from mart`, `show open tables from mart like ...`, `show table status from mart like ...`, `show databases`, `show variables`, `show processlist`, `show engines` | `show_tables_from_database`, `show_open_tables_like`, `show_table_status_from_schema`, `show_databases`, `show_variables`, `show_processlist`, `show_engines` |
| Routine/event metadata reads | `show triggers from mart like ...`, `show events from mart where ...`, `show procedure status where ...` | `show_triggers_from_schema`, `show_events_from_schema`, `show_procedure_function_status` |
| Server variable/status metadata filters | `show global variables like ...`, `show session status where ...` | `show_global_variables_like`, `show_session_status_where` |
| ANALYZE TABLE metadata read | `analyze table mart.t` | `analyze_table` |
| ANALYZE TABLE histogram metadata read | `analyze table mart.t update/drop histogram on c`, `analyze table mart.t update histogram ... using data '...'`, `analyze no_write_to_binlog table mart.t update histogram ...` | `analyze_table_update_histogram`, `analyze_table_histogram_using_data`, `analyze_table_drop_histogram`, `analyze_no_write_histogram` |
| CHECK TABLE metadata read | `check table mart.t`, `check table mart.t for upgrade extended` | `check_table`, `check_table_for_upgrade_extended` |
| CHECKSUM TABLE metadata read | `checksum table mart.t extended`, `checksum table app.t1, app.t2 quick` | `checksum_table_extended`, `checksum_multiple_tables_quick` |
| Replication metadata reads | `show binary logs`, `show master status`, `show replica status` | `show_binary_logs`, `show_master_status`, `show_replica_status` |
| Multi-table metadata maintenance reads | `analyze table app.t1, app.t2`, `check table app.t1, app.t2 for upgrade` | `analyze_multiple_tables`, `check_multiple_tables` |
| OPTIMIZE TABLE maintenance read | `optimize table mart.t`, `optimize local table mart.t` | `optimize_table`, `optimize_local_table` |
| REPAIR TABLE maintenance read | `repair table mart.t`, `repair no_write_to_binlog table mart.t quick use_frm` | `repair_table`, `repair_no_write_quick_use_frm` |
| DESCRIBE TABLE metadata read | `describe table mart.t` | `describe_table` |
| EXPLAIN table metadata read | `explain table mart.users name` | `explain_table_metadata` |
| LOCK TABLES control statement | `lock tables app.users read, mart.user_summary write`, `lock tables app.users as u read local, mart.t low_priority write` | `lock_tables`, `lock_tables_alias_low_priority` |
| SET user variable scalar subquery read | `set @v = (select max(id) from app.t where ...)` | `set_subquery_variable` |
| MySQL executable version comments | `/*!80000 select ... */`, `/*!40101 set names utf8mb4 */` | `executable_comment_select_lineage`, `executable_comment_set_statement` |
| CREATE EVENT parseable DML body lineage | `create event ... do delete/insert ...`; `every ... starts ... ends ... on completion ... enable comment ...` | `create_event`, `create_event_insert_select`, `create_event_schedule_options` |
| MySQL control, account, admin, routine, trigger, event, and dynamic SQL statements | `unlock tables`, `set session ...`, `set names ... collate ...`, `set character set ...`, `start transaction`, `set [global/session] transaction isolation level ...`, `commit`, `rollback`, `savepoint`, `rollback to savepoint`, `release savepoint`, `xa start/end/prepare/commit/rollback/recover`, `do sleep(1)`, `call p()`, `prepare`, `execute`, `deallocate prepare`, `create procedure`, `drop procedure`, `alter procedure`, `create trigger`, `drop trigger`, `create event`, `alter event`, `drop event`, `create/alter/drop user`, `show create user`, `create/drop/set role`, `grant`, `revoke`, `flush`, `kill`, `reset`, `lock/unlock instance`, `clone local`, `clone instance from donor`, `change replication source`, `change master`, `start/stop/reset replica` | `unlock_tables`, `set_session_statement`, `set_names_collate`, `set_character_set`, `start_transaction`, `set_transaction_read_only`, `set_transaction_isolation_repeatable_read`, `set_transaction_isolation_read_committed`, `commit_statement`, `rollback_statement`, `savepoint_statement`, `rollback_to_savepoint`, `release_savepoint`, `xa_start`, `xa_end_suspend`, `xa_prepare`, `xa_commit_one_phase`, `xa_rollback`, `xa_recover`, `do_statement`, `call_statement`, `prepare_statement`, `execute_statement`, `deallocate_prepare`, `create_procedure`, `drop_procedure`, `alter_procedure`, `create_trigger`, `drop_trigger`, `create_event`, `create_event_insert_select`, `alter_event`, `drop_event`, `create_user`, `create_user_account_options`, `alter_user_account_unlock`, `drop_user_if_exists`, `show_create_user`, `create_role`, `drop_role`, `set_role`, `grant_privileges`, `revoke_privileges`, `flush_privileges`, `kill_query`, `reset_master`, `lock_instance_for_backup`, `unlock_instance`, `clone_local_data_directory`, `clone_instance_from_donor`, `change_replication_source`, `change_master_to`, `start_replica`, `stop_slave`, `reset_replica_all` |
| MySQL control expressions with scalar subqueries | `call app.p((select max(ts) from app.users))`, `do (select count(*) from app.orders)` | `call_scalar_subquery_input`, `do_scalar_subquery_input` |
| MySQL schema/database control DDL | `create database ...`, `drop schema ...`, `alter database ... default character set ...`, `alter schema default encryption = ...` | `create_database`, `drop_schema`, `alter_database_charset`, `alter_schema_encryption` |
| MySQL tablespace control DDL | `create tablespace ts add datafile ... engine=...`, `create undo tablespace ...`, `drop tablespace ...` | `create_tablespace`, `create_undo_tablespace`, `drop_tablespace` |
| MySQL plugin/component and federated server control | `install plugin ... soname ...`, `uninstall component ...`, `create/alter/drop server ... options (...)` | `install_plugin`, `uninstall_component`, `create_server`, `alter_server`, `drop_server` |
| MySQL resource group control | `create/alter/drop resource group ...`, `set resource group ... for ...` | `create_resource_group`, `alter_resource_group`, `drop_resource_group`, `set_resource_group` |

Implemented MySQL column-level lineage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Direct single-table projection | `select id as user_id, name from app.users` | `select_basic` |
| MySQL SELECT option projection | `select high_priority sql_calc_found_rows id as user_id from app.users` | `select_with_mysql_options` |
| Alias-qualified JOIN projection | `select u.id, o.amount from users u join orders o` | `join_projection` |
| NATURAL JOIN projection | `select u.id, o.amount from users u natural join orders o` | `natural_join_projection` |
| STRAIGHT_JOIN projection | `select straight_join u.id, o.amount from users u straight_join orders o` | `select_straight_join` |
| Index hint projection | `select u.id from users u force index (...)` | `select_force_index` |
| Partition-qualified table references | `select ... from app.t partition(p1)`, `update app.t partition(p1) set ...`, `delete from app.t partition(p1) where ...` | `select_partition_table`, `update_partition_table`, `delete_partition_table` |
| Backtick-qualified direct projections | ``select u.`department_id` from app.users u`` | `backtick_qualified_direct_projection` |
| Case-insensitive derived column propagation | `select user_id from (select id as User_ID from app.users) u` | `derived_case_insensitive_column_projection` |
| Single-level aliased subquery direct propagation | `select q.c from (select a as c from app.s) q` | `subquery_column_projection` |
| Derived table column alias list propagation | `select q.c1 from (select a from app.s) as q(c1)` | `derived_table_column_aliases` |
| Derived table aliases in outer expressions | `select concat(q.c1, q.c2) from (select a as c1, b as c2 from app.s) q where q.c2 = ...` | `derived_alias_expression_projection` |
| Single CTE direct propagation | `with q as (select a as c from app.s) select c from q` | `cte_column_projection` |
| Recursive CTE base-column propagation | `with recursive q as (...) select id from q` | `with_recursive_select` |
| UNION column sources merged by position | `select a as c1 from s1 union all select b from s2` | `union_column_projection` |
| INTERSECT column sources merged by position | `select a as c1 from s1 intersect select b from s2` | `intersect_column_projection` |
| EXCEPT column sources merged by position | `select a as c1 from s1 except select b from s2` | `except_column_projection` |
| DISTINCT set operation column sources merged by position | `select a from s1 union distinct select b from s2`, `select a from s1 intersect distinct select b from s2` | `union_distinct_column_projection`, `intersect_distinct_column_projection` |
| INSERT target column list mapping | `insert into mart.t(c1, c2) select a, b from app.s` | `insert_select` |
| INSERT from TABLE statement source | `insert into mart.t table app.s order by id limit 100` | `insert_table_statement` |
| INSERT over UNION ALL target column lineage | `insert into t(c1) select a from s1 union all select b from s2` | `insert_union_column_lineage` |
| INSERT over INTERSECT target column lineage | `insert into t(c1) select a from s1 intersect select b from s2` | `insert_intersect_column_lineage` |
| INSERT over EXCEPT target column lineage | `insert into t(c1) select a from s1 except select b from s2` | `insert_except_column_lineage` |
| INSERT IGNORE target column list mapping | `insert ignore into mart.t(c1, c2) select a, b from app.s` | `insert_ignore_select` |
| INSERT priority modifier target mapping | `insert low_priority ignore into mart.t(c1, c2) select a, b from app.s` | `insert_low_priority_ignore_select` |
| INSERT SELECT ORDER BY/LIMIT target mapping | `insert into mart.t(c1, c2) select a, b from app.s order by b limit ...` | `insert_select_order_limit` |
| INSERT SELECT EXISTS target mapping | `insert into mart.t(c1, c2) select a, b from app.s where exists (...)` | `insert_select_exists_subquery` |
| INSERT partition target column mapping | `insert into mart.t partition (p1) (c1, c2) select a, b from app.s` | `insert_partition_select` |
| INSERT duplicate-key SELECT and update mapping | `insert into mart.t(c1) select a from app.s on duplicate key update c1 = values(c1)` | `insert_select_on_duplicate` |
| INSERT duplicate-key expression mapping | `insert into mart.t(c1) select a from app.s on duplicate key update c1 = c1 + values(c1)`, `insert into mart.t set c1 = ... on duplicate key update c2 = values(c1)`, `insert into mart.t set c1 = ... as new(c1_alias) on duplicate key update c2 = new.c1_alias` | `insert_select_on_duplicate_expression`, `insert_set_on_duplicate`, `insert_set_row_alias_on_duplicate` |
| INSERT duplicate-key DEFAULT mapping | `insert into mart.t(c1) select a from app.s on duplicate key update c2 = default`, `... update c2 = default(c2)` | `insert_select_on_duplicate_default`, `insert_values_on_duplicate_default` |
| INSERT VALUES row alias duplicate-key expression mapping | `insert into mart.t(c1) values (...) as new on duplicate key update c1 = concat(c1, new.c1)` | `insert_values_alias_on_duplicate_expression`, `insert_values_column_alias_on_duplicate` |
| WITH before INSERT SELECT target mapping | `with q as (...) insert into mart.t(c1) select q.c1 from q`, `with q as (...) insert ... where exists (...)` | `with_insert_select`, `with_insert_exists_subquery` |
| WITH before REPLACE SELECT target mapping | `with q as (...) replace into mart.t(c1) select q.c1 from q` | `with_replace_select` |
| REPLACE SELECT target column list mapping | `replace into mart.t(c1, c2) select a, b from app.s` | `replace_select` |
| REPLACE priority modifier target mapping | `replace low_priority into mart.t(c1, c2) select a, b from app.s` | `replace_low_priority_select` |
| REPLACE SELECT ORDER BY/LIMIT target mapping | `replace into mart.t(c1, c2) select a, b from app.s order by b limit ...` | `replace_select_order_limit` |
| REPLACE partition target column mapping | `replace into mart.t partition (p1) (c1, c2) select a, b from app.s` | `replace_partition_select` |
| CTAS output column targets | `create table mart.t as select id as c1 from app.s` | `create_table_as_select` |
| CTAS over aliased/expression/aggregate projections | `create table mart.t as select a as c1, upper(b), count(c) ...` | `ctas_expression_projection` |
| CTAS with table options output column targets | `create table mart.t (...) engine=InnoDB as select id as c1 from app.s` | `create_table_options_as_select` |
| CTAS declared-column target names | `create table mart.t(c1 ...) as select a as other_name from app.s` | `create_table_declared_columns_as_select` |
| Partitioned CTAS output column targets | `create table mart.t (...) partition by hash (...) as select id as c1 from app.s` | `create_table_partition_as_select` |
| CTAS over CTE output column targets | `create table mart.t as with q as (...) select c1 from q` | `create_table_as_with_select` |
| CTAS over UNION ALL output column targets | `create table mart.t as select a as c1 from s1 union all select b from s2` | `ctas_union_column_lineage` |
| CTAS declared-column targets over UNION ALL | `create table mart.t(c1 ...) as select a from s1 union all select b from s2` | `ctas_declared_columns_union_lineage` |
| Script-local temporary table column propagation | `create temporary table tmp as select id as c1 from app.s; insert into mart.t(c1) select c1 from tmp` | `script_temp_table_lineage` |
| CREATE VIEW output column targets | `create view mart.v as select u.id from app.users u` | `create_view` |
| CREATE VIEW declared-column target names | `create view mart.v(c1, c2) as select a as x, b as y from app.s` | `create_view_declared_columns` |
| CREATE VIEW over aliased/expression/aggregate projections | `create view mart.v as select a as c1, upper(b), count(c) ...` | `create_view_expression_projection` |
| CREATE VIEW options output column targets | `create algorithm=merge sql security invoker view mart.v as select id as c1 from app.s` | `create_view_with_options` |
| CREATE VIEW DEFINER output column targets | `create definer='u'@'%' view mart.v as select id as c1 from app.s` | `create_view_with_definer` |
| CREATE VIEW over CTE output column targets | `create view mart.v as with q as (...) select c1 from q` | `create_view_as_with_select` |
| CREATE VIEW over UNION ALL output column targets | `create view mart.v as select a as c1 from s1 union all select b from s2` | `create_view_union_column_lineage` |
| CREATE VIEW declared-column targets over UNION ALL | `create view mart.v(c1, c2) as select a, b from s1 union all select x, y from s2` | `create_view_declared_columns_union_lineage` |
| ALTER VIEW output column targets | `alter view mart.v(c1, c2) as select a, b from app.s` | `alter_view` |
| MySQL function expression lineage | `select ifnull(nickname, name), coalesce(phone, email) from app.s` | `mysql_function_expression_projection` |
| MySQL conditional function lineage | `select if(flag, c1, c2), nullif(c3, c4) from app.s` | `if_nullif_expression_lineage` |
| MySQL special function syntax lineage | `extract(year from ts)`, `trim(both ' ' from name)`, `position('@' in email)`, `substring(phone from 1 for 3)` | `mysql_special_function_syntax` |
| MySQL CONVERT special syntax lineage | `convert(name using utf8mb4)`, `convert(amount, decimal(10,2))` | `convert_function_syntax` |
| MySQL CAST signedness type lineage | `cast(amount as signed integer)`, `cast(score as unsigned)` | `cast_signed_unsigned_lineage` |
| MySQL CAST time-zone conversion lineage | `cast(event_ts at time zone timezone_name as datetime)` | `cast_at_time_zone_lineage` |
| MySQL interval function lineage | `date_add(created_at, interval 7 day)`, `date_sub(updated_at, interval retry_count hour)` | `interval_function_lineage` |
| MySQL timestamp unit function lineage | `timestampadd(day, retry_count, created_at)`, `timestampdiff(hour, created_at, updated_at)` | `timestampadd_timestampdiff_lineage` |
| MySQL format type function lineage | `str_to_date(date_text, get_format(date, 'USA'))` | `get_format_type_argument_lineage` |
| MySQL full-text search expression lineage | `select match(title, body) against (... in boolean mode) from cms.articles where match(...) against (... with query expansion)` | `select_match_against_boolean` |
| MySQL JSON arrow expression lineage | `select payload->>'$.id' from app.events` | `json_extract_expression` |
| MySQL JSON_VALUE expression lineage | `json_value(payload, '$.vin' returning char(...) default ... on empty null on error)` | `json_value_returning_lineage` |
| MySQL JSON aggregate function lineage | `json_arrayagg(json_object(...) order by created_at)`, `json_objectagg(k, v)` | `json_aggregate_function_lineage` |
| MySQL JSON MEMBER OF predicate usage | `where user_id member of(payload->'$.ids')` | `json_member_of_predicate` |
| MySQL JSON predicate function usage | `where json_contains(payload, json_quote(sku), '$.skus') and json_overlaps(a, b)` | `json_contains_predicate_usage`, `json_overlaps_predicate_usage` |
| MySQL JSON mutation and validation functions | `json_set(payload, '$.k', value)`, `json_merge_patch(a, b)`, `json_search(...)`, `json_schema_valid(schema, doc)`, `json_length(doc, path)` | `json_mutation_function_lineage`, `json_search_schema_predicate_usage` |
| MySQL JSON utility functions | `json_pretty(payload)`, `json_type(payload)`, `json_storage_size(payload)` | `json_utility_function_lineage` |
| MySQL REGEXP_* and FIND_IN_SET function lineage | `regexp_like(c, p)`, `regexp_replace(c, p, r)`, `find_in_set(c, list)` | `regexp_function_predicate_usage` |
| MySQL numeric and enum-position function lineage | `interval(score, ...)`, `field(status, ...)`, `elt(priority, ...)` | `interval_field_elt_lineage` |
| MySQL string function lineage | `substring_index(email, '@', -1)`, `locate('-', sku_code)`, `insert(phone, ...)`, `repeat(prefix, retry_count)` | `string_function_lineage` |
| MySQL string function special syntax | `char(ascii_code using utf8mb4)`, `concat_ws('-', region, char(code using utf8mb4))` | `char_using_function_lineage` |
| MySQL date/time utility functions | `adddate(created_at, interval grace_days day)`, `period_diff(close_period, open_period)` | `date_time_function_lineage` |
| MySQL date interval arithmetic | `created_at + interval grace_days day`, `expired_at - interval retry_hours hour` | `date_interval_arithmetic_lineage` |
| MySQL BINARY and COLLATE expression lineage | `select binary name from app.users where name collate ...` | `binary_collate_expression` |
| MySQL DIV, MOD, and bitwise expression lineage | `select amount div quantity, score mod 10, flags & 4 from app.orders`, `select ~flags, flags | mask from app.order_flags` | `div_mod_bit_expression`, `bitwise_unary_expression` |
| Keyword-like production column names | `select a.type, a.group from app.accounts a where a.type = ...` | `keyword_columns_type_group` |
| Keyword-like production table aliases | `select delete.open_id from (select ... from app.events) delete` | `reserved_keyword_subquery_alias` |
| CREATE OR REPLACE VIEW output column targets | `create or replace view mart.v as select id as c1 from app.s` | `create_or_replace_view` |
| CREATE TEMPORARY TABLE output column targets | `create temporary table mart.t as select id as c1 from app.s` | `create_temporary_table_as_select` |
| UPDATE SET direct assignment mapping | `update mart.t t join app.s s ... set t.c = s.c` | `update_join` |
| UPDATE modifier assignment mapping | `update low_priority ignore mart.t join app.s ... set t.c = s.c` | `update_low_priority_ignore` |
| UPDATE SET constant assignment target | `update mart.t set status = 'active'` | `update_join` |
| Multi-table UPDATE assignment mapping | `update mart.t, app.s set mart.t.c = app.s.c, app.s.flag = 1` | `update_multi_table` |
| UPDATE SET expression dependencies | `update mart.t join app.s on ... set c1 = upper(s.c2), c3 = s.c4 + t.c5` | `update_expression_assignment` |
| UPDATE JOIN derived query assignment dependencies | `update mart.t join (select c2 from app.s) q on ... set c1 = q.c2` | `update_join_derived_assignment` |
| UPDATE scalar subquery assignment dependencies | `update mart.t t set t.c = coalesce((select max(s.c) from app.s s where s.id = t.id), t.c)` | `update_scalar_subquery_expression` |
| UPDATE SET DEFAULT assignment dependencies | `update mart.t set c1 = default(c1), c2 = coalesce(c2, ...) where exists (...)` | `update_set_default_lineage` |
| UPDATE WHERE EXISTS predicate dependencies | `update mart.t t set c = ... where exists (select 1 from app.s s where s.id = t.id)` | `update_where_exists_subquery` |
| UPDATE ORDER BY expression usage | `update mart.t set c = c + 1 where ... order by coalesce(c1, c2) limit ...` | `update_order_by_expression_limit` |
| WITH before UPDATE JOIN/EXISTS assignment dependencies | `with q as (...) update mart.t join q on ... set c1 = q.c2`, `with q as (...) update ... where exists (...)` | `with_update_join`, `with_update_exists_subquery` |
| CASE expression dependencies | `select case when status = 'A' then score else 0 end as c from t` | `case_expression` |
| Multi-branch CASE expression dependencies | `select case when status = 'A' then score when status = 'P' then pending_score else default_score end from t` | `complex_case_expression` |
| CAST, function, and arithmetic expression dependencies | `select cast(id as char), coalesce(name, nickname), price * quantity from t` | `common_expression_projection` |
| Nested function expression dependencies | `select coalesce(lower(name), upper(nickname), cast(id as char)) from t` | `nested_function_projection` |
| Scalar subquery projection dependencies | `select (select max(amount) from orders) as max_amount from users` | `scalar_subquery_projection` |
| Correlated scalar subquery projection dependencies | `select u.id, (select max(o.amount) from orders o where o.user_id = u.id) from users u` | `correlated_scalar_subquery_projection` |
| IN subquery predicate column usage | `where id in (select user_id from sessions)` | `in_subquery_column_usage` |
| CASE expression with EXISTS subquery dependencies | `case when exists (select 1 from orders o where o.user_id = u.id) then u.vip_score else u.base_score end` | `case_exists_subquery_projection` |
| ORDER BY projection alias column usage | `select c as alias from t order by alias` | `projection_alias_order_usage` |
| ORDER BY ordinal position compatibility | `select id as user_id from t order by 1 desc` | `order_by_position` |
| ORDER BY expression column usage | `select id from t order by coalesce(updated_at, created_at)` | `order_by_expression_column_usage` |
| ORDER BY WITH ROLLUP usage | `select region, sum(amount) as total_amount from t group by region order by total_amount with rollup` | `order_by_with_rollup` |
| ORDER BY MySQL function column usage | `order by field(status, ...), coalesce(updated_at, created_at)` | `order_by_field_function_usage` |
| GROUP BY aggregate expression dependencies | `select user_id, count(order_id), sum(amount) from t group by user_id` | `aggregate_expression_projection` |
| GROUP BY WITH ROLLUP aggregate dependencies | `select region, count(order_id) from t group by region with rollup` | `group_by_with_rollup` |
| GROUP BY ROLLUP(...) aggregate dependencies | `select region, channel, sum(amount) from t group by rollup(region, channel)` | `group_by_rollup_function` |
| GROUP BY direction and ROLLUP usages | `select region, count(order_id) from t group by region desc with rollup` | `group_by_direction_rollup` |
| GROUP BY projection alias usage | `select lower(region) as region_key from app.orders group by region_key` | `group_by_projection_alias_usage` |
| HAVING scalar subquery usage | `having sum(amount) > (select avg(amount) from app.archive where ...)` | `having_scalar_subquery_column_usage` |
| HAVING EXISTS subquery usage | `group by u.region having exists (select 1 from app.region_acl a where a.region = u.region)` | `having_exists_subquery_usage` |
| HAVING BETWEEN aggregate usage | `having sum(amount) between min_amount and max_amount` | `having_between_aggregate_usage` |
| DISTINCT aggregate dependencies and HAVING usage | `select count(distinct user_id) ... group by region having count(distinct order_id) > ...` | `distinct_aggregate_column_usage` |
| GROUP_CONCAT DISTINCT dependencies | `group_concat(distinct name order by created_at desc separator ',')` | `group_concat_distinct_order_separator_lineage` |
| GROUP BY expression column usage | `select lower(region), count(order_id) from t group by lower(region)` | `group_by_expression_column_usage` |
| Window function expression dependencies and window clause usages | `select row_number() over (partition by k order by ts), sum(v) over (...) from t` | `window_function_lineage` |
| Window frame expression dependencies | `sum(v) over(partition by k order by ts rows between ... preceding and current row)`, `rows unbounded preceding`, `range interval 1 day preceding`, `range between interval 7 day preceding and current row` | `window_frame_rows`, `window_frame_rows_unbounded_preceding`, `window_frame_range_interval_preceding`, `window_frame_range_interval` |
| Window function null treatment | `first_value(amount) respect nulls over (partition by user_id order by created_at)` | `window_null_treatment_lineage` |
| Window value direction | `nth_value(amount, 2) from last ignore nulls over (...)` | `window_nth_value_from_last_lineage` |
| Named WINDOW clause expression dependencies and usages | `select sum(v) over w from t window w as (partition by k order by ts)`, `window w2 as (w1 order by ts)` | `named_window_clause`, `named_window_inheritance` |
| Backquoted non-ASCII column identifiers | `` select `用户ID` as `用户标识` from `业务库`.`用户表` `` | `backquoted_identifiers` |
| EXPLAIN wrapped SELECT output columns | `explain select id as user_id from app.users` | `explain_select` |
| EXPLAIN FORMAT/ANALYZE output columns | `explain format=json select id as user_id ...` | `explain_format_json_select`, `explain_analyze_select` |
| SELECT INTO OUTFILE/DUMPFILE output columns | `select id, name into outfile '...' from app.users` | `select_into_outfile`, `select_into_dumpfile`, `select_into_outfile_tail` |
| SELECT INTO variable forms | `select id into @v from app.users`, `select id from app.users into @v` | `select_into_variables`, `select_into_local_variables`, `select_into_variables_tail` |
| User-variable assignment expression lineage | `select @v := id as v from app.users` | `select_user_variable_assignment` |

Implemented MySQL clause-level column usage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| WHERE, GROUP BY, HAVING, and ORDER BY source columns | `select u.id, count(o.id) from users u join orders o where ... group by u.id having ... order by ...` | `clause_column_usage` |
| MySQL SELECT option predicate columns | `select high_priority ... from users where ...` | `select_with_mysql_options` |
| NATURAL JOIN predicate columns | `select ... from users natural join orders where users.status = ...` | `natural_join_projection` |
| CROSS JOIN predicate columns | `select ... from users cross join regions where users.region_id = regions.id` | `cross_join_projection` |
| STRAIGHT_JOIN predicate columns | `select ... from users u straight_join orders o on ... where ...` | `select_straight_join` |
| Index hint predicate and ordering columns | `select ... from users u force index (...) where ...`, `ignore key for order by (...) order by ...` | `select_force_index`, `select_ignore_key_for_order_by` |
| SELECT locking clause predicate columns | `select ... from users where ... for update/for share/lock in share mode`, `for update of alias nowait` | `select_for_update`, `select_for_update_nowait`, `select_for_update_of_alias_nowait`, `select_for_share_skip_locked`, `select_lock_in_share_mode` |
| MySQL regex predicate columns | `where name regexp '^A' and phone rlike '...'` | `regexp_predicate_column_usage` |
| MySQL pattern and phonetic predicate columns | `where name like 'A\\_%' escape '\\'`, `where name sounds like 'Jon'` | `like_escape_predicate`, `sounds_like_predicate` |
| MySQL negative pattern predicates | `where name not like ... and email not regexp ... and phone not rlike ...` | `not_like_not_regexp_usage` |
| MySQL boolean truth predicates | `where a = 1 xor b = 1`, `where (score > 0) is unknown`, `where a && !b` | `xor_predicate_column_usage`, `is_unknown_predicate`, `logical_and_operator_column_usage` |
| MySQL parameter marker predicates | `where id = ? and status = ?` | `parameter_marker_predicate` |
| MySQL typed datetime literal predicates | `where ts >= timestamp '...' and ds = date '...'` | `typed_datetime_literals` |
| MySQL ODBC temporal literal predicates | `where ds >= {d '2026-01-01'} and ts < {ts '2026-01-02 00:00:00'}` | `odbc_temporal_literals` |
| MySQL charset string literal predicates | `where name = _utf8mb4'...' and nickname = N'...'` | `charset_string_literals` |
| MySQL hex, bit, decimal, and exponent literals | `select x'0A', 0xFF, b'1010', 0b1011`, `where score >= .5e1 and ratio < 10.` | `hex_bit_literals`, `exponent_numeric_literals` |
| MySQL current date/time function literals | `select current_date, current_timestamp, utc_timestamp from ...`, `select current_date(), utc_timestamp() from ...` | `current_time_function_literals`, `current_time_function_parentheses` |
| Backquoted current-time keyword columns | `` select `current_date` from app.orders `` | `backquoted_current_time_column` |
| MySQL quantified subquery predicates | `where amount > all (select limit_amount from region_limits where ...)` | `quantified_subquery_predicate` |
| MySQL ANY/SOME quantified subquery predicates | `where amount = any (select ...) and status <> some (select ...)` | `quantified_any_some_subquery_predicate` |
| MySQL correlated scalar subquery predicates | `select ... (select max(o.amount) from orders o where o.user_id = u.id) ...` | `correlated_scalar_subquery_projection` |
| MySQL EXISTS predicates in DML, CTE-DML, and HAVING | `insert/update/delete ... where exists (...)`, `with q as (...) update/delete ... where exists (...)`, `having exists (...)` | `insert_select_exists_subquery`, `update_where_exists_subquery`, `delete_where_exists_subquery`, `with_insert_exists_subquery`, `with_update_exists_subquery`, `with_delete_exists_subquery`, `having_exists_subquery_usage` |
| MySQL BETWEEN and NOT IN predicates | `where amount between min_amount and max_amount and region not in (select ...)` | `between_not_in_subquery_usage` |
| MySQL tuple IN subquery predicates | `where (c1, c2) in (select x, y from ...)` | `tuple_in_subquery_column_usage` |
| MySQL null-safe equality predicate columns | `on a.id <=> b.id where a.c <=> b.c` | `null_safe_equal_predicate` |
| Bang logical NOT predicate columns | `where !(status = ... or name like ...)` | `bang_logical_not_predicate` |
| MySQL boolean predicate columns | `where is_active is true and deleted is not false` | `is_true_false_predicate` |
| MySQL JSON and COLLATE predicate columns | `where payload->>'$.type' = ...`, `where name collate ... = ...` | `json_extract_expression`, `binary_collate_expression` |
| UPDATE JOIN and WHERE source columns | `update users u join orders o on ... set ... where ...` | `dml_predicate_column_usage` |
| Multi-table UPDATE WHERE source columns | `update users u, orders o set ... where u.id = o.user_id` | `update_multi_table` |
| DELETE ORDER BY expression usage | `delete from mart.t where ... order by coalesce(c1, c2) limit ...` | `delete_order_by_expression_limit` |
| Self-join aliases | `select e.id, m.name from employees e left join employees m on e.manager_id = m.id` | `self_join_column_usage` |
| JOIN USING source columns | `select u.id from users u join orders o using (id)`, `right join ... using (id)` | `join_using_column_usage`, `right_join_using_column_usage` |
| Chained JOIN USING source columns | `select u.id from users u join orders o using (id) join payments p using (id)` | `join_using_multi_table_scope` |
| JOIN USING scoped inside comma-separated relations | `select b.id from audit a, users b join orders o using (id)` | `join_using_comma_scope` |
| JOIN USING over CTE references | `with u as (...), o as (...) select ... from u join o using (id)` | `join_using_derived_scope` |
| JOIN USING over derived subqueries | `select ... from (select ...) u join (select ...) o using (id)` | `join_using_subquery_scope` |
| JOIN ON over CTE references | `with u as (...), o as (...) select ... from u join o on u.id = o.user_id` | `join_on_derived_scope` |
| JOIN ON over derived subqueries | `select ... from (select ...) u join (select ...) o on u.id = o.user_id` | `join_on_subquery_scope` |
| UNION branch WHERE source columns | `select id from app.s1 where ... union all select id from app.s2 where ...` | `set_operation_clause_column_usage` |
| INSERT/REPLACE query ORDER BY source columns | `insert/replace into mart.t select ... from app.s order by ...` | `insert_select_order_limit`, `replace_select_order_limit` |
| DELETE derived JOIN predicate columns | `delete t from mart.t t join (select id from app.s) q on t.id = q.id` | `delete_join_derived` |
| Multi-table DELETE predicate columns | `delete t1, t2 from mart.t1 join app.t2 on ... where ...`, `delete t1.*, t2.* from ...` | `delete_multi_table`, `delete_multi_table_star_targets` |
| WITH before DELETE JOIN/EXISTS predicate columns | `with q as (...) delete t from mart.t t join q on t.id = q.id`, `with q as (...) delete ... where exists (...)` | `with_delete_join`, `with_delete_exists_subquery` |
| EXISTS subquery predicate column usage | `where exists (select 1 from app.orders o where o.user_id = u.id)`, `where not exists (...)` | `exists_subquery_column_usage`, `not_exists_subquery_column_usage` |
| CASE/EXISTS nested predicate dependencies | `case when exists (select 1 from app.orders o where o.user_id = u.id) then ... end` | `case_exists_subquery_projection` |
| DELETE WHERE subquery predicate columns | `delete from ads.t where user_id in (select id from ods.s)` | `delete_with_subquery` |
| EXPLAIN wrapped SELECT predicate columns | `explain select ... from app.s where ...` | `explain_select` |
| EXPLAIN FORMAT/ANALYZE predicate columns | `explain analyze select ... from app.s where ...` | `explain_format_json_select`, `explain_analyze_select` |
| SELECT INTO OUTFILE/DUMPFILE predicate columns | `select ... into outfile '...' from app.users where ...`, `select ... into outfile ... columns terminated by ...` | `select_into_outfile`, `select_into_dumpfile`, `select_into_outfile_tail`, `select_into_outfile_columns_options` |
| JSON_TABLE extracted column propagation | `json_table(u.payload, '$.items[*]' columns (sku ... path '$.sku', nested path ..., exists path ...)) jt` | `json_table_projection`, `json_table_nested_columns`, `json_table_exists_default_columns` |
| JSON MEMBER OF predicate columns | `where user_id member of(payload->'$.ids')` | `json_member_of_predicate` |

Current MySQL diagnostics:

| Code | Meaning |
| --- | --- |
| `MYSQL_PARSE_ERROR` | MySQL SQL could not be tokenized or walked by the current parser. |
| `MYSQL_STATEMENT_NOT_SUPPORTED` | The statement was recognized as MySQL but is not in the current statement set. |
| `MYSQL_COLUMN_LINEAGE_NOT_IMPLEMENTED` | No column lineage was produced for a statement shape where table lineage may still be available. |

Known MySQL gaps:

| Gap | Current behavior |
| --- | --- |
| Full MySQL grammar | The parser uses ANTLR tokenization plus a lineage walker; full parser grammar will be expanded incrementally. |
| `select *` expansion | Schema-free table stars are represented as wildcard lineage such as `app.users.*`; known derived-table stars can expand to their projected source columns. |
| Ambiguous plain SQL dialect detection | Explicit MySQL features are auto-detected; dialect-neutral `SELECT` remains default-detected by the current detector. |
| Complex expressions and subqueries | Direct projections, common expressions, joins, UNION, CTAS/view/insert mappings, scalar subqueries, chained CTE propagation, and common recursive CTE base propagation are covered; deeply nested correlation remains limited. |
| DML column lineage | `UPDATE SET`, `UPDATE JOIN`, duplicate-key update assignments, and `DELETE USING` predicate usages are covered; richer MySQL DML forms are still expanding. |

## Spark

Spark is the first implemented dialect. It uses Apache Spark's official ANTLR grammar as the parse baseline, while lineage extraction is implemented by LineSQL.

Current Spark SQL case assets:

```text
linesql-dialect-spark/src/test/resources/sql/spark/manifest.json
linesql-dialect-spark/src/test/resources/sql/spark/cases/*.sql
```

The manifest records executable expectations for statement type, input tables, output tables, column lineage, clause-level column usages, and expected diagnostics.

### Table Lineage

Implemented Spark table-level lineage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Basic SELECT source table | `select ... from ods.users` | `select_basic` |
| TABLE query primary source table | `table ods.users` | `table_query` |
| SELECT with scheduler placeholders | `select ... from ods.s where dt = ${bizdate} and region = {{ region }}` | `select_with_placeholders` |
| SELECT with clause-level field usages | `select ... from ods.s where ... group by ... having ... order by ...` | `clause_column_usage` |
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
| ALTER TABLE partition predicate maintenance | `alter table mart.t drop partition (dt > '2026-01-01')` | `alter_table_drop_partition_predicate` |
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
| Non-lineage session statements classified as control/schema | `use db`, `set catalog c`, `reset key` | `use_database`, `set_catalog`, `reset_configuration` |
| Namespace DDL classified as schema statements | `create namespace mart`, `drop namespace mart` | `create_namespace`, `drop_namespace` |
| Table-free metadata reads | `show namespaces`, `show catalogs`, `analyze tables` | `show_namespaces`, `show_catalogs`, `analyze_tables` |
| Additional metadata reads | `show tables`, `show views`, `show collations`, `describe namespace`, `describe query` | `show_tables`, `show_views`, `show_collations`, `describe_namespace`, `describe_query` |
| Resource and cache control statements | `refresh 'path'`, `clear cache`, `add jar ...` | `refresh_resource`, `clear_cache`, `add_jar_resource` |
| Function and procedure statements classified as routine/control | `create function`, `drop function`, `call proc`, `show/describe function` | `create_function`, `create_udf_return_query`, `drop_function`, `call_procedure`, `show_functions`, `describe_function` |
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
| Table-valued function TABLE identifier argument with wildcard lineage | `select * from custom_tvf(table ods.users)` | `table_valued_function_table_arg` |
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
| GROUPING SETS with bitwise expressions | `select lpad(bin(GROUPING__ID ^ 3), 2, 0) ... group by ... grouping sets (...)` | `grouping_sets_bitwise_xor` |
| Bang logical NOT predicates | `where dt = '${yyyy-MM-dd}' and!(a is null and b is null)` | `bang_logical_not` |
| UDTF-style function column aliases | `select stack(...) as (candidate, id)`, `select posexplode(...) as (seq, x)` | `stack_function_aliases`, `posexplode_function_aliases` |
| Null-safe equality predicates | `where not (a.c1 <=> b.c1)` | `null_safe_equal_operator` |
| Compatibility expression syntax | `select id::varchar ... where name ilike ... qualify row_number() ...` | `compatibility_cast_ilike_qualify` |
| Backslash-escaped string literals in VALUES | `insert overwrite table t partition (...) values ('用户反馈有\\'异响\\'')` | `backslash_escaped_string_values` |
| Double-quoted escaped string literals | `where file_key = "\"bucket/path/file.mp4\""` | `double_quoted_escaped_string` |
| Nested aggregate expressions | `concat_ws(',', collect_set(concat(...)))` | `nested_collect_set_expression` |
| Higher-order lambda functions | `transform(items, x -> x.amount)`, `filter(items, x -> ...)`, `exists(items, x -> ...)`, `aggregate(items, ..., (acc, x) -> ...)`, `zip_with(a, b, (x, y) -> ...)` | `lambda_transform_struct_field`, `lambda_filter_struct_field`, `lambda_exists_outer_capture`, `lambda_aggregate_struct_field`, `lambda_zip_with_struct_field` |
| Interval arithmetic compatibility | `date_add(ds, interval '' - 1 day)` | `date_add_interval_compatibility` |

Invalid SQL returns a diagnostic instead of throwing for the whole parse result. See `parse_error`.

### Column Lineage

Implemented Spark column-level lineage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Direct single-table projection | `select id as user_id, name from ods.users` | `column_direct_projection` |
| Schema-free table wildcard projection | `select * from ods.users` | `SparkDialectParserTest.representsTableStarColumnLineageWithoutMetadata` |
| Alias-qualified subquery wildcard expansion | `select u.* from (select id as user_id, name from ods.users) u` | `SparkDialectParserTest.expandsAliasedSubqueryStarColumnLineage` |
| CTE wildcard expansion | `with u as (select id as user_id, name from ods.users) select * from u` | `SparkDialectParserTest.expandsCteStarColumnLineage` |
| Unique unqualified column from joined derived relations | `select user_id from (select id as user_id from s1) u join (select amount from s2) o ...` | `SparkDialectParserTest.resolvesUnqualifiedProjectionFromUniqueDerivedRelationColumn` |
| Ambiguous unqualified column remains unresolved | `select id from (select id from s1) u join (select id from s2) o ...` | `SparkDialectParserTest.keepsUnqualifiedProjectionAmbiguousAcrossDerivedRelations` |
| UNION ALL wildcard sources are preserved | `select * from ods.users union all select * from dwd.users` | `SparkDialectParserTest.preservesWildcardSourcesAcrossUnionStar` |
| Derived columns over UNION wildcard sources | `select *, end_time - collect_time as durs from (select * from x union all select * from w) s` | `SparkDialectParserTest.resolvesDerivedColumnsFromUnionStarWildcardSources` |
| COUNT star aggregate is not treated as wildcard projection | `select dt, count(*) as cnt from ods.events group by dt` | `SparkDialectParserTest.doesNotTreatCountStarAsWildcardProjection` |
| Aggregate expression containing COUNT star | `select cast(count(*) * (max(end_time) - min(collect_time)) / count(*) as int) as duration from ods.events` | `SparkDialectParserTest.extractsSourcesFromAggregateExpressionContainingCountStar` |
| Unaliased CAST single-source target inference | `select cast(vin as string) from ods.events` | `SparkDialectParserTest.infersTargetColumnForUnaliasedSingleSourceExpression` |
| Function expression source columns | `select lower(name) as name_lower from ods.orders` | `column_expression_projection` |
| Arithmetic expression source columns | `select price * quantity as amount from ods.orders` | `column_expression_projection` |
| Constant projection with no sources | `select 1 as flag from ods.orders` | `column_expression_projection` |
| Unaliased constant expression targets | `select 'aaa', 1 from ods.users` | `unaliased_constant_projection` |
| Multi-branch CASE expression dependencies | `select case when status = 'A' then score when status = 'P' then pending_score else default_score end from t` | `complex_case_expression` |
| Nested function expression dependencies | `select coalesce(lower(name), upper(nickname), cast(id as string)) from t` | `nested_function_projection` |
| Scalar subquery projection dependencies | `select (select max(amount) from orders) as max_amount from users` | `scalar_subquery_projection` |
| IN subquery predicate column usage | `where id in (select user_id from sessions)` | `in_subquery_column_usage` |
| ORDER BY projection alias column usage | `select c as alias from t order by alias` | `projection_alias_order_usage` |
| ORDER BY expression column usage | `select id from t order by coalesce(updated_at, created_at)` | `order_by_expression_column_usage` |
| Partial extraction diagnostics | `select id, lower(name) from ods.users` | `column_partial_projection` |
| Partial expression projection sources | `select case when l.label = d.feedback_tag or updater_email = 'ops' then 1 end ...` | `partial_expression_projection_sources` |
| Qualified JOIN projection | `select u.id, o.amount from users u join orders o ...` | `column_join_projection` |
| Unique qualified JOIN hint for unqualified projections | `select count(case when kind_id = 6 then 1 end) from logs l join kind d on l.kind_id = d.id` | `qualified_join_hint_unqualified_projection` |
| GROUP BY aggregate expression sources | `select user_id, count(order_id), sum(amount) from ods.orders group by user_id` | `aggregate_column_projection` |
| Unaliased aggregate expression targets | `select max(amount) from ods.orders` | `unaliased_aggregate_projection` |
| Window function argument and spec sources | `select row_number() over (partition by user_id order by created_at) from ods.orders`; `order by event_time nulls first` | `window_column_projection`, `window_order_by_nulls_first` |
| Single-table nested struct field sources | `select profile.city as city from ods.users` | `nested_field_projection` |
| Qualified nested struct field sources | `select u.profile.city from ods.users u join ods.orders o ...` | `qualified_nested_field_projection` |
| Derived struct-root field sources | `select values.vin from (select from_json(content, ...) as values from ods.events) s` | `derived_struct_root_field_projection` |
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
| Case-insensitive generated column lookup | `select item from t lateral view explode(items) e as Item` | `lateral_view_case_insensitive_generated_column` |
| INSERT target column list mapping | `insert into ads.t(c1, c2) select a, b from ods.s` | `insert_column_list` |
| INSERT over UNION ALL target column lineage | `insert into t(c1) select a from s1 union all select b from s2` | `insert_union_column_lineage` |
| INSERT over INTERSECT target column lineage | `insert into t(c1) select a from s1 intersect select b from s2` | `insert_intersect_column_lineage` |
| INSERT over EXCEPT target column lineage | `insert into t(c1) select a from s1 except select b from s2` | `insert_except_column_lineage` |
| INSERT BY NAME projection target mapping | `insert into ads.t by name select a as c1 from ods.s` | `insert_by_name` |
| INSERT REPLACE WHERE BY NAME mapping | `insert into ads.t target by name replace where ... select a as c1 from ods.s` | `insert_replace_where` |
| INSERT REPLACE USING BY NAME mapping | `insert into ads.t target by name replace using (...) select a as c1 from ods.s` | `insert_replace_using` |
| INSERT target column list over CTE propagation | `insert into ads.t(c1) with q as (...) select c1 from q` | `insert_from_cte` |
| INSERT target column list over subquery propagation | `insert into ads.t(c1) select c1 from (select a as c1 from ods.s) q` | `insert_from_subquery` |
| INSERT target column list over aliased/expression projections | `insert into t(c1,c2,c3) select a as x, upper(b), count(c) ...` | `insert_column_list_expression_projection` |
| INSERT over script-local temporary view propagation | `create temporary view v as select a as c1 from ods.s; insert into ads.t(c1) select c1 from v` | `script_temp_view_lineage` |
| INSERT over script-local cache table propagation | `cache table c as select a from ods.s; insert into ads.t(c1) select a from c` | `script_cache_table_lineage` |
| UPDATE SET expression dependencies | `update ads.t set c1 = upper(c2), c3 = c4 + c5 where ...` | `update_expression_assignment` |
| MERGE assignment and insert-value dependencies | `merge into t using s on ... when matched then update set c = s.c when not matched then insert (...) values (...)` | `merge_assignment_column_lineage` |
| UNION column sources merged by position | `select a as c1 from s1 union all select b from s2` | `union_column_projection` |
| INTERSECT column sources merged by position | `select a as c1 from s1 intersect select b from s2` | `intersect_column_projection` |
| EXCEPT column sources merged by position | `select a as c1 from s1 except select b from s2` | `except_column_projection` |
| EXCEPT column inputs by position | `select a as c1 from s1 except select b from s2` | `except_column_projection` |
| INTERSECT column inputs by position | `select a as c1 from s1 intersect select b from s2` | `intersect_column_projection` |
| Pipe set operator column inputs by position | `from s1 |> select a as c1 |> union/intersect/except select b from s2` | `pipe_union_column_projection`, `pipe_intersect_table_lineage`, `pipe_except_table_lineage` |
| EXPLAIN wrapped SELECT columns | `explain select id from ods.s` | `explain_select` |
| CREATE VIEW output column targets | `create view mart.v as select id from ods.s` | `create_view` |
| CREATE VIEW over aliased/expression/aggregate projections | `create view mart.v as select a as c1, upper(b), count(c) ...` | `create_view_expression_projection` |
| CREATE VIEW column list target names | `create view mart.v(c1, c2) as select a, b from ods.s` | `create_view_column_list` |
| ALTER VIEW output column targets | `alter view mart.v as select id as c1 from ods.s` | `alter_view_as_select` |
| CTAS output column targets | `create table mart.t as select id as c1 from ods.s` | `ctas_column_projection` |
| CTAS over aliased/expression/aggregate projections | `create table mart.t as select a as c1, upper(b), count(c) ...` | `ctas_expression_projection` |
| CTAS provider and partition clause output targets | `create table mart.t using parquet partitioned by (...) as select id from ods.s` | `ctas_using_partitioned` |
| CREATE OR REPLACE TABLE output column targets | `create or replace table mart.t as select id as c1 from ods.s` | `replace_table_as_select` |
| CREATE MATERIALIZED VIEW output column targets | `create materialized view mart.v as select id as c1 from ods.s` | `create_materialized_view_as_select` |
| CREATE STREAMING TABLE output column targets | `create streaming table mart.t as select id as c1 from stream(ods.s)` | `create_streaming_table_as_select` |
| MERGE update assignments and insert values | `merge into ads.t using ods.s on ... when matched then update set c = s.c when not matched then insert (...) values (...)` | `merge_into` |
| Single-level CTE direct column propagation | `with base as (select id as c1 from ods.s) select c1 from base` | `cte_column_projection` |
| Chained CTE direct column propagation | `with a as (...), b as (select c1 from a) select c1 from b` | `chained_cte_column_projection` |
| CTE column alias list propagation | `with q(c1, c2) as (select a, b from ods.s) select c1 from q` | `cte_column_aliases` |
| Single-level aliased subquery direct column propagation | `select c1 from (select id as c1 from ods.s) q` | `subquery_column_projection` |
| Case-insensitive derived column lookup | `with u as (select id as User_ID from ods.users) select user_id from u` | `cte_case_insensitive_column_projection` |
| Unique base table fallback beside derived relations | `select id from ods.users u join (select order_id from dwd.orders) o ...` | `join_derived_unique_base_fallback` |
| Unique derived wildcard fallback beside known derived columns | `select score from (select * from ods.events) e join (select vin as vin_r from dim.vehicles) v ...` | `unique_derived_wildcard_projection` |
| Explicit derived columns take precedence over adjacent wildcard sources | `select vehicle_category_code from (select vehicle_category_code from dwd.test_drive) t join (select * from dwd.appoint_relation) a ...` | `explicit_derived_column_precedence_over_wildcard` |
| Backtick-qualified direct projections | `select t1.\`department_id\` from eps_ods.ods_coa_staff_df t1` | `backtick_qualified_direct_projection` |
| Known derived columns are preserved beside wildcard inputs | `select c1 from (select *, expr as c2 from (... join table_star ...)) q` | `SparkDialectParserTest.preservesKnownDerivedColumnsWhenStarAlsoCarriesUnknownTableColumns` |
| Same-name columns in joined subqueries stay scoped | `select t1.id, t2.id from (...) t1 join (...) t2 on t1.id = t2.id` | `joined_subquery_same_name_group_usage` |
| Function or UDTF-style multi-column alias output | `select parse_user(id, name) as (user_id, user_name) from ods.s` | `multi_alias_function_output` |

### Clause Column Usage

LineSQL distinguishes projection lineage from columns used by filtering, joining, grouping, HAVING, ordering, MERGE predicates, metadata/statistics reads, index definitions, and table model definitions. These fields are returned as `columnUsages` with usage types such as `WHERE`, `JOIN_ON`, `GROUP_BY`, `HAVING`, `ORDER_BY`, `MERGE_ON`, `MERGE_WHEN`, `READ_METADATA`, `INDEX`, and `TABLE_MODEL`.

`JOIN USING (...)` is covered as a `JOIN_ON` column usage. Active dialect visitors keep chained `USING` predicates scoped to the current joined relation, so unrelated comma-separated relations are not reported as join predicate inputs.

Implemented Spark clause-level column usage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| WHERE, GROUP BY, HAVING, and ORDER BY source columns | `select u.id, count(o.id) from users u join orders o where ... group by u.id having ... order by ...` | `clause_column_usage` |
| Subquery WHERE and GROUP BY source columns | `select q.id from (select id from ods.s where ... group by ...) q where ... group by ...` | `subquery_clause_column_usage` |
| CTE WHERE and GROUP BY source columns | `with q as (select id from ods.s where ...) select id from q where ... group by ...` | `cte_clause_column_usage` |
| UNION branch WHERE source columns | `select id from ods.s1 where ... union all select id from ods.s2 where ...` | `set_operation_clause_column_usage` |
| Same-name GROUP BY and JOIN ON columns in joined subqueries | `select ... from (select id ... group by id) t1 join (select id ... group by id) t2 on t1.id = t2.id` | `joined_subquery_same_name_group_usage` |
| JOIN ON source columns | `select u.id, o.amount from users u join orders o on ...` | `join_on_column_usage` |
| Self-join aliases | `select e.id, m.name from employees e left join employees m on e.manager_id = m.id` | `self_join_column_usage` |
| JOIN USING source columns | `select u.id from users u join orders o using (id)` | `join_using_column_usage` |
| Chained JOIN USING source columns | `select u.id from users u join orders o using (id) join payments p using (id)` | `join_using_multi_table_scope` |
| JOIN USING scoped inside comma-separated relations | `select b.id from audit a, users b join orders o using (id)` | `join_using_comma_scope` |
| JOIN USING over CTE references | `with u as (...), o as (...) select ... from u join o using (id)` | `join_using_derived_scope` |
| JOIN USING over derived subqueries | `select ... from (select ...) u join (select ...) o using (id)` | `join_using_subquery_scope` |
| JOIN ON over CTE references | `with u as (...), o as (...) select ... from u join o on u.id = o.user_id` | `join_on_derived_scope` |
| JOIN ON over derived subqueries | `select ... from (select ...) u join (select ...) o on u.id = o.user_id` | `join_on_subquery_scope` |
| MERGE ON and WHEN source columns | `merge into t using s on ... when matched and ... then ...` | `merge_predicate_column_usage` |

### Diagnostics

Current Spark diagnostics:

| Code | Meaning |
| --- | --- |
| `SPARK_PARSE_ERROR` | Spark SQL could not be parsed by the current grammar entry point. |
| `DYNAMIC_SQL_NOT_EXPANDED` | Dynamic SQL was parsed but intentionally not expanded for lineage extraction. |
| `CODE_LITERAL_NOT_EXPANDED` | Code literal SQL was parsed as a statement shape but not expanded for lineage extraction. |
| `CDC_LINEAGE_DEGRADED` | AUTO CDC target/source tables were extracted, but CDC-specific column semantics were not expanded. |
| `COLUMN_LINEAGE_NOT_IMPLEMENTED` | No column lineage was produced for a projection-capable statement shape. Table lineage may still be available. Table-only DDL and metadata statements do not emit this diagnostic. |
| `COLUMN_LINEAGE_PARTIAL` | Some column lineage was produced, but at least one projection could not be resolved safely. |

### Production SQL Tolerance

Implemented Spark tolerance scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Unquoted scheduler placeholders | `${bizdate}`, `{{ region }}` in expressions | `select_with_placeholders` |
| Backquoted non-ASCII identifiers | Chinese table and column identifiers | `quoted_chinese_identifiers` |
| Bad SQL isolation in scripts | one invalid statement does not block later statements | `script_bad_sql_recovery` |
| Dynamic SQL degradation | `EXECUTE IMMEDIATE` returns diagnostics instead of guessing embedded SQL lineage | `execute_immediate_dynamic_sql` |
| Non-lineage session statements | `USE`, `SET CATALOG`, and `RESET` parse as schema/control statements without lineage diagnostics | `use_database`, `set_catalog`, `reset_configuration` |
| Namespace and catalog statements | Namespace DDL and table-free metadata reads parse as schema/metadata statements without table/column diagnostics | `create_namespace`, `drop_namespace`, `show_namespaces`, `show_catalogs`, `analyze_tables` |
| Function/procedure/variable/cursor statements | Function DDL, CALL, variable, and cursor control statements parse as routine/control statements without table/column diagnostics | `create_function`, `call_procedure`, `create_variable`, `declare_cursor` |
| Resource/cache/metadata statements | Resource commands, cache clearing, and table-free metadata reads parse without table/column diagnostics | `refresh_resource`, `clear_cache`, `add_jar_resource`, `show_tables`, `describe_namespace` |
| Namespace comments | Namespace comment statements parse without table or column diagnostics | `comment_namespace` |

### Known Gaps

The following Spark lineage features are intentionally not complete yet:

| Gap | Current behavior |
| --- | --- |
| `select *` expansion | Schema-free table stars are represented as wildcard lineage. Known derived columns are preserved and expanded when they are available beside wildcard inputs. |
| Complex CTE column propagation | Chained direct CTE projection and CTE column aliases are supported; recursive CTEs and complex CTE joins are not complete yet. |
| Complex subquery column propagation | Single-level aliased direct subquery projection is supported; nested subquery chains and complex subquery joins are not complete yet. |
| Temporary view scope | Temporary view lineage is maintained inside one `parseScript` call only; persistent catalog view expansion is not implemented yet. |
| Unqualified columns in multi-table queries | Not guessed when they cannot be safely mapped to one table. |
| Complex UDTF and lateral view column propagation | Simple generated columns from UDTF input expressions, function-style multi-column aliases, and Spark `range` output are supported, including alias-qualified generated column references; broader function-specific output semantics are not complete yet. |
| Pipe set operator with schema-free TABLE right side | Pipe UNION/INTERSECT/EXCEPT column lineage is supported when the right side has explicit projections; schema-free `TABLE t` right sides are not expanded without metadata. |
| PIVOT and complex UNPIVOT column lineage | PIVOT aggregate output columns, single-value UNPIVOT, and positional multi-value UNPIVOT columns are supported; richer PIVOT grouping/value naming and UNPIVOT alias/null semantics are not complete yet. |
| TRANSFORM column lineage | Table-level lineage is supported; script output semantics are not propagated yet. |
| Pipe AGGREGATE complex grouping semantics | Simple standalone aggregate outputs and following SELECT propagation are supported; grouping analytics and complex grouping sets are not complete yet. |
| Dynamic SQL expansion | `EXECUTE IMMEDIATE` is parsed and diagnosed, but embedded SQL text is not recursively parsed. |
| Code literal expansion | Metric view code literals are parsed and diagnosed, but embedded code text is not recursively parsed. |
| CDC column semantics | AUTO CDC source/target tables are extracted; CDC-specific field propagation is not complete yet. |
| Multi-insert column lineage | Table-level lineage is supported; per-target column lineage is not emitted yet. |
| Complex nested fields and structs | Basic nested field paths are preserved, and fields read from derived expression roots are traced to the root expression sources. Schema-aware struct expansion is not implemented yet. |
