# 🧩 33IQ Next

[![Kotlin Version](https://img.shields.io/badge/Kotlin-2.x-blue.svg)](https://kotlinlang.org)
[![AGP](https://img.shields.io/badge/AGP-8.x-blue?style=flat)](https://developer.android.com/studio/releases/gradle-plugin)
[![Gradle](https://img.shields.io/badge/Gradle-9.x-blue?style=flat)](https://gradle.org)

An unofficial, third-party Android client for [33IQ](https://www.33iq.com) — a Chinese "thinking training" / riddle community (智力题库). **Built for personal learning and technical research only.** 33IQ has no public, documented third-party API. This app talks to the same server-rendered HTML pages a mobile browser would get (parsed with [Jsoup](https://jsoup.org/)) and to a handful of JSON endpoints the official Android app uses (same URLs, with a `p` query parameter). This is documented in detail below and in code comments so the reverse-engineering approach and its limits stay visible.

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
- **Answering** — submit a choice or open answer for real scoring (correct/wrong + 学识 gained/lost, both server-reported); pay 学识 to reveal a hint (member/终身会员 discounts shown from 33IQ's own quote)
- **Favourites** — fully local, on-device bookmark list (Room) — works instantly, no login needed
- **Login** — logs in through 33IQ's own AJAX endpoint; session cookie is persisted so subsequent requests act as the logged-in user
- **Daily check-in** — when a verified session is present, claims 33IQ's daily 签到 and lottery task on launch (once per Asia/Shanghai calendar day)
- **Settings** — session/account status, light/dark/system theme, open-source licenses, disclaimer

## How data is obtained (no public API)

33IQ does not publish a documented API for third-party clients. This app started from inspecting the **public, server-rendered HTML** of `https://www.33iq.com` (page source, embedded `<script>` blocks, and the site's own AJAX endpoints), then was upgraded using a real HAR (HTTP Archive) capture of the official 33IQ Android app's own traffic, supplied by a contributor. That capture confirmed a detail neither pure HTML inspection nor guessing could have found: on several endpoints — notably question detail — the **same URL the public website serves as HTML** returns a much richer plain **JSON** payload instead when a request adds the right `p` query parameter (the value is per-endpoint, confirmed live via `curl`). Everything below is backed either by requests verified live during development or by the HAR capture; see the table in the next section for exactly which is which.

- `GET https://www.33iq.com/question/`, `GET https://www.33iq.com/tag/<gbk-encoded-tag>.html` — question list / category pages (HTML, parsed by [`QuestionHtmlParser`](feature/feed/src/main/kotlin/com/jiugjk/iq33/feature/feed/data/datasource/remote/QuestionHtmlParser.kt))
- `GET https://www.33iq.com/question/<id>.html?p=3` — question detail, returned as **JSON** (parsed by [`QuestionJsonParser`](feature/feed/src/main/kotlin/com/jiugjk/iq33/feature/feed/data/datasource/remote/QuestionJsonParser.kt)) — confirmed from the real app's own traffic
- `GET https://www.33iq.com/index/search?k=<gbk-encoded-keyword>&type=question` — search (HTML)
- `POST https://www.33iq.com/index/login` with `email`/`password`/`ememberme` form fields — login (see [`SessionManager`](library/network/src/main/kotlin/com/jiugjk/iq33/library/network/SessionManager.kt)); the `ememberme` field name (not the more obvious `rememberme`) is confirmed from the real app's login request
- `GET https://www.33iq.com/app/taskall?p=3` — the app's own guest/logged-in probe, replies `{"status":"guest"}` for guests; `SessionManager` uses this instead of scraping the homepage to decide login state
- `POST https://www.33iq.com/member/gettask` with `tasktype=lottery&reason=` then `POST https://www.33iq.com/index/signin` (empty body) — daily lottery + 签到, captured from a logged-in **web** session (see [`DailyCheckIn`](library/network/src/main/kotlin/com/jiugjk/iq33/library/network/DailyCheckIn.kt)). The capture only asserted HTTP 200, so success `status` strings are unconfirmed; the client treats a non-error JSON reply as done, at most once per Asia/Shanghai calendar day
- `POST https://www.33iq.com/index/commentdeal` with `type=comment&sina_post=0&qq_post=0&context=<answer>&id=<q_id>&isanswer=1&action=comment` — submits an answer; confirmed live to reply `{"status":"success"|"wrong",...}` with a `score`/`myScore` 学识 delta, or `{"status":"repeat"}` if the account already answered that question (see [`AnswerRemoteDataSource`](feature/feed/src/main/kotlin/com/jiugjk/iq33/feature/feed/data/datasource/remote/AnswerRemoteDataSource.kt))
- `POST /index/showanswernew` quotes the answer/analysis cost. After explicit confirmation, `POST /index/payforshowanswer` authorises payment when required, then `POST /index/showanswertrue` returns `answer` and `explanation` HTML. The web capture and public JS confirm this sequence; a quote with `status=show` skips payment. These new web AJAX calls use UTF-8 form fields, GBK responses, no `p` suffix, and a fresh reveal nonce; they do not reuse capture cookies or tokens
- `POST https://www.33iq.com/index/showtipsbuy`, `POST https://www.33iq.com/index/showtips` (both `q_id=<id>`) — hint price quote (with separate normal/member/终身会员 prices) and hint text
- `POST https://www.33iq.com/index/login` confirmed live to reply `{"status":"1","uid":"<id>"}` on a real successful login (failure `status` strings are still unconfirmed - no failed login was captured - so `SessionManager` still verifies success via the guest-probe endpoint rather than trusting `status` alone)

Two site-specific quirks the client handles explicitly:
- The site's HTML is served as **GBK**, not UTF‑8 (see `<meta charset="GBK">`) — see [`IqHtmlClient`](library/network/src/main/kotlin/com/jiugjk/iq33/library/network/IqHtmlClient.kt), which decodes responses as GBK. Existing app-protocol forms use GBK; the separately captured web AJAX forms use UTF-8.
- Requests that look automated can be redirected to a login wall — the client detects this (`IqLoginRequiredException`) instead of silently mis-parsing a login page as content.

### What's confirmed vs. best-effort

To be transparent about reliability:

| Capability | Status |
|---|---|
| Browsing questions, tags, search | ✅ Verified against live HTML responses |
| Question detail (title/tags/author/choices/upvotes/comment & collect counts/right-ratio) | ✅ Real JSON endpoint (`?p=3`) and field names confirmed from a captured app session (`QuestionJsonParser`), and re-verified live via `curl` |
| Login field names (`/index/login`, `email`/`password`/`ememberme`) | ✅ Confirmed from a real captured login request |
| Login success detection | ✅ A real successful login's `{"status":"1",...}` reply is confirmed live; login state is still re-derived from the guest-probe endpoint rather than trusted from `status` alone (more robust, and error `status` strings are still unconfirmed - see next row) |
| Login failure detection | ⚠️ No failed login was ever captured, so the exact error `status` string values (wrong password, locked account, ...) are still unconfirmed guesses; they are mapped to a typed `LoginError` and only ever *explain* a failure the guest probe already established. A login whose state cannot be verified is reported as `NotVerified`, never as success |
| Submitting an answer (`/index/commentdeal`) | ✅ Confirmed live for choice questions: correct/wrong/already-answered are all distinct, real server responses with a real 学识 delta. Open-ended (non-choice) question submission is unconfirmed - every captured example was a choice question |
| Viewing answer / analysis | ✅ Implemented as an independent quote → confirmation → payment-if-needed → reveal flow, based on the web capture and public JS. Text and images are rendered natively. ⚠️ Automated tests use mocks; no live purchases were made during verification |
|| Hint price + reveal (`showtipsbuy` / `showtips`) | ✅ Confirmed live, including 33IQ's own normal/member/终身会员 price breakdown - shown as-is rather than recomputed client-side. A transport/parse failure after `showtips` is treated as an unknown paid outcome, not a safe retry |
|| `isLimit` flag on submission/reveal responses | ⚠️ Seen as `"0"` in every capture; its meaning when set (a daily cap of some kind, presumably) is unconfirmed |
| Answer analysis / 汤底 in initial detail | Not fetched automatically. Use the explicit 查看解析 action; viewing can consume 学识, prevent answering and affect wrong-answer statistics |
| Comments on a question | ❌ Not loaded. No comments-list endpoint was present in the HAR capture, so the parser leaves the list empty and the UI says so; only the comment *count* from the detail payload is shown |
| Word-bank questions | ✅ `is_select_answer=1`, `select_answer` and `answerStrNum` confirmed by an anonymous JSON fetch of #589144. Candidate tiles preserve server order/duplicates; incomplete metadata disables submission instead of falling back to free typing |
| Answered markers / hide answered | ✅ Persists locally confirmed correct/wrong/repeat results and the separate `seeanswer` restriction per account; list and detail share the state. ⚠️ No server history endpoint/field is confirmed: older answers and answers on other devices/web are **unknown**, not confirmed unanswered |
| Pagination beyond page 1 | ⚠️ Follows same-site HTTPS next links advertised by the HTML, never guesses `?page=N`. Fresh live pagination could not be verified because the site returned a security challenge. Missing/repeated next links stop paging; failures preserve the list and retry cursor |
| Favouriting a question on 33IQ's own servers | ❌ Not implemented — no server-side "收藏" endpoint was present in the HAR capture. Favourites are instead a genuine, fully-working **local** bookmark list |
| Search (`/index/search`) | ⚠️ HTML scraping only; during this round of development the endpoint started returning a login-wall/anti-bot response to repeated automated requests, so this path is untested against a fresh session — the existing implementation is unchanged and best-effort |
| Daily check-in (`/index/signin`, `/member/gettask`) | ⚠️ URLs and form fields confirmed from a captured web session that only asserted HTTP 200. Success `status` strings are unconfirmed; a non-error JSON reply is treated as done, once per Asia/Shanghai calendar day |

The HAR capture that unlocked the JSON endpoints above was a privacy-scrubbed analysis package (request/response structure and field *names*, without header or body *values*) rather than a raw HAR file, so some fields visible in it (e.g. notification counts, check-in history, feed-update endpoints) are documented as future work in [Roadmap](#roadmap) rather than wired in — they weren't needed for the features this app currently implements. If you can supply more captured traffic (e.g. via Reqable/Charles/Proxyman) for the endpoints still marked best-effort above, the data layer is isolated behind [`QuestionRepository`](feature/feed/src/main/kotlin/com/jiugjk/iq33/feature/feed/domain/repository/QuestionRepository.kt) / [`IqHtmlClient`](library/network/src/main/kotlin/com/jiugjk/iq33/library/network/IqHtmlClient.kt) so it can be swapped for a precise implementation without touching the UI.

### Feed refresh and local progress

- Pull-to-refresh replaces the batch using the saved next link. A cold reopen resumes that position; returning to the feed after 30 minutes without a successful batch load refreshes automatically.
- Cursor and the latest 300 loaded question IDs are stored separately by account and category. A refresh skips recent/displayed IDs and scans at most three pages to avoid duplicate-only or answered-only loops. Reaching that bound preserves the next cursor for another manual refresh.
- The hide-answered/viewed-analysis switch is a persistent device preference. Hidden cards are retained in the current batch so switching it off can show them again. Empty/fully hidden batches offer a next-batch or retry action.
- Correct, wrong and repeated submissions all confirm an answered ID. `seeanswer` confirms a **separate** viewed-analysis restriction, not that an answer was submitted. The official App's capture plus the user's on-screen message confirmed “看过解析无法再答题”. Both restrictions may coexist. Network errors, unrecognised replies and answer limits do not confirm either. Only IDs/preferences/cursors are saved here, not submitted answers.
- `isDone` / `uqr_type` are not treated as historical answer flags: captured details showed both as `0` even before a `seeanswer` rejection. Previously unknown server history still cannot be blocked in advance until a reliable read-only history signal is confirmed.
- Verified logins use the server UID for account isolation. Existing sessions without a UID receive a persistent opaque namespace; a later login cannot safely associate that anonymous legacy namespace with a UID. Web/other-device/old-version history is not imported. A missing local record is labelled unknown in detail.
- Automated tests cover cursor persistence, refresh retries, duplicate bounds, filtering, account isolation and submission outcomes. Actual server pagination and historical status still require a sanitised capture from a working session; do not use answer submissions or paid endpoints to probe history.

### Word-bank input

- #589144 returns `ischoose=0` **and** `is_select_answer=1`; checking `ischoose` alone incorrectly rendered it as a free-text answer. The explicit word-bank flag now selects the tile UI.
- Only `select_answer` is used for candidates. The similarly named `qc_wronganswer` field is a distractor pool, not a complete selectable answer bank. The minimal test fixture preserves the actual candidate array, including duplicates, without cookies or author/account metadata.
- Tiles are identified by array index and selected in tap order. Users can change a one-character selection, deselect, undo or clear. Submission sends the concatenated selected text through the existing open-answer protocol, not an A/B label or tile index; it requires the server's character count. No automatic submission occurs on selection.
- A missing/malformed candidate array or length keeps the word-bank type but disables answering with a retry prompt. Free text is never used as a silent fallback. Tests validate the literal-text request through a mock; no real answers or paid requests were sent to verify this change, and historical server grading results are not rewritten.

### Viewing analysis safely

- 查看提示 and 查看解析 are separate actions. Opening or cancelling the analysis price dialog does not pay or fetch the answer. Even a zero-cost quote requires confirmation because viewing can disqualify answering and affect wrong-answer statistics.
- The quote's `pay` is displayed as-is, not hardcoded to 60 or multiplied by membership/email factors. A `show` quote grants direct-fetch permission. A `success` quote (even with `pay=0`) still requires the server's authorisation call. The quote is rechecked before payment; changed charge terms require new confirmation. The legacy endpoint does not accept a client-side maximum-price parameter.
- A pending flag is synchronously persisted per account/question **before** any payment/reveal request. Network timeouts, malformed replies and cancellation keep it, including across process restart. Recovery calls `showanswertrue` only, never the payment endpoint; an unresolved recovery directs the user to verify on the website instead of offering another purchase.
- Web action bodies are one-shot and transport retries/redirects are disabled to prevent transparent POST replay. A repository-level lock plus ViewModel/reducer guards also prevent overlapping confirmations and competing answer/hint flows.
- Success marks viewed-analysis separately from answered status, preserving both if applicable. The current screen retains rendered content on rotation/return. Content is not persisted to disk; later visits obtain a new quote and do not assume repeat viewing is free.
- Tests cover captured quote/reveal shapes, free permission, changed price, rejection, double taps, cancellation, account changes, durable recovery and literal HTML/image rendering. Raw HARs and credentials are not stored in the project.
- The web capture also confirmed `POST /answer/getquestion` with current `q_id`, `tag`, `star`, `qtype`. The user confirmed clicking 下一题 does not change 学识. This is a single-question training stream, not `/question/` list pagination; integration into the list remains separate work. No `getquestion` request was replayed during this feature implementation.

## Tech-Stack

**Core Technologies:**
- **[Kotlin 2.x](https://kotlinlang.org/)** — Coroutines, Flow, KSP, Serialization
- **[Jsoup](https://jsoup.org/)** — HTML parsing (33IQ has no public documented API; some official-app JSON endpoints are used where confirmed)

**Android Jetpack:**
- **[Compose](https://developer.android.com/jetpack/compose)** + **[Navigation Compose](https://developer.android.com/jetpack/compose/navigation)** (type-safe routes)
- **[ViewModel](https://developer.android.com/topic/libraries/architecture/viewmodel)**, **[Room](https://developer.android.com/jetpack/androidx/releases/room)** (local favourites), **Core Splashscreen**

**Networking & Images:**
- **[OkHttp](https://square.github.io/okhttp/)** — HTTP client with a persistent, `SharedPreferences`-backed cookie jar for session persistence
- **[Coil 3](https://github.com/coil-kt/coil)** — image loading (over the same OkHttp stack, via `coil-network-okhttp`)

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
- **`library-network`** — the 33IQ HTTP/scraping layer (`IqHtmlClient`, cookie persistence, `SessionManager`, daily check-in) shared by the features above
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

For `feature-feed`, lists and search are HTML (`QuestionHtmlParser`, Jsoup selectors) while question detail is 33IQ's app-facing JSON (`QuestionJsonParser`); `QuestionRemoteDataSource` builds the right URL for each and moves every parse off the caller's thread. For `feature-favourite`, the data source is a local Room database.

### `library/network`

Everything specific to talking to `www.33iq.com` lives here, isolated from the UI:
- `IqHtmlClient` — GET/POST with GBK-aware encoding/decoding, login-wall detection
- `PersistentCookieJar` — keeps session cookies across app restarts, keyed by name/domain/path and matched against the request URL, under a lock so concurrent requests cannot corrupt or stale-write the store
- `SessionManager` — login/logout, and the only source of truth for "am I logged in": the reply from 33IQ's own `/app/taskall` probe is parsed and classified as authenticated, guest or unknown (an HTTP 200, or merely holding cookies, is never taken as proof of a session), and a probe that cannot be completed leaves the last verified state alone

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

> **Note**: an Android SDK + JDK 17 toolchain was later installed in the development sandbox and every command above (plus `:app:assembleDebug`) was run for real, with each real compile/lint/detekt/format failure it surfaced fixed in place — this is no longer a "written but unverified" codebase. The `UseCaseKonsistTest`/`ViewModelKonsistTest` "every class must have a matching unit test" gates from the original template were still removed, since this fork intentionally doesn't keep 1:1 test coverage for every generated class — see the comments in those files. CI (`.github/workflows/check.yml`) runs the same checks on every push.

## Project Scope & Limitations

- **Not affiliated with 33IQ.** This is a personal-learning reverse-engineering exercise, not a redistribution or commercial product.
- **No public API existed to build against.** The data layer works by parsing 33IQ's own public HTML; see [How data is obtained](#how-data-is-obtained-no-public-api) for exactly what was verified vs. guessed.
- **Paid content spends real 学识 on your real account.** Revealing an answer or a hint calls 33IQ's own paid endpoints and will actually deduct 学识 from whatever account is logged in - this client does not attempt to bypass that currency, it just gives the official app's own paid actions a UI.
- **Be respectful of 33IQ's servers** — this client makes the same kind of requests a mobile browser would; don't modify it to hammer the site or scrape at scale.

## Getting Started

**Prerequisites:** Android Studio, JDK 17+, Android SDK (compileSdk 36).

```bash
git clone <this-repo>
# Open in Android Studio: File -> Open -> select the cloned directory
```

No API key/config is required to browse — the app talks straight to `https://www.33iq.com`. Logging in uses your real 33IQ account credentials, sent directly to `33iq.com`'s own server (see the in-app disclaimer on the login screen).

### Building and signing

Both CI workflows (`build.yml`, run manually or on a feature branch, and `check.yml`, run on pull requests and pushes to `main`) build **both** variants — `:app:assembleDebug` and `:app:assembleRelease` — and upload each APK as an artifact. Building release on every run means R8 and resource shrinking, which only run for that build type, are checked continuously rather than the first time a release is actually needed.

Signing material is read from the environment and is never stored in this repository. Configure these four repository secrets to have CI sign what it builds:

| Secret | Contents |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | The keystore itself, base64-encoded (`base64 -w0 upload-keystore.jks`) |
| `SIGNING_KEYSTORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_ALIAS` | Key alias inside the keystore |
| `SIGNING_KEY_PASSWORD` | Password for that key |

The workflow decodes the keystore into the runner's temp directory for the length of the job. Both build types then use that one key, so successive CI builds install over each other instead of being rejected for a mismatched signature.

Nothing fails when the secrets are absent — a fork's pull request (GitHub withholds secrets from those) or a fresh clone still builds: debug keeps the throwaway key AGP generates and release comes out as `app-release-unsigned.apk`.

Locally, the same four values can go in `~/.gradle/gradle.properties` as `signingKeystoreFile`, `signingKeystorePassword`, `signingKeyAlias` and `signingKeyPassword` (a path, not base64), or be exported as `SIGNING_KEYSTORE_FILE`, `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD`.

## Roadmap

- Verify the real "收藏" (server-side favourite) and comment-posting/comments-list endpoints against a live account and wire them in behind the existing `QuestionRepository`/`BookmarkRepository` interfaces
- Verify the question-list next-link markup against a working live session; the client now follows advertised links instead of guessing pagination parameters
- Confirm a read-only historical answer-status endpoint/field to complement local answered markers
- Wire up further endpoints seen in the HAR captures but not yet used by any feature in this app (e.g. `/index/loadnummc` notification counts, `/app/signrecord` check-in history, `/follow/feedupdate` feed updates)
- Confirm the login endpoint's error `status` strings (only a successful login has ever been captured) and populate `IqSession.score` from `/app/userinfo`'s confirmed `score` field
- Verify analysis viewing on-device with the user's own explicit consent. Never assume correct answers or past viewing imply a zero price; use the server quote
- Confirm answer submission for open-ended (non-choice) questions - every captured submission was a multiple-choice question
- Turtle-soup (海龟汤) style guessing-game questions, exam/竞技 modes — not modeled yet

## Credits

The modular Clean Architecture skeleton (build-logic convention plugins, base ViewModel/state pattern, Konsist rules) is adapted from Igor Wojda's [android-showcase](https://github.com/igorwojda/android-showcase) (MIT licensed).

The real JSON question-detail endpoint, the correct login field names, and the guest-detection endpoint documented above were confirmed from a HAR capture of the official 33IQ Android app's own traffic, contributed by a project maintainer. A follow-up HAR capture of a real logged-in session actually answering questions, buying hints, and revealing answers confirmed the answer-submission/paid-reveal endpoints and the real login-success response shape.

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
