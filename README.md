# iCalDAV

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.icalDAV/icalendar-core.svg)](https://search.maven.org/search?q=g:io.github.icalDAV)

A modern, pure Kotlin CalDAV and iCalendar library for JVM and Android applications.

## Overview

iCalDAV provides comprehensive support for calendar data interchange and synchronization. It implements the core iCalendar specification (RFC 5545) along with CalDAV (RFC 4791), WebDAV Sync (RFC 6578), and modern extensions for rich event data.

## Why iCalDAV?

### The Gap in the Ecosystem

Existing Java/Kotlin calendar libraries fall into two categories:

1. **Low-level parsers** (like ical4j) - Excellent RFC compliance but require significant work to build sync engines, handle server quirks, or integrate with Android.

2. **Connector libraries** (like ical4j-connector) - Built on Apache HttpClient, which was removed from Android in API 23 (2015). No incremental sync support.

iCalDAV bridges this gap by providing:

- **Modern HTTP stack** using OkHttp, native to Android
- **Complete sync engine** with delta tracking, conflict resolution, and state management
- **Server compatibility layer** tested against iCloud, Google Calendar, Fastmail, and others
- **Kotlin-first API** with coroutines, sealed results, and data classes

### Comparison with Alternatives

Libraries in this space operate at different abstraction levels:

- **Parser libraries** (ical4j, biweekly) - Parse and generate iCalendar content only
- **Protocol libraries** (dav4jvm) - Handle WebDAV/CalDAV transport, but no parsing or sync logic
- **Solution libraries** (iCalDAV) - Full stack: parsing + protocol + sync engine

| Feature | iCalDAV | [dav4jvm](https://github.com/bitfireAT/dav4jvm)* | [ical4j](https://github.com/ical4j/ical4j) | [ical4j-connector](https://github.com/ical4j/ical4j-connector) | [biweekly](https://github.com/mangstadt/biweekly) |
|---------|---------|---------|--------|------------------|----------|
| **Abstraction Level** | Solution | Protocol | Parser | Protocol | Parser |
| RFC 5545 Parsing | Yes (via ical4j) | No | Yes | Yes | Yes |
| RFC 4791 CalDAV | Yes | Yes | No | Yes | No |
| RFC 6578 Sync | Yes | No | No | No | No |
| RFC 7986 Extensions | Yes | N/A | Yes | No | Partial |
| RFC 9073 Rich Events | Yes | N/A | Yes | No | No |
| RFC 9074 VALARM | Yes | N/A | Yes | No | No |
| CardDAV Support | No | Yes | No | Yes | No |
| Android Compatible | Yes | Yes | Yes | No** | Yes |
| Kotlin Coroutines | Yes | Yes | No | No | No |
| Sync State Management | Yes | No | No | No | No |
| ICS Subscriptions | Yes | No | No | No | No |

*dav4jvm is the protocol layer powering [DAVx⁵](https://www.davx5.com/), the popular Android CalDAV/CardDAV sync app

**ical4j-connector uses Apache HttpClient, removed from Android API 23+

## Features

### Core Capabilities

- **iCalendar Parsing and Generation** - Full RFC 5545 support for VEVENT, VTODO, VALARM, VFREEBUSY
- **Recurrence Rule Expansion** - RRULE, RDATE, EXDATE with comprehensive timezone handling
- **CalDAV Client** - Calendar discovery, CRUD operations, ETag-based synchronization
- **WebDAV Sync** - Incremental synchronization with sync-token (RFC 6578)
- **ICS Subscriptions** - webcal:// URL support with HTTP caching and refresh intervals
- **Conflict Resolution** - Pluggable strategies for handling sync conflicts

### Modern iCalendar Extensions

| Extension | RFC | Description |
|-----------|-----|-------------|
| Color, Images | RFC 7986 | Event colors, thumbnail images, conference URLs |
| Enhanced Alarms | RFC 9074 | Alarm UIDs, acknowledgment, proximity triggers |
| Rich Locations | RFC 9073 | Structured venues with coordinates and addresses |
| Participants | RFC 9073 | Extended attendee information with roles |
| Availability | RFC 7953 | Calendly-style booking availability |
| Relationships | RFC 9253 | Event linking with LINK and enhanced RELATED-TO |

## Modules

| Module | Description | Dependencies |
|--------|-------------|--------------|
| `icalendar-core` | ICS parsing, generation, RRULE expansion | ical4j |
| `webdav-core` | WebDAV protocol, XML handling | OkHttp |
| `caldav-core` | CalDAV client, calendar discovery | webdav-core, icalendar-core |
| `caldav-sync` | Sync engine with conflict resolution | caldav-core |
| `ics-subscription` | ICS/webcal subscription client | icalendar-core, OkHttp |

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    // Core iCalendar parsing (required)
    implementation("io.github.icalDAV:icalendar-core:1.0.0")

    // CalDAV client (includes webdav-core)
    implementation("io.github.icalDAV:caldav-core:1.0.0")

    // Sync engine (optional)
    implementation("io.github.icalDAV:caldav-sync:1.0.0")

    // ICS subscriptions (optional)
    implementation("io.github.icalDAV:ics-subscription:1.0.0")
}
```

### Gradle (Groovy)

```groovy
implementation 'io.github.icalDAV:icalendar-core:1.0.0'
implementation 'io.github.icalDAV:caldav-core:1.0.0'
```

## Quick Start

### Parse an ICS File

```kotlin
import com.icalendar.core.parser.ICalParser

val icsContent = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    UID:event-123
    DTSTART:20241215T100000Z
    DTEND:20241215T110000Z
    SUMMARY:Team Meeting
    END:VEVENT
    END:VCALENDAR
""".trimIndent()

val result = ICalParser.parse(icsContent)
when (result) {
    is ParseResult.Success -> {
        result.events.forEach { event ->
            println("${event.summary} at ${event.dtStart}")
        }
    }
    is ParseResult.Error -> println("Parse error: ${result.message}")
}
```

### Generate ICS Content

```kotlin
import com.icalendar.core.generator.ICalGenerator
import com.icalendar.core.model.*

val event = ICalEvent(
    uid = "event-123",
    importId = "event-123",
    summary = "Team Meeting",
    dtStart = ICalDateTime.now(),
    dtEnd = ICalDateTime.now().plusHours(1),
    status = EventStatus.CONFIRMED,
    transparency = Transparency.OPAQUE,
    // ... other properties
)

val icsContent = ICalGenerator.generate(listOf(event))
```

### Expand Recurring Events

```kotlin
import com.icalendar.core.recurrence.RRuleExpander
import com.icalendar.core.model.RRule
import com.icalendar.core.model.Frequency

val rrule = RRule(
    freq = Frequency.WEEKLY,
    interval = 1,
    byDay = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
    count = 10
)

val instances = RRuleExpander.expand(
    rule = rrule,
    start = event.dtStart,
    rangeEnd = event.dtStart.plusMonths(3),
    maxInstances = 100
)
```

### CalDAV Client

```kotlin
import com.icalendar.caldav.client.CalDavClient
import com.icalendar.caldav.discovery.CalDavDiscovery

// Discover calendars
val discovery = CalDavDiscovery(httpClient)
val calendars = discovery.discoverCalendars(
    serverUrl = "https://caldav.example.com",
    username = "user",
    password = "pass"
)

// Fetch events from a calendar
val client = CalDavClient(httpClient)
val result = client.fetchEvents(calendars.first().url)

when (result) {
    is DavResult.Success -> result.data.forEach { println(it.event.summary) }
    is DavResult.HttpError -> println("HTTP ${result.code}: ${result.message}")
    is DavResult.NetworkError -> println("Network error: ${result.exception}")
    is DavResult.ParseError -> println("Parse error: ${result.message}")
}
```

### Sync Engine

```kotlin
import com.icalendar.sync.engine.SyncEngine
import com.icalendar.sync.model.*

val syncEngine = SyncEngine(calDavClient)

val report = syncEngine.sync(
    calendarUrl = "https://caldav.example.com/calendar/",
    previousState = SyncState.initial(calendarUrl),
    localProvider = myLocalProvider,
    handler = mySyncHandler,
    callback = object : SyncCallback {
        override fun onConflict(conflict: SyncConflict): ConflictResolution {
            return ConflictResolution.USE_REMOTE
        }
    }
)

println("Synced: ${report.upserted.size} added, ${report.deleted.size} deleted")
```

## Server Compatibility

Tested and verified with:

| Server | Discovery | CRUD | Sync | Notes |
|--------|-----------|------|------|-------|
| Google Calendar | Yes | Yes | Yes | OAuth 2.0 required |
| Apple iCloud | Yes | Yes | Yes | App-specific password required |
| Fastmail | Yes | Yes | Yes | Full RFC compliance |
| Microsoft 365 | Yes | Yes | Yes | Via Outlook CalDAV |
| Nextcloud | Yes | Yes | Yes | Self-hosted |
| Radicale | Yes | Yes | Yes | Lightweight Python server |
| DAViCal | Yes | Yes | Yes | PostgreSQL backend |
| Baikal | Yes | Yes | Yes | PHP-based |

## RFC Compliance

### Directly Implemented

| RFC | Name | Module | Status |
|-----|------|--------|--------|
| [RFC 5545](https://tools.ietf.org/html/rfc5545) | iCalendar | icalendar-core | Full |
| [RFC 4791](https://tools.ietf.org/html/rfc4791) | CalDAV | caldav-core | Full |
| [RFC 6578](https://tools.ietf.org/html/rfc6578) | WebDAV Sync | caldav-sync | Full |
| [RFC 4918](https://tools.ietf.org/html/rfc4918) | WebDAV | webdav-core | Partial |
| [RFC 3744](https://tools.ietf.org/html/rfc3744) | WebDAV ACL | webdav-core | Partial |

### iCalendar Extensions (via ical4j)

| RFC | Name | Status |
|-----|------|--------|
| [RFC 7986](https://tools.ietf.org/html/rfc7986) | iCalendar Extensions | Full (COLOR, IMAGE, CONFERENCE) |
| [RFC 9074](https://tools.ietf.org/html/rfc9074) | VALARM Extensions | Full (UID, ACKNOWLEDGED, PROXIMITY) |
| [RFC 9073](https://tools.ietf.org/html/rfc9073) | Event Publishing | Full (VLOCATION, PARTICIPANT) |
| [RFC 7953](https://tools.ietf.org/html/rfc7953) | VAVAILABILITY | Full |
| [RFC 9253](https://tools.ietf.org/html/rfc9253) | Relationships | Full (LINK, enhanced RELATED-TO) |


## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| [ical4j](https://github.com/ical4j/ical4j) | 4.2.2 | iCalendar parsing (RFC 5545) |
| [OkHttp](https://square.github.io/okhttp/) | 4.12.0 | HTTP client |
| [Kotlin](https://kotlinlang.org/) | 1.9.22 | Language runtime |
| [kotlinx-coroutines](https://github.com/Kotlin/kotlinx.coroutines) | 1.7.3 | Async operations |
| [SLF4J](https://www.slf4j.org/) | 2.0.12 | Logging facade |

## Requirements

- JDK 17 or higher
- Android API 26+ (for Android projects)

## Documentation

- [CONTRIBUTING.md](CONTRIBUTING.md) - Contribution guidelines

## Contributing

Contributions are welcome. Please read our [Contributing Guide](CONTRIBUTING.md) before submitting pull requests.

## Security

For reporting security vulnerabilities, please see our [Security Policy](SECURITY.md).

## License

```
Copyright 2025 iCalDAV Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## Acknowledgments

- [ical4j](https://github.com/ical4j/ical4j) - Foundation for iCalendar parsing
- [OkHttp](https://square.github.io/okhttp/) - HTTP client
- [CalConnect](https://www.calconnect.org/) - Calendar standards organization
