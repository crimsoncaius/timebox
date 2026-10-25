"""Check both entry points on a managed emulator with a PIN already configured.

Install the APK, grant RECORD_AUDIO, select Timebox as assistant and add its tile
before running: python scripts/check-locked-quick-tracking.py TOKEN [assistant|tile]
No commands are submitted and no backend data is changed.
"""
import pathlib
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

helper = pathlib.Path(__file__).with_name("android-emulator.py")
token = sys.argv[1]


def adb(*args):
    return subprocess.check_output(
        [sys.executable, str(helper), "adb", token, "shell", *args], text=True
    )


def screen():
    adb("uiautomator", "dump", "/sdcard/locked-quick-tracking.xml")
    return adb("cat", "/sdcard/locked-quick-tracking.xml")


for entry in sys.argv[2:] or ("assistant", "tile"):
    assert entry in ("assistant", "tile"), "Entry must be assistant or tile"
    adb("am", "force-stop", "com.timebox.android")
    adb("input", "keyevent", "KEYCODE_SLEEP")
    time.sleep(1)
    adb("input", "keyevent", "KEYCODE_WAKEUP")
    time.sleep(1)
    policy = adb("dumpsys", "window", "policy")
    assert "showing=true" in policy and "secure=true" in policy, "Set a secure PIN lock first"
    if entry == "assistant":
        adb("input", "keyevent", "--duration", "1000", "KEYCODE_POWER")
    else:
        adb("cmd", "statusbar", "expand-settings")
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            tile = next((node for node in ET.fromstring(screen()).iter("node")
                         if node.get("content-desc") == "Track activity"
                         and node.get("clickable") == "true" and node.get("enabled") == "true"), None)
            if tile is not None:
                x1, y1, x2, y2 = map(int, re.findall(r"\d+", tile.get("bounds")))
                adb("input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                break
        else:
            raise AssertionError("Track activity tile is not visible on the first Quick Settings page")
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        windows = adb("dumpsys", "window")
        if re.search(r"mCurrentFocus=.*QuickTrackingActivity", windows):
            break
        time.sleep(0.5)
    else:
        raise AssertionError(f"{entry}: capture did not receive focus over the lock screen")
    policy = adb("dumpsys", "window", "policy")
    assert "showing=true" in policy and "secure=true" in policy, f"{entry}: device was unlocked"
    ui = screen()
    assert "Track activity" in ui, f"{entry}: capture content is hidden"
    assert "Unlock your device" not in ui, f"{entry}: microphone requires unlock"
    print(f"PASS {entry}: capture visible and device still securely locked", flush=True)
