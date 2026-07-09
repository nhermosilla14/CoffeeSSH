# CoffeeSSH — Project Plan

An Android SSH client inspired by JuiceSSH (abandonware, last updated ~2020), rebuilt
from scratch with a modern stack. Reference screenshots live in `views/`.

---

## 1. Agreed decisions

| Topic              | Decision                                                                 |
|--------------------|--------------------------------------------------------------------------|
| App name           | **CoffeeSSH**                                                            |
| Application id     | **`cl.segfault.coffeessh`**                                              |
| Branding           | Coffee/espresso brown Material 3 palette (layout inspired by JuiceSSH)   |
| UI toolkit         | **Jetpack Compose** + Material 3                                         |
| SSH library        | **sshj** (+ BouncyCastle for crypto/key generation)                      |
| Terminal emulator  | **Written from scratch** (own VT100/xterm subset engine)                 |
| Terminal fonts     | Bundled: **JetBrains Mono (default)**, Fira Mono, Hack — user-selectable |
| License            | **MIT**                                                                  |
| Distribution       | Sideload / personal use (no store requirements)                          |
| Min Android        | API 26 (Android 8.0) — target latest (API 36)                            |
| "Plugins"          | Built-in **copy public key to server** feature (no third-party plugin API)|
| Languages          | English + Spanish from the start                                          |
| First milestone extras | Copy public key to server, "Frequently Used" dashboard section       |

## 2. Reference screens (from `views/`)

| File   | Screen                                                                      |
|--------|-----------------------------------------------------------------------------|
| 1.png  | Dashboard, light theme: Connections / Frequently Used / Plugins / CloudSync / TeamShare / Settings / Help cards |
| 2.png  | Same dashboard, dark theme                                                  |
| 3.png  | Terminal (Solarized-like scheme, tmux) + extra-keys bar + soft keyboard     |
| 4.png  | Terminal running vim with syntax colors (256-color support matters)         |
| 5.png  | Settings → terminal Theme/Colors picker (later milestone)                   |
| 6.png  | Identity editor + "Import Private Key" dialog (Generate / Paste / Import)   |
| 7.png  | Connections screen: tabs (Port Forwards / Connections / Identities), collapsible groups, FAB |
| 8.png  | Terminal long-press menu: Copy, Paste, Snippets, transcripts, Keep screen on, Clear |

What we replicate now vs later:
- **Now**: dashboard (minus CloudSync/TeamShare/Plugins), connections with groups,
  identities with key management, terminal + extra keys, copy/paste, frequently used.
- **Later**: color scheme picker, snippets, transcripts, port forwards.
- **Never (out of scope)**: CloudSync, TeamShare, third-party plugin SDK, Telnet/Mosh.

## 3. Tech stack

- Kotlin 2.x, Gradle (Kotlin DSL) + version catalog (`libs.versions.toml`), AGP current stable
- Jetpack Compose (BOM) + Material 3 + Navigation Compose
- Room (SQLite) for persistence, DataStore for settings
- Kotlin Coroutines + Flow throughout
- sshj + BouncyCastle (registered as security provider, replacing Android's stripped BC)
- Android Keystore for at-rest encryption of secrets
- JUnit for JVM tests (terminal engine), instrumented tests only where unavoidable

Module layout:

```
CoffeeSSH/
├── app/                 # Compose UI, DI wiring, Android services
│   └── src/main/kotlin/cl/segfault/coffeessh/
│       ├── ui/          # screens: dashboard, connections, identities, terminal, settings
│       ├── data/        # Room entities/DAOs, repositories, Keystore crypto
│       ├── ssh/         # sshj wrapper: session mgmt, auth, host keys, foreground service
│       └── di/
└── terminal/            # PURE JVM module: emulator engine (no Android deps)
    └── src/main/kotlin/ # parser, screen buffer, scrollback — fully unit-testable
        (TerminalView, the Android custom View, lives in :app and consumes this)
```

## 4. Data model (Room)

```
Identity(id, nickname, username, passwordEnc?, privateKeyEnc?, publicKey?, keyType?, createdAt)
Group(id, name, sortOrder)
Connection(id, nickname?, host, port=22, identityId?, sortOrder, createdAt)
ConnectionGroup(connectionId, groupId)          # many-to-many: arbitrary categories
ConnectionLog(id, connectionId, connectedAt)    # feeds "Frequently Used"
KnownHost(id, host, port, keyType, fingerprint, addedAt)
```

Notes:
- A connection may belong to **multiple groups** (JuiceSSH-style tags); ungrouped ones
  show under an implicit "All"/"Ungrouped" section.
- Identities are separate from connections and reusable across them (as in screenshot 6/7).

## 5. Security design

- One AES-256-GCM master key in **Android Keystore** (non-exportable).
- Private keys and passwords stored in Room **only encrypted** with that master key.
- Key material decrypted in memory only while connecting; never written to disk in plaintext.
- Host key verification: TOFU — on first connect show fingerprint dialog (accept/reject),
  store in `KnownHost`; on mismatch show a loud warning and refuse until user intervenes.
- Generated keys: Ed25519 (default), ECDSA P-256/384/521, RSA 2048/3072/4096; optional
  passphrase on export. Public key exportable via share sheet/clipboard.

## 6. Terminal emulator scope (the hard part)

Engine (`:terminal`, pure JVM):
- Cell grid with fg/bg color + attributes (bold, italic, underline, inverse, etc.)
- Main + **alternate screen** (DECSET 1049 — required by vim/tmux/less)
- Scrollback ring buffer (main screen only), configurable size
- Parser state machine: C0 controls, ESC, CSI, OSC (window title), DCS (ignored initially)
- SGR: 16 colors, **256-color**, truecolor; reset/bold/inverse etc.
- Cursor addressing, scroll regions (DECSTBM), insert/delete line/char — tmux needs these
- Modes: autowrap (7), cursor visibility (25), bracketed paste (2004), application cursor keys (1)
- UTF-8 decoding; East Asian wide chars can start best-effort (full width handling later)
- Resize handling (no scrollback reflow in v1 — same as most terminals' early versions)
- `TERM=xterm-256color`

Rendering & input (`:app`):
- Custom `View` drawing on `Canvas` with monospace typeface, embedded in Compose via
  `AndroidView`; bundled fonts: **JetBrains Mono (default)**, Fira Mono, Hack (picker in Settings)
- Soft keyboard via `InputConnection`; hardware keyboard support
- **Extra-keys bar** (Compose): ESC, TAB, CTRL, ALT, arrows, HOME/END, PGUP/PGDN, `/`, `|`, `-`,
  FN toggle, hide-keyboard — CTRL/ALT act as sticky modifiers
- Long-press menu: Copy (selection mode), Paste, Keep screen on, Clear (Snippets/transcripts later)
- Pinch-to-zoom font size

Test strategy: unit tests feed escape-sequence byte streams and assert full screen state
(inspired by esctest); golden tests for vim/tmux startup sequences captured from a real
session. This is what makes "write our own" viable.

## 7. SSH layer

- sshj `SSHClient` per session; auth methods: password, public key, keyboard-interactive
  (fallback chain based on what the identity provides)
- PTY shell channel with initial size + `window-change` on resize/rotation
- Keepalives; clean disconnect handling with UI state (connecting / connected / failed / closed)
- **Foreground service** owning active sessions (survives app backgrounding; notification
  with disconnect action) — Android will kill sessions otherwise
- Copy public key to server: connect with existing credentials, then
  `mkdir -p ~/.ssh && append to authorized_keys` (dedup check first) — ssh-copy-id semantics

## 8. Screens (Compose)

1. **Dashboard** — cards: Connections, Frequently Used (top 3 by log count), Settings, Help
2. **Connections** — tabs: Connections / Identities (Port Forwards tab added later);
   collapsible group sections; FAB to add; long-press: edit/delete/duplicate
3. **Connection editor** — nickname, host, port, identity picker, groups multi-select
4. **Identity editor** — nickname, username, password, key section with
   Generate / Paste / Import (SAF file picker) dialog, export public key
5. **Groups management** — create/rename/delete (reachable from connection editor + connections screen)
6. **Terminal** — full-screen, extra-keys bar, long-press menu, connection status overlay
7. **Settings** — app theme (system/light/dark), terminal font (JetBrains Mono/Fira Mono/Hack)
   + font size, scrollback size, keepalive
8. Dialogs: host key TOFU prompt, host key mismatch, copy-key-to-server flow, delete confirmations

i18n: all strings in `strings.xml` (`values/` EN, `values-es/` ES) from day one.

## 9. Milestones

### M0 — Scaffolding (small) — DONE (2026-07-08)
- Git repo, `.gitignore`, MIT LICENSE, README stub
- Gradle project: `:app` + `:terminal`, version catalog, Compose, minSdk 26 / target 36
- Coffee-brown M3 theme (light/dark), navigation skeleton, EN+ES string scaffolding
- App icon (simple coffee-cup + terminal glyph)
- **Done when**: app builds and runs on the API 36 AVD showing the dashboard shell.
- Build notes from execution:
  - AGP 9.x has **built-in Kotlin**: `org.jetbrains.kotlin.android` must NOT be applied
    (only `org.jetbrains.kotlin.plugin.compose` and, for pure-JVM modules, `org.jetbrains.kotlin.jvm`).
  - AndroidX (Compose BOM 2026.06.01) requires **compileSdk 37**; AGP auto-installed the platform.
  - Toolchain pinned: Gradle 9.6.1 wrapper + Temurin JDK 21 at `~/.jdks/jdk-21.0.11+10`
    (system JDK 26 unsupported by AGP; export `JAVA_HOME` before `./gradlew`).

### M1 — Data & CRUD — DONE (2026-07-08)
- Room schema + migrations baseline, Keystore crypto helper
- Groups, Connections, Identities CRUD screens (no SSH yet)
- Password/key fields encrypted at rest
- **Done when**: connections/identities/groups can be created, edited, grouped, and survive restarts.
- Build notes from execution:
  - Room + KSP: `androidx.room` Gradle plugin (2.8.4) handles schema export via
    `room { schemaDirectory(...) }`; KSP 2.3.9 matches Kotlin 2.4.0 built into AGP 9.
  - `AppContainer` is a hand-rolled DI container (no Hilt/Koin) — small app, kept simple.
  - Verified with 10 instrumented tests (`connectedDebugAndroidTest`): 5 crypto round-trip/
    tamper-detection tests, 5 Room DAO tests (cascade/SET_NULL FK behavior, frequent-sort query).
  - Manually verified full CRUD + persistence-across-process-death on a **physical device**
    (Motorola Edge 30 Neo, Android 14) over USB, not just the emulator — confirmed dark theme
    and Spanish locale auto-selection also work correctly outside the AVD.
  - Process note: automated `adb input tap` on a personal physical device can hit floating
    overlays (chat bubbles, assistive touch) not present on a clean AVD — one such tap briefly
    opened Telegram. Going forward, physical-device interaction is user-driven; only read-only
    `adb screencap` is used from the agent side to verify results.

### M2 — Terminal engine — DONE (2026-07-09)
- `:terminal` engine per section 6, with unit-test suite
- `TerminalView` rendering + local echo/pipe test harness (fake session) to exercise UI without SSH
- **Done when**: engine passes test suite incl. vim/tmux golden captures; test harness renders and scrolls correctly.
- Build notes from execution:
  - Engine implemented as: `Cell`/`CellAttrs`/`TermColor` (grid + SGR state) → `Screen` (main/alt
    grid with scroll-region-aware ops) → `Scrollback` (ring buffer) → `EscapeSequenceParser`
    (DEC ANSI-compatible state machine: Ground/Escape/CSI/OSC/DCS, operating on codepoints
    from a streaming `Utf8Decoder` so multi-byte sequences split across network reads survive)
    → `Terminal` (façade implementing `ParserSink`, owns cursor/modes/charsets, dispatches
    CSI/ESC/OSC into actual VT100 semantics). `AnsiColors` maps `TermColor` to ARGB Int,
    kept dependency-free in `:terminal` so it's unit-testable on the plain JVM.
  - **70 total unit tests** in `:terminal` (up from the M0 placeholder): printing/wrapping,
    cursor movement + origin mode, erase functions, scroll regions + reverse-index +
    scrollback capture rules, SGR (16/256/truecolor + individual attribute on/off), modes
    (alt screen, bracketed paste, app cursor keys, insert mode, cursor visibility), DEC
    Special Graphics charset (SO/SI), UTF-8 streaming decode (split sequences, invalid
    bytes), ANSI color palette, key encoding.
  - **Golden tests** (`GoldenReplayTest`): raw pty byte captures from real `vim`, `tmux`,
    and `bash` (via a small Python harness, `terminal/golden-fixtures/`) replayed through
    `Terminal` and compared row-by-row against an independent oracle (`pyte`, a separate
    Python VT100 emulator) — not hand-written expectations. Found that plain `pyte.Screen`
    has no alternate-screen-buffer concept at all (confirmed by reading its source), so the
    vim fixture captures mid-session rather than after `:q!`; the exit-transition behavior
    is instead covered by a dedicated hand-written test.
  - `TerminalView` (Android custom `View`, in `:app`): per-row run-batched Canvas rendering
    (background rect + text per contiguous same-attribute span), bold-as-bright color +
    fake-bold stroke, block cursor, soft-keyboard input via a minimal `BaseInputConnection`,
    hardware key handling via `KeyEncoder`. Bundled fonts: JetBrains Mono (default), Fira
    Mono, Hack (OFL-licensed, `licenses/fonts/`).
  - `DemoShell` + `TerminalDemoScreen`: a tiny local pseudo-shell (reachable from
    Connections' overflow menu, "M2" dev-only entry) exercising the whole pipeline without
    SSH — `help`/`colors`/`boxes`/`wide`/`wrap`/`altscreen`/`echo`/`clear`, plus a minimal
    extra-keys bar (ESC/TAB/sticky-CTRL/arrows/HOME/END/PGUP/PGDN).
  - **Real bugs found and fixed during physical-device verification** (exactly what this
    kind of testing is for):
    1. Banner text got written at a hardcoded 80-col placeholder size before the view's
       real (narrower) size was known, then a later `resize()` truncated it instead of
       reflowing (engine behaves correctly per spec - "no reflow on resize" - the bug was
       in the demo's sequencing). Fixed by deferring `DemoShell.start()` until
       `TerminalView.onReady` fires after the first real layout.
    2. The extra-keys bar became completely inaccessible behind the soft keyboard
       (edge-to-edge + Compose needs explicit IME inset handling). Fixed with
       `Modifier.imePadding()`.
    3. (Demo-only, not the engine) `printBoxes()` left the DEC Special Graphics charset
       invoked while printing a human-readable label, garbling it — confirmed the
       charset-switching feature itself was working exactly as specified, the demo just
       mis-sequenced SO/SI.
  - Verified end-to-end on the physical device (Motorola Edge 30 Neo, Android 14):
    16-color + 256-color SGR rendering, Unicode and DEC-charset box-drawing side by side,
    CJK wide characters, alt-screen switch with content preserved underneath, soft-keyboard
    typing + backspace line editing, hardware Enter/Backspace, sticky-CTRL extra-key
    (Ctrl+L clear), and device rotation (resize reflows cleanly, no crash).
  - Known, deliberate engine limitations (documented in `Terminal`'s class doc too):
    combining marks dropped rather than merged; no scrollback reflow on resize; DCS/SOS/
    PM/APC payloads discarded; no mouse reporting; no left/right margins (DECSLRM).

### M3 — SSH integration
- sshj wiring, auth chain, TOFU host keys, foreground service, resize, reconnect UX
- Extra-keys bar, copy/paste, keep-screen-on
- Connection logging (feeds Frequently Used)
- **Done when**: real sessions to your servers work; vim + tmux usable end to end; session survives backgrounding.

### M4 — Keys & polish
- Key generation (Ed25519/ECDSA/RSA), paste/import, export public key
- **Copy public key to server** flow
- **Frequently Used** dashboard section
- Spanish translation pass, icon polish, settings screen completion
- **Done when**: full workflow — generate key, push to server, connect with it — works from the app alone.

### Backlog (post-M4, rough order)
1. Terminal color schemes + picker (screenshot 5)
2. Snippets
3. Save/share transcript
4. Port forwarding (tab already reserved in UI)
5. Mouse reporting, wide-char/emoji correctness, scrollback reflow
6. SFTP file browser? (not in original scope — decide later)

## 10. Dev environment notes (this machine)

- Android SDK at `~/Android/Sdk` (platforms 34 & 36, build-tools 36.1, emulator) — set
  `ANDROID_HOME`/`local.properties`
- AVD **Medium_Phone_API_36.1** available; `adb` works (no physical device attached currently)
- System JDK is **26** — likely too new for AGP/Gradle: install a JDK 17 or 21 alongside and
  point Gradle at it (toolchain or `org.gradle.java.home`); verify at M0
- No `gradle` CLI needed — the wrapper will be committed

## 11. Risks & mitigations

| Risk | Mitigation |
|------|------------|
| Terminal emulator complexity balloons | Strict scope (section 6), test-first with golden captures, alternate screen + SGR + scroll regions before anything exotic |
| sshj/BouncyCastle friction on Android (provider conflicts, missing algorithms) | Register full BC at startup; smoke-test all auth methods in M3 against a real sshd early |
| OpenSSH key format edge cases (openssh-key-v1, encrypted keys) | Use BC PEM/OpenSSH parsers; test matrix of key types in M4 |
| IME quirks with custom terminal view (autocorrect, swipe keyboards) | `InputConnection` with `TYPE_NULL`-style handling like mature terminals; test GBoard early in M2 |
| JDK 26 vs Gradle/AGP incompatibility | Pin JDK 21 toolchain at M0 before writing code |
