# InputLeaf Android - Development Journey

**Date Range:** March 21, 2026  
**Project:** Complete UI redesign + bug fixes + hotspot network fix  
**Branch:** `feature/android14-ui-redesign`

---

## Overview

InputLeaf is an Android app that allows you to control your PC (running InputLeap server) from your Android device. This session focused on:

1. **Complete UI redesign** matching Android 14 Material You design language
2. **Fixing reported bugs** (navigation, scrolling, theme issues)
3. **Adding requested features** (theme toggle, permission cards, screen name editing)
4. **Fixing network discovery** for hotspot connections

---

## What Was Built

### 1. UI Redesign (Android 14 Material You)

#### Theme System
- **Files:** `ui/theme/Color.kt`, `ui/theme/Shape.kt`
- **Features:**
  - Purple/lavender primary color palette (#A78BFA to #8B5CF6)
  - Light/dark mode support following system theme
  - Material You dynamic colors (Android 12+) with fallback
  - Success/warning color definitions
  - Gradient definitions for primary, accent, and success states

#### Reusable Components
- **AnimatedBottomNavigation.kt** (145 lines)
  - Floating pill-shaped bottom navigation
  - Animated sliding indicator (300ms cubic-bezier)
  - Theme-aware colors (works in light/dark mode)
  - 4 tabs: Home, Servers, Permissions, Settings
  
- **GradientCard.kt** - Card containers with gradient support
  
- **CircularAvatar.kt** - Icon avatars with gradient backgrounds
  
- **MaterialToggleSwitch.kt** - Android 14 style toggle switch
  
- **ShizukuStatusCard.kt** (extracted as shared component)
  - Shows Shizuku permission status
  - Action buttons for each status state
  - Theme-aware colors

#### Screens (Redesigned)

**MainScreen.kt (Home)**
- Connection status card
- Quick info cards (Screen, Cursor)
- Shizuku status card (hidden when ready)
- Server search bar with Scan button
- Discovered servers list
- Removed duplicate "Scan Again" button

**ServerListScreen.kt (New)**
- Dedicated server list view
- Scan functionality
- Add manual server dialog
- Empty state handling

**SetupScreen.kt (Renamed to Permissions)**
- Shizuku permission card
- Overlay permission card
- Battery optimization permission card
- Setup instructions

**SettingsScreen.kt**
- Connection section (screen name, auto-connect)
- Display section (theme toggle)
- Security section (show cursor)
- Fingerprints section
- All settings fully scrollable

#### Features Added

1. **Theme Toggle**
   - System default / Light / Dark modes
   - Persisted to DataStore
   - Immediate effect on UI

2. **Screen Name Editing**
   - Click to edit dialog
   - Validation (non-empty)
   - Pre-filled with current name

3. **Permission Cards**
   - Shizuku status
   - Overlay permission
   - Battery optimization status
   - Action buttons to request permissions

4. **Cursor Overlay Service**
   - Redesigned with ripple animation
   - Purple color scheme
   - Android "Show Taps" style

5. **App Icon**
   - New design: green leaf + white cursor
   - Teal background (#0B3D4A)
   - Adaptive icon support
   - PNG assets for all densities

---

## Technical Decisions

### Architecture
- **Pattern:** MVVM with AndroidViewModel
- **UI Framework:** Jetpack Compose with Material3
- **State Management:** StateFlow + Compose collectAsStateWithLifecycle
- **Persistence:** DataStore for preferences

### Icon Strategy
- Limited to `material-icons-core` (no extended package due to OOM)
- Icons used: Home, Settings, Info, Build, Phone, Lock, CheckCircle, Warning, Search, etc.
- All icons from rounded variant for consistency

### Theme Implementation
- Android 12+: `dynamicLightColorScheme()` / `dynamicDarkColorScheme()`
- Older Android: Fallback purple theme with light/dark variants
- All components use `MaterialTheme.colorScheme.*` for colors
- No hardcoded colors in UI components

### Navigation
- Single Activity architecture
- State-based screen switching
- Bottom navigation with animated indicator
- Separate composable functions for each screen

### Network Discovery Fix
- **Problem:** WifiManager.connectionInfo.ipAddress doesn't work for hotspot
- **Solution:** NetworkInterface enumeration finds correct IP
- Works for: Wi-Fi client, mobile hotspot, ethernet, any interface

---

## Files Modified/Created

### Theme & Components
```
app/src/main/java/com/inputleaf/android/ui/theme/
├── Color.kt          # Purple palette, gradients, semantic colors
└── Shape.kt         # Rounded shapes (24-32dp)

app/src/main/java/com/inputleaf/android/ui/components/
├── AnimatedBottomNavigation.kt  # Floating bottom nav
├── CircularAvatar.kt            # Icon avatars
├── GradientCard.kt              # Gradient card container
├── MaterialToggleSwitch.kt      # Custom toggle switch
└── ShizukuStatusCard.kt        # Shizuku status display
```

### Screens
```
app/src/main/java/com/inputleaf/android/ui/
├── MainActivity.kt      # Main activity with theme setup
├── MainScreen.kt       # Home screen
├── ServerListScreen.kt # Server list screen
├── SetupScreen.kt     # Permissions screen
├── SettingsScreen.kt  # Settings screen
└── SplashActivity.kt   # Splash screen with logo
```

### Services
```
app/src/main/java/com/inputleaf/android/service/
└── CursorOverlayService.kt  # Cursor overlay with ripple animation
```

### Resources
```
app/src/main/res/
├── drawable/
│   ├── ic_logo.xml              # App logo (leaf + cursor)
│   ├── ic_logo_splash.xml       # Splash logo
│   └── ic_launcher_foreground.xml # Launcher foreground
└── mipmap-*/                    # Launcher icons (all densities)
```

### Data/ViewModel
```
app/src/main/java/com/inputleaf/android/
├── ui/MainViewModel.kt          # Main ViewModel with all logic
└── storage/AppPreferences.kt     # DataStore preferences
```

---

## Build Status

- **Build:** ✅ Successful
- **Installation:** ✅ Installed on device (CPH2661 - Android 16)
- **Branch:** `feature/android14-ui-redesign`
- **Commits:** 20+ commits on feature branch

---

## Testing Checklist

After any changes, verify:

### UI/UX
- [ ] Bottom navigation switches screens correctly
- [ ] Theme toggle works (System/Light/Dark)
- [ ] Settings screen scrolls with many fingerprints
- [ ] Screen name edit dialog opens and saves
- [ ] Permission cards show correct status
- [ ] Only one Scan button on home screen
- [ ] Shizuku card hidden when ready
- [ ] App follows device wallpaper colors (Material You)

### Network
- [ ] Scan discovers servers on Wi-Fi network
- [ ] Scan discovers servers on hotspot network
- [ ] Manual server addition works
- [ ] Connection to server works

### Permissions
- [ ] Overlay permission request works
- [ ] Battery optimization exemption works
- [ ] Shizuku permission flow works

---

## Future Enhancements (Not Implemented)

User mentioned these ideas for future development:

1. **Setup Workflow**
   - Detailed screenshots and videos for Shizuku setup
   - Step-by-step guided setup

2. **Logging System**
   - Save app logs to accessible location
   - Easy sharing for debugging

3. **Hotspot Improvements**
   - Better support for PC as server via hotspot
   - Ensure single device connection at a time

4. **Android Compatibility**
   - Test Android 9 to latest
   - Handle edge cases
   - No crashes on any version

5. **Connection Management**
   - Hotspot connection to PC
   - PC acting as server
   - Single device priority

---

## Known Issues / Pre-existing Failures

- 5 pre-existing test failures in protocol/network layers (unrelated to UI changes)
- These were present before the redesign and should be investigated separately

---

## How to Build & Install

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Or manually
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Development Notes

### For Future Agents

When continuing development on this project:

1. **Read this document first** - It provides full context of what's been built
2. **Check the branch** - All changes are on `feature/android14-ui-redesign`
3. **Test on device** - Emulators may not show Material You colors properly
4. **Check Android version** - Dynamic colors need Android 12+
5. **Theme consistency** - Always use `MaterialTheme.colorScheme.*` colors
6. **No icon library** - Only use icons from `material-icons-core`

### Design Principles Applied

1. **Material You First** - Use dynamic colors when available
2. **Consistency** - Same patterns across all screens
3. **Theme-Aware** - All colors adapt to light/dark mode
4. **DRY** - Shared components, no code duplication
5. **User Feedback** - Loading states, status indicators, action buttons

---

## Key Decisions & Rationale

### Why No material-icons-extended?
- Causes OOM (Out of Memory) during dex merging
- Limited to icons in core package
- Uses workarounds: Build icon for servers, Info for servers, Lock for permissions

### Why Separate Screens?
- Home shows connection status + quick actions
- Servers shows detailed server list
- Permissions shows all permission statuses
- Settings shows app configuration
- Clear separation of concerns

### Why NetworkInterface Instead of WifiManager?
- WifiManager only gets Wi-Fi client IP
- Doesn't work for hotspot mode (phone is AP)
- NetworkInterface finds any active IPv4 interface
- Works for Wi-Fi, hotspot, ethernet, etc.

---

## Contact / Context

This document was created from the development session on March 21, 2026.

**Project:** InputLeaf Android  
**Purpose:** Control InputLeap server from Android device  
**Platform:** Android 9+ (Material You on Android 12+)  
**Architecture:** Jetpack Compose + MVVM

For questions or continuation of work, refer to:
- This document for context
- `docs/superpowers/plans/` for implementation plans
- `docs/superpowers/specs/` for design specifications
- Git history on `feature/android14-ui-redesign` for commit history
