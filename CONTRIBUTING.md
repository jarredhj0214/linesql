# Contributing to LineSQL

Thanks for helping LineSQL understand more real-world SQL.

LineSQL is developed grammar-domain first, then validated by SQL cases. A good contribution usually updates these files together:

| Artifact | Purpose |
| --- | --- |
| ANTLR grammar | Accept the dialect syntax. |
| Lineage visitor | Map syntax to table lineage, column lineage, and column usages. |
| SQL case file | Capture the supported SQL shape. |
| Manifest entry | Define the expected lineage contract. |
| Supported scenarios doc | Tell users the scenario is supported. |

## Development Setup

LineSQL builds with JDK 11 and emits Java 8 compatible bytecode.

```bash
./scripts/mvn-jdk11 -B test
```

For focused dialect work:

```bash
./scripts/mvn-jdk11 -B -pl linesql-dialect-mysql -am test
```

Replace `linesql-dialect-mysql` with the dialect module you are changing.

## Adding a SQL Scenario

1. Add a SQL file under `linesql-dialect-*/src/test/resources/sql/<dialect>/cases/`.
2. Add the expected result to `manifest.json`.
3. Update `docs/supported-scenarios.md`.
4. Run the focused dialect tests.
5. Run the full test suite before opening a pull request.

## Pull Request Checklist

- The change is backed by at least one SQL case unless it is documentation-only.
- The manifest expectation describes table lineage and column lineage where possible.
- Unsupported behavior returns diagnostics or partial lineage instead of silently failing.
- Public API changes are documented in `README.md`.
- License provenance is clear for any copied or adapted grammar material.

## Reporting SQL Cases

When opening a SQL case issue, please include the dialect, engine version, SQL text, expected input tables, expected output tables, and expected column lineage if you know it.

Anonymized production SQL is welcome. Keep enough structure to reproduce the parser behavior.
