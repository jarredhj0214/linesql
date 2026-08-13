# License Policy

LineSQL is an Apache License 2.0 project.

This policy is intentionally conservative because SQL grammars are often copied across projects with unclear provenance. LineSQL should be easy for data platforms and commercial systems to adopt.

## Project License

- LineSQL source code is licensed under Apache-2.0.
- Contributions submitted to the project are accepted under Apache-2.0 unless explicitly stated otherwise.
- The repository keeps `LICENSE`, `NOTICE`, and `THIRD_PARTY_NOTICES.md` at the project root.

## Dependency Policy

Allowed by default:

- Apache-2.0
- BSD-2-Clause / BSD-3-Clause
- MIT
- ISC

Review required:

- EPL
- MPL
- LGPL
- Public domain or Unlicense claims without clear source provenance
- Any dependency with missing, custom, or unclear license metadata

Avoid for runtime dependencies:

- GPL
- AGPL
- SSPL
- Commercial or source-available licenses that restrict redistribution or commercial use

## Grammar Policy

ANTLR `.g4` grammar files are source code and must be treated as third-party code when copied or adapted from another project.

LineSQL may reference external grammar projects for learning and compatibility research, but it must not copy grammar files unless all of the following are true:

- The grammar file has a clear permissive license.
- Original copyright and license notices are retained.
- The source URL, license, and local path are recorded in `THIRD_PARTY_NOTICES.md`.
- Significant local modifications are documented in the file header or adjacent notes.

If a grammar source has no clear license, it can be used only as behavioral reference. Do not copy file content, rule structure wholesale, comments, tests, or generated code.

## Reference Projects

Existing SQL parser projects are useful references for LineSQL's architecture and compatibility research.

Allowed:

- Referencing public documentation and module organization.
- Comparing supported dialects and APIs.
- Learning high-level parser layering and test strategy.

Not allowed without explicit review:

- Copying Java, Kotlin, grammar, test, or generated source files.
- Porting grammar rules file-by-file.
- Reusing examples that are not trivial unless attribution and license requirements are documented.

LineSQL should be described as independently implemented, not as a fork of another parser project.

## grammars-v4

ANTLR `grammars-v4` is a useful source of grammar research, but it does not provide a single simple license answer for every grammar in the repository. Each dialect grammar must be reviewed independently before use.

Do not assume every grammar in `grammars-v4` is automatically usable in LineSQL.

## Generated Code

ANTLR-generated Java files inherit obligations from the grammar files used to generate them. Generated parser artifacts should not be committed until the grammar source and license are recorded.

## Release Checklist

Before a public release:

- Run a dependency license report.
- Review `THIRD_PARTY_NOTICES.md`.
- Confirm no unreviewed `.g4` files were added.
- Confirm generated sources are either excluded from source control or covered by reviewed grammar notices.
- Confirm all copied third-party code has retained attribution.
