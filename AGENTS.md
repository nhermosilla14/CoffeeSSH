# Project Guidance

## Architecture and scope

- Keep `:terminal` pure JVM with no Android dependencies. Changes to the
  terminal engine should remain unit-testable without an emulator.
- Keep Android UI, persistence, SSH orchestration, and services in `:app`.
- Use Jetpack Compose and Material 3 for screens. Keep user-visible strings in
  both `app/src/main/res/values/strings.xml` and `values-es/strings.xml`.
- The app id is `cl.segfault.coffeessh`; do not change it without an explicit
  migration decision.
- The product is for sideloading/personal use. Do not add store-specific
  requirements or third-party plugin APIs without an explicit scope decision.

## Security requirements

- Store passwords and private keys only encrypted with the Android Keystore-backed
  encryption layer; plaintext key material may exist only in memory while needed.
- Preserve TOFU host-key verification. Unknown hosts require explicit acceptance;
  changed host keys must produce a warning and refuse connection until the user
  intervenes.
- Generated private keys use the OpenSSH `openssh-key-v1` format for sshj
  compatibility, while application storage remains encrypted.
- Do not commit credentials, private keys, host fixtures containing secrets, or
  local machine configuration.

## Build and test

- Use the committed Gradle wrapper and JDK 21 when the system JDK is newer than
  the Android Gradle Plugin supports.
- Run `./gradlew :terminal:test` for terminal changes and
  `./gradlew :app:assembleDebug` for Android build validation.
- JVM integration tests may use the Docker OpenSSH fixture. Keep those tests
  repeatable and verify key generation, copy-key behavior, authentication,
  duplicate suppression, and changed-host rejection when modifying that flow.
- Prefer unit tests for the terminal engine and instrumented tests only where
  Android behavior or Room requires them.

## Open-source licenses

Whenever a new open-source dependency, library, plugin, font, icon set, or other distributable component is added:

- Identify the exact component version and its license from the authoritative upstream source.
- Confirm whether its license requires attribution, copyright notices, license text, source disclosures, or other notices.
- Add the required attribution and license text to the in-app **Open source licenses** view before shipping.
- Include transitive components when their license terms require redistribution notices.
- Keep this list synchronized when dependency versions or licensing terms change.
- Do not replace a component's required notice with a generic license label.
- Rebuild and review the release artifact after changing the license catalog.
