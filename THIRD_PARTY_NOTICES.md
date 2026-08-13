# Third-party Notices

This file records third-party source code, grammar files, generated parser artifacts, and license obligations included in LineSQL.

At this stage, LineSQL includes Spark grammar files adapted from Apache Spark.

Runtime and build dependencies are managed through Maven and must be reviewed before release.

## Dependency Baseline

| Component | Purpose | License | Notes |
| --- | --- | --- | --- |
| ANTLR4 runtime and Maven plugin | Parser generation and runtime | BSD-3-Clause | Permissive license compatible with Apache-2.0 projects. |
| Jackson | CLI JSON serialization | Apache-2.0 | Used by `linesql-cli`. |
| JUnit | Tests | EPL-1.0 | Test scope only. |

## Grammar Source Register

Before adding any external `.g4` grammar, add an entry here.

| Dialect | Source | Source license | Copied or adapted | Local path | Review status |
| --- | --- | --- | --- | --- | --- |
| Hive | TBD | TBD | TBD | TBD | Not started |
| Spark | Apache Spark `sql/api/src/main/antlr4/org/apache/spark/sql/catalyst/parser/SqlBaseLexer.g4` and `SqlBaseParser.g4` | Apache-2.0 | Adapted | `linesql-dialect-spark/src/main/antlr4/io/github/linesql/dialect/spark/antlr/SqlBaseLexer.g4`, `linesql-dialect-spark/src/main/antlr4/io/github/linesql/dialect/spark/antlr/SqlBaseParser.g4` | Reviewed |
| StarRocks | TBD | TBD | TBD | TBD | Not started |
| Flink | TBD | TBD | TBD | TBD | Not started |
| MySQL | TBD | TBD | TBD | TBD | Not started |
| Oracle | TBD | TBD | TBD | TBD | Not started |
| SQL Server | TBD | TBD | TBD | TBD | Not started |
