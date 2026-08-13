# Contributing to LineSQL

Thanks for helping improve LineSQL.

## Current Phase

LineSQL is in the early requirements and architecture phase. Before adding parser logic, prefer opening an issue or discussion that describes:

- SQL dialect and engine version.
- Statement examples.
- Expected table-level lineage.
- Expected column-level lineage, if relevant.
- Whether partial results are acceptable.

## Development

```bash
mvn test
```

The project targets Java 11.

## Pull Requests

- Keep changes focused.
- Add SQL examples as tests when parser behavior is introduced.
- Do not add broad grammar support without representative real-world cases.
- Do not copy external grammar or parser code unless its license has been reviewed and documented.
