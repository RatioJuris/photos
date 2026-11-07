<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Changelog

## v1.0.0 — 2026-11-07

First release.

### Gallery
- iOS-style Library with Years / Months / All Photos; newest photo at the bottom; pinch to change grid size.
- Albums with My Albums, Media Types (Videos, Screenshots, Animated, RAW) and Utilities
  (Places, Protected, Archive, Bin).
- Search by name, album or month.
- Viewer with momentum zoom and pan, swipe between photos, swipe down to close, swipe up for details,
  keyboard / D-pad navigation, filmstrip.
- Video playback (videos are never encrypted).
- Archive and Bin (Bin on Android 11+), with settings.
- Places map and location in Details (locations read on-device; maps opt-in, OpenStreetMap).
- Light and dark appearance.

### Protected photos
- Argon2id (64 MiB, 3 passes) + AES-256-GCM, PHTX v1 format.
- Protected photos are ordinary JPEG previews to every other app; the encrypted original is appended.
- No visible protection indicators; discreet "Continue Securely"; wrong passwords fail silently.
- Verified write before originals can be removed; screenshots blocked while an original is shown.

### Icons
- Adaptive icon with themed (monochrome) layer, legacy square and round icons for all densities,
  and a 512 px Play Store icon — all generated from one original marigold-petal geometry.
