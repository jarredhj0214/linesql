# LineSQL

JVM-native, ANTLR4-based, lineage-first SQL parser framework for real-world data platform SQL.

一个 JVM 原生、基于 ANTLR4、血缘优先、面向真实数据平台 SQL 的多方言解析框架。

## At A Glance / 一眼看懂

- **Not a SQL engine**: LineSQL does not execute, optimize, or plan SQL.
  **不是 SQL 引擎**：不执行 SQL，不做优化器，也不做完整查询规划。
- **Lineage-first**: the public output is a unified lineage model, not only an AST.
  **血缘优先**：输出统一血缘模型，而不仅是 AST。
- **JVM-native**: designed for Java data platform services.
  **JVM 原生**：方便 Java 数据平台服务直接接入。
- **Dialect auto-detection first**: users should not need to know the SQL engine in advance.
  **优先自动识别方言**：用户不应该必须提前知道 SQL 来自哪种引擎。
- **Production SQL friendly**: scripts, variables, temp views, UDTF, Chinese identifiers, bad SQL isolation, and partial results are first-class goals.
  **面向生产 SQL**：多语句脚本、变量、临时视图、UDTF、中文标识符、坏 SQL 隔离和 partial result 都是核心目标。

## Support Matrix / 支持矩阵

LineSQL is usable today for Spark, MySQL, and Hive lineage experiments. Flink and StarRocks modules are already registered as scaffolds. Oracle and SQL Server are planned.

LineSQL 目前已经可以用于 Spark、MySQL、Hive 血缘解析实验。Flink、StarRocks 的模块架子已经接入，Oracle 和 SQL Server 在规划中。

| Dialect | Status | Auto Detection | Lineage Coverage |
| --- | --- | --- | --- |
| Spark | Active parser / 可用 | Spark-specific syntax | Broad stage-1 table and column lineage |
| MySQL | Active MVP / 可用 MVP | MySQL-specific syntax | Common SELECT, write, DDL, DML, direct column mappings |
| Hive | Active MVP / 可用 MVP | Hive anchors | SELECT, JOIN, INSERT SELECT, CTAS, CREATE VIEW |
| Flink | Scaffold / 架子已接入 | Flink anchors | Not implemented yet |
| StarRocks | Scaffold / 架子已接入 | StarRocks anchors | Not implemented yet |
| Oracle | Planned / 规划中 | Planned | Planned |
| SQL Server | Planned / 规划中 | Planned | Planned |

Detailed, case-backed compatibility is tracked in [Supported Scenarios](docs/supported-scenarios.md).

详细、由 SQL case 支撑的兼容性记录见 [Supported Scenarios](docs/supported-scenarios.md)。

## Modules / 模块

- `linesql-core`: model, SPI, facade, statement splitter, dialect detector, and shared utilities.
- `linesql-dialect-spark`: Spark dialect parser.
- `linesql-dialect-mysql`: MySQL dialect parser.
- `linesql-dialect-hive`: Hive dialect parser.
- `linesql-dialect-flink`: Flink dialect parser scaffold.
- `linesql-dialect-starrocks`: StarRocks dialect parser scaffold.
- `linesql-cli`: command-line entry.

中文说明：

- `linesql-core`：模型、SPI、门面入口、语句拆分、方言识别和共享工具。
- `linesql-dialect-*`：各 SQL 方言解析模块。
- `linesql-cli`：命令行入口。

## Current Scope / 当前范围

LineSQL is in early implementation. The first-stage implementation targets common SQL lineage scenarios in modern data platforms.

LineSQL 仍处于早期实现阶段。第一阶段聚焦现代数据平台中常见的 SQL 血缘场景。

- Java 11
- ANTLR4-based dialect parsers
- Automatic dialect detection through `LineSql.parse(sql)`
- Table-level lineage and column-level lineage
- Graceful degradation when only partial lineage can be resolved
- Real production SQL cases as primary test assets

中文重点：

- 基于 Java 11。
- 方言解析器基于 ANTLR4。
- 通过 `LineSql.parse(sql)` 自动识别方言。
- 目标同时覆盖表级血缘和字段级血缘。
- 支持优雅降级：字段血缘解析不完整时，尽量返回表级血缘和诊断信息。
- 真实生产 SQL case 是核心测试资产。

## Usage / 使用方式

Build:

```bash
./scripts/mvn-jdk11 clean test
```

API:

```java
LineageResult result = LineSql.parse(sql);
List<LineageResult> results = LineSql.parseScript(script);
```

CLI:

```bash
./scripts/mvn-jdk11 -q -pl linesql-cli -am package
"/Applications/IntelliJ IDEA CE.app/Contents/jbr/Contents/Home/bin/java" \
  -jar linesql-cli/target/linesql-cli-0.1.0-SNAPSHOT.jar \
  "insert overwrite table ads.user_summary select id from ods.users"
```

Target output shape / 目标输出结构：

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

## Design Docs / 设计文档

- [Architecture Vision](docs/design/architecture.md)
- [Supported Scenarios](docs/supported-scenarios.md)
- [Spark Stage 1](docs/design/spark-stage-1.md)
- [Development](docs/development.md)
- [License Policy](docs/legal/license-policy.md)
- [Third-party Notices](THIRD_PARTY_NOTICES.md)

## Roadmap / 路线图

- **Stage 1**: production-ready lineage parser foundation for Hive, Spark, StarRocks, Flink, MySQL, Oracle, and SQL Server.
  **阶段 1**：搭建面向生产可用的多方言血缘解析基础。
- **Stage 2**: richer column lineage, UDTF, temporary views, CTE column propagation, and compatibility matrix.
  **阶段 2**：增强字段级血缘、UDTF、临时视图、CTE 字段传播和兼容性矩阵。
- **Stage 3**: anonymized production SQL corpus, CLI improvements, syntax diagnostics, and editor-facing keyword support.
  **阶段 3**：建设脱敏生产 SQL 语料、完善 CLI、语法诊断和面向编辑器的关键字支持。
