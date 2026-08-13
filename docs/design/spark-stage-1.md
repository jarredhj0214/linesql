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

## Initial Statement Coverage

- `SELECT`
- `INSERT INTO ... SELECT`
- `INSERT OVERWRITE ... SELECT`
- `CREATE TABLE ... AS SELECT`
- `CREATE VIEW ... AS SELECT`
- `WITH` / CTE
- `JOIN`
- `UNION`

## Initial Column Lineage Coverage

- Direct projection: `select a as b`
- Qualified projection: `select t.a`
- Function expression: `select lower(name) as name_lower`
- Constants: `select 1 as flag`
- Simple arithmetic: `select price * quantity as amount`

Unresolved cases should produce diagnostics instead of failing the entire result.

## Grammar Strategy

The Spark dialect uses grammar files adapted from Apache Spark's official ANTLR grammar:

- `SqlBaseLexer.g4`
- `SqlBaseParser.g4`

The files retain Apache-2.0 license headers and are recorded in `THIRD_PARTY_NOTICES.md`.

Lineage extraction is implemented independently in LineSQL visitors. Parse coverage and lineage coverage are tracked separately: a statement may parse successfully before LineSQL has full lineage extraction for that statement family.

## Non-goals

- Complete `select *` expansion without schema metadata.
- Full UDTF and lateral view column propagation.
- Query optimization or execution planning.
