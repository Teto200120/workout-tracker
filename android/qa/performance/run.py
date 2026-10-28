"""Build an independent UI timer with installed SDK tools; use a disposable emulator."""

import argparse
import json
import os
from pathlib import Path
import subprocess
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--serial", required=True)
parser.add_argument("--output", type=Path, required=True)
parser.add_argument("--mode", choices=["workout", "stats"], default="workout")
parser.add_argument("--runs", type=int, default=9)
parser.add_argument("--workouts", type=int, default=1003)
args = parser.parse_args()
sdk = Path(os.environ["ANDROID_HOME"])
java = Path(os.environ["JAVA_HOME"]) / "bin"
tools = sdk / "build-tools/36.0.0"
platform = sdk / "platforms/android-36/android.jar"
source = Path(__file__).resolve().parent
output = args.output.resolve()
output.mkdir(parents=True, exist_ok=True)
windows = os.name == "nt"


def run(*command):
    result = subprocess.run(
        list(map(str, command)), capture_output=True, text=True, encoding="utf-8"
    )
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    return result.stdout.strip()


def adb(*command):
    return run(sdk / "platform-tools" / ("adb.exe" if windows else "adb"), "-s", args.serial, *command)


def tool(name):
    return tools / (name + (".bat" if windows else ""))


if adb("shell", "getprop", "ro.kernel.qemu") != "1":
    raise SystemExit("Use a disposable Android emulator with synthetic history.")
if not 1 <= args.runs <= 100:
    raise SystemExit("Choose 1 to 100 runs.")
for directory in ["classes", "dex"]:
    (output / directory).mkdir(exist_ok=True)
run(java / "javac", "-source", "8", "-target", "8", "-cp", platform, "-d", output / "classes", source / "Driver.java")
run(java / "jar", "cf", output / "classes.jar", "-C", output / "classes", ".")
run(tool("d8"), "--min-api", "26", "--lib", platform, "--output", output / "dex", output / "classes.jar")
run(tools / "aapt2", "link", "-I", platform, "--manifest", source / "AndroidManifest.xml", "-o", output / "unsigned.apk")
with zipfile.ZipFile(output / "unsigned.apk", "a") as archive:
    archive.write(output / "dex/classes.dex", "classes.dex")
run(tools / "zipalign", "-f", "4", output / "unsigned.apk", output / "aligned.apk")
run(tool("apksigner"), "sign", "--ks", Path.home() / ".android/debug.keystore", "--ks-key-alias", "androiddebugkey", "--ks-pass", "pass:android", "--key-pass", "pass:android", "--out", output / "driver.apk", output / "aligned.apk")
adb("install", "-r", output / "driver.apk")
adb("shell", "cmd", "package", "compile", "-m", "speed", "-f", "com.hector.workouttracker")
result = adb("shell", "am", "instrument", "-w", "-e", "runs", args.runs, "-e", "warmups", "2", "-e", "mode", args.mode, "-e", "workouts", args.workouts, "com.hector.workoutbenchmark/.Driver")
(output / "instrumentation.txt").write_text(result + "\n", encoding="utf-8")
if "PERFORMANCE_OK" not in result:
    raise SystemExit(result)
name = "stats.json" if args.mode == "stats" else "flows.json"
data = json.loads(adb("exec-out", "run-as", "com.hector.workoutbenchmark", "cat", "files/results/" + name))
(output / name).write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
print(f"Saved {len(data)} measured runs to {output / name}")
