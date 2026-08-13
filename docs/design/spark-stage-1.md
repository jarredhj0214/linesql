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
- `DROP VIEW` cleanup for script-local temporary views
- Bad SQL recovery in multi-statement scripts
- Unquoted scheduler placeholders in expressions
- Backquoted non-ASCII identifiers

## Initial Column Lineage Coverage

- Direct projection: `select a as b`
- Qualified projection: `select t.a`
- Function expression: `select lower(name) as name_lower`
- Constants: `select 1 as flag`
- Simple arithmetic: `select price * quantity as amount`
- Aggregate expressions: `count(order_id)`, `sum(amount)`
- Window expressions: function arguments plus partition/order columns
- Qualified JOIN projection: `select u.id, o.amount from users u join orders o`
- STREAM table direct projection: `select s.id from stream(events) s`
- CHANGES relation direct projection
- UNNEST generated column propagation
- JSON_TABLE generated column propagation
- Alias-qualified UNNEST and JSON_TABLE generated column propagation
- Pipe SELECT direct projection
- Pipe DROP then SELECT direct projection
- Pipe JOIN direct projection
- Nested field paths: `profile.city`, `u.profile.city`
- LATERAL VIEW generated column propagation: `lateral view explode(items) e as item`
- INSERT target column list mapping: `insert into t(c1, c2) select a, b from s`
- INSERT BY NAME projection target mapping
- INSERT REPLACE WHERE BY NAME projection target mapping
- INSERT REPLACE USING BY NAME projection target mapping
- INSERT target column list over CTE and subquery propagation
- UNION column lineage merged by output column position
- EXCEPT and INTERSECT column inputs by output column position
- EXPLAIN wrapped SELECT projection lineage
- CREATE VIEW and CTAS output table targets
- CREATE VIEW column list target names
- Single-level CTE direct column propagation
- Chained CTE direct column propagation
- CTE column alias list propagation
- Single-level aliased subquery direct column propagation
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
- Full UDTF and lateral view column propagation.
- Multi-insert per-target column lineage.
- Query optimization or execution planning.
