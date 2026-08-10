<p align="center">
  <img src="docs/logo.svg" alt="CoffeeSSH logo" width="144">
</p>

<h1 align="center">CoffeeSSH</h1>

<p align="center">
  A personal Android SSH client with a built-in terminal emulator.
</p>

CoffeeSSH is an Android SSH client inspired by JuiceSSH, rebuilt with Kotlin,
Jetpack Compose, Material 3, and sshj. It is distributed for sideloading and
personal use under the MIT license.

The app supports connection and identity management,
password and public-key SSH authentication, TOFU host-key verification, a
custom terminal emulator, foreground sessions, key generation/import, copying
public keys to servers, frequently used connections, and configurable terminal
settings. English and Spanish are supported.

## Requirements

- JDK 21 to run Gradle
- Android SDK with the platform and build tools versions declared by the project
- Android API 26 or newer

## Build

```sh
./gradlew :app:assembleDebug     # build APK
./gradlew :app:installDebug      # install on connected device/emulator
./gradlew :terminal:test         # terminal engine tests
```

## GitHub Releases

Every push to `main` publishes a signed APK as a timestamped prerelease nightly.
Pushing a version tag such as `v0.4.0` publishes a normal GitHub Release with the
same signed APK. The release signing key is stored only in GitHub Actions secrets;
it must never be committed to the repository.

The repository requires these Actions secrets: `ANDROID_KEYSTORE_BASE64`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`.

The SSH integration tests use a Docker OpenSSH server. See the test sources for
the required container setup and connection details.

## Modules

- `:app` — Compose UI, persistence, and SSH layers
- `:terminal` — pure-JVM terminal emulator engine (no Android dependencies)

Within `:app`, `ui/` contains screens, `data/` contains Room persistence and
Keystore-backed encryption, and `ssh/` contains sshj session management,
authentication, host-key verification, and the foreground service.

The terminal engine intentionally implements a focused VT100/xterm subset:
alternate screen, scrollback, cursor and scroll-region operations, 16/256/
truecolor SGR, common terminal modes, UTF-8 input, and resize handling. It is
rendered by the Android `TerminalView` in `:app` and is tested independently on
the JVM.

The terminal deliberately does not yet provide combining-mark composition,
scrollback reflow after resize, mouse reporting, or left/right margins.

## Data and SSH Behavior

Room stores reusable identities, connections, groups, connection-to-group
membership, connection history, and known host fingerprints. A connection can
belong to multiple groups and identities can be reused by multiple connections.

SSH sessions use password, public-key, and keyboard-interactive authentication
as available from the selected identity. Interactive sessions run through a
foreground service, send PTY resize events, and keep connection history for the
Frequently Used dashboard section. Unknown host keys require an explicit TOFU
acceptance; changed keys are rejected until the user intervenes.

Generated keys support Ed25519, ECDSA P-256/384/521, and RSA 2048/3072/4096.
Private keys are serialized as OpenSSH `openssh-key-v1` for sshj compatibility
and remain encrypted in application storage. Copy-key-to-server follows
`ssh-copy-id` semantics, including host-key verification and duplicate-entry
suppression.

The test suite covers key generation, password-authenticated key copying,
public-key authentication, duplicate suppression, changed-host rejection, and
settings persistence through JVM/Docker integration tests. Emulator smoke tests
cover the corresponding UI flows.

## License

[MIT](LICENSE)
