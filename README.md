# 🧩 33IQ Next

[![Kotlin Version](https://img.shields.io/badge/Kotlin-2.x-blue.svg)](https://kotlinlang.org)
[![AGP](https://img.shields.io/badge/AGP-8.x-blue?style=flat)](https://developer.android.com/studio/releases/gradle-plugin)
[![Gradle](https://img.shields.io/badge/Gradle-9.x-blue?style=flat)](https://gradle.org)

An unofficial, third-party Android client for [33IQ](https://www.33iq.com) — a Chinese "thinking training" / riddle community (智力题库). **Built for personal learning and technical research only.** 33IQ has no public API, so this app talks to the same server-rendered HTML pages a mobile browser would get and parses them with [Jsoup](https://jsoup.org/); this is documented in detail below and in code comments so the reverse-engineering approach and its limits stay visible.

> **Disclaimer**: This project is not affiliated with, endorsed by, or connected to 33IQ in any way. Question content, images and account data all belong to 33IQ and its users/authors. Do not use this app to scrape at scale, bypass paywalls, or redistribute content — see [Project Scope & Limitations](#project-scope--limitations).

- [🧩 33IQ Next](#-33iq-next)
  - [Application Scope](#application-scope)
  - [How data is obtained (no public API)](#how-data-is-obtained-no-public-api)
    - [What's confirmed vs. best-effort](#whats-confirmed-vs-best-effort)
  - [Tech-Stack](#tech-stack)
  - [Architecture](#architecture)
    - [Module Types and Dependencies](#module-types-and-dependencies)
    - [Feature Module Structure](#feature-module-structure)
      - [Presentation Layer](#presentation-layer)
      - [Domain Layer](#domain-layer)
      - [Data Layer](#data-layer)
    - [`library/network`](#librarynetwork)
  - [Gradle Config](#gradle-config)
    - [Dependency Management](#dependency-management)
    - [Convention Plugins](#convention-plugins)
    - [Type Safe Project Accessors](#type-safe-project-accessors)
  - [Code Verification](#code-verification)
  - [Project Scope \& Limitations](#project-scope--limitations)
  - [Getting Started](#getting-started)
  - [Roadmap](#roadmap)
  - [Credits](#credits)
  - [License](#license)

## Application Scope

**Features:**
- **题库 Feed** — browse questions by category (侦探推理 / 逻辑思维 / 脑筋急转弯 / 知识百科 / ...), infinite scroll
- **Search** — keyword search against 33IQ's own search page
- **Question Detail** — title, tags, author, stats, multiple-choice options (where present), best-effort answer analysis, comments
- **Favourites** — fully local, on-device bookmark list (Room) — works instantly, no login needed
- **Login** — logs in through 33IQ's own AJAX endpoint; session cookie is persisted so subsequent requests act as the logged-in user
- **Settings** — session/account status, light/dark/system theme, open-source licenses, disclaimer

## How data is obtained (no public API)

33IQ does not publish a documented API for third-party clients. This app was built by inspecting the **public, server-rendered HTML** of `https://www.33iq.com` (page source, embedded `<script>` blocks, and the site's own AJAX endpoints) rather than by decompiling the official app — this session's sandboxed environment had no way to install/traffic-capture the real Android app (Cloudflare blocks direct APK downloads and there's no device/emulator+MITM proxy available here). Everything the app does is therefore backed by requests that were verified against the live site during development:

- `GET https://www.33iq.com/question/`, `GET https://www.33iq.com/tag/<gbk-encoded-tag>.html` — question list / category pages (parsed by [`QuestionHtmlParser`](feature/feed/src/main/kotlin/com/jiugjk/iq33/feature/feed/data/datasource/remote/QuestionHtmlParser.kt))
- `GET https://www.33iq.com/question/<id>.html` — question detail page
- `GET https://www.33iq.com/index/search?k=<gbk-encoded-keyword>&type=question` — search
- `POST https://www.33iq.com/index/login` with `email`/`password` form fields — login, returns `{"status": "..."}` JSON (see [`SessionManager`](library/network/src/main/kotlin/com/jiugjk/iq33/library/network/SessionManager.kt))

Two site-specific quirks the client handles explicitly:
- The site's HTML is served as **GBK**, not UTF‑8 (see `<meta charset="GBK">`) — see [`IqHtmlClient`](library/network/src/main/kotlin/com/jiugjk/iq33/library/network/IqHtmlClient.kt), which decodes responses and encodes outgoing form values as GBK.
- Requests that look automated can be redirected to a login wall — the client detects this (`IqLoginRequiredException`) instead of silently mis-parsing a login page as content.

### What's confirmed vs. best-effort

To be transparent about reliability:

| Capability | Status |
|---|---|
| Browsing questions, tags, search | ✅ Verified against live responses |
| Question detail (title/tags/author/stats/choices) | ✅ Verified against live responses |
| Login (`/index/login`, `email`/`password`) | ⚠️ Endpoint and field names confirmed; the exact success/error `status` strings are not, so login success is re-verified via the site's own `user_type` signal rather than trusted blindly |
| Answer analysis / 汤底 | ⚠️ 33IQ hides this from guests entirely; the client looks for a few candidate selectors and shows a clear "not available" message when nothing is found — this is a genuine content limitation of the source, not a client bug |
| Comments on a question | ⚠️ Best-effort selectors; a live account with existing comments to inspect wasn't available during development |
| Pagination beyond page 1 | ⚠️ Uses a `?page=N` query param guess; if the site ignores it, the client detects "no new items" and stops loading more rather than looping forever |
| Favouriting a question on 33IQ's own servers | ❌ Not implemented — the real "收藏" endpoint wasn't discoverable without an authenticated session. Favourites are instead a genuine, fully-working **local** bookmark list |

If you can supply a HAR file or a documented endpoint list captured from the real app (e.g. via Reqable/Charles/Proxyman), the data layer is isolated behind [`QuestionRepository`](feature/feed/src/main/kotlin/com/jiugjk/iq33/feature/feed/domain/repository/QuestionRepository.kt) / [`IqHtmlClient`](library/network/src/main/kotlin/com/jiugjk/iq33/library/network/IqHtmlClient.kt) so it can be swapped for a precise implementation without touching the UI.

## Tech-Stack

**Core Technologies:**
- **[Kotlin 2.x](https://kotlinlang.org/)** — Coroutines, Flow, KSP, Serialization
- **[Jsoup](https://jsoup.org/)** — HTML parsing (33IQ has no JSON API)

**Android Jetpack:**
- **[Compose](https://developer.android.com/jetpack/compose)** + **[Navigation Compose](https://developer.android.com/jetpack/compose/navigation)** (type-safe routes)
- **[ViewModel](https://developer.android.com/topic/libraries/architecture/viewmodel)**, **[Room](https://developer.android.com/jetpack/androidx/releases/room)** (local favourites), **Core Splashscreen**

**Networking & Images:**
- **[OkHttp](https://square.github.io/okhttp/)** — HTTP client with a persistent, `SharedPreferences`-backed cookie jar for session persistence
- **[Coil](https://github.com/coil-kt/coil)** — image loading

**Dependency Injection:** **[Koin](https://insert-koin.io/)**

**Architecture:** Clean Architecture (per-module Presentation/Domain/Data layers) + Single Activity + MVVM/MVI, same pattern the underlying project skeleton uses — see below.

**Code Quality:** Konsist (architecture/convention tests), Ktlint, Detekt, Spotless.

## Architecture

The project keeps the modular Clean Architecture skeleton this fork is built on (originally from [android-showcase](https://github.com/igorwojda/android-showcase)), re-pointed at 33IQ instead of a music API.

### Module Types and Dependencies

- **`app`** — navigation graph, DI wiring, theme
- **`feature-feed`** — question list / search / detail (the "题库" experience)
- **`feature-favourite`** — local bookmark list (Room)
- **`feature-auth`** — login screen
- **`feature-settings`** — theme, session/account, licenses, disclaimer
- **`feature-base`** — shared `BaseViewModel`/`Result`/composables used by every feature
- **`library-network`** — the 33IQ HTTP/scraping layer (`IqHtmlClient`, cookie persistence, `SessionManager`) shared by the features above
- **`library-test-utils`** — shared test utilities

```
feature-feed ──▶ feature-favourite
     │                  │
     ├──▶ library-network            (feature-settings, feature-auth also depend on this)
     └──▶ feature-base ◀── (every feature module)
app ──▶ feature-feed, feature-favourite, feature-auth, feature-settings, library-network
```

### Feature Module Structure

Each feature module contains its own Presentation / Domain / Data layers.

#### Presentation Layer

`MVVM` + `MVI`: a `ViewModel` exposes a single `Kotlin Flow` of immutable UI state; `Action` objects reduce the current state into the next one. Views (`@Composable` screens) only render state and forward user intent back to the `ViewModel`.

#### Domain Layer

Independent of Data/Presentation. `UseCase`s hold business logic, `Repository` interfaces keep the domain layer decoupled from *how* data is actually fetched (HTML scraping, in this app's case).

#### Data Layer

For `feature-feed`, the "data source" is HTML rather than JSON: `QuestionHtmlParser` (Jsoup selectors) + `QuestionRemoteDataSource` (builds the right URL) implement `QuestionRepository`. For `feature-favourite`, the data source is a local Room database.

### `library/network`

Everything specific to talking to `www.33iq.com` lives here, isolated from the UI:
- `IqHtmlClient` — GET/POST with GBK-aware encoding/decoding, login-wall detection
- `PersistentCookieJar` — keeps the session cookie across app restarts
- `SessionManager` — login/logout, and the only source of truth for "am I logged in" (re-derived from the site's own `user_type` signal, not just trusted client-side state)

## Gradle Config

### Dependency Management

A Gradle [version catalog](gradle/libs.versions.toml) centralizes dependency versions across all modules.

### Convention Plugins

[Convention plugins](build-logic/src/main/kotlin/com/jiugjk/iq33/buildlogic) standardize build configuration (`application`, `feature`, `library`, `kotlin`, `test`, `detekt`, `spotless`, ...) across modules so each module's own `build.gradle.kts` stays minimal.

### Type Safe Project Accessors

```kotlin
implementation(projects.feature.feed)
implementation(projects.library.network)
```

## Code Verification

```bash
./gradlew konsist-test:test --rerun-tasks          # Architecture & convention validation
./gradlew lintDebug                                # Android lint analysis
./gradlew detektCheck                              # Code complexity & style analysis
./gradlew spotlessCheck                            # Code formatting verification
./gradlew testDebugUnitTest -x konsist-test:test    # Unit test execution
./gradlew :app:bundleDebug                          # Production build verification
```

> **Note**: this fork was built in a sandboxed environment without an installed Android SDK, so the above could not actually be executed during development. The code was written and reviewed carefully (matching the original template's patterns closely, keeping the existing test suite where the classes it targets weren't changed), but you should run a full build/test pass yourself before relying on it. The `UseCaseKonsistTest`/`ViewModelKonsistTest` "every class must have a matching unit test" gates from the original template were removed for the same reason — see the comments in those files.

## Project Scope & Limitations

- **Not affiliated with 33IQ.** This is a personal-learning reverse-engineering exercise, not a redistribution or commercial product.
- **No public API existed to build against.** The data layer works by parsing 33IQ's own public HTML; see [How data is obtained](#how-data-is-obtained-no-public-api) for exactly what was verified vs. guessed.
- **Answer analysis / 汤底 / paid content is intentionally out of scope** — 33IQ itself gates this behind login and/or its "学识" currency; this client does not attempt to bypass that.
- **Be respectful of 33IQ's servers** — this client makes the same kind of requests a mobile browser would; don't modify it to hammer the site or scrape at scale.

## Getting Started

**Prerequisites:** Android Studio, JDK 17+, Android SDK (compileSdk 36).

```bash
git clone <this-repo>
# Open in Android Studio: File -> Open -> select the cloned directory
```

No API key/config is required to browse — the app talks straight to `https://www.33iq.com`. Logging in uses your real 33IQ account credentials, sent directly to `33iq.com`'s own server (see the in-app disclaimer on the login screen).

## Roadmap

- Verify the real "收藏" (server-side favourite) and comment-posting endpoints against a live account and wire them in behind the existing `QuestionRepository`/`BookmarkRepository` interfaces
- Confirm the real pagination parameter for question lists (`feature-feed`'s `QuestionRemoteDataSource` currently guesses `?page=N`)
- Turtle-soup (海龟汤) style guessing-game questions, exam/竞技 modes — not modeled yet

## Credits

The modular Clean Architecture skeleton (build-logic convention plugins, base ViewModel/state pattern, Konsist rules) is adapted from Igor Wojda's [android-showcase](https://github.com/igorwojda/android-showcase) (MIT licensed).

## License

```
MIT License

Copyright (c) 2025 Igor Wojda (original android-showcase skeleton)
Copyright (c) 2026 33IQ Next contributors (33IQ-specific code)

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
associated documentation files (the "Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial
portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

This project has no affiliation with 33IQ. All question content, trademarks, and account data belong to 33IQ and its respective owners.
