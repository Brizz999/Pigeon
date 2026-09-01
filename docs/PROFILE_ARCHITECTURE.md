# Concurrent profile architecture

## Overview

A Pigeon profile is a complete notification policy and delivery
configuration. Any number of profiles can be enabled simultaneously, and each
enabled profile independently decides whether and where to send a detected
event.

RuneLite events are consumed once by shared trackers and notifiers. Pigeon
then evaluates the resulting notification against an immutable snapshot of the
enabled profiles. Profile documents and their runtime configuration are cached
instead of being decoded again for every gameplay event. Creating, importing,
editing, enabling, disabling, or deleting a profile invalidates the snapshot,
and the next read rebuilds it from the current profile data.

## Profile document

Profiles use a versioned JSON document. The current schema is version 1:

```json
{
  "schemaVersion": 1,
  "id": "4f0fbb88-3c2b-4db0-8ad4-b34ec2c3995e",
  "name": "Clan server",
  "enabled": true,
  "settings": {},
  "webhooks": {
    "primary": [],
    "overrides": {}
  }
}
```

Profile IDs are stable local UUIDs. Cloning and importing generate new IDs so
shared data cannot overwrite an existing local profile. Names are user-facing
and do not need to be unique. Settings use stable Pigeon configuration keys
rather than Java field names.

## Persistence

The profile repository stores data through RuneLite's `ConfigManager` in the
dedicated `pigeonProfiles` group:

- `index` records the ordered profile IDs and schema version.
- `profile_<uuid>` stores one complete profile document.

Documents are validated before they are saved. Unknown settings are retained
when a profile is loaded and exported, allowing it to pass through a client
that does not yet understand those settings without silently discarding them.

## Routing

Each enabled profile evaluates notifier enablement, thresholds, filters,
templates, screenshot policy, and destinations independently. A notification
uses its notifier-specific webhook override when present and otherwise uses
the profile's primary webhook URLs.

Two profiles may intentionally send the same event to the same destination.
Pigeon does not silently merge those deliveries because their policies and
payloads may differ. The profile panel warns when enabled profiles have
overlapping routes so users can decide whether the duplicate destination is
intentional.

## Import and export safety

Safe exports omit all webhook URLs. Including webhooks requires an explicit
choice and displays a credential warning. Imports are parsed and validated
before storage, receive a new local UUID, and start disabled so the user can
review their rules and destinations before any message is sent.

## Legacy compatibility

The original flat Dink/Pigeon configuration remains an internal compatibility
source for installations that have not created profiles. It is not exposed as
a second editing interface: normal configuration and routing should be managed
from the Pigeon profile panel. The generic RuneLite configuration panel only
controls whether the sidebar icon is visible.
