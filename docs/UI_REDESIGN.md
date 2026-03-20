# Android 14 UI Redesign

**Date:** 2026-03-21  
**Version:** v2.0

## Overview

Complete redesign of InputLeaf Android UI to match Android 14 AOSP Material You design language.

## Key Changes

### Visual Design
- Material Symbols Rounded icon pack
- Material You dynamic color system (Android 12+)
- Heavy rounded corners (24-32dp)
- Gradient backgrounds and accents
- Generous spacing and breathing room

### Components
- **Animated Bottom Navigation:** Floating pill with sliding background indicator
- **Gradient Cards:** Reusable cards with optional gradient backgrounds
- **Circular Avatars:** Icon avatars with gradient backgrounds
- **Material Toggle Switch:** Android 14 style toggle with gradient active state
- **Redesigned Cursor:** Android "Show Taps" style with ripple animation

### Screens
- **Home Screen:** Large connection card, quick info cards, Shizuku status, search bar
- **Servers Screen:** Search bar, gradient server cards
- **Settings Screen:** Grouped settings with toggle switches

## Design Specifications

See: `docs/superpowers/specs/2026-03-21-android14-ui-redesign.md`

## Implementation

All UI components use Jetpack Compose with Material 3. Dynamic color support on Android 12+ with graceful fallback for older versions.

## Testing

Tested on:
- Android 14 (Material You dynamic color)
- Android 12-13 (dynamic color)
- Android 11 and below (static theme)

## Screenshots

[Add screenshots here after implementation]
