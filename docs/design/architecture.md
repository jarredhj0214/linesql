# Architecture Vision

LineSQL is a JVM-native, ANTLR4-based, lineage-first SQL parser framework for real-world data platform SQL.

The goal is to provide a production-oriented lineage model that works across SQL engines without requiring users to choose a dialect manually.

## Product Principles

- Lineage-first: table and column lineage are the primary output.
- Automatic dialect detection: `LineSql.parse(sql)` is the main entry point.
- Production SQL tolerant: partial results are better than total failure.
- Multi-dialect by design: every engine has an isolated parser module.
- Catalog-friendly: APIs should fit Java data catalog and governance services.
- Grammar governance: external grammar reuse requires explicit license review.

## First-stage Dialects

The first stage targets the engines needed by `dip-lake-catalog`:

- Hive
- Spark
- StarRocks
- Flink
- MySQL
- Oracle
- SQL Server

## API Direction

Primary API:

```java
LineageResult result = LineSql.parse(sql);
List<LineageResult> results = LineSql.parseScript(script);
```

Advanced API:

```java
LineageResult result = LineSql.parse(sql, ParseOptions.builder()
    .dialectHints(List.of(SqlDialect.SPARK, SqlDialect.HIVE))
    .build());
```

Users should not need to instantiate dialect-specific parsers. Dialect-specific parsers remain internal SPI implementations.

## Internal Flow

```text
SQL or script
  -> statement splitting
  -> dialect detection
  -> dialect parser
  -> parse tree
  -> table lineage extraction
  -> column lineage extraction
  -> unified LineageResult
```

## Core Modules

- `linesql-core`: public API, model, SPI, parse options, diagnostics, lineage graph abstractions.
- `linesql-dialect-*`: ANTLR grammar, parser adapter, and lineage visitor for one SQL engine.
- `linesql-cli`: command-line JSON output and regression debugging.
- `linesql-tests`: future shared SQL corpus and compatibility matrix module.

## Dialect Detection

Dialect detection should return candidates, not only one enum.

```text
SPARK 0.72
HIVE 0.61
MYSQL 0.20
```

The selected dialect is the highest-confidence candidate after applying parse options, catalog context, and fallback policy.

## Lineage Strategy

Table lineage and column lineage should be separate extraction phases.

- Table lineage should be robust and available for more statements.
- Column lineage can return partial mappings with warnings.
- Failure to resolve column lineage must not erase table lineage.

## Differentiation Goals

- Public API optimized for lineage rather than statement metadata.
- Automatic dialect detection as the default entry point.
- Unified table and column lineage model across dialects.
- Explicit graceful degradation and partial-result semantics.
- Legal and grammar provenance governance from day one.
- Production SQL corpus as a first-class test asset.
