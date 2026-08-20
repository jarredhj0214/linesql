# Spark Stage 1

Spark is the first dialect implementation target for LineSQL.

This stage should create a small end-to-end lineage parser path before adding other dialects.

## Goals

- Keep `LineSql.parse(sql)` as the primary user API.
- Support table-level lineage and initial column-level lineage.
- Use ANTLR4 for Spark SQL parsing.
- Use Apache Spark's official SQL grammar as the Spark dialect grammar baseline.
- Keep Spark-specific classes inside `linesql-dialect-spark`.
- Use real SQL cases as regression assets.

## SQL Case Assets

Spark SQL cases live under:

```text
linesql-dialect-spark/src/test/resources/sql/spark/
```

- `manifest.json` records case metadata and expected lineage.
- `cases/*.sql` stores the SQL text.

JUnit tests must read SQL from these resources instead of embedding long SQL strings in Java code.

Every newly supported Spark scenario must update both:

- `docs/supported-scenarios.md`
- `linesql-dialect-spark/src/test/resources/sql/spark/manifest.json`

## Initial Statement Coverage

- `SELECT`
- `TABLE ...`
- `INSERT INTO ... SELECT`
- `INSERT OVERWRITE ... SELECT`
- `INSERT OVERWRITE ... PARTITION (...) (...) SELECT`
- `INSERT INTO ... REPLACE WHERE/ON ... SELECT`
- `INSERT INTO ... REPLACE USING ... SELECT`
- `INSERT OVERWRITE DIRECTORY ... SELECT`
- `LOAD DATA ... INTO TABLE`
- `DROP TABLE`
- `TRUNCATE TABLE`
- `ALTER TABLE ... RENAME TO ...`
- `ALTER TABLE` column/property/partition maintenance
- `ALTER TABLE ... RECOVER PARTITIONS` and `MSCK REPAIR TABLE`
- `COMMENT ON TABLE` / `COMMENT ON COLUMN`
- `EXPLAIN ...` wrapped statement lineage
- Table metadata reads: `ANALYZE TABLE`, `DESCRIBE TABLE`, `SHOW CREATE TABLE`, `SHOW COLUMNS`, `SHOW PARTITIONS`, `REFRESH TABLE`
- Index maintenance: `CREATE INDEX ... ON TABLE`, `DROP INDEX ... ON TABLE`
- FROM-first multi-insert table lineage
- `CREATE TABLE ... AS SELECT`
- `CREATE TABLE ... USING ... PARTITIONED BY ... AS SELECT`
- `CREATE OR REPLACE TABLE ... AS SELECT`
- `CREATE MATERIALIZED VIEW ... AS SELECT`
- `CREATE STREAMING TABLE ... AS SELECT`
- `CREATE TABLE ... LIKE ...`
- `CREATE VIEW ... AS SELECT`
- `CREATE VIEW ... AS code literal` degradation
- `CREATE TEMPORARY VIEW ... USING ... OPTIONS ...`
- `ALTER VIEW ... AS SELECT`
- `WITH` / CTE
- `JOIN`
- `UNION`
- `PIVOT` / `UNPIVOT` table lineage
- `TRANSFORM ... USING ...` table lineage
- `STREAM(table)` source lineage
- CHANGES relation source lineage
- UNNEST and JSON_TABLE generated-column source lineage, including alias-qualified generated columns
- Table-valued function `TABLE` argument source lineage
- Pipe query source lineage for `SELECT`, `WHERE`, `DROP`, `EXTEND`, `AGGREGATE`, `JOIN`, and set operators
- `MERGE INTO`
- `MERGE INTO ... USING (subquery)`
- `UPDATE ... SET ... WHERE ...`
- `DELETE ... WHERE ...`
- `CACHE TABLE ... AS SELECT`
- Script-local `CACHE TABLE ... AS SELECT` propagation
- `UNCACHE TABLE` cleanup for script-local cache tables
- Script-local `CREATE TEMPORARY VIEW ... AS SELECT`
- `CREATE FLOW ... AS INSERT ... SELECT` table and direct column lineage
- `CREATE FLOW ... AS AUTO CDC ...` source/target lineage with CDC-specific degradation
- `DROP VIEW` cleanup for script-local temporary views
- Bad SQL recovery in multi-statement scripts
- Dynamic SQL degradation for `EXECUTE IMMEDIATE`
- Parse-only non-lineage handling for `USE`, `SET CATALOG`, and `RESET`
- Parse-only namespace DDL and table-free metadata reads
- Parse-only function/procedure/variable/cursor control statements
- Parse-only resource/cache control and table-free SHOW/DESCRIBE variants
- Unquoted scheduler placeholders in expressions
- Backquoted non-ASCII identifiers

## Initial Column Lineage Coverage

- Direct projection: `select a as b`
- Qualified projection: `select t.a`
- Function expression: `select lower(name) as name_lower`
- Constants: `select 1 as flag`
- Unaliased constants use the expression text as the output target and no sources
- Simple arithmetic: `select price * quantity as amount`
- Aggregate expressions: `count(order_id)`, `sum(amount)`
- Unaliased aggregate expressions use the expression text as the output target
- Aliased scalar subquery projections, including outer SELECT statements without a FROM clause
- Scalar subquery predicates are isolated from outer projection source resolution
- HAVING subqueries are isolated from outer derived table column resolution
- Window expressions: function arguments plus partition/order columns
- Qualified JOIN projection: `select u.id, o.amount from users u join orders o`
- Unique qualified JOIN predicate hints can resolve otherwise unqualified projection columns
- Partially resolved expression projections keep the source columns that can be mapped safely
- STREAM table direct projection: `select s.id from stream(events) s`
- CHANGES relation direct projection
- Spark `range` table-valued function generated `id` column
- UNNEST generated column propagation
- JSON_TABLE generated column propagation
- Alias-qualified UNNEST and JSON_TABLE generated column propagation
- Function or UDTF-style multi-column aliases: `select f(a, b) as (c1, c2)`
- PIVOT generated aggregate column propagation for aliased pivot values
- Single-value UNPIVOT generated name/value propagation
- Multi-value UNPIVOT generated value propagation by column-set position
- Pipe SELECT direct projection
- Pipe DROP then SELECT direct projection
- Pipe EXTEND generated column projection
- Standalone Pipe AGGREGATE output projection
- Pipe AGGREGATE generated column projection into a following SELECT
- Pipe JOIN direct projection
- Nested field paths: `profile.city`, `u.profile.city`
- Derived expression root field propagation: `from_json(content, ...) as values` followed by `values.vin`
- LATERAL VIEW generated column propagation: `lateral view explode(items) e as item`
- INSERT target column list mapping: `insert into t(c1, c2) select a, b from s`
- INSERT BY NAME projection target mapping
- INSERT REPLACE WHERE BY NAME projection target mapping
- INSERT REPLACE USING BY NAME projection target mapping
- INSERT target column list over CTE and subquery propagation
- UNION column lineage merged by output column position
- EXCEPT and INTERSECT column inputs by output column position
- Pipe UNION, EXCEPT, and INTERSECT column inputs by output column position when the right side has explicit projections
- EXPLAIN wrapped SELECT projection lineage
- CREATE VIEW and CTAS output table targets
- CREATE VIEW column list target names
- Single-level CTE direct column propagation
- Chained CTE direct column propagation
- CTE column alias list propagation
- Single-level aliased subquery direct column propagation
- Case-insensitive derived and generated column lookup
- Unqualified projections can fall back to the unique visible base table when adjacent derived relations have known output columns that do not expose the projected column
- Unqualified projections can fall back to a unique derived wildcard source when adjacent derived relations have known output columns that do not expose the projected column
- Explicit derived output columns take precedence over adjacent schema-free wildcard sources, so resolvable columns are not lost in mixed explicit/wildcard joins
- Backtick-qualified direct projections such as ``t1.`department_id``` are normalized before direct-column target inference
- Known derived columns are preserved when `select *` also carries schema-free wildcard inputs
- Script-local temporary view propagation

Unresolved cases should produce diagnostics instead of failing the entire result.

See [Supported Scenarios](../supported-scenarios.md) for the current executable scenario matrix.

See [Spark Coverage Audit](spark-coverage-audit.md) for the implementation checklist that maps Spark grammar families to current LineSQL behavior.

## Grammar Strategy

The Spark dialect uses grammar files adapted from Apache Spark's official ANTLR grammar:

- `SqlBaseLexer.g4`
- `SqlBaseParser.g4`

The files retain Apache-2.0 license headers and are recorded in `THIRD_PARTY_NOTICES.md`.

Lineage extraction is implemented independently in LineSQL visitors. Parse coverage and lineage coverage are tracked separately: a statement may parse successfully before LineSQL has full lineage extraction for that statement family.

## Non-goals

- Complete `select *` expansion without schema metadata.
- Full function-specific UDTF and lateral view output semantics.
- Multi-insert per-target column lineage.
- Query optimization or execution planning.
