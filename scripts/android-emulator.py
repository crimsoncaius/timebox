"""Exclusive local Android emulator pool. Python standard library only."""
import argparse
import contextlib
import json
import os
from pathlib import Path
import socket
import sqlite3
import subprocess
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parent.parent


@contextlib.contextmanager
def exclusive(path):
    """Kernel lock is released even when the helper is killed."""
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a+b") as handle:
        handle.seek(0)
        if os.name == "nt":
            import msvcrt
            if path.stat().st_size == 0:
                handle.write(b"0")
                handle.flush()
            handle.seek(0)
            try:
                msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
            except OSError:
                raise RuntimeError("Device operation already running; retry later.") from None
        else:
            import fcntl
            try:
                fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except OSError:
                raise RuntimeError("Device operation already running; retry later.") from None
        try:
            yield
        finally:
            if os.name == "nt":
                handle.seek(0)
                msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)


class Pool:
    def __init__(self, home=None):
        self.home = Path(home or Path(os.environ.get("LOCALAPPDATA", Path.home())) / "Timebox" / "emulators")
        self.home.mkdir(parents=True, exist_ok=True)
        self.config = json.loads((ROOT / "scripts/android-emulator.json").read_text())
        self.db = sqlite3.connect(self.home / "registry.sqlite", timeout=30)
        self.db.row_factory = sqlite3.Row
        self.db.execute("CREATE TABLE IF NOT EXISTS slots (id INTEGER PRIMARY KEY, token TEXT, owner TEXT, worktree TEXT, state TEXT, touched REAL)")
        self.db.commit()
        self.db.execute("CREATE TABLE IF NOT EXISTS settings (id INTEGER PRIMARY KEY, config TEXT)")
        with self.db:
            self.db.execute("BEGIN IMMEDIATE")
            existing = self.db.execute("SELECT config FROM settings WHERE id=1").fetchone()
            serialized = json.dumps(self.config, sort_keys=True)
            conflict = existing and existing[0] != serialized and self.db.execute("SELECT 1 FROM slots WHERE state != 'free'").fetchone()
            if not conflict:
                self.db.execute("INSERT OR REPLACE INTO settings VALUES (1, ?)", (serialized,))
        if conflict:
            self.db.close()
            raise RuntimeError("Pool configuration differs from active reservations. Use the configuration that created them.")

    def rows(self):
        return [dict(r) for r in self.db.execute("SELECT * FROM slots ORDER BY id")]

    def claim(self, owner, extra=False):
        with self.db:
            self.db.execute("BEGIN IMMEDIATE")
            if self.db.execute("SELECT config FROM settings WHERE id=1").fetchone()[0] != json.dumps(self.config, sort_keys=True):
                raise RuntimeError("Pool configuration changed; restart the helper.")
            slots = range(1, self.config["capacity"] + 1)
            if extra:
                # Explicit user-requested device; do not change shared pool configuration.
                highest = self.db.execute("SELECT COALESCE(MAX(id), 0) FROM slots").fetchone()[0]
                slots = range(self.config["capacity"] + 1, max(highest, self.config["capacity"]) + 2)
            for slot in slots:
                row = self.db.execute("SELECT * FROM slots WHERE id=?", (slot,)).fetchone()
                if row is None or row["state"] == "free":
                    token = uuid.uuid4().hex
                    self.db.execute("INSERT OR REPLACE INTO slots VALUES (?, ?, ?, ?, 'active', ?)",
                                    (slot, token, owner, str(ROOT), time.time()))
                    return self.get(token)
        raise RuntimeError("Pool full. Use status to see active tasks and pending reviews; retry when a device is released.")

    def get(self, token):
        row = self.db.execute("SELECT * FROM slots WHERE token=? AND state != 'free'", (token,)).fetchone()
        if row is None:
            raise RuntimeError("Invalid or released reservation token.")
        return dict(row)

    def touch(self, token, state=None):
        self.get(token)
        with self.db:
            self.db.execute("UPDATE slots SET touched=?, state=COALESCE(?, state) WHERE token=?", (time.time(), state, token))

    def lock(self, row):
        return exclusive(self.home / f"slot-{row['id']}.lock")

    def name(self, row):
        return f"timebox-agent-{row['id']:02d}"

    def serial(self, row):
        return f"emulator-{self.config['first_port'] + (row['id'] - 1) * 2}"

    def sdk(self):
        value = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
        if not value:
            props = ROOT / "android/local.properties"
            if props.exists():
                value = next((line.split("=", 1)[1] for line in props.read_text().splitlines() if line.startswith("sdk.dir=")), None)
        if not value:
            raise RuntimeError("Set ANDROID_SDK_ROOT or android/local.properties sdk.dir.")
        return Path(value.replace("\\:", ":").replace("\\\\", "\\"))

    def adb(self, row, args, **kwargs):
        exe = self.sdk() / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")
        return subprocess.run([str(exe), "-s", self.serial(row), *args], **kwargs)

    def identity(self, row):
        result = self.adb(row, ["emu", "avd", "name"], capture_output=True, text=True, timeout=10)
        if result.returncode or not result.stdout.strip():
            return False
        if result.stdout.splitlines()[0] != self.name(row):
            raise RuntimeError("Reserved port belongs to an unmanaged emulator; leaving it untouched.")
        return True

    def stop(self, row):
        if self.identity(row):
            self.adb(row, ["emu", "kill"], check=True, capture_output=True, timeout=15)
            for _ in range(30):
                if not self.identity(row) and self.ports_free(row):
                    return
                time.sleep(1)
            raise RuntimeError("Emulator did not stop; reservation retained.")
        if not self.ports_free(row):
            raise RuntimeError("Device is offline or its ports are occupied; reservation retained until its process stops.")

    def ports_free(self, row):
        port = int(self.serial(row).split("-")[1])
        try:
            for candidate in (port, port + 1):
                with socket.socket() as sock:
                    if os.name == "nt":
                        sock.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
                    sock.bind(("127.0.0.1", candidate))
            return True
        except OSError:
            return False

    def boot(self, row):
        self.stop(row)
        port = int(self.serial(row).split("-")[1])
        if not self.ports_free(row):
            raise RuntimeError("Emulator ports are occupied.")
        image = self.sdk() / self.config["image"]
        if not (image / "system.img").exists():
            raise RuntimeError(f"Install the configured Android system image: {image}")
        avds = self.home / "avds"
        avd = avds / (self.name(row) + ".avd")
        avd.mkdir(parents=True, exist_ok=True)
        (avds / (self.name(row) + ".ini")).write_text(f"avd.ini.encoding=UTF-8\npath={avd}\ntarget=android-36\n")
        settings = {
            "AvdId": self.name(row), "avd.ini.displayname": self.name(row),
            "image.sysdir.1": str(image) + os.sep, "abi.type": "x86_64", "hw.cpu.arch": "x86_64",
            "hw.cpu.ncore": 2, "hw.ramSize": self.config["ram_mb"], "hw.gpu.enabled": "yes",
            "hw.gpu.mode": "auto", "hw.lcd.width": self.config["width"],
            "hw.lcd.height": self.config["height"], "hw.lcd.density": self.config["density"],
            "hw.keyboard": "yes", "hw.mainKeys": "no", "disk.dataPartition.size": "4G",
            "showDeviceFrame": "no", "PlayStore.enabled": "true", "tag.id": "google_apis_playstore",
        }
        (avd / "config.ini").write_text("".join(f"{key}={value}\n" for key, value in settings.items()))
        exe = self.sdk() / "emulator" / ("emulator.exe" if os.name == "nt" else "emulator")
        env = dict(os.environ, ANDROID_AVD_HOME=str(avds))
        with (self.home / f"{self.name(row)}.log").open("w") as log:
            process = subprocess.Popen([str(exe), "-avd", self.name(row), "-port", str(port),
                                        "-no-snapshot", "-wipe-data", "-no-boot-anim"],
                                       env=env, stdout=log, stderr=log,
                                       creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
        deadline = time.time() + 240
        while time.time() < deadline:
            result = self.adb(row, ["shell", "getprop", "sys.boot_completed"], capture_output=True, text=True, timeout=10)
            if result.returncode == 0 and result.stdout.strip() == "1" and self.identity(row):
                self.adb(row, ["shell", "settings", "put", "system", "font_scale", "1.0"], check=True)
                self.touch(row["token"])
                return
            if process.poll() is not None:
                raise RuntimeError(f"Emulator exited; inspect {self.home / (self.name(row) + '.log')}")
            time.sleep(2)
        raise RuntimeError("Emulator boot timed out; reservation retained for recovery.")

    def acquire(self, owner, extra=False):
        row = self.claim(owner, extra=extra)
        with self.lock(row):
            self.boot(row)
        return row

    def release(self, token, review_done=False, recover=False):
        row = self.get(token)
        with self.lock(row):
            row = self.get(token)
            if row["state"] == "review" and not review_done:
                raise RuntimeError("User review is pending. Release only after the user finishes or replaces it.")
            if recover and (row["state"] == "review" or time.time() - row["touched"] < self.config["lease_seconds"]):
                raise RuntimeError("Only expired non-review reservations can be recovered.")
            marker = self.home / f"operation-{row['id']}.json"
            if marker.exists():
                record = json.loads(marker.read_text())
                pid = record["pid"]
                if record.get("kind") == "gradle":
                    raise RuntimeError("Interrupted Gradle operation: verify its command and descendants have ended before removing the operation marker.")
                if pid is None:
                    raise RuntimeError("Interrupted command startup. Verify no host command remains before removing the operation marker.")
                if os.name == "nt":
                    import ctypes
                    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
                    kernel.OpenProcess.restype = ctypes.c_void_p
                    handle = kernel.OpenProcess(0x100000, False, pid)
                    if handle:
                        kernel.WaitForSingleObject.argtypes = [ctypes.c_void_p, ctypes.c_ulong]
                        kernel.CloseHandle.argtypes = [ctypes.c_void_p]
                        running = kernel.WaitForSingleObject(handle, 0) == 258
                        kernel.CloseHandle(handle)
                    else:
                        running = ctypes.get_last_error() != 87
                else:
                    try:
                        os.kill(pid, 0)
                        running = True
                    except ProcessLookupError:
                        running = False
                if running:
                    raise RuntimeError(f"Host command {pid} still exists; reservation retained.")
                marker.unlink()
            # Stop first: this terminates orphaned on-device instrumentation before reuse.
            self.stop(row)
            self.touch(token, "free")

    def operation(self, token, kind, args):
        row = self.get(token)
        with self.lock(row):
            row = self.get(token)
            if row["state"] == "review":
                raise RuntimeError("Resume the review reservation before changing its device.")
            if not self.identity(row):
                raise RuntimeError("Reserved emulator is offline; release and acquire a fresh instance.")
            self.touch(token)
            env = dict(os.environ, ANDROID_SERIAL=self.serial(row))
            if kind == "adb":
                if not args or args[0] not in {"shell", "install", "install-multiple", "uninstall", "push", "pull", "logcat", "exec-out", "get-state"}:
                    raise RuntimeError("Use a device-scoped adb operation; global server and emulator commands are excluded.")
                command = [str(self.sdk() / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")), "-s", self.serial(row), *args]
            else:
                command = ["powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(ROOT / "scripts/android-gradle.ps1"), *args]
            marker = self.home / f"operation-{row['id']}.json"
            if marker.exists():
                raise RuntimeError("Previous command did not finish cleanly; recover this reservation before reuse.")
            marker.write_text(json.dumps({"pid": None, "kind": kind}))
            try:
                process = subprocess.Popen(command, env=env)
            except BaseException:
                marker.unlink()
                raise
            marker.write_text(json.dumps({"pid": process.pid, "kind": kind}))
            try:
                while True:
                    try:
                        return process.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        self.touch(token)
            finally:
                # If interrupted, preserve ownership until the child has exited.
                if process.poll() is None:
                    process.wait()
                marker.unlink()
                self.touch(token)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    subs = parser.add_subparsers(dest="action", required=True)
    subs.add_parser("status")
    for action in ("acquire", "test"):
        p = subs.add_parser(action)
        p.add_argument("--owner", required=True, help="Task id or descriptive unique session name")
        if action == "acquire":
            p.add_argument("--extra", action="store_true", help="Reserve an additional device only when explicitly requested by the user")
        if action == "test":
            p.add_argument("args", nargs=argparse.REMAINDER)
    for action in ("adb", "gradle", "renew", "review", "resume", "release", "recover"):
        p = subs.add_parser(action)
        p.add_argument("token")
        if action in ("adb", "gradle"):
            p.add_argument("args", nargs=argparse.REMAINDER)
        if action == "release":
            p.add_argument("--review-done", action="store_true")
    args = parser.parse_args()
    pool = Pool()
    if args.action == "status":
        rows = pool.rows()
        for row in rows:
            row["serial"] = pool.serial(row)
            row["expired"] = row["state"] == "active" and time.time() - row["touched"] >= pool.config["lease_seconds"]
        print(json.dumps(rows, indent=2))
    elif args.action in ("acquire", "test"):
        row = pool.acquire(args.owner, extra=getattr(args, "extra", False))
        print(json.dumps(dict(row, serial=pool.serial(row))), flush=True)
        if args.action == "test":
            try:
                return pool.operation(row["token"], "gradle", args.args or ["connectedDebugAndroidTest"])
            finally:
                pool.release(row["token"])
    elif args.action in ("release", "recover"):
        pool.release(args.token, getattr(args, "review_done", False), args.action == "recover")
    elif args.action in ("adb", "gradle"):
        return pool.operation(args.token, args.action, args.args)
    else:
        row = pool.get(args.token)
        with pool.lock(row):
            pool.get(args.token)
            pool.touch(args.token, {"review": "review", "resume": "active"}.get(args.action))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (RuntimeError, OSError, subprocess.SubprocessError) as error:
        print(f"Emulator pool: {error}", file=sys.stderr)
        sys.exit(1)
