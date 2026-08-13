# LineSQL

JVM-native, ANTLR4-based, lineage-first SQL parser framework for real-world data platform SQL.

一个 JVM 原生、血缘优先、面向真实数据平台 SQL 的多方言解析框架。

## Positioning

This project is not a SQL executor, optimizer, or complete query planner. It focuses on extracting unified lineage results from production SQL used in data platforms.

Compared with common alternatives:

- Lighter than Calcite: no full planning or optimization pipeline.
- More data-platform oriented than generic SQL parsers: Hive, Spark, StarRocks, Flink, MySQL, Oracle, and SQL Server are first-stage targets.
- JVM-native: easy to embed in Java data platform services.
- Lineage-first: parser output is a unified lineage model, not only an AST.
- Dialect auto-detection first: users should not need to know which SQL engine produced a statement.
- Production SQL friendly: multi-statement scripts, variables, temporary views, UDTF, Chinese identifiers, complex strings, bad SQL isolation, and partial results are core design goals.
- Independently implemented: external source or grammar reuse requires explicit license review.

## Modules

- `linesql-core`: model, SPI, facade, statement splitter, dialect detector, and shared utilities.
- `linesql-dialect-spark`: Spark dialect parser module.
- `linesql-dialect-hive`: Hive dialect parser module.
- Future first-stage dialect modules: StarRocks, Flink, MySQL, Oracle, and SQL Server.
- `linesql-cli`: command-line entry module.

## First-stage Scope

LineSQL is currently in requirements and architecture design. The first-stage implementation targets common SQL lineage scenarios in modern data platforms:

- Java 11.
- ANTLR4-based dialect parsers.
- Automatic dialect detection through `LineSql.parse(sql)`.
- Table-level lineage and column-level lineage.
- Dialects: Hive, Spark, StarRocks, Flink, MySQL, Oracle, and SQL Server.
- Graceful degradation: return table lineage and warnings when column lineage cannot be fully resolved.
- Real production SQL cases as primary test assets.

## Usage

Build:

```bash
./scripts/mvn-jdk11 clean test
```

Planned API:

```java
LineageResult result = LineSql.parse(sql);
List<LineageResult> results = LineSql.parseScript(script);
```

Target output shape:

```json
[
  {
    "version": "0.1",
    "dialect": "SPARK",
    "dialectConfidence": 0.92,
    "statementType": "INSERT",
    "inputTables": [
      {
        "catalog": null,
        "schema": "ods",
        "name": "s"
      }
    ],
    "outputTables": [
      {
        "catalog": null,
        "schema": "ads",
        "name": "t"
      }
    ],
    "columnLineage": [],
    "diagnostics": []
  }
]
```

## Design Docs

- [Architecture Vision](docs/design/architecture.md)
- [Development](docs/development.md)
- [License Policy](docs/legal/license-policy.md)
- [Third-party Notices](THIRD_PARTY_NOTICES.md)

## Roadmap

- Stage 1: production-ready lineage parser foundation for Hive, Spark, StarRocks, Flink, MySQL, Oracle, and SQL Server.
- Stage 2: richer column lineage, UDTF, temporary views, CTE column propagation, and compatibility matrix.
- Stage 3: anonymized production SQL corpus, CLI improvements, syntax diagnostics, and editor-facing keyword support.
