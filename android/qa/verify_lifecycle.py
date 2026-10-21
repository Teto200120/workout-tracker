"""Host-driven acceptance on a fresh disposable emulator; never runs inside the app process."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

APP = "com.hector.workouttracker"
TEST_APP = APP + ".test"
RUNNER = TEST_APP + "/androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS = APP + ".LifecycleAcceptanceTest"


class Acceptance:
    def __init__(self, serial, output):
        self.serial = serial
        self.output = output
        self.output.mkdir(parents=True, exist_ok=True)
        self.events = []
        self.phases = []
        self.last_ui = None

    def adb(self, *args, check=True, binary=False, timeout=60):
        result = subprocess.run(["adb", "-s", self.serial, *map(str, args)], capture_output=True, timeout=timeout)
        if check and result.returncode:
            raise RuntimeError(result.stderr.decode(errors="replace") + result.stdout.decode(errors="replace"))
        return result.stdout if binary else result.stdout.decode(errors="replace").strip()

    def ui(self):
        self.adb("shell", "uiautomator", "dump", "/sdcard/workout-lifecycle.xml", timeout=25)
        text = self.adb("exec-out", "cat", "/sdcard/workout-lifecycle.xml")
        self.last_ui = ET.fromstring(text[text.index("<?xml"):])
        return self.last_ui

    @staticmethod
    def matches(root, text, exact=True):
        return [n for n in root.iter("node") if any(
            (n.get(key, "") == text if exact else text in n.get(key, "")) for key in ("text", "content-desc"))]

    def wait_text(self, text, exact=True):
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            root = self.ui()
            if self.matches(root, text, exact):
                return root
            time.sleep(0.3)
        raise AssertionError(f"Visible UI never contained {text!r}")

    @staticmethod
    def bounds(node):
        values = list(map(int, re.findall(r"-?\d+", node.get("bounds", ""))))
        if len(values) != 4 or values[2] <= values[0] or values[3] <= values[1]:
            raise AssertionError("UI node has no tappable bounds")
        return values

    def click(self, text, scroll=False):
        for _ in range(12 if scroll else 1):
            root = self.ui()
            nodes = self.matches(root, text)
            if nodes:
                left, top, right, bottom = self.bounds(nodes[0])
                self.adb("shell", "input", "tap", (left + right) // 2, (top + bottom) // 2)
                return
            if not scroll:
                break
            containers = [n for n in root.iter("node") if n.get("scrollable") == "true"]
            if not containers:
                break
            left, top, right, bottom = max((self.bounds(n) for n in containers), key=lambda b: (b[2]-b[0])*(b[3]-b[1]))
            x = (left + right) // 2
            self.adb("shell", "input", "swipe", x, bottom - 80, x, top + 80, 250)
        raise AssertionError(f"No visible control for {text!r}")

    def capture(self, label):
        self.ui()
        ET.ElementTree(self.last_ui).write(self.output / f"{label}.xml", encoding="utf-8")
        (self.output / f"{label}.png").write_bytes(self.adb("exec-out", "screencap", "-p", binary=True))

    def phase(self, phase, version, label):
        text = self.adb("shell", "am", "instrument", "-w", "-r", "-e", "class", TEST_CLASS,
                        "-e", "lifecyclePhase", phase, "-e", "expectedVersion", version, RUNNER, timeout=90)
        (self.output / f"{label}-instrumentation.txt").write_text(text)
        if "OK (1 test)" not in text or "FAILURES!!!" in text or "INSTRUMENTATION_FAILED" in text:
            raise AssertionError(f"Instrumentation phase {label} failed: {text[-4000:]}")
        self.phases.append(label)
        print(f"PASS {label}", flush=True)

    def launch(self, draft=True):
        # NEW_TASK | CLEAR_TASK drops activity-state shortcuts without clearing persisted data.
        self.adb("shell", "am", "start", "-W", "-f", "0x10008000", "-n", APP + "/.MainActivity")
        self.wait_text("Upgrade Fixture", exact=False)
        self.wait_text("Resume workout" if draft else "Start workout")

    def kill_and_restore(self, label, draft=True):
        self.launch(draft)
        before = self.adb("shell", "pidof", APP)
        assert before, "The app must actually be running before termination"
        self.adb("shell", "am", "force-stop", APP)
        assert not self.adb("shell", "pidof", APP, check=False), "App process survived force-stop"
        self.launch(draft)
        after = self.adb("shell", "pidof", APP)
        assert after and after != before, "Cold launch did not create a different process"
        self.events.append({"label": label, "before_pid": before, "after_pid": after})
        self.capture(label)
        if draft:
            self.click("Resume workout")
            self.wait_text("Recovery fixture")
            self.capture(label + "-session")

    def checkpoint_copy(self, label, expected=None):
        destination = self.output / f"checkpoint-{label}.json"
        self.adb("pull", "/sdcard/Android/data/" + APP + "/files/qa-lifecycle/checkpoint.json", destination)
        contents = destination.read_bytes()
        if expected is not None:
            assert contents == expected, "The independent host checkpoint changed across recovery/upgrade"
        return contents

    def installed_version(self):
        text = self.adb("shell", "dumpsys", "package", APP)
        return int(re.search(r"\bversionCode=(\d+)", text).group(1))


def certificate(apk):
    sdk = Path(os.environ["ANDROID_HOME"])
    text = subprocess.check_output([str(sdk / "build-tools/36.0.0/apksigner"), "verify", "--print-certs", str(apk)], text=True)
    return re.search(r"certificate SHA-256 digest: ([a-f0-9]+)", text).group(1)


def version_code(apk):
    sdk = Path(os.environ["ANDROID_HOME"])
    text = subprocess.check_output([str(sdk / "build-tools/36.0.0/aapt2"), "dump", "badging", str(apk)], text=True)
    match = re.search(r"^package: name='com\.hector\.workouttracker' versionCode='(\d+)'", text)
    assert match, "APK package must be the workout tracker"
    return int(match.group(1))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--baseline-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=Path("app/build/outputs/lifecycle"))
    args = parser.parse_args()
    qa = Acceptance(args.serial, args.output)
    current = Path.cwd()
    app_path = Path("app/build/outputs/apk/debug/app-debug.apk")
    test_path = Path("app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")
    old_app, new_app = args.baseline_dir / app_path, current / app_path
    old_test, new_test = args.baseline_dir / test_path, current / test_path
    current_version = version_code(new_app)
    assert version_code(old_app) == 2 and current_version > 2
    summary = {"passed": False, "baseline_ref": Path("qa/upgrade-baseline.txt").read_text().strip(),
               "before_version": 2, "after_version": current_version}
    try:
        assert qa.adb("shell", "getprop", "ro.kernel.qemu") == "1", "Only a disposable emulator is allowed"
        assert not qa.adb("shell", "pm", "list", "packages", APP), "Use a fresh emulator with no tracker/test installation"
        certificates = [certificate(p) for p in (old_app, new_app, old_test, new_test)]
        assert len(set(certificates)) == 1, "Both versions and test APKs must share one signing certificate"
        summary["certificate_sha256"] = certificates[0]
        summary["apk_sha256"] = {str(p.name) + suffix: hashlib.sha256(p.read_bytes()).hexdigest()
                                 for p, suffix in ((old_app, "-v2"), (new_app, "-current"))}
        qa.adb("install", "-t", old_app)
        qa.adb("install", "-t", old_test)
        assert qa.installed_version() == 2
        qa.phase("prepare-old", 2, "prepare-v2")
        old_checkpoint = qa.checkpoint_copy("v2-original")
        qa.kill_and_restore("v2-force-stop")
        qa.phase("verify", 2, "v2-restored")
        qa.checkpoint_copy("v2-after-kill", old_checkpoint)
        # No uninstall or data clearing occurs across this replacement.
        qa.adb("install", "-r", "-t", new_app)
        qa.adb("install", "-r", "-t", new_test)
        assert qa.installed_version() == current_version
        qa.launch()
        qa.click("Resume workout")
        qa.wait_text("Recovery fixture")
        qa.capture("current-upgraded-session")
        qa.phase("verify", current_version, "current-upgrade-retained")
        qa.checkpoint_copy("current-after-upgrade", old_checkpoint)
        qa.phase("prepare-current", current_version, "prepare-current-origin")
        current_checkpoint = qa.checkpoint_copy("current-original")
        qa.kill_and_restore("current-force-stop")
        qa.phase("verify", current_version, "current-restored")
        qa.checkpoint_copy("current-after-kill", current_checkpoint)
        qa.launch()
        qa.click("Resume workout")
        qa.wait_text("Recovery fixture")
        qa.click("Finish workout", scroll=True)
        qa.wait_text("Save workout")
        qa.click("Save workout")
        qa.wait_text("Start workout")
        qa.phase("verify-finished", current_version, "current-finished-once")
        qa.kill_and_restore("current-after-finish", draft=False)
        qa.phase("verify-finished", current_version, "current-finish-still-once")
        qa.adb("pull", "/sdcard/Android/data/" + APP + "/files/qa-lifecycle", qa.output / "database")
        summary.update(passed=True, checkpoint_sha256={
            "legacy": hashlib.sha256(old_checkpoint).hexdigest(),
            "current": hashlib.sha256(current_checkpoint).hexdigest()})
        # The harness owns these synthetic records; normal Compose tests need fresh onboarding.
        qa.adb("shell", "am", "force-stop", APP)
        assert qa.adb("shell", "pm", "clear", APP) == "Success"
        print(f"PASS installed version 2→{current_version} upgrade, cold-process recovery and exactly-once finish", flush=True)
    except Exception:
        try:
            qa.capture("failure")
        except Exception:
            pass
        raise
    finally:
        summary.update(process_restarts=qa.events, phases=qa.phases)
        (qa.output / "lifecycle-summary.json").write_text(json.dumps(summary, indent=2) + "\n")
        (qa.output / "logcat.txt").write_text(qa.adb("logcat", "-d", check=False))


if __name__ == "__main__":
    main()
