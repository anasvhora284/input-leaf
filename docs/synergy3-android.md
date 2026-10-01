# Synergy 3 on macOS → Input Leaf on Android (experimental)

Compatibility has been tested with **Synergy 3.7.2 / core 1.21.4** on macOS and a
**Pixel 10** using Shizuku. Input Leaf connects directly to Synergy's input core
on TCP **24800**. Synergy's desktop management service is separate; Input Leaf
does not participate in its discovery or settings synchronization.

Keep TLS encryption and **Only allow verified computers** enabled. The Android
app generates its own client certificate. No Mac private key or Synergy license
needs to be copied to the phone.

## Prepare the phone

1. Install an Input Leaf build containing this compatibility support. For local
   builds, see [Testing](TESTING.md). Updating an existing installation requires
   the same signing certificate; uninstalling removes app settings and creates
   a new client certificate.
2. Start Shizuku, authorize Input Leaf, and select Shizuku as the input method.
3. Open **Input Leaf → Settings → This device's fingerprint** and copy the complete
   SHA-256 fingerprint. The shortened Settings summary is insufficient.
4. Connect the phone and Mac to the same LAN and select the Mac as Synergy's primary.

## Register the phone on the Mac

Connecting to port 24800 alone does not create a screen or trigger Synergy 3.7's
normal trust prompt: those prompts depend on management-service peers. The
optional Python helper adds a screen and its public certificate fingerprint to
the Mac's existing settings.

The helper supports the observed **global schema 17 / local schema 8**. It refuses
other schemas, custom configuration overrides, and occupied screen positions.
It preserves existing computers, layout, primary selection, certificate trust,
encryption settings, and license data.

From the repository root, preview the registration:

```sh
python3 scripts/synergy3_setup.py \
  --name android-phone \
  --fingerprint 'PASTE_THE_FULL_PHONE_SHA256_HERE' \
  --side below
```

Python 3 is required; there are no third-party dependencies. Position choices are
`above`, `below`, `left`, and `right`. The preview prints:

- The exact **Input Leaf screen name**, such as `androidphone-c32a6d0e`. This is
  different from the display name `android-phone` in Synergy's layout editor.
- The local Mac's LAN IPv4 address hints and server certificate fingerprint.
- The corresponding Input Leaf settings to use.

Nothing is written without `--apply`. After comparing the supplied fingerprint
with the phone:

1. Quit Synergy's GUI and stop its background service. This interrupts sharing
   between computers. Pausing the core alone is insufficient because the service
   can overwrite settings. If the service is still running, Synergy 3.7.2 exposes
   this local stop endpoint:

   ```sh
   curl --fail --request POST http://127.0.0.1:24803/v1/controls/stopService
   ```

2. Repeat the preview command with `--apply`. The helper refuses to write while
   `synergy-service` or `synergy-core` is running. It saves exact copies of
   `db.json` and `local.json` in a private backup directory, then writes the
   registration. A failed write restores both original files.
3. Reopen Synergy. Its service generates the core layout and trusted-client file
   from the updated settings. The phone can subsequently be moved in the GUI.

Keep backups private: they contain the existing Synergy license and settings.
To undo registration, stop the service, restore **both** JSON files from the
printed backup directory, then restart Synergy. A full restore also reverts
settings changed since the backup.

Rerunning with the same name and fingerprint makes no changes. After regenerating
the phone's certificate, rerun with its new fingerprint and the same name to keep
its screen identity and position.

## Connect from Input Leaf

1. Set **Settings → Screen name** to the exact name printed by the helper.
2. Under **Settings → Connection security**, select **TLS only**. Auto also
   supports TLS; this setting is outside the Add Server dialog.
3. Add the primary Mac's LAN IP. Enter only the address: the app automatically
   uses port **24800** and does not expose a port selector. If several address
   hints are printed, choose the interface on the same network as the phone.
4. In **Trust This Server?**, verify the server fingerprint before accepting it.
5. Move the desktop pointer across the configured edge to enter Android.

## Verify the server fingerprint

On the Mac being connected to, open **Synergy Settings → Security** under the
**Advanced** sidebar section. Under **This computer**, find **Fingerprint**.
Compare all **64 hexadecimal digits** with Input Leaf's dialog. Spaces, colons,
and capitalization do not affect the value.

The fingerprint under **This computer** belongs to that Mac. Entries under
**Other computers** belong to trusted peers, including the phone. Each Mac has
its own server certificate. The helper also prints the local Mac's full SHA-256
fingerprint and identifies when another Mac is primary.

## Use more than one Mac

Synergy synchronizes the phone's screen entry and layout, but certificate trust
is local to each Mac. For another Mac that will act as primary:

1. Let Synergy synchronize the phone entry and select that Mac as primary.
2. Run the helper on it with the same phone name and fingerprint. Stop its
   service before applying, then reopen Synergy. The existing phone entry is
   reused, and its certificate is added to that Mac's local trust store.
3. On Android, select that Mac's LAN IP and verify its own server fingerprint.
   Keep the phone's Input Leaf screen name unchanged.

Input Leaf has one active connection and does not follow primary changes
without selecting the new server. Changing primary also does not rearrange the
layout: if the phone is below Mac A, move through A's screen and then down, or
place the phone beside Mac B for a direct edge from B.

Do not copy one Mac's `local.json` or private certificate onto another Mac.

## Inspect connection status

With Synergy running on the primary Mac:

```sh
python3 scripts/synergy3_setup.py --status --name android-phone
```

Use the display name currently shown in Synergy. This reads the local service API
and network interfaces, then reports the phone's runtime status, exact screen
name, and local Mac's address/fingerprint. It does not require the phone
fingerprint again or modify settings. Renaming the phone in Synergy changes its
core screen name; update Input Leaf accordingly.

A gray Synergy tile can coexist with a working input connection. Synergy 3.7.2
tracks both `core` (keyboard/mouse connection) and `serviceReachable` (desktop
management connection). Its GUI requires management reachability for a blue tile,
while Input Leaf only connects to the input core. Use the helper's **Keyboard/mouse
connection** line, or hover the GUI's Ethernet status badge. The absent management
link is expected and does not indicate an input-sharing failure.

## Compatibility and validation

The implementation:

- Reads signed 16-bit `DMRM` mouse deltas while retaining the previously accepted
  32-bit variant.
- Converts macOS Carbon keycodes plus one to evdev for Android HID injection,
  while retaining existing evdev/X11 decoding. Held keys keep matching key-up
  mappings if source detection changes during a chord. Configure Android's
  hardware keyboard layout to match the Mac for physical-key input.
- Waits for `DSOP` to complete the handshake. Synergy can acknowledge dimensions
  with `CIAK` and then reject the screen with `EUNK`.
- Reports unknown screens and protocol errors explicitly and rejects unsupported
  protocol major versions.
- Uses the existing client certificate and server fingerprint verification flow.

Manual validation covered mutual TLS, keyboard/mouse input, and screen-edge
switching with the macOS/Pixel setup above. The server advertised `Synergy 1.8`
and accepted the client's negotiated `1.6` protocol. Regression tests use local
fixtures and loopback sockets alongside the existing Input Leap/Deskflow tests.
Other Synergy versions, Windows/Linux server setup, clipboard/file transfer, and
management-service discovery are outside the verified scope.

## Development

```sh
./gradlew :koverXmlReportDebugJvm
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts/tests -v
```

`Synergy3LiveTest` is optional and skipped in normal CI. To run it against an
already configured TLS server, set `INPUT_LEAF_SYNERGY_HOST`,
`INPUT_LEAF_SYNERGY_SCREEN`, `INPUT_LEAF_SYNERGY_FINGERPRINT` (server SHA-256),
`INPUT_LEAF_SYNERGY_PKCS12`, and `INPUT_LEAF_SYNERGY_PASSWORD`; optionally set
`INPUT_LEAF_SYNERGY_PORT`. Use a dedicated test screen and a client certificate
already trusted by the server, then run:

```sh
./gradlew :app:testDebugUnitTest --tests '*Synergy3LiveTest' --rerun-tasks
```

The test changes no server settings and refuses an unexpected server certificate.
Never commit private keys or real settings snapshots.

Interoperability references:

- [Maintainer discussion, issue #47](https://github.com/anasvhora284/input-leaf/issues/47).
- [Deskflow packet layouts](https://github.com/deskflow/deskflow/blob/081f6478e654a112168879e67a41ce7d7e4ae3af/src/lib/deskflow/ProtocolTypes.cpp).
- [Deskflow handshake state machine](https://github.com/deskflow/deskflow/blob/081f6478e654a112168879e67a41ce7d7e4ae3af/src/lib/client/ServerProxy.cpp).
- [Deskflow macOS keycode encoding](https://github.com/deskflow/deskflow/blob/081f6478e654a112168879e67a41ce7d7e4ae3af/src/lib/platform/OSXKeyState.cpp)
  and Apple's HIToolbox `Events.h` for Carbon virtual keycode values.

The helper independently implements the settings format observed in Synergy
3.7.2. No Synergy application code is included in this repository.
