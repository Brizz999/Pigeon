# Making a release

## Prepare

- [ ] Confirm `origin` points to Pigeon's public GitHub repository. Keep the
      Dink repository as a read-only `upstream` remote for attribution and
      optional upstream comparison.
- [ ] Confirm the repository's default branch and GitHub Actions are enabled.
- [ ] Decide the release version and move the relevant entries from
      **Unreleased** into a versioned section in `CHANGELOG.md`.
- [ ] Set the same release version in `build.gradle.kts` and
      `runelite-plugin.properties`.
- [ ] Confirm the plugin metadata, description, tags, author attribution, and
      `pigeon.PigeonPlugin` entry point are current.
- [ ] Run `gradlew.bat clean test build` with the supported JDK.
- [ ] Complete the manual checklist in [TESTING.md](TESTING.md), including
      concurrent profiles, routing overrides, persistence, and import/export.
- [ ] Search the repository and generated artifacts for webhook URLs,
      credentials, test account data, and local absolute paths.
- [ ] Review dependency and RuneLite API updates and resolve all new warnings.
- [ ] Review the complete diff and open a pull request for review.

## Publish

- [ ] Merge the reviewed release commit into the main branch.
- [ ] Create an annotated version tag on that exact commit.
- [ ] Create a GitHub release using the matching changelog section.
- [ ] Submit or update the entry in the
      [RuneLite Plugin Hub](https://github.com/runelite/plugin-hub) using its
      current contribution instructions and standard source build.
- [ ] Confirm the Plugin Hub build succeeds and test the installed version in
      the normal RuneLite client.

Do not distribute the `shadowJar` development artifact as the Plugin Hub
release. It contains test-runtime dependencies and exists only for local
development.
