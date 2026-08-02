# External plugin messaging

Another RuneLite plugin can post a `PluginMessage` through `EventBus#post` to
ask Pigeon to deliver a webhook notification. The user remains in control:
each enabled profile must have **External Plugin Notifications** enabled and
applies its own screenshot and routing settings.

If the request supplies webhook URLs, those destinations are used. Otherwise,
each accepting profile uses its external-plugin webhook override or falls back
to its primary webhook URLs. Consequently, one request can result in a
delivery from more than one enabled profile.

## Message identity

Use namespace `pigeon` and message name `notify`. The legacy namespace `dink`
is also accepted for compatibility with existing integrations, but new code
should use `pigeon`.

The message's `Map<String, Object>` payload is converted to
[`ExternalNotificationRequest`](../src/main/java/pigeon/domain/ExternalNotificationRequest.java).

| Field            | Required | Type    | Description                                                                                  |
| ---------------- | -------- | ------- | -------------------------------------------------------------------------------------------- |
| `text`           | Yes      | String  | Notification body. Supports the replacements below; `%USERNAME%` is available automatically. |
| `sourcePlugin`   | Yes      | String  | Human-facing name of the requesting plugin.                                                  |
| `urls`           | No       | List    | Destination `okhttp3.HttpUrl` values. Omitting this delegates routing to each profile.       |
| `title`          | No       | String  | Discord embed title.                                                                         |
| `thumbnail`      | No       | String  | URL for the Discord embed thumbnail.                                                         |
| `imageRequested` | No       | boolean | Requests a screenshot. Each profile's screenshot policy still applies.                       |
| `fields`         | No       | List    | Discord embed-field objects containing `name` and `value`, with optional `inline`.           |
| `replacements`   | No       | Map     | Template tokens mapped to objects containing `value` and optional `richValue`.               |
| `metadata`       | No       | Map     | Gson-serializable values included for non-Discord webhook consumers.                         |

## Example

The example assumes RuneLite's event bus has been injected as
`private @Inject EventBus eventBus;`.

```java
Map<String, Object> data = new HashMap<>();
data.put("sourcePlugin", "My Plugin Name");
data.put("text", "A message for %USERNAME% with %XYZ%");
data.put("replacements", Map.of(
    "%XYZ%", createTextReplacement("sample replacement")));
data.put("title", "Optional embed title");
data.put("imageRequested", true);
data.put("fields", List.of(createField("sample key", "sample value")));
data.put("metadata", Map.of("custom key", "custom value"));

PluginMessage pigeonRequest = new PluginMessage("pigeon", "notify", data);
eventBus.post(pigeonRequest);
```

To request explicit destinations, add `urls`:

```java
data.put("urls", Arrays.asList(
    HttpUrl.parse("https://discord.com/api/webhooks/example/one"),
    HttpUrl.parse("https://discord.com/api/webhooks/example/two")));
```

Webhook URLs are credentials. Integrating plugins should avoid logging them
or storing them outside RuneLite's protected configuration mechanisms.

## Helper methods

```java
public static Map<String, Object> createField(String name, String value)
{
    return Map.of("name", name, "value", value);
}

public static Map<String, Object> createField(
    String name, String value, boolean inline)
{
    return Map.of("name", name, "value", value, "inline", inline);
}

public static Map<String, String> createTextReplacement(String text)
{
    return Map.of("value", text);
}

public static Map<String, String> createLinkReplacement(
    String text, String link)
{
    return Map.of(
        "value", text,
        "richValue", String.format("[%s](%s)", text, link));
}

public static Map<String, String> createWikiReplacement(
    String text, String searchPhrase)
{
    return createLinkReplacement(
        text,
        "https://oldschool.runescape.wiki/w/Special:Search?search="
            + UrlEscapers.urlPathSegmentEscaper()
                .escape(searchPhrase));
}
```
