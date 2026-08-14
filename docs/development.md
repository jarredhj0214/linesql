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
