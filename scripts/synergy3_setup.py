#!/usr/bin/env python3
"""Register an Input Leaf client or inspect its Synergy 3.7 connection (macOS).

Registration requires a stopped service to apply; --status reads the running
service. Dry-run by default. Never reads or copies certificate private keys, and never
prints the settings database (which also contains the Synergy license).
"""
import argparse
import copy
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import time
from urllib.request import urlopen


SETTINGS_URL = "http://127.0.0.1:24803/v1/settings"


def fingerprint(value):
    value = re.sub(r"[:\s]", "", value).lower()
    if not re.fullmatch(r"[0-9a-f]{64}", value):
        raise ValueError("Provide the complete SHA-256 fingerprint from Input Leaf Settings.")
    return value


def core_name(computer):
    name = re.sub(r"[^a-zA-Z0-9]", "", computer["name"]).lower()
    suffix = computer["id"][-8:]
    return f"{name}-{suffix}" if name else suffix


def usable_ipv4(value):
    try:
        address = ipaddress.IPv4Address(value)
    except ipaddress.AddressValueError:
        return False
    return not (
        address.is_loopback or address.is_link_local or address.is_unspecified
        or address.is_multicast or address.is_reserved
    )


def lan_addresses_from_ifconfig(output):
    """Collect active Ethernet/Wi-Fi/bridge IPv4 addresses, excluding VPN/tunnel interfaces."""
    addresses = []
    blocks = re.split(r"(?=^[^\s:]+: flags=)", output, flags=re.MULTILINE)
    for block in blocks:
        header = re.match(r"((?:en|bridge)\d+): flags=\d+<([^>]+)>", block)
        if not header or "UP" not in header[2].split(",") or "status: inactive" in block:
            continue
        for value in re.findall(r"^\s+inet\s+(\S+)", block, flags=re.MULTILINE):
            candidate = (header[1], value)
            if usable_ipv4(value) and candidate not in addresses:
                addresses.append(candidate)
    return addresses


def detect_lan_addresses():
    try:
        result = subprocess.run(
            ["/sbin/ifconfig", "-a"], check=True, capture_output=True, text=True, timeout=3
        )
        return lan_addresses_from_ifconfig(result.stdout)
    except (OSError, subprocess.SubprocessError):
        return []


def show_mac_settings(settings):
    computers = settings.get("computers", [])
    own = next((c for c in computers if c["id"] == settings.get("myId")), None)
    primary = next((c for c in computers if c["id"] == settings.get("mainID", {}).get("id")), None)
    print()
    print(f"This Mac: {own['name']}" if own else "This Mac:")
    addresses = detect_lan_addresses()
    if addresses:
        print("LAN IPv4 address hints for this Mac:")
        for interface, address in addresses:
            print(f"    {address} ({interface})")
        print("Use the address on the same network as the phone; reachability is not tested.")
    else:
        print("LAN IP not detected. Check macOS System Settings → Network → Wi-Fi/Ethernet → Details → TCP/IP.")
    if own and primary and own["id"] != primary["id"]:
        print(f"This Mac is SECONDARY. Connect Input Leaf to the current primary, {primary['name']}, instead.")
        primary_ip = settings.get("local_computers", {}).get(primary["id"])
        if isinstance(primary_ip, str) and usable_ipv4(primary_ip):
            print(f"Current primary's last-known LAN IP: {primary_ip} (verify on that Mac).")
    else:
        print("When this Mac is primary, use its LAN IP in Input Leaf → Add Server (port 24800 is automatic).")
    own_fp = own.get("misc", {}).get("tlsFingerprint") if own else None
    try:
        normalized = fingerprint(own_fp) if isinstance(own_fp, str) else None
    except ValueError:
        normalized = None
    if normalized:
        print("This Mac's server certificate SHA-256:")
        print("    " + ":".join(normalized[i:i+2].upper() for i in range(0, 64, 2)))
        print("Compare this fingerprint only when connecting to THIS Mac.")
    else:
        print("This Mac's server fingerprint is not available in the settings.")
    print("Find it in Synergy Settings → Security (under Advanced) → This computer → Fingerprint.")
    print("Compare all 64 hex digits with Input Leaf's Trust This Server dialog; ignore spaces, colons and case.")


def show_phone_settings(name, screen, settings):
    print()
    print(f"Synergy display name: {name}")
    print("On Android, open Input Leaf → Settings → Screen name and enter exactly:")
    print(f"    {screen}")
    print("The Synergy display name and Input Leaf screen name are different.")
    print("Input Leaf → Settings → Connection security: TLS only (Auto also works).")
    show_mac_settings(settings)


def show_status(settings, name):
    """Report live core status, never the persisted misc.connected placeholder."""
    matches = [
        c for c in settings["computers"]
        if c["name"] == name and not c.get("isRemoved")
    ]
    if len(matches) != 1:
        raise ValueError("Expected one registered phone with that display name; check --name.")
    phone = matches[0]
    status = settings.get("screenStatus", {}).get(phone["id"], {})
    core = status.get("core", "unknown")
    labels = {"connected": "CONNECTED", "disconnected": "DISCONNECTED", "unknown": "UNKNOWN"}
    print(f"Keyboard/mouse connection: {labels.get(core, 'UNKNOWN')}")
    reachable = status.get("serviceReachable")
    if reachable is True:
        service_label = "connected"
    elif reachable is False:
        service_label = "not connected (expected for Input Leaf)"
    else:
        service_label = "unknown"
    print(f"Synergy desktop management link: {service_label}")
    if core == "connected" and reachable is False:
        print("Keyboard/mouse sharing is connected. The missing desktop management link only explains the gray Synergy tile; no action is needed for input sharing.")
    elif core == "unknown" or core not in labels:
        print("Run this command on the primary Mac to see the input connection status.")
    show_phone_settings(phone["name"], core_name(phone), settings)


def read_live_settings():
    with urlopen(SETTINGS_URL, timeout=5) as response:
        return json.load(response)["data"]


def overlaps(a, b):
    return (
        a["left"] < b["left"] + b["width"]
        and b["left"] < a["left"] + a["width"]
        and a["top"] < b["top"] + b["height"]
        and b["top"] < a["top"] + a["height"]
    )


def prepare(database, local, name, client_fingerprint, side="below"):
    """Return updated copies; preserve unrelated computers, trust, and options."""
    if database.get("version", {}).get("schemaVersion") != 17 or local.get("version") != 8:
        raise ValueError("Unsupported Synergy settings schema; this helper targets Synergy 3.7 (17/8).")
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9 _.-]{0,47}", name):
        raise ValueError("Name must be 1–48 ASCII letters, digits, spaces, dots, underscores or hyphens.")
    client_fingerprint = fingerprint(client_fingerprint)
    updated, updated_local = copy.deepcopy(database), copy.deepcopy(local)
    data = updated["data"]
    if data.get("override", {}).get("shouldOverride"):
        raise ValueError("Custom config is enabled; this helper cannot modify custom configurations.")
    if not data.get("security", {}).get("encryption"):
        raise ValueError("Enable TLS encryption in Synergy before using this helper.")
    primary_id = data["mainID"]["id"]
    if primary_id != local["myId"]:
        raise ValueError("Run this helper on the primary Mac (choose it as primary in Synergy first).")
    computers = data["computers"]
    primary = next(c for c in computers if c["id"] == primary_id and not c.get("isRemoved"))
    # Stable across certificate regeneration. Never reuse an existing desktop identity.
    client_id = hashlib.sha256(("input-leaf:synergy3:" + name).encode()).hexdigest()
    existing = next((c for c in computers if c["id"] == client_id), None)
    if any(c["name"] == name and c["id"] != client_id for c in computers):
        raise ValueError("That display name already belongs to another computer.")
    if existing is None or existing.get("isRemoved"):
        bounds = primary["misc"]
        position = {key: bounds[key] for key in ("left", "top", "width", "height")}
        if side == "below":
            position["top"] += bounds["height"] + 1
        elif side == "above":
            position["top"] -= position["height"] + 1
        elif side == "right":
            position["left"] += bounds["width"] + 1
        elif side == "left":
            position["left"] -= position["width"] + 1
        else:
            raise ValueError("Unknown side")
        if any(
            overlaps(position, c["misc"]) for c in computers
            if not c.get("isRemoved") and c["id"] != client_id
        ):
            raise ValueError("That position is occupied. Choose another --side or move screens in Synergy.")
        phone = {
            "id": client_id, "name": name, "hostname": name, "isRemoved": False,
            "dead_corners": {corner: "" for corner in ("top-left", "top-right", "bottom-left", "bottom-right")},
            "scroll_sensitivity": 0.5,
            "fix": {key: False for key in ("num_lock", "xtest", "capslock", "scr_lock", "sync_language", "invert_scroll", "prevent_sleep")},
            "hdk": {key: False for key in ("caps", "scr_lock", "num_lock")},
            "modifier": {key: key for key in ("Alt", "Meta", "Shift", "Super", "Ctrl")},
            # Synergy's layout editor understands Linux, not Android, as an OS label.
            "misc": {**position, "os": "linux", "connected": False, "logo": "", "customImage": None,
                     "screenCount": 1, "screens": {}, "tlsFingerprint": client_fingerprint},
        }
        if existing is not None:
            computers.remove(existing)
        computers.append(phone)
    else:
        phone = existing
        phone["misc"]["tlsFingerprint"] = client_fingerprint
    peers = updated_local["local_security"].setdefault("trusted_peers", [])
    if any(p["fingerprint"] == client_fingerprint and p["computerId"] != client_id for p in peers):
        raise ValueError("This fingerprint already belongs to a different computer; use the phone's own certificate.")
    matching = [p for p in peers if p["computerId"] == client_id]
    if len(matching) != 1 or matching[0]["fingerprint"] != client_fingerprint:
        peers[:] = [p for p in peers if p["computerId"] != client_id]
        peers.append({
            "fingerprint": client_fingerprint,
            "computerId": client_id,
            "name": name,
            "trustedAt": int(time.time() * 1000),
        })
    if data != database["data"]:
        updated["version"]["syncVersion"] = max(int(time.time() * 1000), database["version"]["syncVersion"] + 1)
    return updated, updated_local, core_name(phone)


def require_stopped():
    # Pausing the core is insufficient: a running service can overwrite both files.
    result = subprocess.run(["ps", "-axo", "comm="], check=True, capture_output=True, text=True)
    if any(Path(line.strip()).name in ("synergy-service", "synergy-core") for line in result.stdout.splitlines()):
        raise ValueError("Quit Synergy and stop its background service before --apply; pausing sharing is insufficient.")


def atomic_write(path, contents):
    fd, temporary = tempfile.mkstemp(prefix=".input-leaf-", dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as output:
            output.write(contents)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def apply_changes(directory, originals, updates):
    require_stopped()
    for name, original in originals.items():
        if (directory / name).read_bytes() != original:
            raise ValueError("Settings changed since the preview was read. Run the command again.")
    backup = Path(tempfile.mkdtemp(prefix="input-leaf-backup-", dir=directory))
    for name, original in originals.items():
        atomic_write(backup / name, original)
    try:
        for name, value in updates.items():
            atomic_write(directory / name, (json.dumps(value, indent=2) + "\n").encode())
    except BaseException:
        for name, original in originals.items():
            atomic_write(directory / name, original)
        raise
    return backup


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config-dir", type=Path, default=Path.home() / "Library/Preferences/Synergy")
    parser.add_argument("--name", default="android-phone", help="Display name for the phone in Synergy")
    parser.add_argument("--fingerprint", help="Full SHA-256 fingerprint shown by Input Leaf; required for registration")
    parser.add_argument("--side", choices=("below", "above", "left", "right"), default="below")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--apply", action="store_true", help="Write backed-up settings; requires a stopped Synergy service")
    mode.add_argument("--status", action="store_true", help="Show live input status and the exact screen name from the running local Synergy service")
    args = parser.parse_args()
    if not args.status and not args.fingerprint:
        parser.error("--fingerprint is required for registration (or use --status to inspect an existing phone)")
    try:
        if args.status:
            try:
                settings = read_live_settings()
            except (OSError, ValueError, KeyError) as error:
                raise ValueError("Cannot read the local Synergy service. Start Synergy on the primary Mac and retry.") from error
            show_status(settings, args.name)
            return
        originals = {name: (args.config_dir / name).read_bytes() for name in ("db.json", "local.json")}
        database, local = (json.loads(originals[name]) for name in ("db.json", "local.json"))
        updated, updated_local, screen = prepare(database, local, args.name, args.fingerprint, args.side)
        print(f"Phone SHA-256: {fingerprint(args.fingerprint)}")
        if updated == database and updated_local == local:
            print("Already registered; no changes needed.")
        elif args.apply:
            backup = apply_changes(args.config_dir, originals, {"db.json": updated, "local.json": updated_local})
            print(f"Saved. Backup: {backup}")
            print("Start Synergy, then complete the phone settings below.")
        else:
            print("Preview only. After verifying the phone fingerprint, stop Synergy's service and rerun with --apply.")
        show_phone_settings(args.name, screen, {**database["data"], "myId": local["myId"]})
    except (OSError, ValueError, KeyError, StopIteration, subprocess.SubprocessError) as error:
        action = "read status" if args.status else "register phone"
        parser.exit(1, f"Cannot {action}: {error}\n")


if __name__ == "__main__":
    main()
