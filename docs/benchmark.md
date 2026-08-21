# Benchmark and Coverage

LineSQL treats SQL cases as compatibility contracts. A feature is not considered supported until it has a SQL case, an expected manifest entry, and a regression test path.

This page explains the current coverage snapshot and how benchmark numbers should be interpreted.

## Current Case Corpus

The repository contains a case-backed regression corpus across bundled dialect modules.

| Dialect | Cases | Column-lineage cases | Diagnostic cases |
| --- | ---: | ---: | ---: |
| Spark | 182 | 92 | 14 |
| Hive | 65 | 53 | 0 |
| Flink | 74 | 62 | 0 |
| StarRocks | 124 | 73 | 0 |
| MySQL | 223 | 120 | 0 |
| Oracle | 70 | 59 | 0 |
| SQL Server | 70 | 61 | 0 |
| PostgreSQL | 18 | 16 | 0 |
| OceanBase | 8 | 6 | 0 |
| **Total** | **834** | **542** | **14** |

These numbers describe the public regression suite, not a claim of full SQL grammar coverage.

## What the Numbers Mean

LineSQL reports practical lineage coverage in several layers:

| Layer | Meaning |
| --- | --- |
| Statement recognized | The parser selected a statement type and did not treat the SQL as an opaque unknown statement. |
| Table lineage extracted | Source and target or affected tables were found. |
| Column lineage extracted | Source-to-target column edges were emitted for supported projection or write shapes. |
| Clause usage extracted | Columns used by clauses such as `WHERE`, `JOIN`, `GROUP_BY`, `HAVING`, and `ORDER_BY` were emitted. |
| Partial diagnostics | Unsupported fragments or parse limitations were reported without losing usable results. |

This layered view is important because production SQL parsing is not binary. A difficult statement can still be useful if table lineage is preserved and column lineage is marked as partial.

## Running the Regression Corpus

```bash
./scripts/mvn-jdk11 -B test
```

The core quality test prints a dialect-by-dialect summary from manifest files:

```text
LineSQL SQL case coverage
| Dialect | Cases | Column cases | Diagnostic cases | Statement types |
```

When adding support for a new SQL shape, update:

```text
linesql-dialect-*/src/test/resources/sql/<dialect>/cases/*.sql
linesql-dialect-*/src/test/resources/sql/<dialect>/manifest.json
docs/supported-scenarios.md
docs/grammar-coverage-matrix.md
```

## Production SQL Benchmark Policy

Production SQL benchmarks are useful only when sensitive names are anonymized and the methodology is clear.

Recommended benchmark dimensions:

| Metric | Description |
| --- | --- |
| Total statements | Number of SQL statements after script splitting. |
| Recognized statements | Statements with a concrete `StatementType` other than `UNKNOWN` or parser error. |
| Table-lineage success | Statements where at least one source, target, or affected table is extracted when expected. |
| Column-lineage success | Statements where expected source-to-target column edges are emitted. |
| Partial results | Statements with useful lineage and non-fatal warnings. |
| Hard failures | Statements with no useful lineage due to unsupported syntax or parse errors. |

For public reports, do not publish raw business table names, column names, comments, scheduler variables, tenant identifiers, or domain-specific constants unless they are intentionally synthetic.

## Release Readiness Signal

For an alpha release, LineSQL should publish:

| Requirement | Status |
| --- | --- |
| Maven Central artifacts | Required |
| Full regression corpus passes | Required |
| README support matrix updated | Required |
| Supported scenarios updated | Required for user-visible parser changes |
| Grammar coverage matrix updated | Required for dialect-scope changes |
| Production benchmark report | Recommended when representative anonymized data is available |

## Interpretation Guidelines

Avoid describing coverage as "complete" unless the statement is tied to a dialect version and a grammar domain.

Prefer precise claims:

- "Spark `INSERT SELECT`, CTAS, view, CTE, set operations, common expressions, and clause usages are covered by regression cases."
- "MySQL table lineage and common column lineage are active, with ongoing work on dialect-specific DDL and procedural syntax."
- "Unknown or partially supported SQL returns diagnostics instead of silently pretending the lineage is complete."

This makes the project credible to users who will test it against real warehouse SQL.
