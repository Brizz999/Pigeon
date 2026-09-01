# Publishing a Plugin Hub update

Pigeon is distributed through RuneLite's Plugin Hub. Routine bug fixes and
maintenance updates do not require a separately versioned GitHub release or
detailed release notes. Tags, GitHub releases, and changelog sections remain
optional for notable milestones.

## Prepare

- [ ] Confirm `origin` points to Pigeon's public GitHub repository. Keep the
      Dink repository as a read-only `upstream` remote for attribution and
      optional upstream comparison.
- [ ] Pull the latest Pigeon default branch and resolve any changes before
      preparing the update.
- [ ] Confirm the plugin metadata, description, tags, author attribution, and
      `pigeon.PigeonPlugin` entry point are current.
- [ ] Run `gradlew.bat clean test build` with the supported JDK.
- [ ] Complete the manual checklist in [TESTING.md](TESTING.md), including
      concurrent profiles, routing overrides, persistence, and import/export.
- [ ] Search the repository and generated artifacts for webhook URLs,
      credentials, test account data, and local absolute paths.
- [ ] Review dependency and RuneLite API updates and resolve all new warnings.
- [ ] Review the complete source diff and commit it to Pigeon's default branch.

## Publish

- [ ] Push the tested Pigeon source commit to GitHub.
- [ ] Update Pigeon's Plugin Hub manifest to reference that exact commit and
      submit the manifest change using the
      [current Plugin Hub contribution instructions](https://github.com/runelite/plugin-hub#adding-new-plugins).
- [ ] Confirm the Plugin Hub checks pass and address any maintainer feedback.
- [ ] Confirm the Plugin Hub build succeeds and test the installed version in
      the normal RuneLite client.

Do not distribute the `shadowJar` development artifact as the Plugin Hub
release. It contains test-runtime dependencies and exists only for local
development.
