# Pigeon

Pigeon is a RuneLite webhook notification plugin built around shareable,
independently configurable profiles. Multiple profiles can be active at the
same time, allowing one game event to be evaluated and routed differently for
several Discord servers or custom webhook consumers.

## Status

Pigeon 0.1.0 is in beta. Profile management, concurrent routing,
credential-aware import and export, and notification fan-out have been
implemented and manually exercised in the RuneLite development client. The
initial development test pass is complete, and the plugin is currently under
RuneLite Plugin Hub review.

## Features

- Create, configure, clone, delete, enable, and disable profiles.
- Run any number of enabled profiles concurrently.
- Give each profile its own notification rules, thresholds, templates,
  screenshot policies, primary webhook URLs, and notifier-specific overrides.
- Import profiles directly from the clipboard.
- Export a safe profile without webhook credentials by default.
- Explicitly export webhook credentials when sharing with a trusted recipient.
- Warn when enabled profiles contain overlapping webhook routes.
- Hide or show the Pigeon sidebar icon without disabling notifications.

Each RuneLite event is detected once. Pigeon then applies every enabled
profile's policy independently, so multiple profiles do not replay or corrupt
stateful game-event detection.

## Getting started

1. Enable Pigeon and open the Pigeon icon in the RuneLite sidebar.
2. Select **Create profile** and enter a name.
3. Open **Configure profile**.
4. Add one or more primary webhook URLs.
5. Expand the notification types you want, enable them, and configure their
   thresholds, messages, screenshots, or webhook overrides.
6. Save the profile and ensure its checkbox is enabled.

The generic RuneLite configuration panel contains only **Show sidebar icon**.
All notification and routing settings belong to profiles in the Pigeon panel.

## Profiles and routing

Primary webhook URLs receive any accepted notification without a configured
override. A notifier-specific override routes that notification type to its
own destination instead. Multiple URLs may be entered one per line.

Every enabled profile evaluates an event independently. If two enabled
profiles accept the same event, both may send a message. This is intentional,
including when the profiles target the same webhook; Pigeon displays an
overlapping-route warning to make that behavior visible.

Profiles are persisted locally through RuneLite configuration storage. Cloning
and importing always create a new local profile ID, and imported profiles start
disabled so the user can inspect them before anything is sent.

## Import, export, and webhook security

Use **Export profile** to copy a versioned JSON profile to the clipboard:

- **Safe export** removes all primary and override webhook URLs.
- **Include webhooks** retains those URLs and must be treated as a secret.

Discord webhook URLs are credentials: anyone who obtains one can post to that
webhook. Do not commit profile exports containing webhooks, paste them into
public chats, or include them in bug reports. Revoke and replace any webhook
that may have been exposed.

Use **Import profile** to read profile JSON from the clipboard. Pigeon validates
the schema and displays whether webhook credentials are present before creating
the disabled local copy. Imported profiles retain their exported names.

## Development and testing

Open the project as a Gradle project and run the `run` task to launch a RuneLite
development client with Pigeon loaded. See [Testing Pigeon](docs/TESTING.md) for
build commands, Jagex Account login setup, automated tests, and the manual
release test checklist.

The webhook payload format is documented in [JSON examples](docs/json-examples.md).
Other Plugin Hub plugins can integrate through the
[external plugin messaging API](docs/external-plugin-messaging.md).

## Architecture

See [Concurrent profile architecture](docs/PROFILE_ARCHITECTURE.md) for profile
persistence, runtime snapshots, routing behavior, and import/export safety.

## Heritage and license

Pigeon is an independent project derived from
[Dink](https://github.com/pajlads/DinkPlugin) 1.14.4. Dink's original
documentation is preserved in [DINK_UPSTREAM_README.md](docs/DINK_UPSTREAM_README.md),
and historical Dink changelog entries remain attributed to their original
contributors. Pigeon is not affiliated with or endorsed by the Dink
maintainers.

Pigeon is distributed under the BSD 2-Clause License. See [LICENSE](LICENSE)
and [NOTICE](NOTICE).
