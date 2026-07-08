# CoffeeSSH

An Android SSH client inspired by JuiceSSH, built with Kotlin, Jetpack Compose and sshj.
See [PLAN.md](PLAN.md) for the full roadmap.

**Status**: M0 — project scaffolding (dashboard shell, no SSH yet).

## Requirements

- JDK 17–21 to run Gradle (system JDK 26 is too new; a Temurin 21 lives in `~/.jdks/jdk-21.0.11+10`)
- Android SDK with platform 36 (`local.properties` → `sdk.dir`)

## Build

```sh
export JAVA_HOME=~/.jdks/jdk-21.0.11+10
./gradlew :app:assembleDebug     # build APK
./gradlew :app:installDebug      # install on connected device/emulator
./gradlew :terminal:test         # terminal engine tests
```

## Modules

- `:app` — Compose UI, and (from M1 on) data + SSH layers
- `:terminal` — pure-JVM terminal emulator engine (no Android dependencies)

## License

[MIT](LICENSE)
