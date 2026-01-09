# iCalDAV

A modern Kotlin library for CalDAV calendar synchronization and iCalendar parsing.

Built for production use with real-world CalDAV servers including iCloud, Google Calendar, Fastmail, and standard CalDAV implementations.

## Features

- **Kotlin-first, Java-compatible** - Idiomatic Kotlin with full Java interop
- **Production-ready HTTP** - OkHttp 4.x with retries, rate limiting, and resilience
- **Provider quirks handling** - Automatic handling of iCloud, Google, and other server differences
- **Complete sync engine** - Pull/push synchronization with offline support and conflict resolution
- **RFC compliant** - Full support for CalDAV (RFC 4791), iCalendar (RFC 5545), and Collection Sync (RFC 6578)

## Installation

```kotlin
// build.gradle.kts
dependencies {
    // Core CalDAV client (includes iCalendar parsing)
    implementation("io.github.icaldav:caldav-core:1.0.0")

    // Optional: Sync engine with offline support and conflict resolution
    implementation("io.github.icaldav:caldav-sync:1.0.0")

    // Optional: ICS subscription fetcher for read-only calendar feeds
    implementation("io.github.icaldav:ics-subscription:1.0.0")
}
```

**Requirements:** JVM 17+, Kotlin 1.9+

## Quick Start

### Discover Calendars

```kotlin
val client = CalDavClient.forProvider(
    serverUrl = "https://caldav.icloud.com",
    username = "user@icloud.com",
    password = "app-specific-password"  // Use app-specific password for iCloud
)

when (val result = client.discoverAccount("https://caldav.icloud.com")) {
    is DavResult.Success -> {
        val account = result.value
        println("Found ${account.calendars.size} calendars:")
        account.calendars.forEach { calendar ->
            println("  - ${calendar.displayName} (${calendar.href})")
        }
    }
    is DavResult.HttpError -> println("HTTP ${result.code}: ${result.message}")
    is DavResult.NetworkError -> println("Network error: ${result.exception.message}")
    is DavResult.ParseError -> println("Parse error: ${result.message}")
}
```

### Create, Update, Delete Events

```kotlin
// Create an event
val event = ICalEvent(
    uid = UUID.randomUUID().toString(),
    summary = "Team Meeting",
    description = "Weekly sync",
    dtStart = ICalDateTime.fromInstant(Instant.now()),
    dtEnd = ICalDateTime.fromInstant(Instant.now().plus(1, ChronoUnit.HOURS)),
    location = "Conference Room A"
)

val createResult = client.createEvent(calendarUrl, event)
if (createResult is DavResult.Success) {
    val (href, etag) = createResult.value
    println("Created event at $href")

    // Update the event
    val updated = event.copy(summary = "Team Meeting (Updated)")
    client.updateEvent(href, updated, etag)

    // Delete the event
    client.deleteEvent(href, etag)
}
```

### Fetch Events

```kotlin
// Fetch events in a date range
val events = client.fetchEvents(
    calendarUrl = calendarUrl,
    start = Instant.now(),
    end = Instant.now().plus(30, ChronoUnit.DAYS)
)

// Fetch specific events by URL
val specific = client.fetchEventsByHref(calendarUrl, listOf(href1, href2))
```

## Modules

```
┌─────────────────────────────────────────────────────────────┐
│                       caldav-sync                           │
│         (Sync engine, conflict resolution, offline)         │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                       caldav-core                           │
│            (CalDAV client, discovery, CRUD)                 │
└─────────────────────────────────────────────────────────────┘
                    │                   │
┌───────────────────────────┐   ┌───────────────────────────┐
│      icalendar-core       │   │       webdav-core         │
│  (RFC 5545 parse/generate)│   │   (WebDAV HTTP protocol)  │
└───────────────────────────┘   └───────────────────────────┘
```

| Module | Purpose |
|--------|---------|
| `icalendar-core` | Parse and generate iCalendar (RFC 5545) data |
| `webdav-core` | Low-level WebDAV HTTP operations |
| `caldav-core` | High-level CalDAV client with discovery |
| `caldav-sync` | Sync engine with offline support and conflict resolution |
| `ics-subscription` | Fetch read-only .ics calendar subscriptions |

## Provider Quirks System

CalDAV servers have implementation differences. iCalDAV handles these automatically:

```kotlin
// Auto-detects provider from URL
val client = CalDavClient.forProvider(serverUrl, username, password)

// Or explicitly specify
val client = CalDavClient(
    auth = DavAuth.Basic(username, password),
    quirks = ICloudQuirks()
)
```

### Supported Providers

| Provider | Quirks Handled |
|----------|----------------|
| **iCloud** | CDATA-wrapped responses, non-prefixed XML namespaces, regional server redirects, app-specific passwords |
| **Google Calendar** | OAuth token auth, specific date formatting |
| **Fastmail** | Standard CalDAV with minor variations |
| **Generic CalDAV** | RFC-compliant default behavior |

## Sync Engine

The `caldav-sync` module provides production-grade synchronization:

### Pull Changes from Server

```kotlin
val engine = SyncEngine(client)

// Initial sync (full fetch)
val result = engine.sync(
    calendarUrl = calendarUrl,
    previousState = SyncState.initial(calendarUrl),
    localProvider = myLocalProvider,
    handler = myResultHandler
)

// Incremental sync (only changes since last sync)
val result = engine.syncWithIncremental(
    calendarUrl = calendarUrl,
    previousState = savedSyncState,
    localProvider = myLocalProvider,
    handler = myResultHandler,
    forceFullSync = false
)

// Save state for next sync
saveSyncState(result.newState)
```

### Push Local Changes

```kotlin
val syncEngine = CalDavSyncEngine(client, localProvider, handler, pendingStore)

// Queue local changes (works offline)
syncEngine.queueCreate(calendarUrl, newEvent)
syncEngine.queueUpdate(modifiedEvent, eventUrl, etag)
syncEngine.queueDelete(eventUid, eventUrl, etag)

// Push to server when online
val pushResult = syncEngine.push()
```

### Conflict Resolution

When local and server changes conflict (HTTP 412):

```kotlin
// Automatic resolution
syncEngine.resolveConflict(operation, ConflictStrategy.SERVER_WINS)
syncEngine.resolveConflict(operation, ConflictStrategy.LOCAL_WINS)
syncEngine.resolveConflict(operation, ConflictStrategy.NEWEST_WINS)

// Manual resolution
syncEngine.resolveConflict(operation, ConflictStrategy.MANUAL) { local, server ->
    // Return merged event
    mergeEvents(local, server)
}
```

### Operation Coalescing

Multiple local changes to the same event are automatically combined:

| Sequence | Result |
|----------|--------|
| CREATE → UPDATE | Single CREATE with final data |
| CREATE → DELETE | No server operation needed |
| UPDATE → UPDATE | Single UPDATE with final data |
| UPDATE → DELETE | Single DELETE |

## iCalendar Parsing

Parse and generate RFC 5545 compliant iCalendar data:

```kotlin
val parser = ICalParser()

// Parse iCalendar string
when (val result = parser.parseAllEvents(icalString)) {
    is ParseResult.Success -> {
        result.value.forEach { event ->
            println("${event.summary} at ${event.dtStart}")
        }
    }
    is ParseResult.Error -> println("Parse error: ${result.message}")
}

// Generate iCalendar string
val generator = ICalGenerator()
val icalString = generator.generate(event)
```

### Supported Properties

| Category | Properties |
|----------|------------|
| **Core** | UID, SUMMARY, DESCRIPTION, LOCATION, STATUS |
| **Timing** | DTSTART, DTEND, DURATION, TRANSP |
| **Recurrence** | RRULE, EXDATE, RECURRENCE-ID |
| **People** | ORGANIZER, ATTENDEE |
| **Alerts** | VALARM (DISPLAY, EMAIL, AUDIO) |
| **Extended** | CATEGORIES, URL, ATTACH, IMAGE, CONFERENCE |

### Timezone Handling

```kotlin
// UTC time
ICalDateTime.fromInstant(Instant.now())

// With timezone
ICalDateTime.fromZonedDateTime(ZonedDateTime.now(ZoneId.of("America/New_York")))

// All-day event
ICalDateTime.fromLocalDate(LocalDate.now())

// Floating time (device timezone)
ICalDateTime.floating(LocalDateTime.now())
```

## Authentication

### Basic Auth

```kotlin
val client = CalDavClient.withBasicAuth(username, password)
```

### Bearer Token (OAuth)

```kotlin
val client = CalDavClient(
    auth = DavAuth.Bearer(accessToken),
    quirks = GoogleQuirks()
)
```

### iCloud App-Specific Passwords

iCloud requires app-specific passwords for third-party apps:
1. Go to appleid.apple.com → Security → App-Specific Passwords
2. Generate a password for your app
3. Use that password (not your Apple ID password)

## Error Handling

All operations return `DavResult<T>` for explicit error handling:

```kotlin
sealed class DavResult<out T> {
    data class Success<T>(val value: T) : DavResult<T>()
    data class HttpError(val code: Int, val message: String) : DavResult<Nothing>()
    data class NetworkError(val exception: Exception) : DavResult<Nothing>()
    data class ParseError(val message: String) : DavResult<Nothing>()
}

// Usage
when (val result = client.fetchEvents(calendarUrl, start, end)) {
    is DavResult.Success -> handleEvents(result.value)
    is DavResult.HttpError -> when (result.code) {
        401 -> promptReauth()
        404 -> handleNotFound()
        412 -> handleConflict()
        429 -> handleRateLimit()
        else -> handleError(result)
    }
    is DavResult.NetworkError -> showOfflineMessage()
    is DavResult.ParseError -> reportBug(result.message)
}
```

## HTTP Resilience

Built-in resilience for production use:

| Feature | Behavior |
|---------|----------|
| **Retries** | 2 retries with exponential backoff (500-2000ms) |
| **Rate Limiting** | Respects `Retry-After` header on 429 responses |
| **Response Limits** | 10MB max response size (prevents OOM) |
| **Timeouts** | Connect: 30s, Read: 300s, Write: 60s |
| **Redirects** | Preserves auth headers on cross-host redirects |

## ICS Subscriptions

Fetch read-only calendar feeds:

```kotlin
val client = IcsSubscriptionClient()

// Fetch with ETag caching
val result = client.fetch(
    url = "webcal://example.com/calendar.ics",
    previousEtag = savedEtag
)

when (result) {
    is IcsResult.Success -> {
        saveEtag(result.etag)
        processEvents(result.events)
    }
    is IcsResult.NotModified -> println("No changes")
    is IcsResult.Error -> println("Error: ${result.message}")
}
```

## Java Interoperability

iCalDAV is written in Kotlin but fully compatible with Java:

```java
CalDavClient client = CalDavClient.withBasicAuth("user", "pass");
DavResult<CalDavAccount> result = client.discoverAccount(serverUrl);

if (result instanceof DavResult.Success) {
    CalDavAccount account = ((DavResult.Success<CalDavAccount>) result).getValue();
    for (Calendar calendar : account.getCalendars()) {
        System.out.println(calendar.getDisplayName());
    }
}
```

## RFC Compliance

| RFC | Description | Support |
|-----|-------------|---------|
| RFC 5545 | iCalendar | Full |
| RFC 4791 | CalDAV | Full |
| RFC 6578 | Collection Sync | Full |
| RFC 7986 | iCalendar Extensions | Partial (IMAGE, CONFERENCE) |
| RFC 9073 | Structured Locations | Partial |

## License

Apache License 2.0

## Contributing

Contributions are welcome. Please open an issue to discuss significant changes before submitting a PR.

## Security

Report security vulnerabilities privately to the maintainers. Do not open public issues for security concerns.
