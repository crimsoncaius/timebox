"""Exclusive local Android emulator pool. Python standard library only."""
import argparse
import contextlib
import json
import os
from pathlib import Path
import re
import shutil
import socket
import sqlite3
import stat
import subprocess
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parent.parent


def free_space(path):
    while not path.exists():
        path = path.parent
    return shutil.disk_usage(path).free


def check_managed_path(root, path):
    if is_link(root):
        raise RuntimeError(f"Unexpected link at pool root: {root}")
    resolved_root = root.resolve()
    if path.resolve() == resolved_root or not path.resolve().is_relative_to(resolved_root):
        raise RuntimeError(f"Deletion target is outside managed storage: {path}")
    for component in (path, *path.parents):
        if component == root:
            break
        if is_link(component):
            raise RuntimeError(f"Unexpected link in managed storage: {component}")


def disk_usage(root):
    logical = allocated = 0
    if not root.exists():
        return logical, allocated
    if os.name == "nt":
        import ctypes
        kernel = ctypes.WinDLL("kernel32", use_last_error=True)
        kernel.GetCompressedFileSizeW.argtypes = [ctypes.c_wchar_p, ctypes.POINTER(ctypes.c_uint32)]
        kernel.GetCompressedFileSizeW.restype = ctypes.c_uint32
        sectors, sector_bytes, free_clusters, total_clusters = [ctypes.c_uint32() for _ in range(4)]
        kernel.GetDiskFreeSpaceW.argtypes = [ctypes.c_wchar_p, *([ctypes.POINTER(ctypes.c_uint32)] * 4)]
        if not kernel.GetDiskFreeSpaceW(str(root.resolve().anchor), ctypes.byref(sectors), ctypes.byref(sector_bytes),
                                       ctypes.byref(free_clusters), ctypes.byref(total_clusters)):
            raise ctypes.WinError(ctypes.get_last_error())
        cluster_bytes = sectors.value * sector_bytes.value
    for base, dirs, files in os.walk(root, followlinks=False):
        dirs[:] = [name for name in dirs if not is_link(Path(base) / name)]
        for name in files:
            path = Path(base) / name
            if is_link(path):
                continue
            try:
                info = path.stat()
                if os.name == "nt":
                    high = ctypes.c_uint32()
                    ctypes.set_last_error(0)
                    low = kernel.GetCompressedFileSizeW(str(path), ctypes.byref(high))
                    if low == 0xffffffff and ctypes.get_last_error():
                        raise ctypes.WinError(ctypes.get_last_error())
                    size = (high.value << 32) | low
                    size = (size + cluster_bytes - 1) // cluster_bytes * cluster_bytes
                else:
                    size = info.st_blocks * 512
                logical += info.st_size
                allocated += size
            except FileNotFoundError:
                pass  # A release can finish while status is scanning.
    return logical, allocated


def is_link(path):
    return path.is_symlink() or (os.name == "nt" and path.exists() and
                                bool(path.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT))


def emulator_processes():
    """Read process identity independently of ADB, including offline emulators."""
    if os.name == "nt":
        script = """$ErrorActionPreference='Stop'; @(Get-CimInstance Win32_Process -Filter "Name = 'emulator.exe' OR Name LIKE 'qemu-system-%'" | ForEach-Object {
            if (-not $_.CommandLine -or -not $_.CreationDate) { throw 'Cannot identify emulator process' }
            [pscustomobject]@{pid=$_.ProcessId; parent=$_.ParentProcessId; started=$_.CreationDate.ToUniversalTime().ToFileTimeUtc().ToString(); command=$_.CommandLine}
        }) | ConvertTo-Json -Compress"""
        result = subprocess.run(["powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script],
                                check=True, capture_output=True, text=True, timeout=30,
                                creationflags=subprocess.CREATE_NO_WINDOW)
        value = json.loads(result.stdout or "[]")
        processes = value if isinstance(value, list) else [value]
        for item in processes:
            item["started"] = str(int(item["started"]) // 10000)
        return processes
    result = subprocess.run(["ps", "-axo", "pid=,ppid=,lstart=,args="],
                            check=True, capture_output=True, text=True, timeout=10)
    processes = []
    for line in result.stdout.splitlines():
        parts = line.split(None, 7)
        if len(parts) == 8 and re.search(r"(?:^|/)(?:emulator|qemu-system-[^ /]+)(?:\s|$)", parts[7]):
            processes.append({"pid": int(parts[0]), "parent": int(parts[1]),
                              "started": " ".join(parts[2:7]), "command": parts[7]})
    return processes


def launch_identity(process):
    if os.name == "nt":
        import ctypes
        from ctypes import wintypes
        kernel = ctypes.WinDLL("kernel32", use_last_error=True)
        stamps = [wintypes.FILETIME() for _ in range(4)]
        kernel.GetProcessTimes.argtypes = [wintypes.HANDLE, *([ctypes.POINTER(wintypes.FILETIME)] * 4)]
        if not kernel.GetProcessTimes(int(process._handle), *(ctypes.byref(t) for t in stamps)):
            raise ctypes.WinError(ctypes.get_last_error())
        started = str(((stamps[0].dwHighDateTime << 32) | stamps[0].dwLowDateTime) // 10000)
        return {"pid": process.pid, "started": started}
    for current in emulator_processes():
        if current["pid"] == process.pid:
            return {"pid": current["pid"], "started": current["started"]}
    raise RuntimeError("Cannot establish emulator launch identity; inspect startup before recovery.")


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
    def __init__(self, home=None, readonly=False):
        self.home = Path(home) if home is not None else Path.home() / "TimeboxRuntime" / "emulators"
        self.config = json.loads((ROOT / "scripts/android-emulator.json").read_text())
        self.db = None
        if readonly:
            database = self.home / "registry.sqlite"
            if database.exists():
                self.db = sqlite3.connect(database.resolve().as_uri() + "?mode=ro", uri=True, timeout=30)
                self.db.row_factory = sqlite3.Row
                existing = self.db.execute("SELECT config FROM settings WHERE id=1").fetchone()
                if existing:
                    self.config = json.loads(existing[0])
            return
        self.home.mkdir(parents=True, exist_ok=True)
        self.home = self.home.resolve()
        self.db = sqlite3.connect(self.home / "registry.sqlite", timeout=30)
        self.db.row_factory = sqlite3.Row
        self.db.execute("CREATE TABLE IF NOT EXISTS slots (id INTEGER PRIMARY KEY, token TEXT, owner TEXT, worktree TEXT, state TEXT, touched REAL)")
        self.db.commit()
        self.db.execute("CREATE TABLE IF NOT EXISTS settings (id INTEGER PRIMARY KEY, config TEXT)")
        with self.db:
            self.db.execute("BEGIN IMMEDIATE")
            columns = {row[1] for row in self.db.execute("PRAGMA table_info(slots)")}
            for column in ("cleanup_error", "launch"):
                if column not in columns:
                    self.db.execute(f"ALTER TABLE slots ADD COLUMN {column} TEXT")
            existing = self.db.execute("SELECT config FROM settings WHERE id=1").fetchone()
            serialized = json.dumps(self.config, sort_keys=True)
            conflict = existing and existing[0] != serialized and self.db.execute("SELECT 1 FROM slots WHERE state != 'free'").fetchone()
            if not conflict:
                self.db.execute("INSERT OR REPLACE INTO settings VALUES (1, ?)", (serialized,))
        if conflict:
            self.db.close()
            raise RuntimeError("Pool configuration differs from active reservations. Use the configuration that created them.")

    def rows(self):
        return [dict(r) for r in self.db.execute("SELECT * FROM slots ORDER BY id")] if self.db else []

    def summary(self):
        rows = self.rows()
        counts = {state: sum(r["state"] == state for r in rows)
                  for state in ("free", "active", "review", "cleanup_pending")}
        logical, allocated = disk_usage(self.home)
        return {"root": str(self.home.resolve()), "free_bytes": free_space(self.home),
                "file_length_bytes": logical, "allocated_bytes": allocated,
                "reservation_counts": counts,
                "cleanup_errors": [{"token": r["token"], "owner": r["owner"],
                                    "error": r["cleanup_error"]} for r in rows if r.get("cleanup_error")]}

    def claim(self, owner, extra=False):
        with self.db:
            self.db.execute("BEGIN IMMEDIATE")
            if self.db.execute("SELECT config FROM settings WHERE id=1").fetchone()[0] != json.dumps(self.config, sort_keys=True):
                raise RuntimeError("Pool configuration changed; restart the helper.")
            required = self.config["min_free_space_gib"] * 1024**3
            available = free_space(self.home)
            if available < required:
                raise RuntimeError(f"Low disk space: {available / 1024**3:.1f} GiB free; "
                                   f"at least {required / 1024**3:g} GiB required. Inspect status --summary and release completed reviews.")
            capacity = self.config["capacity"]
            highest = self.db.execute("SELECT COALESCE(MAX(id), 0) FROM slots").fetchone()[0]
            if extra:
                # Additional device beyond the baseline; do not change shared pool configuration.
                slots = range(capacity + 1, max(highest, capacity) + 2)
            else:
                slots = range(1, max(highest, capacity) + 2)
            for slot in slots:
                row = self.db.execute("SELECT * FROM slots WHERE id=?", (slot,)).fetchone()
                if row is None or row["state"] == "free":
                    token = uuid.uuid4().hex
                    self.db.execute("INSERT OR REPLACE INTO slots (id, token, owner, worktree, state, touched) VALUES (?, ?, ?, ?, 'active', ?)",
                                    (slot, token, owner, str(ROOT), time.time()))
                    return self.get(token)
        raise RuntimeError("Pool full. Use status to see active tasks and pending reviews; retry when a device is released.")

    def get(self, token):
        row = self.db.execute("SELECT * FROM slots WHERE token=? AND state != 'free'", (token,)).fetchone()
        if row is None:
            raise RuntimeError("Invalid or released reservation token.")
        return dict(row)

    def touch(self, token, state=None):
        row = self.get(token)
        if row["state"] == "cleanup_pending" and state not in (None, "cleanup_pending", "free"):
            raise RuntimeError("Cleanup pending; release or recover this reservation first.")
        with self.db:
            self.db.execute("UPDATE slots SET touched=?, state=COALESCE(?, state) WHERE token=?", (time.time(), state, token))

    def lock(self, row):
        return exclusive(self.home / f"slot-{row['id']}.lock")

    def name(self, row):
        return f"timebox-agent-{row['id']:02d}"

    def serial(self, row):
        return f"emulator-{self.config['first_port'] + (row['id'] - 1) * 2}"

    def update(self, token, **values):
        with self.db:
            self.db.execute("UPDATE slots SET " + ", ".join(f"{key}=?" for key in values) + " WHERE token=?",
                            (*values.values(), token))

    def device_paths(self, row):
        name = self.name(row)
        return [self.home / "avds" / (name + ".avd"),
                self.home / "avds" / (name + ".ini"), self.home / (name + ".log")]

    def delete_device(self, row):
        paths = self.device_paths(row)
        # Validate the complete tree before deleting anything; never follow a junction.
        for path in paths:
            check_managed_path(self.home, path)
            if path.is_dir():
                for base, dirs, files in os.walk(path, followlinks=False):
                    for name in dirs + files:
                        check_managed_path(self.home, Path(base) / name)
        for path in paths:
            deadline = time.monotonic() + 30
            while True:
                try:
                    if path.is_dir():
                        shutil.rmtree(path)
                    else:
                        path.unlink(missing_ok=True)
                    break
                except OSError as error:
                    # The SDK's short-lived shutdown helper can retain the log handle.
                    if getattr(error, "winerror", None) != 32 or time.monotonic() >= deadline:
                        raise
                    time.sleep(1)
                    check_managed_path(self.home, path)

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

    def owned_processes(self, row):
        record = json.loads(row["launch"]) if row.get("launch") else None
        if record and record["phase"] == "starting":
            raise RuntimeError("Uncertain emulator startup; inspect launch and descendants before recovery.")
        processes = emulator_processes()
        name_pattern = rf'(?:^|\s)-avd\s+"?{re.escape(self.name(row))}"?(?:\s|$)'
        port_pattern = rf'(?:^|\s)-port\s+{self.serial(row).split("-")[1]}(?:\s|$)'
        candidates = [p for p in processes if re.search(name_pattern, p["command"]) or re.search(port_pattern, p["command"])]
        known = record["processes"] if record else []
        identities = {(p["pid"], p["started"]) for p in known}
        # Existing identities also catch a process whose command line no longer matches.
        live = [p for p in processes if (p["pid"], p["started"]) in identities]
        for candidate in candidates:
            if not re.search(name_pattern, candidate["command"]) or not re.search(port_pattern, candidate["command"]):
                raise RuntimeError("Emulator name/port belongs to another launch; leaving it untouched.")
        remaining = [p for p in candidates if (p["pid"], p["started"]) not in identities]
        while remaining:
            children = [p for p in remaining if any(parent["pid"] == p["parent"] for parent in live)]
            if not children:
                raise RuntimeError("Unrecorded emulator process; ownership is uncertain, leaving it untouched.")
            for child in children:
                identities.add((child["pid"], child["started"]))
                known.append({"pid": child["pid"], "started": child["started"]})
                live.append(child)
                remaining.remove(child)
        if record:
            self.update(row["token"], launch=json.dumps(record))
        return live

    def stop(self, row):
        live = self.owned_processes(row)
        online = self.identity(row)
        if online and not row.get("launch"):
            raise RuntimeError("Emulator has no recorded launch; leaving it untouched.")
        if online:
            self.adb(row, ["emu", "kill"], check=True, capture_output=True, timeout=15)
        elif live:
            raise RuntimeError("Recorded emulator is still running but ADB is offline; retry cleanup after it exits.")
        for _ in range(30):
            current = self.get(row["token"])
            if not self.owned_processes(current) and self.ports_free(row):
                return
            time.sleep(1)
        raise RuntimeError("Emulator process or ports remain in use; cleanup pending.")

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
        for path in self.device_paths(row):
            check_managed_path(self.home, path)
            if path.exists():
                raise RuntimeError(f"Unexpected device files in a free slot: {path}; inspect before reuse.")
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
            self.update(row["token"], launch=json.dumps({"phase": "starting", "processes": []}))
            try:
                process = subprocess.Popen([str(exe), "-avd", self.name(row), "-port", str(port),
                                            "-no-snapshot", "-wipe-data", "-no-boot-anim"],
                                           env=env, stdout=log, stderr=log,
                                           creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
            except OSError:
                self.update(row["token"], launch=None)
                raise
            self.update(row["token"], launch=json.dumps({"phase": "running", "processes": [launch_identity(process)]}))
        deadline = time.time() + 240
        while time.time() < deadline:
            self.owned_processes(self.get(row["token"]))
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
        try:
            with self.lock(row):
                self.boot(self.get(row["token"]))
        except BaseException as error:
            self.release_after_failure(row["token"], error)
            raise
        return self.get(row["token"])

    def release_after_failure(self, token, error):
        try:
            self.release(token)
        except BaseException as cleanup:
            raise RuntimeError(f"{error}; cleanup also failed for {token}: {cleanup}") from error

    def release(self, token, review_done=False, recover=False):
        row = self.get(token)
        with self.lock(row):
            row = self.get(token)
            if row["state"] == "review" and not review_done:
                raise RuntimeError("User review is pending. Release only after the user finishes or replaces it.")
            if recover and row["state"] != "cleanup_pending" and (row["state"] == "review" or time.time() - row["touched"] < self.config["lease_seconds"]):
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
            self.touch(token, "cleanup_pending")
            try:
                self.stop(self.get(token))
                self.delete_device(row)
            except BaseException as error:
                self.update(token, cleanup_error=f"{type(error).__name__}: {error}")
                raise
            self.update(token, cleanup_error=None, launch=None)
            self.touch(token, "free")

    def operation(self, token, kind, args):
        row = self.get(token)
        with self.lock(row):
            row = self.get(token)
            if row["state"] == "cleanup_pending":
                raise RuntimeError("Cleanup pending; release or recover this reservation first.")
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
    subs.add_parser("status").add_argument("--summary", action="store_true")
    for action in ("acquire", "test"):
        p = subs.add_parser(action)
        p.add_argument("--owner", required=True, help="Task id or descriptive unique session name")
        if action == "acquire":
            p.add_argument("--extra", action="store_true", help="Reserve an additional managed slot beyond the configured baseline")
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
    pool = Pool(readonly=args.action == "status")
    if args.action == "status":
        if args.summary:
            print(json.dumps(pool.summary(), indent=2))
            return 0
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
                result = pool.operation(row["token"], "gradle", args.args or ["connectedDebugAndroidTest"])
            except BaseException as error:
                pool.release_after_failure(row["token"], error)
                raise
            pool.release_after_failure(row["token"], RuntimeError(f"Tests exited with code {result}"))
            return result
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
