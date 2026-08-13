# LineSQL

JVM-native, ANTLR4-based, lineage-first SQL parser framework for real-world data platform SQL.

一个 JVM 原生、基于 ANTLR4、血缘优先、面向真实数据平台 SQL 的多方言解析框架。

## Current Support / 当前支持情况

LineSQL is usable today for Spark and MySQL lineage experiments. Hive, Flink, and StarRocks modules are already registered as scaffolds, so their parser implementations can grow behind stable module and SPI boundaries. Oracle and SQL Server are first-stage targets but are not wired yet.

LineSQL 目前已经可以用于 Spark 和 MySQL 血缘解析实验。Hive、Flink、StarRocks 的模块架子已经接入 Maven、CLI 和 SPI，后续可以在稳定模块边界内逐步补解析能力。Oracle 和 SQL Server 是第一阶段目标，但暂未接入。

| Dialect / 方言 | Module status / 模块状态 | Auto-detection / 自动识别 | Table lineage / 表级血缘 | Column lineage / 字段级血缘 | Notes / 说明 |
| --- | --- | --- | --- | --- | --- |
| Spark | Active parser / 可用解析器 | Supported for Spark-specific syntax / 支持 Spark 特征语法 | Broad stage-1 coverage / 阶段 1 覆盖较多 | Broad stage-1 coverage / 阶段 1 覆盖较多 | Uses ANTLR4 grammar baseline with real SQL case assets. / 基于 ANTLR4 grammar，并保留真实 SQL case。 |
| MySQL | Active MVP parser / 可用 MVP 解析器 | Supported for MySQL-specific syntax / 支持 MySQL 特征语法 | Common SELECT, write, DDL, DML shapes / 常见查询、写入、DDL、DML | Direct projections, joins, CTE/subquery, UNION, write mappings, UPDATE SET / 直接投影、JOIN、CTE/子查询、UNION、写入映射、UPDATE SET | Uses ANTLR4 lexer plus a lineage walker while the full grammar evolves. / 当前使用 ANTLR4 lexer 和血缘 walker，后续逐步演进完整 grammar。 |
| Hive | Scaffold registered / 架子已注册 | Supported for clear Hive anchors / 支持明确 Hive 特征 | Not implemented yet / 暂未实现 | Not implemented yet / 暂未实现 | Module, SPI, diagnostics, and seed SQL cases are in place. / 模块、SPI、诊断码和种子 SQL case 已就绪。 |
| Flink | Scaffold registered / 架子已注册 | Supported for clear Flink anchors / 支持明确 Flink 特征 | Not implemented yet / 暂未实现 | Not implemented yet / 暂未实现 | Module, SPI, diagnostics, and seed SQL cases are in place. / 模块、SPI、诊断码和种子 SQL case 已就绪。 |
| StarRocks | Scaffold registered / 架子已注册 | Supported for clear StarRocks anchors / 支持明确 StarRocks 特征 | Not implemented yet / 暂未实现 | Not implemented yet / 暂未实现 | Module, SPI, diagnostics, and seed SQL cases are in place. / 模块、SPI、诊断码和种子 SQL case 已就绪。 |
| Oracle | Planned / 规划中 | Planned / 规划中 | Planned / 规划中 | Planned / 规划中 | First-stage target. / 第一阶段目标。 |
| SQL Server | Planned / 规划中 | Planned / 规划中 | Planned / 规划中 | Planned / 规划中 | First-stage target. / 第一阶段目标。 |

See [Supported Scenarios](docs/supported-scenarios.md) for the detailed, case-backed compatibility record.

详细、由 SQL case 支撑的兼容性记录见 [Supported Scenarios](docs/supported-scenarios.md)。

## Positioning / 项目定位

This project is not a SQL executor, optimizer, or complete query planner. It focuses on extracting unified lineage results from production SQL used in data platforms.

本项目不是 SQL 执行器、优化器，也不是完整查询规划器。它专注于从真实数据平台 SQL 中提取统一的血缘结果。

- Lighter than Calcite: no full planning or optimization pipeline.
- 比 Calcite 更轻量：不做完整查询规划和优化链路。
- More data-platform oriented than generic SQL parsers: Hive, Spark, StarRocks, Flink, MySQL, Oracle, and SQL Server are first-stage targets.
- 比通用 SQL parser 更偏数据平台场景：第一阶段目标包含 Hive、Spark、StarRocks、Flink、MySQL、Oracle、SQL Server。
- JVM-native: easy to embed in Java data platform services.
- JVM 原生：方便 Java 数据平台服务直接接入。
- Lineage-first: parser output is a unified lineage model, not only an AST.
- 血缘优先：输出统一血缘模型，而不仅仅是 AST。
- Dialect auto-detection first: users should not need to know which SQL engine produced a statement.
- 优先自动识别方言：用户不应该必须知道 SQL 来自哪种引擎。
- Production SQL friendly: multi-statement scripts, variables, temporary views, UDTF, Chinese identifiers, complex strings, bad SQL isolation, and partial results are core design goals.
- 面向生产 SQL：多语句脚本、变量、临时视图、UDTF、中文标识符、复杂字符串、坏 SQL 隔离、partial result 都是核心设计目标。
- Independently implemented: external source or grammar reuse requires explicit license review.
- 独立实现：任何外部源码或 grammar 复用都需要明确的许可证审查。

## Modules / 模块

- `linesql-core`: model, SPI, facade, statement splitter, dialect detector, and shared utilities. / 模型、SPI、门面入口、语句拆分、方言识别和共享工具。
- `linesql-dialect-spark`: Spark dialect parser module. / Spark 方言解析模块。
- `linesql-dialect-hive`: Hive dialect parser module. / Hive 方言解析模块。
- `linesql-dialect-flink`: Flink dialect parser module. / Flink 方言解析模块。
- `linesql-dialect-starrocks`: StarRocks dialect parser module. / StarRocks 方言解析模块。
- `linesql-dialect-mysql`: MySQL dialect parser module. / MySQL 方言解析模块。
- `linesql-cli`: command-line entry module. / 命令行入口模块。

## Current Scope / 当前范围

LineSQL is in early implementation. The first-stage implementation targets common SQL lineage scenarios in modern data platforms.

LineSQL 仍处于早期实现阶段。第一阶段聚焦现代数据平台中常见的 SQL 血缘场景。

- Java 11.
- 基于 Java 11。
- ANTLR4-based dialect parsers.
- 方言解析器基于 ANTLR4。
- Automatic dialect detection through `LineSql.parse(sql)`.
- 通过 `LineSql.parse(sql)` 自动识别方言。
- Table-level lineage and column-level lineage.
- 支持表级血缘和字段级血缘。
- Graceful degradation: return table lineage and warnings when column lineage cannot be fully resolved.
- 支持优雅降级：字段血缘无法完整解析时，尽量返回表级血缘和诊断信息。
- Real production SQL cases as primary test assets.
- 真实生产 SQL case 是核心测试资产。

## Supported Scenarios / 支持场景

Current implemented coverage is documented as the project evolves.

当前实现范围会随着项目推进持续记录在文档中。

- [Supported Scenarios](docs/supported-scenarios.md)
- [Spark Stage 1](docs/design/spark-stage-1.md)
- MySQL MVP coverage includes MySQL-specific write statements such as `ON DUPLICATE KEY UPDATE` and `REPLACE INTO`.
- MySQL MVP 已覆盖部分 MySQL 特有写入语法，例如 `ON DUPLICATE KEY UPDATE` 和 `REPLACE INTO`。
- Hive, Flink, and StarRocks currently expose scaffold diagnostics until their lineage extractors are implemented.
- Hive、Flink、StarRocks 当前会返回 scaffold 诊断信息，后续逐步实现血缘解析。

## Usage / 使用方式

Build / 构建：

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

- Stage 1: production-ready lineage parser foundation for Hive, Spark, StarRocks, Flink, MySQL, Oracle, and SQL Server.
- 阶段 1：搭建面向生产可用的 Hive、Spark、StarRocks、Flink、MySQL、Oracle、SQL Server 血缘解析基础。
- Stage 2: richer column lineage, UDTF, temporary views, CTE column propagation, and compatibility matrix.
- 阶段 2：增强字段级血缘、UDTF、临时视图、CTE 字段传播和兼容性矩阵。
- Stage 3: anonymized production SQL corpus, CLI improvements, syntax diagnostics, and editor-facing keyword support.
- 阶段 3：建设脱敏生产 SQL 语料、完善 CLI、语法诊断和面向编辑器的关键字支持。
