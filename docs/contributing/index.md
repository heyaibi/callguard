# Contributor docs

This folder holds the rules for people and tools that change CallGuard code or tests. Start here to find the right page.

The folder owns the testing standard. The work rules for issues, scope, branches, and pull requests live in [AGENTS.md](../../AGENTS.md) at the repository root. Build and CI commands live in [CONTRIBUTING.md](../../CONTRIBUTING.md).

```text
AGENTS.md                      work rules (repository root)
CONTRIBUTING.md                setup and CI gates (repository root)
docs/contributing/
├── index.md                   this page
└── testing.md                 test types, rules, and templates
```

## Pages

- [Testing standard](testing.md): the four test types, naming, rules, and templates.
- [AGENTS.md](../../AGENTS.md): scope, test-only rules, verification commands, and the PR body.
- [CONTRIBUTING.md](../../CONTRIBUTING.md): build, CI gates, and commit style.

## Reading paths

- New contributor: read AGENTS.md, then testing.md.
- Test-only contributor: read the test-only section of AGENTS.md, then "Writing tests against existing code" in testing.md.
- Reviewer: read the definition of done in AGENTS.md, then the pull request template.
