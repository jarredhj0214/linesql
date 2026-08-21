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
| MySQL | `REPLACE INTO`, `ON DUPLICATE KEY`, `LIMIT offset, size`, `UPDATE ... JOIN ... SET`, `LOW_PRIORITY`/`QUICK` DML modifiers, `RENAME TABLE`, `LOCK TABLES`, `INTO OUTFILE`, JSON arrow operators |
| Hive | `ROW FORMAT`, `STORED AS`, `SERDEPROPERTIES`, `CLUSTERED BY` |
| Flink | connector options, `WATERMARK FOR` |
| StarRocks | `CREATE TABLE ... DUPLICATE KEY`, `CREATE TABLE ... AGGREGATE KEY`, `CREATE TABLE ... DISTRIBUTED BY HASH`, replication properties |
| Oracle | `FROM DUAL`, `CONNECT BY`, `START WITH` |
| SQL Server | `SELECT TOP n`, bracketed identifiers, `WITH (NOLOCK)`, bracketed DML identifiers |
| Spark | `INSERT OVERWRITE`, `LATERAL VIEW`, `CREATE TEMPORARY VIEW`, `USING`, fallback |

Known conflict guards:

| Guard | Reason |
| --- | --- |
| Spark `MERGE INTO` is not classified as Oracle | `MERGE INTO` is shared across engines and is not a safe Oracle-only anchor. |
| JSON path array wildcard `[*]` is not classified as SQL Server | Brackets inside strings are not SQL Server identifiers. |
| MySQL `ON DUPLICATE KEY` is not classified as StarRocks | StarRocks key anchors are scoped to `CREATE TABLE` statements. |

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

PostgreSQL is a baseline MVP dialect path. The current implementation uses a dedicated lightweight ANTLR grammar for common PostgreSQL lineage SQL.

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
| WITH before INSERT SELECT | `with q as (...) insert into mart.t select ... from q` | `with_insert_select` |
| CREATE TABLE AS SELECT | `create table mart.t as select ... from public.s` | `create_table_as_select` |
| CREATE VIEW AS SELECT | `create view mart.v as select ... from public.s` | `create_view` |
| CTE column propagation | `with q as (...) select q.c1 from q` | `cte_column_projection` |
| UPDATE FROM with RETURNING | `update mart.t set ... from staging.s where ... returning ...` | `update_from_returning` |
| DELETE with subquery and RETURNING | `delete from mart.t where id in (...) returning ...` | `delete_using_returning` |
| WITH before UPDATE FROM | `with q as (...) update mart.t set c = q.c from q where ...` | `with_update_from` |
| WITH before DELETE USING | `with q as (...) delete from mart.t using q where ...` | `with_delete_using` |
| MERGE target and source tables | `merge into mart.t using staging.s on ... when matched then update ...` | `merge_into` |
| MERGE source subquery tables | `merge into mart.t using (select ... from staging.s) q on ...` | `merge_using_subquery` |
| WITH before MERGE | `with q as (...) merge into mart.t using q on ...` | `with_merge` |
| JOIN, WHERE, GROUP BY, HAVING, ORDER BY usages | `select ... from u join o ... where ... group by ...` | `clause_column_usage` |
| PostgreSQL cast operator and ILIKE usage | `select id::text ... where email ilike ...` | `postgres_cast_ilike` |
| Double-quoted non-ASCII identifiers | `select "用户ID" from "业务库"."用户表"` | `quoted_identifiers` |

Known PostgreSQL gaps:

| Gap | Current behavior |
| --- | --- |
| Full PostgreSQL grammar | A dedicated lightweight ANTLR grammar exists for baseline cases; broad PostgreSQL syntax is still being expanded. |
| RETURNING row lineage | Write lineage is preserved, but returned-row lineage is not modeled as a separate output stream yet. |
| Full ON CONFLICT action lineage | Source-to-target insert lineage is preserved; conflict update action lineage is not fully expanded yet. |
| PostgreSQL-specific DDL, arrays, and JSON operators | Planned for grammar-driven follow-up work. Basic `MERGE` table and column lineage is covered. |

## OceanBase

OceanBase is a baseline MVP dialect path. The current implementation models OceanBase as a compatibility-mode dialect and delegates common SQL lineage to either MySQL-mode or Oracle-mode parsing while keeping the public dialect as `OCEANBASE`.

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
| MySQL mode UPDATE JOIN | `update mart.t join staging.s ... set ...` | `mysql_mode_update_join` |
| MySQL mode DELETE USING | `delete from mart.t using mart.t join staging.s ...` | `mysql_mode_delete_using` |
| Oracle mode DUAL pseudo table query | `select sysdate from dual` | `oracle_mode_dual` |
| Oracle mode MERGE | `merge into mart.t using staging.s on (...) when matched ...` | `oracle_mode_merge` |
| Oracle mode CREATE VIEW | `create view mart.v as select ... from app.s` | `oracle_mode_create_view` |

Known OceanBase gaps:

| Gap | Current behavior |
| --- | --- |
| Explicit compatibility-mode option | Mode is inferred from syntax anchors today; parser options for explicit mode are planned. |
| OceanBase-specific DDL, hints, partitions, and tenant syntax | Planned after the MySQL/Oracle compatibility baselines are broader. |
| Divergent MySQL-mode and Oracle-mode semantics | Common lineage is reused; OceanBase-specific semantics need dedicated cases. |

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
| INSERT OVERWRITE target and source | `insert overwrite table ads.t select ... from ods.s` | `insert_overwrite` |
| INSERT INTO VALUES target lineage | `insert into ads.t(c1) values (...)` | `insert_values` |
| CREATE TABLE AS SELECT | `create table ads.t as select ... from ods.s` | `create_table_as_select` |
| CREATE TABLE model AS SELECT | `create table ads.t duplicate key(...) distributed by hash(...) properties(...) as select ...` | `create_table_model_as_select` |
| CREATE VIEW AS SELECT | `create view ads.v as select ... from ods.s join dwd.o` | `create_view` |
| CREATE OR REPLACE VIEW AS SELECT | `create or replace view ads.v as select ... from ods.s` | `create_or_replace_view` |
| INSERT SELECT over CTE | `insert into ads.t with q as (...) select ... from q` | `insert_from_cte` |
| WITH before INSERT SELECT | `with q as (...) insert into ads.t select ... from q` | `with_insert_select` |
| CREATE VIEW over CTE | `create view ads.v as with q as (...) select ... from q` | `create_view_with_cte` |
| UNION source table propagation | `select a from dbo.s1 union all select b from dbo.s2` | `union_column_projection` |
| Bracketed non-ASCII identifiers | `select [用户ID] from [业务库].[用户表]` | `bracket_identifiers` |
| SELECT TOP and table hint | `select top 10 ... from dbo.users with (nolock)` | `top_with_nolock` |
| Single CTE source table propagation | `with q as (...) select ... from q` | `cte_column_projection` |
| Single derived subquery source table propagation | `select ... from (select ... from ods.s) q` | `subquery_column_projection` |
| GROUP BY ROLLUP/GROUPING SETS source columns | `group by rollup(a, b)`, `group by grouping sets ((a), (b))` | `group_by_rollup`, `group_by_grouping_sets` |
| QUALIFY window filter source columns | `qualify row_number() over(partition by ... order by ...) = 1` | `qualify_window_filter` |
| StarRocks bitmap/HLL function projection lineage | `bitmap_union(to_bitmap(c))`, `hll_union(hll_hash(c))` | `starrocks_bitmap_hll_functions` |
| UPDATE FROM target and source tables | `update ads.t set c = s.c from ods.s s` | `update_from` |
| WITH before UPDATE FROM | `with q as (...) update ads.t set c = q.c from q where ...` | `with_update_from` |
| DELETE FROM JOIN target and source tables | `delete t from ads.t t join ods.s s ...` | `delete_from_join` |
| WITH before DELETE FROM JOIN | `with q as (...) delete t from ads.t t join q ...` | `with_delete_join` |
| MERGE target and source tables | `merge into ads.t using ods.s on ... when matched then update ...` | `merge_into` |
| MERGE source subquery tables | `merge into ads.t using (select ... from ods.s) q on ...` | `merge_using_subquery` |
| UPDATE with subquery sources | `update ads.t set c = (select ... from ods.s1) where id in (select ... from ods.s2)` | `update_with_subquery` |
| DELETE with subquery sources | `delete from ads.t where id in (select ... from ods.s)` | `delete_with_subquery` |
| DROP TABLE affected table | `drop table if exists dbo.t` | `drop_table` |
| TRUNCATE TABLE affected table | `truncate table dbo.t` | `truncate_table` |
| ALTER TABLE column maintenance | `alter table dbo.t add c int` | `alter_table_add_column` |

Implemented SQL Server column-level lineage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Direct single-table projection | `select id as user_id, name from ods.users` | `select_basic` |
| Alias-qualified JOIN projection | `select u.id, o.amount from users u join orders o` | `join_projection` |
| Backtick-qualified direct projections | ``select u.`department_id` from ods.users u`` | `backtick_qualified_direct_projection` |
| Case-insensitive derived column propagation | `select user_id from (select id as User_ID from ods.users) u` | `derived_case_insensitive_column_projection` |
| INSERT SELECT target mapping | `insert into ads.t select a as c1 from ods.s` | `insert_into` |
| INSERT OVERWRITE target column mapping | `insert overwrite table ads.t(c1, c2) select a, b from ods.s` | `insert_overwrite` |
| INSERT target column list mapping | `insert into ads.t(c1, c2) select a, b from ods.s` | `insert_column_list` |
| INSERT over UNION ALL target column lineage | `insert into t(c1) select a from s1 union all select b from s2` | `insert_union_column_lineage` |
| INSERT over INTERSECT target column lineage | `insert into t(c1) select a from s1 intersect select b from s2` | `insert_intersect_column_lineage` |
| INSERT over EXCEPT target column lineage | `insert into t(c1) select a from s1 except select b from s2` | `insert_except_column_lineage` |
| CTAS output column targets | `create table ads.t as select id as c1 from ods.s` | `create_table_as_select` |
| CTAS over aliased/expression/aggregate projections | `create table ads.t as select a as c1, upper(b), count(c) ...` | `ctas_expression_projection` |
| CTAS with StarRocks table model options | `create table ads.t duplicate key(...) distributed by hash(...) as select ...` | `create_table_model_as_select` |
| CREATE VIEW output column targets | `create view ads.v as select u.id from ods.users u` | `create_view` |
| CREATE VIEW over aliased/expression/aggregate projections | `create view ads.v as select a as c1, upper(b), count(c) ...` | `create_view_expression_projection` |
| CREATE VIEW column list target names | `create view ads.v(c1, c2) as select a, b from ods.s` | `create_view_column_list` |
| INSERT SELECT target mapping over CTE | `insert into ads.t with q as (...) select q.c1 from q` | `insert_from_cte` |
| WITH before INSERT SELECT target mapping | `with q as (...) insert into ads.t(c1) select q.c1 from q` | `with_insert_select` |
| INSERT target column list over subquery propagation | `insert into ads.t(c1) select c1 from (select a as c1 from ods.s) q` | `insert_from_subquery` |
| INSERT target column list over aliased/expression projections | `insert into t(c1,c2,c3) select a as x, upper(b), count(c) ...` | `insert_column_list_expression_projection` |
| CREATE VIEW output columns over CTE | `create view ads.v as with q as (...) select q.c1 from q` | `create_view_with_cte` |
| Bracketed identifier column mapping | `select [用户ID] as [用户标识] from [业务库].[用户表]` | `bracket_identifiers` |
| SELECT TOP projection mapping | `select top (10) u.id as user_id from dbo.users u` | `top_parenthesized` |
| CASE expression dependencies | `select case when status = 'A' then score else 0 end as c from t` | `case_expression` |
| Multi-branch CASE expression dependencies | `select case when status = 'A' then score when status = 'P' then pending_score else default_score end from t` | `complex_case_expression` |
| StarRocks function expression lineage | `select ifnull(nickname, name), date_trunc('day', ts) from ods.s` | `starrocks_function_expression_projection` |
| CAST, function, and arithmetic expression dependencies | `select cast(id as varchar), coalesce(name, nickname), price * quantity from t` | `common_expression_projection` |
| Nested function expression dependencies | `select coalesce(lower(name), upper(nickname), cast(id as varchar)) from t` | `nested_function_projection` |
| Scalar subquery projection dependencies | `select (select max(amount) from orders) as max_amount from users` | `scalar_subquery_projection` |
| IN subquery predicate column usage | `where id in (select user_id from sessions)` | `in_subquery_column_usage` |
| WHERE subquery scope isolation | `select id from users where id in (select user_id from sessions)` | `SparkDialectParserTest.keepsOuterProjectionLineageWhenWhereContainsSubquery` |
| Fully qualified column references | `select db.table.col from db.table` | `SparkDialectParserTest.resolvesFullyQualifiedColumnReferences` |
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
| UPDATE assignment mapping | `update ads.t set c = s.c from ods.s s` | `update_from` |
| UPDATE SET expression dependencies | `update ads.t set c1 = upper(s.c2), c3 = s.c4 + t.c5 from ods.s s` | `update_expression_assignment` |
| UPDATE FROM derived query assignment dependencies | `update ads.t set c1 = q.c2 from (select c2 from ods.s) q where ...` | `update_from_derived_assignment` |
| WITH before UPDATE FROM assignment dependencies | `with q as (...) update ads.t set c1 = q.c2 from q where ...` | `with_update_from` |
| MERGE update assignments and insert values | `merge into ads.t using ods.s on ... when matched then update set c = s.c when not matched by target then insert (...) values (...)` | `merge_into` |
| MERGE source subquery field propagation | `merge into ads.t using (select a as c from ods.s) q on ...` | `merge_using_subquery` |

Implemented SQL Server clause-level column usage scenarios:

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
| DELETE FROM JOIN predicate columns | `delete t from ads.t t join ods.s s on t.id = s.id` | `delete_from_join` |
| DELETE FROM derived JOIN predicate columns | `delete t from ads.t t join (select id from ods.s) q on t.id = q.id` | `delete_from_join_derived` |
| WITH before DELETE JOIN predicate columns | `with q as (...) delete t from ads.t t join q on t.id = q.id` | `with_delete_join` |
| MERGE ON source columns | `merge into ads.t using ods.s on t.id = s.id ...` | `merge_into` |
| MERGE ON over source subquery columns | `merge into ads.t using (select id from ods.s) q on t.id = q.id` | `merge_using_subquery` |
| UNION branch WHERE source columns | `select id from dbo.s1 where ... union all select id from dbo.s2 where ...` | `set_operation_clause_column_usage` |
| EXISTS subquery predicate column usage | `where exists (select 1 from dbo.orders o where o.user_id = u.id)` | `exists_subquery_column_usage` |
| UPDATE/DELETE WHERE subquery predicate columns | `update/delete dbo.t where id in (select user_id from ods.s)` | `update_with_subquery`, `delete_with_subquery` |

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
| Advanced T-SQL DML and procedural syntax | Basic `MERGE`, `UPDATE FROM`, and `DELETE FROM JOIN` lineage is covered. `OUTPUT`, table variables, temp tables, and stored-procedure bodies are not yet covered. |
| Complex CTEs and subqueries | Single CTE, chained CTE direct projection, CTE column aliases, and single derived subquery direct projection propagation are covered. Recursive CTEs and complex nested subqueries are not complete yet. |

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
| INSERT INTO VALUES target lineage | `insert into ads.t(c1) values (...)` | `insert_values` |
| INSERT ALL multi-table target lineage | `insert all into t1 (...) values (...) into t2 (...) values (...) select ...` | `insert_all` |
| INSERT FIRST multi-table target lineage | `insert first into t1 (...) values (...) into t2 (...) values (...) select ...` | `insert_first` |
| CREATE TABLE AS SELECT | `create table ads.t as select ... from ods.s` | `create_table_as_select` |
| CREATE VIEW AS SELECT | `create view ads.v as select ... from ods.s join dwd.o` | `create_view` |
| INSERT SELECT over CTE | `insert into ads.t with q as (...) select ... from q` | `insert_from_cte` |
| CREATE VIEW over CTE | `create view ads.v as with q as (...) select ... from q` | `create_view_with_cte` |
| UNION source table propagation | `select a from ods.s1 union all select b from ods.s2` | `union_column_projection` |
| Double-quoted non-ASCII identifiers | `select "用户ID" from "业务库"."用户表"` | `quoted_identifiers` |
| DUAL pseudo table | `select sysdate from dual` | `dual_pseudo_table` |
| Hierarchical query clauses | `select ... from app.org start with ... connect by ...` | `hierarchical_query` |
| Single CTE source table propagation | `with q as (...) select ... from q` | `cte_column_projection` |
| Single derived subquery source table propagation | `select ... from (select ... from ods.s) q` | `subquery_column_projection` |
| UPDATE target table lineage | `update ads.t set c = c2 where ...` | `update_set` |
| DELETE target table lineage | `delete from ads.t where ...` | `delete_where` |
| MERGE target and source tables | `merge into ads.t using ods.s on (...) when matched then update ...` | `merge_into` |
| MERGE source subquery tables | `merge into ads.t using (select ... from ods.s) q on (...)` | `merge_using_subquery` |
| UPDATE with subquery sources | `update ads.t set c = (select ... from ods.s1) where id in (select ... from ods.s2)` | `update_with_subquery` |
| DELETE with subquery sources | `delete from ads.t where id in (select ... from ods.s)` | `delete_with_subquery` |
| DROP TABLE affected table | `drop table mart.t` | `drop_table` |
| TRUNCATE TABLE affected table | `truncate table ads.t` | `truncate_table` |
| ALTER TABLE RENAME TO old and new tables | `alter table mart.old rename to new_name` | `rename_table` |
| ALTER TABLE column maintenance | `alter table mart.t add c number` | `alter_table_add_column` |
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
| INSERT ALL target column mapping | `insert all into t1(c1) values (a) into t2(c1) values (a) select a from s` | `insert_all` |
| INSERT FIRST target column mapping | `insert first into t1(c1) values (a) into t2(c1) values (a) select a from s` | `insert_first` |
| CTAS output column targets | `create table ads.t as select id as c1 from ods.s` | `create_table_as_select` |
| CTAS over aliased/expression/aggregate projections | `create table ads.t as select a as c1, upper(b), count(c) ...` | `ctas_expression_projection` |
| CREATE VIEW output column targets | `create view ads.v as select u.id from ods.users u` | `create_view` |
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
| CAST, function, and arithmetic expression dependencies | `select cast(id as varchar), coalesce(name, nickname), price * quantity from t` | `common_expression_projection` |
| Nested function expression dependencies | `select coalesce(lower(name), upper(nickname), cast(id as varchar)) from t` | `nested_function_projection` |
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
| `ORACLE_PARSE_ERROR` | Oracle SQL could not be tokenized or walked by the current MVP parser. |
| `ORACLE_STATEMENT_NOT_SUPPORTED` | The statement was recognized as Oracle but is not in the current MVP statement set. |
| `ORACLE_COLUMN_LINEAGE_NOT_IMPLEMENTED` | No column lineage was produced for a statement shape where table lineage may still be available. |

Known Oracle gaps:

| Gap | Current behavior |
| --- | --- |
| Full Oracle grammar | The MVP uses ANTLR tokenization plus a lineage walker; full parser grammar will be expanded incrementally. |
| `select *` expansion | Not expanded without schema metadata. |
| Oracle-specific query syntax | `MODEL`, `PIVOT`, packages, and PL/SQL blocks are not yet covered. Basic `MERGE INTO` and hierarchical `START WITH` / `CONNECT BY` lineage are covered. |
| Complex CTEs and subqueries | Single CTE, chained CTE direct projection, CTE column aliases, and single derived subquery direct projection propagation are covered. Recursive CTEs and complex nested subqueries are not complete yet. |

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
| INSERT INTO VALUES target lineage | `insert into ads.t(c1) values (...)` | `insert_values` |
| CREATE TABLE AS SELECT | `create table ads.t as select ... from ods.s` | `create_table_as_select` |
| CREATE TABLE AS SELECT over CTE | `create table ads.t as with q as (...) select ... from q` | `create_table_as_with_select` |
| CREATE VIEW AS SELECT | `create view ads.v as select ... from ods.s join dwd.o` | `create_view` |
| INSERT SELECT over CTE | `insert into ads.t with q as (...) select ... from q` | `insert_from_cte` |
| CREATE VIEW over CTE | `create view ads.v as with q as (...) select ... from q` | `create_view_with_cte` |
| UNION source table propagation | `select a from ods.s1 union all select b from ods.s2` | `union_column_projection` |
| Single CTE source table propagation | `with q as (...) select ... from q` | `cte_column_projection` |
| Single derived subquery source table propagation | `select ... from (select ... from ods.s) q` | `subquery_column_projection` |
| UPDATE FROM target and source tables | `update ads.t set c = s.c from ods.s s` | `update_from` |
| UPDATE FROM derived query source tables | `update ads.t set c = q.c from (select ... from ods.s) q where ...` | `update_from_derived_assignment` |
| WITH before UPDATE FROM | `with q as (...) update ads.t set c = q.c from q where ...` | `with_update_from` |
| DELETE USING target and source tables | `delete from ads.t using ods.s s where ...` | `delete_using` |
| DELETE USING derived query source tables | `delete from ads.t using (select ... from ods.s) q where ...` | `delete_using_derived` |
| WITH before DELETE USING | `with q as (...) delete from ads.t using q where ...` | `with_delete_using` |
| UPDATE with subquery sources | `update ads.t set c = (select ... from ods.s1) where id in (select ... from ods.s2)` | `update_with_subquery` |
| DELETE with subquery sources | `delete from ads.t where id in (select ... from ods.s)` | `delete_with_subquery` |
| INSERT WITH LABEL SELECT | `insert into ads.t with label job (...) select ... from ods.s` | `insert_with_label_select` |
| CREATE TABLE LIKE structure lineage | `create table mart.t like ods.s` | `create_table_like` |
| CREATE TABLE table model DDL | `create table t (...) duplicate/aggregate/unique/primary key (...) distributed by ...` | `create_table_duplicate_key`, `create_table_aggregate_key`, `create_table_unique_key`, `create_table_primary_key_random` |
| CREATE TABLE ENGINE=OLAP DDL | `create table t (...) engine=olap duplicate key(...) distributed by ...` | `create_table_engine_olap` |
| CREATE TABLE aggregate column types | `pv bigint sum`, `uv hll hll_union`, `tags bitmap bitmap_union` | `create_table_aggregate_types` |
| CREATE TABLE range partition DDL | `create table t (...) duplicate key (...) partition by range (...) distributed by ...` | `create_table_duplicate_key` |
| DROP TABLE affected table | `drop table if exists mart.t` | `drop_table` |
| DROP VIEW affected view | `drop view if exists ads.v` | `drop_view` |
| DROP MATERIALIZED VIEW affected view | `drop materialized view if exists mart.mv` | `drop_materialized_view` |
| TRUNCATE TABLE affected table | `truncate table ads.t` | `truncate_table` |
| REFRESH MATERIALIZED VIEW affected view | `refresh materialized view mart.mv with sync mode` | `refresh_materialized_view` |
| CREATE MATERIALIZED VIEW with distribution/refresh | `create materialized view ... distributed by ... refresh async as select ...` | `create_materialized_view_distributed_refresh` |
| ALTER TABLE RENAME TO old and new tables | `alter table ads.old rename to ads.new` | `rename_table` |
| ALTER TABLE column maintenance | `alter table mart.t add column c int` | `alter_table_add_column` |
| ALTER TABLE drop column maintenance | `alter table mart.t drop column c` | `alter_table_drop_column` |
| ALTER TABLE partition maintenance | `alter table mart.t add/drop partition ...` | `alter_table_add_partition`, `alter_table_drop_partition` |
| ALTER TABLE property maintenance | `alter table mart.t set (...)` | `alter_table_set_properties` |
| ALTER TABLE swap affected tables | `alter table mart.t swap with table mart.t_shadow` | `alter_table_swap` |
| CREATE INDEX affected table | `create index idx on ads.t(c)` | `create_index` |
| DROP INDEX affected table | `drop index idx on ads.t` | `drop_index` |
| SHOW CREATE TABLE metadata read | `show create table ads.t` | `show_create_table` |
| SHOW CREATE VIEW metadata read | `show create view ads.v` | `show_create_view` |
| SHOW CREATE MATERIALIZED VIEW metadata read | `show create materialized view mart.mv` | `show_create_materialized_view` |
| SHOW PARTITIONS metadata read | `show partitions from ads.t` | `show_partitions` |
| SHOW COLUMNS metadata read | `show columns from ads.t` | `show_columns_from_table` |
| SHOW TABLET metadata read | `show tablet from mart.t` | `show_tablet_from_table` |
| SHOW PROC metadata read | `show proc '/dbs'` | `show_proc` |
| ANALYZE TABLE metadata read | `analyze table ads.t` | `analyze_table` |
| DESCRIBE TABLE metadata read | `desc table ads.t` | `describe_table` |
| Schema/database control DDL | `create database ...`, `drop database ...` | `create_database`, `drop_database` |
| Routine load target table | `create routine load job on ods.t ... from kafka (...)` | `create_routine_load_kafka` |
| Broker load target table | `load label job (data infile (...) into table ods.t ...) with broker ...` | `load_label_data_infile` |
| Load job control statements | `pause/resume/stop routine load for job`, `cancel load ...` | `pause_routine_load`, `resume_routine_load`, `stop_routine_load`, `cancel_load` |
| Load job metadata reads | `show load ...`, `show routine load ...`, `show routine load task ...` | `show_load`, `show_routine_load`, `show_routine_load_task` |
| Export source table lineage | `export table mart.t [partition(...)] to 'path' properties (...)` | `export_table`, `export_table_partition` |
| Admin control statements | `admin set frontend config (...)`, `admin set replica status properties (...)` | `admin_set_frontend_config`, `admin_set_replica_status` |

Implemented StarRocks column-level lineage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Direct single-table projection | `select id as user_id, name from ods.users` | `select_basic` |
| Alias-qualified JOIN projection | `select u.id, o.amount from users u join orders o` | `join_projection` |
| INSERT SELECT target mapping | `insert into ads.t select a as c1 from ods.s` | `insert_into` |
| INSERT target column list mapping | `insert into ads.t(c1, c2) select a, b from ods.s` | `insert_column_list` |
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
| CASE expression dependencies | `select case when status = 'A' then score else 0 end as c from t` | `case_expression` |
| Multi-branch CASE expression dependencies | `select case when status = 'A' then score when status = 'P' then pending_score else default_score end from t` | `complex_case_expression` |
| CAST, function, and arithmetic expression dependencies | `select cast(id as varchar), coalesce(name, nickname), price * quantity from t` | `common_expression_projection` |
| Nested function expression dependencies | `select coalesce(lower(name), upper(nickname), cast(id as varchar)) from t` | `nested_function_projection` |
| Scalar subquery projection dependencies | `select (select max(amount) from orders) as max_amount from users` | `scalar_subquery_projection` |
| IN subquery predicate column usage | `where id in (select user_id from sessions)` | `in_subquery_column_usage` |
| ORDER BY projection alias column usage | `select c as alias from t order by alias` | `projection_alias_order_usage` |
| ORDER BY expression column usage | `select id from t order by coalesce(updated_at, created_at)` | `order_by_expression_column_usage` |
| GROUP BY aggregate expression dependencies | `select user_id, count(order_id), sum(amount) from t group by user_id` | `aggregate_expression_projection` |
| DISTINCT aggregate dependencies and HAVING usage | `select count(distinct user_id) ... group by region having count(distinct order_id) > ...` | `distinct_aggregate_column_usage` |
| GROUP BY expression column usage | `select lower(region), count(order_id) from t group by lower(region)` | `group_by_expression_column_usage` |
| Single CTE direct column propagation | `with q as (select id as user_id from ods.s) select q.user_id from q` | `cte_column_projection` |
| Chained CTE direct column propagation | `with a as (...), b as (select c1 from a) select c1 from b` | `chained_cte_column_projection` |
| CTE column alias list propagation | `with q(c1, c2) as (select a, b from ods.s) select c1 from q` | `cte_column_aliases` |
| Single derived subquery direct column propagation | `select q.user_id from (select id as user_id from ods.s) q` | `subquery_column_projection` |
| Same-name columns in joined subqueries stay scoped | `select t1.id, t2.id from (...) t1 join (...) t2 on t1.id = t2.id` | `joined_subquery_scope` |
| UNION column sources merged by position | `select a as c1 from s1 union all select b from s2` | `union_column_projection` |
| INTERSECT column sources merged by position | `select a as c1 from s1 intersect select b from s2` | `intersect_column_projection` |
| EXCEPT column sources merged by position | `select a as c1 from s1 except select b from s2` | `except_column_projection` |
| UPDATE assignment mapping | `update ads.t set c = s.c from ods.s s` | `update_from` |
| UPDATE SET expression dependencies | `update ads.t set c1 = upper(s.c2), c3 = s.c4 + t.c5 from ods.s s` | `update_expression_assignment` |
| UPDATE FROM derived query assignment dependencies | `update ads.t set c1 = q.c2 from (select c2 from ods.s) q where ...` | `update_from_derived_assignment` |
| WITH before UPDATE FROM assignment dependencies | `with q as (...) update ads.t set c1 = q.c2 from q where ...` | `with_update_from` |

Implemented StarRocks clause-level column usage scenarios:

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
| UNION branch WHERE source columns | `select id from ods.s1 where ... union all select id from ods.s2 where ...` | `set_operation_clause_column_usage` |
| DELETE USING WHERE source columns | `delete from ads.t using ods.s s where t.id = s.id` | `delete_using` |
| DELETE USING derived WHERE columns | `delete from ads.t using (select id from ods.s) q where t.id = q.id` | `delete_using_derived` |
| WITH before DELETE USING predicate columns | `with q as (...) delete from ads.t using q where t.id = q.id` | `with_delete_using` |
| EXISTS subquery predicate column usage | `where exists (select 1 from ods.orders o where o.user_id = u.id)` | `exists_subquery_column_usage` |
| UPDATE/DELETE WHERE subquery predicate columns | `update/delete ads.t where id in (select user_id from ods.s)` | `update_with_subquery`, `delete_with_subquery` |

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
| StarRocks table model DDL | `DUPLICATE KEY`, `AGGREGATE KEY`, `UNIQUE KEY`, `PRIMARY KEY`, hash/random distribution, and properties are covered as affected target table lineage. |
| Complex CTEs, subqueries, and routine-load syntax | Single CTE, chained CTE direct projection, CTE column aliases, single derived subquery direct projection propagation, and materialized-view SELECT lineage are covered. Recursive CTEs and routine-load syntax are not complete yet. |

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
| INSERT INTO VALUES target lineage | `insert into ads_t(c1) values (...)` | `insert_values` |
| CREATE TABLE LIKE structure lineage | `create table mart_t like ods_s` | `create_table_like` |
| CREATE VIEW AS SELECT | `create view v as select ... from ods_s join dwd_o` | `create_view` |
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
| UPDATE target table lineage | `update ads_t set c = c2 where ...` | `update_set` |
| DELETE target table lineage | `delete from ads_t where ...` | `delete_where` |
| UPDATE with subquery sources | `update ads_t set c = (select ... from ods_s1) where id in (select ... from ods_s2)` | `update_with_subquery` |
| DELETE with subquery sources | `delete from ads_t where id in (select ... from ods_s)` | `delete_with_subquery` |
| Statement set write lineage | `execute statement set begin insert into ...; end` | `execute_statement_set` |
| CREATE TABLE connector DDL | `create table ods_t (...) with ('connector' = 'kafka')` | `create_table_connector` |
| MERGE INTO target and source tables | `merge into ads.t using ods.s on ... when matched then update ...` | `merge_into` |
| Temporal join source tables | `join rates for system_time as of o.proc_time` | `temporal_join` |
| TUMBLE table-valued function source table | `from table(tumble(table ods.orders, descriptor(ts), interval '1' hour))` | `tumble_window` |
| DROP TABLE affected table | `drop table if exists mart_t` | `drop_table` |
| ALTER TABLE RENAME TO old and new tables | `alter table mart_old rename to mart_new` | `rename_table` |
| ALTER TABLE column maintenance | `alter table mart_t add c int` | `alter_table_add_column` |
| DESCRIBE TABLE metadata read | `describe table mart_t` | `describe_table` |

Implemented Flink column-level lineage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Direct single-table projection | `select id as user_id, name from ods_users` | `select_basic` |
| Alias-qualified JOIN projection | `select u.id, o.amount from users u join orders o` | `join_projection` |
| INSERT SELECT target mapping | `insert into ads_t select a as c1 from ods_s` | `insert_into` |
| INSERT target column list mapping | `insert into ads_t(c1, c2) select a, b from ods_s` | `insert_column_list` |
| INSERT over UNION ALL target column lineage | `insert into t(c1) select a from s1 union all select b from s2` | `insert_union_column_lineage` |
| INSERT over INTERSECT target column lineage | `insert into t(c1) select a from s1 intersect select b from s2` | `insert_intersect_column_lineage` |
| INSERT over EXCEPT target column lineage | `insert into t(c1) select a from s1 except select b from s2` | `insert_except_column_lineage` |
| CTAS output column targets | `create table ads_t as select id as c1 from ods_s` | `create_table_as_select` |
| CTAS over aliased/expression/aggregate projections | `create table ads_t as select a as c1, upper(b), count(c) ...` | `ctas_expression_projection` |
| CREATE VIEW output column targets | `create view v as select u.id from ods_users u` | `create_view` |
| CREATE VIEW over aliased/expression/aggregate projections | `create view v as select a as c1, upper(b), count(c) ...` | `create_view_expression_projection` |
| CREATE VIEW column list target names | `create view v(c1, c2) as select a, b from ods_s` | `create_view_column_list` |
| INSERT SELECT target mapping over CTE | `insert into ads_t with q as (...) select q.c1 from q` | `insert_from_cte` |
| INSERT target column list over subquery propagation | `insert into ads_t(c1) select c1 from (select a as c1 from ods_s) q` | `insert_from_subquery` |
| CREATE VIEW output columns over CTE | `create view v as with q as (...) select q.c1 from q` | `create_view_with_cte` |
| CASE expression dependencies | `select case when status = 'A' then score else 0 end as c from t` | `case_expression` |
| Multi-branch CASE expression dependencies | `select case when status = 'A' then score when status = 'P' then pending_score else default_score end from t` | `complex_case_expression` |
| CAST, function, and arithmetic expression dependencies | `select cast(id as string), coalesce(name, nickname), price * quantity from t` | `common_expression_projection` |
| Nested function expression dependencies | `select coalesce(lower(name), upper(nickname), cast(id as string)) from t` | `nested_function_projection` |
| Scalar subquery projection dependencies | `select (select max(amount) from orders) as max_amount from users` | `scalar_subquery_projection` |
| Scalar subquery-only projection dependencies | `with q as (...) select (select max(amount) from q) as max_amount` | `scalar_subquery_only_projection` |
| Scalar subquery predicate isolation | `select round(cnt / (select count(*) from t where c > 0), 2) from q` | `scalar_subquery_predicate_isolation` |
| IN subquery predicate column usage | `where id in (select user_id from sessions)` | `in_subquery_column_usage` |
| HAVING subquery scope isolation | `group by vin having vin in (select vin from dim.vehicles)` | `having_subquery_scope_isolation` |
| ORDER BY projection alias column usage | `select c as alias from t order by alias` | `projection_alias_order_usage` |
| ORDER BY expression column usage | `select id from t order by coalesce(updated_at, created_at)` | `order_by_expression_column_usage` |
| GROUP BY aggregate expression dependencies | `select user_id, count(order_id), sum(amount) from t group by user_id` | `aggregate_expression_projection` |
| DISTINCT aggregate dependencies and HAVING usage | `select count(distinct user_id) ... group by region having count(distinct order_id) > ...` | `distinct_aggregate_column_usage` |
| GROUP BY expression column usage | `select lower(region), count(order_id) from t group by lower(region)` | `group_by_expression_column_usage` |
| Temporal join projection dependencies | `select o.amount * r.rate from orders o join rates for system_time as of ... r` | `temporal_join` |
| TUMBLE window projection dependencies | `select window_start, user_id, count(order_id) from table(tumble(...))` | `tumble_window` |
| Window frame aggregate dependencies and window clause usages | `sum(amount) over (partition by user_id order by ts rows between ...)` | `window_frame` |
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
| UNION branch WHERE source columns | `select id from s1 where ... union all select id from s2 where ...` | `set_operation_clause_column_usage` |
| UPDATE/DELETE WHERE subquery predicate columns | `update/delete ads_t where id in (select user_id from ods_s)` | `update_with_subquery`, `delete_with_subquery` |

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
| Flink DDL connector options | Connector DDL is covered as affected target table lineage; connector properties are not exposed as a separate lineage model yet. |
| Complex CTEs, subqueries, temporal joins, and window TVFs | Single CTE, chained CTE direct projection, CTE column aliases, single derived subquery direct projection propagation, temporal join table lineage, and basic TUMBLE source table lineage are covered. Recursive CTEs and full window TVF generated-column semantics are not complete yet. |

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
| TRUNCATE TABLE affected table | `truncate table ads.t partition (...)` | `truncate_table` |
| ALTER TABLE RENAME TO old and new tables | `alter table mart.old rename to mart.new` | `rename_table` |
| ALTER TABLE column maintenance | `alter table mart.t add columns (...)` | `alter_table_add_columns` |
| DESCRIBE TABLE metadata read | `describe table mart.t` | `describe_table` |

Implemented Hive column-level lineage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Direct single-table projection | `select id as user_id, name from ods.users` | `select_basic` |
| Alias-qualified JOIN projection | `select u.id, o.amount from users u join orders o` | `join_projection` |
| INSERT SELECT target mapping | `insert overwrite table ads.t select a as c1 from ods.s` | `insert_overwrite` |
| INSERT target column list mapping | `insert into ads.t(c1, c2) select a, b from ods.s` | `insert_column_list` |
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
| Complex expressions, CTEs, subqueries, and lateral view | Single CTE, chained CTE direct projection, CTE column aliases, and single derived subquery direct projection propagation are covered. Complex expressions, recursive CTEs, nested subqueries, and lateral view are not complete yet. |

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
| TABLE statement source table | `table app.users order by id limit 10` | `table_statement` |
| VALUES statement without table lineage | `values row(1, 'a'), row(2, 'b') order by 1 limit 1` | `values_statement` |
| DUAL pseudo-table ignored | `select 1 as one from dual` | `select_from_dual` |
| SELECT with MySQL options | `select high_priority sql_calc_found_rows ... from app.users` | `select_with_mysql_options` |
| JOIN source tables | `select ... from app.users u join app.orders o ...` | `join_projection` |
| NATURAL JOIN source tables | `select ... from app.users natural join app.orders` | `natural_join_projection` |
| STRAIGHT_JOIN source tables | `select straight_join ... from app.users u straight_join app.orders o ...` | `select_straight_join` |
| Table index hints | `select ... from app.users u force index for join (...)` | `select_force_index` |
| SELECT locking clauses | `select ... from app.users for update`, `select ... lock in share mode` | `select_for_update`, `select_lock_in_share_mode` |
| Subquery source table propagation | `select q.c from (select a as c from app.s) q` | `subquery_column_projection` |
| CTE source table propagation | `with q as (select a as c from app.s) select c from q` | `cte_column_projection` |
| Recursive CTE base-table propagation | `with recursive q as (...) select ... from q` | `with_recursive_select` |
| UNION source table propagation | `select a from app.s1 union all select b from app.s2` | `union_column_projection` |
| SELECT INTO OUTFILE/DUMPFILE source lineage | `select ... into outfile '...' from app.users`, `select ... into dumpfile '...' from app.users`, `select ... from app.users into outfile '...'` | `select_into_outfile`, `select_into_dumpfile`, `select_into_outfile_tail` |
| SELECT INTO user variables source lineage | `select c1, c2 into @v1, @v2 from app.users` | `select_into_variables` |
| GROUP_CONCAT separator syntax | `select * from (select group_concat(c separator '、') from app.t group by k) q limit 0, 10` | `group_concat_separator` |
| GROUP BY WITH ROLLUP | `select region, count(*) from app.orders group by region with rollup` | `group_by_with_rollup` |
| GROUP BY direction with ROLLUP | `select region, count(*) from app.orders group by region desc with rollup` | `group_by_direction_rollup` |
| ORDER BY ordinal position | `select id as user_id from app.users order by 1 desc limit 10` | `order_by_position` |
| INSERT INTO SELECT target and source | `insert into mart.t(c1) select a from app.s` | `insert_select` |
| INSERT IGNORE SELECT target and source | `insert ignore into mart.t(c1) select a from app.s` | `insert_ignore_select` |
| INSERT priority modifier target and source | `insert low_priority ignore into mart.t(c1) select a from app.s` | `insert_low_priority_ignore_select` |
| INSERT SELECT with ORDER BY/LIMIT | `insert into mart.t(c1) select a from app.s order by a limit 100` | `insert_select_order_limit` |
| INSERT target partition and source | `insert into mart.t partition (p1) (c1) select a from app.s` | `insert_partition_select` |
| INSERT delayed VALUES target lineage | `insert delayed into mart.t(c1) values (...)` | `insert_delayed_values` |
| INSERT INTO VALUES target lineage | `insert into mart.t(c1) values (...)` | `insert_values` |
| INSERT VALUES row alias with duplicate-key update | `insert into mart.t(c1) values (...) as new on duplicate key update c1 = new.c1` | `insert_values_alias_on_duplicate` |
| INSERT VALUES ROW constructor target lineage | `insert into mart.t(c1) values row(...), row(...)` | `insert_values_row_constructor` |
| INSERT SET target and constant column lineage | `insert into mart.t set c1 = ...` | `insert_set` |
| INSERT SELECT with duplicate-key update | `insert into mart.t(c1) select a from app.s on duplicate key update ...` | `insert_select_on_duplicate` |
| INSERT SELECT with duplicate-key aggregate update | `insert into mart.t(c1, c2) select a, sum(b) from app.s group by a on duplicate key update ...` | `insert_select_on_duplicate_key_update` |
| WITH before INSERT SELECT | `with q as (...) insert into mart.t(c1) select q.c1 from q` | `with_insert_select` |
| REPLACE INTO SELECT target and source | `replace into mart.t(c1) select a from app.s` | `replace_select` |
| REPLACE priority modifier target and source | `replace low_priority into mart.t(c1) select a from app.s` | `replace_low_priority_select` |
| REPLACE SELECT with ORDER BY/LIMIT | `replace into mart.t(c1) select a from app.s order by a limit 100` | `replace_select_order_limit` |
| REPLACE target partition and source | `replace into mart.t partition (p1) (c1) select a from app.s` | `replace_partition_select` |
| REPLACE INTO VALUES target lineage | `replace into mart.t(c1) values (...)` | `replace_values` |
| REPLACE SET target and constant column lineage | `replace into mart.t set c1 = ...` | `replace_set` |
| LOAD DATA target table | `load data local infile '...' into table mart.t` | `load_data_local_infile` |
| LOAD DATA options target table | `load data infile '...' into table mart.t fields ... lines ... ignore ...` | `load_data_with_options` |
| LOAD DATA SET target and constant column lineage | `load data infile '...' into table mart.t (...) set loaded_at = now()` | `load_data_set_assignments` |
| CREATE TABLE AS SELECT | `create table mart.t as select ... from app.s` | `create_table_as_select` |
| CREATE TABLE SELECT without AS | `create table mart.t select ... from app.s` | `create_table_select_without_as` |
| CREATE TABLE IGNORE/REPLACE SELECT | `create table mart.t ignore select ... from app.s`, `create table mart.t replace select ...` | `create_table_ignore_select`, `create_table_replace_select` |
| CREATE TABLE options before AS SELECT | `create table mart.t (...) engine=InnoDB default charset=utf8mb4 as select ...` | `create_table_options_as_select` |
| CREATE TABLE declared columns AS SELECT | `create table mart.t(c1 ...) as select a as other_name from app.s` | `create_table_declared_columns_as_select` |
| CREATE TABLE schema DDL with constraints | `create table mart.t (id bigint auto_increment, primary key (id), unique key uk_c (c), index idx_c (c))` | `create_table_constraints` |
| CREATE TABLE generated columns and ON UPDATE constraints | `create table mart.t (full_name varchar(...) generated always as (...), updated_at ... on update ...)` | `create_table_generated_columns` |
| CREATE TABLE advanced table options | `create table mart.t (...) row_format=compressed key_block_size=8 compression='zlib' tablespace ts` | `create_table_advanced_options` |
| CREATE TABLE MySQL enum/set data types | `create table mart.t (status enum('A','B'), channels set('EMAIL','SMS'))` | `create_table_enum_set_types` |
| CREATE TABLE CHECK and invisible attributes | `create table mart.t (c int check (c > 0), index idx(c) invisible)` | `create_table_check_invisible` |
| CREATE TABLE FULLTEXT/SPATIAL indexes | `create table mart.t (..., fulltext index idx(c), spatial index idx(g))` | `create_table_fulltext_spatial_index` |
| CREATE TEMPORARY TABLE schema DDL | `create temporary table if not exists mart.t (...) engine=InnoDB` | `create_temporary_table_schema` |
| CREATE TABLE partition DDL | `create table mart.t (...) partition by hash/range (...)` | `create_table_hash_partition`, `create_table_range_partition` |
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
| CREATE VIEW with DEFINER | `create definer='u'@'%' sql security definer view mart.v as select ...` | `create_view_with_definer` |
| CREATE VIEW AS WITH SELECT | `create view mart.v as with q as (...) select ... from q` | `create_view_as_with_select` |
| ALTER VIEW AS SELECT | `alter algorithm=merge view mart.v(c1) as select ...` | `alter_view` |
| CREATE TEMPORARY TABLE AS SELECT | `create temporary table if not exists mart.t as select ...` | `create_temporary_table_as_select` |
| UPDATE JOIN table lineage | `update mart.t join app.s on ... set ...` | `update_join` |
| UPDATE LOW_PRIORITY IGNORE | `update low_priority ignore mart.t join app.s ... set ...` | `update_low_priority_ignore` |
| Multi-table UPDATE lineage | `update mart.t, app.s set ... where ...` | `update_multi_table` |
| UPDATE JOIN over derived query | `update mart.t join (select ... from app.s) q on ... set ...` | `update_join_derived_assignment` |
| UPDATE ORDER BY LIMIT | `update mart.t set c = ... where ... order by ... limit ...` | `update_order_by_limit` |
| WITH before UPDATE JOIN | `with q as (...) update mart.t join q on ... set ...` | `with_update_join` |
| DELETE USING table lineage | `delete from mart.t using mart.t join app.s ...` | `delete_using` |
| DELETE LOW_PRIORITY QUICK IGNORE | `delete low_priority quick ignore from mart.t where ...` | `delete_low_priority_quick_ignore` |
| DELETE ORDER BY LIMIT | `delete from mart.t where ... order by ... limit ...` | `delete_order_by_limit` |
| DELETE alias FROM JOIN table lineage | `delete t from mart.t t join app.s s ...` | `delete_join` |
| Multi-table DELETE lineage | `delete t1, t2 from mart.t1 join app.t2 ...` | `delete_multi_table` |
| DELETE alias FROM derived JOIN table lineage | `delete t from mart.t t join (select ... from app.s) q ...` | `delete_join_derived` |
| WITH before DELETE alias FROM JOIN | `with q as (...) delete t from mart.t t join q ...` | `with_delete_join` |
| Backquoted non-ASCII identifiers | `` select `用户ID` from `业务库`.`用户表` `` | `backquoted_identifiers` |
| DROP TABLE affected table | `drop table if exists mart.t` | `drop_table` |
| DROP TABLE multiple affected tables | `drop table if exists mart.t1, mart.t2` | `drop_multiple_tables` |
| DROP TEMPORARY TABLE affected table | `drop temporary table if exists tmp_users` | `drop_temporary_table` |
| DROP VIEW affected view | `drop view if exists mart.v` | `drop_view` |
| DROP VIEW multiple affected views | `drop view if exists mart.v1, mart.v2` | `drop_multiple_views` |
| TRUNCATE TABLE affected table | `truncate table ads.t` | `truncate_table` |
| ALTER TABLE RENAME TO old and new tables | `alter table mart.old rename to mart.new` | `rename_table` |
| RENAME TABLE old and new tables | `rename table app.old to app.new`, `rename table a.old to a.new, b.old to b.new` | `rename_table_statement`, `rename_multiple_tables` |
| ALTER TABLE column maintenance | `alter table mart.t add column c int` | `alter_table_add_column` |
| ALTER TABLE drop column maintenance | `alter table mart.t drop column c` | `alter_table_drop_column` |
| ALTER TABLE multiple comma-separated actions | `alter table mart.t add column c int, modify column d varchar(32), drop column e` | `alter_table_multiple_actions` |
| ALTER TABLE online DDL options | `alter table mart.t add column c int, algorithm=inplace, lock=none` | `alter_table_online_options` |
| ALTER TABLE index maintenance | `alter table mart.t add index idx_c (c)` | `alter_table_add_index` |
| ALTER TABLE change column maintenance | `alter table mart.t change column old_c new_c varchar(128)` | `alter_table_change_column` |
| ALTER TABLE modify column maintenance | `alter table mart.t modify column c varchar(256)` | `alter_table_modify_column` |
| ALTER TABLE rename column maintenance | `alter table mart.t rename column old_c to new_c` | `alter_table_rename_column` |
| ALTER TABLE primary key maintenance | `alter table mart.t add/drop primary key` | `alter_table_add_primary_key`, `alter_table_drop_primary_key` |
| ALTER TABLE drop index maintenance | `alter table mart.t drop index idx_c` | `alter_table_drop_index` |
| ALTER TABLE foreign key reference | `alter table mart.child add foreign key (...) references mart.parent (...)` | `alter_table_add_foreign_key` |
| ALTER TABLE partition maintenance | `alter table app.users add/drop partition ...` | `alter_table_add_partition`, `alter_table_drop_partition` |
| ALTER TABLE partition exchange | `alter table app.users exchange partition p with table staging.users_p ...` | `alter_table_exchange_partition` |
| CREATE INDEX affected table | `create index idx on mart.t(c)`, `create fulltext index idx on mart.t(c)`, `create index idx on mart.t(name(32) desc)` | `create_index`, `create_fulltext_index`, `create_index_prefix_order` |
| DROP INDEX affected table | `drop index idx on mart.t` | `drop_index` |
| EXPLAIN wrapped SELECT lineage | `explain select ... from app.s where ...` | `explain_select` |
| EXPLAIN FORMAT/ANALYZE wrapped SELECT lineage | `explain format=json select ...`, `explain analyze select ...` | `explain_format_json_select`, `explain_analyze_select` |
| USE database session statement | `use mart` | `use_database` |
| SHOW CREATE TABLE metadata read | `show create table mart.t` | `show_create_table` |
| SHOW CREATE VIEW metadata read | `show create view mart.v` | `show_create_view` |
| SHOW CREATE DATABASE metadata read | `show create database app` | `show_create_database` |
| SHOW COLUMNS metadata read | `show columns from mart.t` | `show_columns_from_table` |
| SHOW INDEX metadata read | `show index from mart.t` | `show_index_from_table` |
| Schema-level and server metadata reads | `show full tables from mart`, `show databases`, `show variables`, `show processlist`, `show engines` | `show_tables_from_database`, `show_databases`, `show_variables`, `show_processlist`, `show_engines` |
| ANALYZE TABLE metadata read | `analyze table mart.t` | `analyze_table` |
| ANALYZE TABLE histogram metadata read | `analyze table mart.t update/drop histogram on c` | `analyze_table_update_histogram`, `analyze_table_drop_histogram` |
| CHECK TABLE metadata read | `check table mart.t` | `check_table` |
| Multi-table metadata maintenance reads | `analyze table app.t1, app.t2`, `check table app.t1, app.t2 for upgrade` | `analyze_multiple_tables`, `check_multiple_tables` |
| OPTIMIZE TABLE maintenance read | `optimize table mart.t` | `optimize_table` |
| REPAIR TABLE maintenance read | `repair table mart.t` | `repair_table` |
| DESCRIBE TABLE metadata read | `describe table mart.t` | `describe_table` |
| LOCK TABLES metadata/control read | `lock tables app.users read, mart.user_summary write` | `lock_tables` |
| MySQL control, account, admin, and dynamic SQL statements | `unlock tables`, `set session ...`, `start transaction`, `commit`, `rollback`, `do sleep(1)`, `call p()`, `prepare`, `execute`, `deallocate prepare`, `create procedure`, `create trigger`, `create event`, `create user`, `grant`, `revoke`, `flush`, `kill`, `reset` | `unlock_tables`, `set_session_statement`, `start_transaction`, `commit_statement`, `rollback_statement`, `do_statement`, `call_statement`, `prepare_statement`, `execute_statement`, `deallocate_prepare`, `create_procedure`, `create_trigger`, `create_event`, `create_user`, `grant_privileges`, `revoke_privileges`, `flush_privileges`, `kill_query`, `reset_master` |
| MySQL schema/database control DDL | `create database ...`, `drop schema ...` | `create_database`, `drop_schema` |

Implemented MySQL column-level lineage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| Direct single-table projection | `select id as user_id, name from app.users` | `select_basic` |
| MySQL SELECT option projection | `select high_priority sql_calc_found_rows id as user_id from app.users` | `select_with_mysql_options` |
| Alias-qualified JOIN projection | `select u.id, o.amount from users u join orders o` | `join_projection` |
| NATURAL JOIN projection | `select u.id, o.amount from users u natural join orders o` | `natural_join_projection` |
| STRAIGHT_JOIN projection | `select straight_join u.id, o.amount from users u straight_join orders o` | `select_straight_join` |
| Index hint projection | `select u.id from users u force index (...)` | `select_force_index` |
| Backtick-qualified direct projections | ``select u.`department_id` from app.users u`` | `backtick_qualified_direct_projection` |
| Case-insensitive derived column propagation | `select user_id from (select id as User_ID from app.users) u` | `derived_case_insensitive_column_projection` |
| Single-level aliased subquery direct propagation | `select q.c from (select a as c from app.s) q` | `subquery_column_projection` |
| Single CTE direct propagation | `with q as (select a as c from app.s) select c from q` | `cte_column_projection` |
| Recursive CTE base-column propagation | `with recursive q as (...) select id from q` | `with_recursive_select` |
| UNION column sources merged by position | `select a as c1 from s1 union all select b from s2` | `union_column_projection` |
| INTERSECT column sources merged by position | `select a as c1 from s1 intersect select b from s2` | `intersect_column_projection` |
| EXCEPT column sources merged by position | `select a as c1 from s1 except select b from s2` | `except_column_projection` |
| INSERT target column list mapping | `insert into mart.t(c1, c2) select a, b from app.s` | `insert_select` |
| INSERT over UNION ALL target column lineage | `insert into t(c1) select a from s1 union all select b from s2` | `insert_union_column_lineage` |
| INSERT over INTERSECT target column lineage | `insert into t(c1) select a from s1 intersect select b from s2` | `insert_intersect_column_lineage` |
| INSERT over EXCEPT target column lineage | `insert into t(c1) select a from s1 except select b from s2` | `insert_except_column_lineage` |
| INSERT IGNORE target column list mapping | `insert ignore into mart.t(c1, c2) select a, b from app.s` | `insert_ignore_select` |
| INSERT priority modifier target mapping | `insert low_priority ignore into mart.t(c1, c2) select a, b from app.s` | `insert_low_priority_ignore_select` |
| INSERT SELECT ORDER BY/LIMIT target mapping | `insert into mart.t(c1, c2) select a, b from app.s order by b limit ...` | `insert_select_order_limit` |
| INSERT partition target column mapping | `insert into mart.t partition (p1) (c1, c2) select a, b from app.s` | `insert_partition_select` |
| INSERT duplicate-key SELECT and update mapping | `insert into mart.t(c1) select a from app.s on duplicate key update c1 = values(c1)` | `insert_select_on_duplicate` |
| WITH before INSERT SELECT target mapping | `with q as (...) insert into mart.t(c1) select q.c1 from q` | `with_insert_select` |
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
| Script-local temporary table column propagation | `create temporary table tmp as select id as c1 from app.s; insert into mart.t(c1) select c1 from tmp` | `script_temp_table_lineage` |
| CREATE VIEW output column targets | `create view mart.v as select u.id from app.users u` | `create_view` |
| CREATE VIEW declared-column target names | `create view mart.v(c1, c2) as select a as x, b as y from app.s` | `create_view_declared_columns` |
| CREATE VIEW over aliased/expression/aggregate projections | `create view mart.v as select a as c1, upper(b), count(c) ...` | `create_view_expression_projection` |
| CREATE VIEW options output column targets | `create algorithm=merge sql security invoker view mart.v as select id as c1 from app.s` | `create_view_with_options` |
| CREATE VIEW DEFINER output column targets | `create definer='u'@'%' view mart.v as select id as c1 from app.s` | `create_view_with_definer` |
| CREATE VIEW over CTE output column targets | `create view mart.v as with q as (...) select c1 from q` | `create_view_as_with_select` |
| ALTER VIEW output column targets | `alter view mart.v(c1, c2) as select a, b from app.s` | `alter_view` |
| MySQL function expression lineage | `select ifnull(nickname, name), coalesce(phone, email) from app.s` | `mysql_function_expression_projection` |
| MySQL JSON arrow expression lineage | `select payload->>'$.id' from app.events` | `json_extract_expression` |
| MySQL BINARY and COLLATE expression lineage | `select binary name from app.users where name collate ...` | `binary_collate_expression` |
| MySQL DIV, MOD, and bitwise expression lineage | `select amount div quantity, score mod 10, flags & 4 from app.orders` | `div_mod_bit_expression` |
| CREATE OR REPLACE VIEW output column targets | `create or replace view mart.v as select id as c1 from app.s` | `create_or_replace_view` |
| CREATE TEMPORARY TABLE output column targets | `create temporary table mart.t as select id as c1 from app.s` | `create_temporary_table_as_select` |
| UPDATE SET direct assignment mapping | `update mart.t t join app.s s ... set t.c = s.c` | `update_join` |
| UPDATE modifier assignment mapping | `update low_priority ignore mart.t join app.s ... set t.c = s.c` | `update_low_priority_ignore` |
| UPDATE SET constant assignment target | `update mart.t set status = 'active'` | `update_join` |
| Multi-table UPDATE assignment mapping | `update mart.t, app.s set mart.t.c = app.s.c, app.s.flag = 1` | `update_multi_table` |
| UPDATE SET expression dependencies | `update mart.t join app.s on ... set c1 = upper(s.c2), c3 = s.c4 + t.c5` | `update_expression_assignment` |
| UPDATE JOIN derived query assignment dependencies | `update mart.t join (select c2 from app.s) q on ... set c1 = q.c2` | `update_join_derived_assignment` |
| WITH before UPDATE JOIN assignment dependencies | `with q as (...) update mart.t join q on ... set c1 = q.c2` | `with_update_join` |
| CASE expression dependencies | `select case when status = 'A' then score else 0 end as c from t` | `case_expression` |
| Multi-branch CASE expression dependencies | `select case when status = 'A' then score when status = 'P' then pending_score else default_score end from t` | `complex_case_expression` |
| CAST, function, and arithmetic expression dependencies | `select cast(id as char), coalesce(name, nickname), price * quantity from t` | `common_expression_projection` |
| Nested function expression dependencies | `select coalesce(lower(name), upper(nickname), cast(id as char)) from t` | `nested_function_projection` |
| Scalar subquery projection dependencies | `select (select max(amount) from orders) as max_amount from users` | `scalar_subquery_projection` |
| IN subquery predicate column usage | `where id in (select user_id from sessions)` | `in_subquery_column_usage` |
| ORDER BY projection alias column usage | `select c as alias from t order by alias` | `projection_alias_order_usage` |
| ORDER BY ordinal position compatibility | `select id as user_id from t order by 1 desc` | `order_by_position` |
| ORDER BY expression column usage | `select id from t order by coalesce(updated_at, created_at)` | `order_by_expression_column_usage` |
| GROUP BY aggregate expression dependencies | `select user_id, count(order_id), sum(amount) from t group by user_id` | `aggregate_expression_projection` |
| GROUP BY WITH ROLLUP aggregate dependencies | `select region, count(order_id) from t group by region with rollup` | `group_by_with_rollup` |
| GROUP BY direction and ROLLUP usages | `select region, count(order_id) from t group by region desc with rollup` | `group_by_direction_rollup` |
| DISTINCT aggregate dependencies and HAVING usage | `select count(distinct user_id) ... group by region having count(distinct order_id) > ...` | `distinct_aggregate_column_usage` |
| GROUP BY expression column usage | `select lower(region), count(order_id) from t group by lower(region)` | `group_by_expression_column_usage` |
| Window function expression dependencies and window clause usages | `select row_number() over (partition by k order by ts), sum(v) over (...) from t` | `window_function_lineage` |
| Window frame expression dependencies | `sum(v) over(partition by k order by ts rows between ... preceding and current row)` | `window_frame_rows` |
| Named WINDOW clause expression dependencies and usages | `select sum(v) over w from t window w as (partition by k order by ts)` | `named_window_clause` |
| Backquoted non-ASCII column identifiers | `` select `用户ID` as `用户标识` from `业务库`.`用户表` `` | `backquoted_identifiers` |
| EXPLAIN wrapped SELECT output columns | `explain select id as user_id from app.users` | `explain_select` |
| EXPLAIN FORMAT/ANALYZE output columns | `explain format=json select id as user_id ...` | `explain_format_json_select`, `explain_analyze_select` |
| SELECT INTO OUTFILE/DUMPFILE output columns | `select id, name into outfile '...' from app.users` | `select_into_outfile`, `select_into_dumpfile`, `select_into_outfile_tail` |
| User-variable assignment expression lineage | `select @v := id as v from app.users` | `select_user_variable_assignment` |

Implemented MySQL clause-level column usage scenarios:

| Scenario | Example shape | Case id |
| --- | --- | --- |
| WHERE, GROUP BY, HAVING, and ORDER BY source columns | `select u.id, count(o.id) from users u join orders o where ... group by u.id having ... order by ...` | `clause_column_usage` |
| MySQL SELECT option predicate columns | `select high_priority ... from users where ...` | `select_with_mysql_options` |
| NATURAL JOIN predicate columns | `select ... from users natural join orders where users.status = ...` | `natural_join_projection` |
| STRAIGHT_JOIN predicate columns | `select ... from users u straight_join orders o on ... where ...` | `select_straight_join` |
| Index hint predicate columns | `select ... from users u force index (...) where ...` | `select_force_index` |
| SELECT locking clause predicate columns | `select ... from users where ... for update` | `select_for_update`, `select_lock_in_share_mode` |
| MySQL regex predicate columns | `where name regexp '^A' and phone rlike '...'` | `regexp_predicate_column_usage` |
| MySQL null-safe equality predicate columns | `on a.id <=> b.id where a.c <=> b.c` | `null_safe_equal_predicate` |
| Bang logical NOT predicate columns | `where !(status = ... or name like ...)` | `bang_logical_not_predicate` |
| MySQL boolean predicate columns | `where is_active is true and deleted is not false` | `is_true_false_predicate` |
| MySQL JSON and COLLATE predicate columns | `where payload->>'$.type' = ...`, `where name collate ... = ...` | `json_extract_expression`, `binary_collate_expression` |
| UPDATE JOIN and WHERE source columns | `update users u join orders o on ... set ... where ...` | `dml_predicate_column_usage` |
| Multi-table UPDATE WHERE source columns | `update users u, orders o set ... where u.id = o.user_id` | `update_multi_table` |
| Self-join aliases | `select e.id, m.name from employees e left join employees m on e.manager_id = m.id` | `self_join_column_usage` |
| JOIN USING source columns | `select u.id from users u join orders o using (id)` | `join_using_column_usage` |
| Chained JOIN USING source columns | `select u.id from users u join orders o using (id) join payments p using (id)` | `join_using_multi_table_scope` |
| JOIN USING scoped inside comma-separated relations | `select b.id from audit a, users b join orders o using (id)` | `join_using_comma_scope` |
| JOIN USING over CTE references | `with u as (...), o as (...) select ... from u join o using (id)` | `join_using_derived_scope` |
| JOIN USING over derived subqueries | `select ... from (select ...) u join (select ...) o using (id)` | `join_using_subquery_scope` |
| JOIN ON over CTE references | `with u as (...), o as (...) select ... from u join o on u.id = o.user_id` | `join_on_derived_scope` |
| JOIN ON over derived subqueries | `select ... from (select ...) u join (select ...) o on u.id = o.user_id` | `join_on_subquery_scope` |
| UNION branch WHERE source columns | `select id from app.s1 where ... union all select id from app.s2 where ...` | `set_operation_clause_column_usage` |
| INSERT/REPLACE query ORDER BY source columns | `insert/replace into mart.t select ... from app.s order by ...` | `insert_select_order_limit`, `replace_select_order_limit` |
| DELETE derived JOIN predicate columns | `delete t from mart.t t join (select id from app.s) q on t.id = q.id` | `delete_join_derived` |
| Multi-table DELETE predicate columns | `delete t1, t2 from mart.t1 join app.t2 on ... where ...` | `delete_multi_table` |
| WITH before DELETE JOIN predicate columns | `with q as (...) delete t from mart.t t join q on t.id = q.id` | `with_delete_join` |
| EXISTS subquery predicate column usage | `where exists (select 1 from app.orders o where o.user_id = u.id)` | `exists_subquery_column_usage` |
| DELETE WHERE subquery predicate columns | `delete from ads.t where user_id in (select id from ods.s)` | `delete_with_subquery` |
| EXPLAIN wrapped SELECT predicate columns | `explain select ... from app.s where ...` | `explain_select` |
| EXPLAIN FORMAT/ANALYZE predicate columns | `explain analyze select ... from app.s where ...` | `explain_format_json_select`, `explain_analyze_select` |
| SELECT INTO OUTFILE/DUMPFILE predicate columns | `select ... into outfile '...' from app.users where ...` | `select_into_outfile`, `select_into_dumpfile`, `select_into_outfile_tail` |

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
| Window function argument and spec sources | `select row_number() over (partition by user_id order by created_at) from ods.orders` | `window_column_projection` |
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

LineSQL distinguishes projection lineage from columns used by filtering, joining, grouping, HAVING, ordering, and MERGE predicates. These fields are returned as `columnUsages` with usage types such as `WHERE`, `JOIN_ON`, `GROUP_BY`, `HAVING`, `ORDER_BY`, `MERGE_ON`, and `MERGE_WHEN`.

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
| Non-lineage session statements | `USE`, `SET CATALOG`, and `RESET` parse without lineage diagnostics | `use_database`, `set_catalog`, `reset_configuration` |
| Namespace and catalog statements | Namespace DDL and table-free metadata reads parse without table/column diagnostics | `create_namespace`, `drop_namespace`, `show_namespaces`, `show_catalogs`, `analyze_tables` |
| Function/procedure/variable/cursor statements | Function DDL, CALL, variable, and cursor control statements parse without table/column diagnostics | `create_function`, `call_procedure`, `create_variable`, `declare_cursor` |
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
