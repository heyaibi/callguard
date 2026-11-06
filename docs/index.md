# CallGuard documentation

CallGuard is an Android dialer and messaging app that shows who is calling and blocks
unwanted calls and texts. It resolves caller names from India's CNAP network first, then the
device phonebook, then an on-device cache. Everything stays on the phone: no account, no
backend, no remote database.

New here? Read [Build CallGuard and run it on a device](tutorials/get-started.md) first. It
takes about ten minutes and ends with the app running.

## Do something

Task guides for people who already have the app built and running.

- [Make CallGuard the default phone app](how-to/set-as-default-dialer.md): take over incoming
  and outgoing calls from the stock dialer.
- [Make CallGuard the default SMS app](how-to/set-as-default-sms-app.md): own the messaging
  role so non-contact texts can be screened.
- [Tune what CallGuard blocks](how-to/tune-blocking-rules.md): change how calls and texts get
  screened, including the deadline fallback.
- [Manage the allow and block lists](how-to/manage-lists.md): add numbers, remove them, and
  see which list wins.
- [Cut a release](how-to/cut-a-release.md): tag a version and get a signed APK from CI.

## Look something up

- [CallGuard settings reference](reference/settings.md): every setting, its storage key, its
  default, and what it changes.
- [Permissions and roles reference](reference/permissions-and-roles.md): what the app asks
  for, why, and what breaks without it.
- [Gradle tasks reference](reference/gradle-tasks.md): the build, test, and lint commands you
  will actually use.

## Understand the design

- [How caller identity is resolved](explanation/caller-identity.md): why CNAP comes first and
  where the fallbacks sit.
- [How screening decides](explanation/screening.md): the decision model behind allow, block,
  and ask.
- [The App Bridge](explanation/app-bridge.md): how WhatsApp notifications reach CallGuard, and
  what the bridge cannot see.
- [Architecture](explanation/architecture.md): layers, module boundaries, and the reasons
  behind them.

## Design records

The product's domain specification is the source of truth for behavior. Start at
[the blueprint](blueprint/README.md); it defines the bounded contexts, their rules, and the
[glossary](blueprint/glossary.md) this documentation borrows from.

## Contributing

[CONTRIBUTING.md](../CONTRIBUTING.md) covers the build, the CI gates, and the pull request
rule: `master` takes changes through a pull request, never a direct push.

[AGENTS.md](../AGENTS.md) sets the rules for working from an issue. [Testing standard](contributing/testing.md) sets the test types, names, rules, and templates.
