# Testing Pigeon

Pigeon uses Gradle, JUnit 5, and Mockito. Java 11 is the compilation target;
JDK 21 is recommended for local development and is the JDK used for this
project's development client.

## Launch the development client

Open the repository root as a Gradle project in IntelliJ, then run the Gradle
`run` task. The task launches `pigeon.PigeonTest` with assertions, developer
mode, and debug logging enabled.

You can also use the checked-in **Run Pigeon** IntelliJ run configuration or
run the Gradle task directly:

```text
gradlew.bat run
```

Do not run `RuneLite.java` from a separate RuneLite source checkout. Pigeon is
an external Plugin Hub-style project and supplies its own development launcher.

### Jagex Accounts

RuneLite's official development workflow can write a temporary launcher
credential that the IDE-launched client can read. Follow the current
[Using Jagex Accounts](https://github.com/runelite/wiki/blob/master/Using-Jagex-Accounts.md)
guide. Never share or commit `.runelite/credentials.properties`; remove it when
development testing is finished.

## Automated tests

Run the complete suite:

```text
gradlew.bat test
```

Build all normal verification artifacts:

```text
gradlew.bat build
```

Test reports are written beneath `build/reports/tests/test/`.

Notifier tests normally use a mocked HTTP client. To intentionally direct a
compatible notifier test to a private test webhook, set `TEST_WEBHOOK_URL` in
the test process environment. Set `TEST_WEBHOOK_RICH=false` to exercise plain
text formatting. Never use a production webhook or commit its URL.

## Manual release checklist

Use private test webhooks and at least two profiles.

- [ ] Create a profile, configure it, restart RuneLite, and confirm persistence.
- [ ] Clone a profile and confirm the clone has an independent name and ID.
- [ ] Delete a profile and confirm it does not return after restart.
- [ ] Enable multiple profiles and confirm one event is independently evaluated
      and routed for each profile.
- [ ] Disable one profile and confirm it no longer sends.
- [ ] Verify primary webhook routing.
- [ ] Verify notifier-specific webhook overrides.
- [ ] Confirm overlapping destinations produce the visible route warning.
- [ ] Verify enabled/disabled notification rules, thresholds, templates, and
      screenshot settings for the notifier families under test.
- [ ] Safe-export a profile and confirm all webhook URLs are absent.
- [ ] Export with webhooks and confirm URLs are retained only after the warning.
- [ ] Import both export types and confirm each new profile starts disabled.
- [ ] Reject malformed JSON, unsupported schemas, invalid URLs, and empty names.
- [ ] Toggle **Show sidebar icon** off and on; confirm notifications remain
      enabled while the icon is hidden.
- [ ] Review logs and bug-report output for leaked webhook URLs.

## Executable development JAR

`shadowJar` creates a development-only executable JAR containing test runtime
dependencies:

```text
gradlew.bat shadowJar
```

It must be launched with assertions enabled (`-ea`). This artifact is for local
testing, not Plugin Hub distribution; Plugin Hub builds the standard plugin
artifact from source.
