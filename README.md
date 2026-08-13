# LineSQL

JVM-native, lineage-first SQL parser framework for real-world data platform SQL.

一个 JVM 原生、血缘优先、面向真实数据平台 SQL 的多方言解析框架。

## Positioning

This project is not a SQL executor, optimizer, or complete query planner. It focuses on extracting unified lineage results from production SQL used in data platforms.

Compared with common alternatives:

- Lighter than Calcite: no full planning or optimization pipeline.
- More data-platform oriented than generic SQL parsers: Spark, Hive, Flink, StarRocks, Trino/Presto, and MySQL are first-class targets.
- JVM-native: easy to embed in Java data platform services.
- Lineage-first: parser output is a unified lineage model, not only an AST.
- Production SQL friendly: multi-statement scripts, variables, temporary views, UDTF, Chinese identifiers, complex strings, bad SQL isolation, and partial results are core design goals.

## Modules

- `linesql-core`: model, SPI, facade, statement splitter, dialect detector, and shared utilities.
- `linesql-dialect-spark`: Spark dialect parser module.
- `linesql-dialect-hive`: Hive dialect parser module.
- `linesql-cli`: command-line entry module.

## MVP Scope

The first version is still being scoped. The repository currently contains only the Maven multi-module skeleton, core lineage model, and parser SPI.

## Usage

Build:

```bash
mvn test
```

Run CLI:

```bash
mvn test
```

Target output shape:

```json
[
  {
    "dialect": "SPARK",
    "statementType": "INSERT",
    "sourceTables": [
      {
        "catalog": null,
        "schema": "ods",
        "name": "s"
      }
    ],
    "targetTables": [
      {
        "catalog": null,
        "schema": "ads",
        "name": "t"
      }
    ],
    "columnLineage": [],
    "warnings": [],
    "errors": []
  }
]
```

## Roadmap

- Stage 1: core project, Spark/Hive table-level lineage, CLI, real SQL test cases.
- Stage 2: Flink, StarRocks, MySQL, Trino/Presto.
- Stage 3: column-level lineage, UDTF, temporary views, CTE column propagation.
- Stage 4: anonymized production SQL corpus, compatibility matrix, richer CLI.
