# Development

LineSQL targets Java 11.

Some local machines still use Java 8 as their global `JAVA_HOME` for legacy projects. To avoid changing the global environment, use the project helper script:

```bash
./scripts/mvn-jdk11 clean test
```

The script resolves JDK 11 in this order:

- `JAVA11_HOME`
- IntelliJ IDEA CE bundled JBR 11
- Common Homebrew `openjdk@11` locations
- `/usr/libexec/java_home -v 11`

If auto-detection fails, set:

```bash
export JAVA11_HOME="/path/to/jdk-11"
```

Then rerun:

```bash
./scripts/mvn-jdk11 clean test
```

## SQL Case Quality Checks

SQL case manifests are treated as compatibility assets, not loose fixtures. The core test suite includes a cross-dialect quality check that validates:

- every manifest has unique case ids
- every manifest entry points to an existing `cases/*.sql` file
- every `statementType` is either a public `StatementType` or a test-only script/error marker
- table arrays, column lineage arrays, diagnostics arrays, and descriptions have the expected shape
- every case id appears in `docs/supported-scenarios.md`

Run the guardrail directly:

```bash
./scripts/mvn-jdk11 -pl linesql-core -Dtest=SqlCaseManifestQualityTest test
```

Print the current coverage summary:

```bash
./scripts/mvn-jdk11 -q -pl linesql-core -Dtest=SqlCaseManifestQualityTest#printsCoverageReport test
```

## Grammar-Driven Parser Work

Parser work should be planned by dialect grammar domains, then validated with SQL cases. Do not use one-off SQL examples as the only roadmap for implementation.

Use [Grammar Coverage Matrix](grammar-coverage-matrix.md) to choose the next grammar domain and priority. For each capability:

- update or confirm the dialect ANTLR grammar rule
- implement the lineage visitor behavior for that grammar node
- add representative SQL cases
- update the manifest expected output
- update [Supported Scenarios](supported-scenarios.md)
- run the focused dialect tests and then `./scripts/mvn-jdk11 -B clean install`

When deciding whether to create a new dialect, prefer this rule:

- use the existing dialect when the difference is a connector option, minor version-gated feature, or parser feature flag
- add a dialect profile when the grammar family is shared but compatibility mode changes behavior, such as OceanBase MySQL mode vs OceanBase Oracle mode
- add a new dialect when statement forms, identifier rules, DML semantics, or lineage behavior differ materially, such as PostgreSQL
