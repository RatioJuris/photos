<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Contributing to Photos

Thank you for your interest in Photos by RatioJuris. This repository is currently **private**;
contributions are by invitation. These guidelines apply to everyone with access.

## Ground rules
- Be respectful and constructive.
- **Never commit secrets or personal data**: no keystores, `keystore.properties`, `local.properties`,
  API keys, tokens, real personal photos or screenshots containing personal information. Test images
  must be generated or freely licensed.
- **Security issues are not filed as normal issues** — follow [SECURITY.md](SECURITY.md).
- The repository holds source code and documentation only. Build outputs, APKs and CI workflows are
  intentionally not part of it.

## Licensing of contributions
Photos is licensed under **GPL-3.0-or-later**. By submitting a contribution you agree that it is
licensed under the same terms, and you confirm that you wrote it or have the right to submit it.

Add this header to every new source file:
```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
```
Dependencies must be compatible with GPL-3.0 (for example Apache-2.0, MIT, BSD). Do not add analytics,
advertising, tracking or cloud SDKs.

## Development setup
See the "Getting the app" section of the [README](README.md). Architecture notes and the protected
image format specification are maintained by the project maintainer and shared with contributors on request.

## Code style
- Kotlin official style; match the surrounding code.
- Public types and key functions carry the `RatioJuris` / `ratioJuris` prefix, as in the existing code.
- Keep cryptography and file I/O out of composables; run them off the main thread.
- No placeholder buttons or fake settings — every visible control must work.
- Follow the privacy rules in the architecture document: protected photos must never be labelled,
  badged or announced, and wrong passwords must not produce warnings.
- iOS-inspired design must use original components and artwork only (no Apple or Google assets).

## Protected image format
Changes to `phtx/` must keep v1 files readable. Any
incompatible change needs a new format version, an updated specification and new tests. Never weaken
cryptographic parameters to make tests faster — use the test-only fixtures instead.

## Before opening a pull request
```bash
./gradlew :phtx:test :app:lintStandardRelease :app:assembleStandardRelease
```
- All tests pass and lint reports no errors.
- New behaviour has tests (JVM tests in `phtx/`, instrumented tests in `app/src/androidTest/`).
- User-visible strings live in `res/values/strings.xml`.
- Screenshots in documentation contain test content only.
- Update [CHANGELOG.md](CHANGELOG.md) and the relevant documents.

## Commits and pull requests
- One logical change per commit, with a clear message in the imperative mood
  ("Add Bin recovery confirmation").
- Describe what you changed, why, and how you tested it (device, Android version, results).
