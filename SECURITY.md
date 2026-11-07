<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Security policy

Photos protects people's private photographs, so security reports are taken seriously.

## Supported versions

| Version | Supported |
|---|---|
| 1.0.x | Yes |
| older | No |

## Reporting a vulnerability

**Please do not open a public issue or pull request for security problems.**

Report privately using GitHub's **"Report a vulnerability"** (private security advisory) on this repository, or contact the maintainer **@RatioJuris** privately on GitHub with the subject `Photos security`.

Please include:
- the affected version and Android version/device;
- a description of the issue and its impact;
- steps to reproduce, and a proof-of-concept file if relevant (use test images only — never real
  personal photos or real passwords).

What to expect:
- acknowledgement within **7 days**;
- an assessment and, where confirmed, a fix plan within **30 days**;
- credit in the release notes if you wish, once a fix is available.

Please allow reasonable time for a fix before any public disclosure.

## Scope

In scope:
- the protected photo format and cryptography (`phtx/`);
- anything that reveals an original without the correct password, or reveals that a photo is protected
  where the app promises it does not;
- plaintext left behind (temporary exports, caches, logs);
- parsing of untrusted files (crashes, excessive memory use, unsafe handling);
- permission, intent, FileProvider or MediaStore misuse.

Out of scope:
- recovering a forgotten password — this is impossible by design;
- attacks requiring a rooted or compromised device, or an unlocked phone with the original already on
  screen;
- other apps re-encoding a protected photo and keeping only its preview (documented behaviour);
- what OpenStreetMap's servers learn when the user has turned on Maps (documented behaviour).

## Security design summary
- Argon2id (64 MiB, 3 passes) per-file key derivation; random 256-bit content key; AES-256-GCM with
  the entire header authenticated; fresh salt, keys and nonces for every file and password change.
- Strict, bounded parsing; unsupported versions rejected; verified write before originals can be removed.
- No passwords stored; originals kept only in memory while shown; `FLAG_SECURE` while visible.
- No analytics, advertising or tracking; network used only for opt-in map tiles.
