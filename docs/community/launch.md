# Launch and Community Notes

This document keeps reusable copy and small community tasks for growing LineSQL.

## One-line Positioning

LineSQL is a JVM-native, ANTLR4-based SQL lineage parser for real-world data platform SQL.

## GitHub About

Description:

```text
JVM-native ANTLR4 SQL lineage parser for Spark, Hive, Flink, StarRocks, MySQL, Oracle, SQL Server, PostgreSQL, and OceanBase.
```

Suggested topics:

```text
sql, sql-parser, sql-lineage, data-lineage, antlr4, java, jvm, spark-sql,
hive-sql, flink-sql, mysql, starrocks, oracle, sqlserver, postgresql, oceanbase
```

## Short Launch Post

```text
LineSQL is now available on Maven Central.

It is a JVM-native, ANTLR4-based SQL lineage parser focused on real-world data platform SQL. It supports automatic dialect detection and returns a unified model for table lineage, column lineage, clause-level column usages, and diagnostics.

Current dialect modules include Spark, Hive, Flink, StarRocks, MySQL, Oracle, SQL Server, PostgreSQL, and OceanBase.

The public regression corpus currently contains 800+ SQL cases, including 500+ column-lineage cases.

Maven:
io.github.jarredhj0214:linesql-all:0.1.0-alpha.4

GitHub:
https://github.com/jarredhj0214/linesql
```

## Technical Community Post

```text
I am building LineSQL, a JVM-native SQL lineage parser for data platforms.

The project is not a SQL executor or optimizer. It focuses on parsing production SQL and returning lineage-friendly output:

- automatic dialect detection
- table-level lineage
- column-level lineage
- clause column usages such as WHERE, JOIN, GROUP BY, HAVING, ORDER BY
- multi-statement scripts
- parser diagnostics and partial results
- dialect modules for Spark, Hive, Flink, StarRocks, MySQL, Oracle, SQL Server, PostgreSQL, and OceanBase
- a public case-backed regression corpus with 800+ SQL cases

It is published to Maven Central:

<dependency>
    <groupId>io.github.jarredhj0214</groupId>
    <artifactId>linesql-all</artifactId>
    <version>0.1.0-alpha.4</version>
</dependency>

The most useful contributions right now are anonymized production SQL cases with expected table and column lineage.

GitHub:
https://github.com/jarredhj0214/linesql
```

## Good First Issues

Good first issues should be small, case-backed, and scoped to one dialect. Examples:

- Add a missing SQL case for a supported syntax shape.
- Add one dialect-specific DDL statement that only needs affected table lineage.
- Improve a manifest expectation for column lineage.
- Add documentation for a supported scenario.
- Add an auto-detection anchor test for a distinctive dialect feature.

## Channels

Useful places to share after a release:

- GitHub personal profile README
- company or team engineering chat
- data engineering communities
- Java communities
- SQL parser or data lineage discussions
- release notes attached to a GitHub Release

## Release Checklist for Visibility

- README badges show build, Maven Central, license, Java, and ANTLR.
- GitHub description and topics are set.
- Release notes include Maven coordinates and 3 to 5 concrete supported scenarios.
- At least 2 to 3 issues are labeled `good first issue`.
- SQL case issue template works and uses the `sql-case` label.
- Supported scenarios documentation is updated before each release.
