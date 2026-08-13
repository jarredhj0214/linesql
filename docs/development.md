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
