#!/usr/bin/env python3
"""Build with official Android SDK tools only; no Gradle dependencies."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def run(*args):
    subprocess.run([str(x) for x in args], check=True, cwd=ROOT)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--sdk", default=os.environ.get("ANDROID_HOME", ""))
    args = parser.parse_args()
    if not args.sdk:
        parser.error("Pass --sdk with your Android SDK directory.")
    sdk = Path(args.sdk).resolve()
    bt = sdk / "build-tools" / "35.0.0"
    android = sdk / "platforms" / "android-35" / "android.jar"
    java = Path(os.environ["JAVA_HOME"]) / "bin" if "JAVA_HOME" in os.environ else None
    def jtool(name): return java / (name + (".exe" if os.name == "nt" else "")) if java else name
    def tool(name): return bt / (name + (".exe" if os.name == "nt" else ""))
    if not android.is_file() or not bt.is_dir():
        raise SystemExit("Install SDK platform android-35 and build-tools 35.0.0 first.")
    build = ROOT / "build"
    if build.exists(): shutil.rmtree(build)
    for name in ("classes", "generated", "dex"): (build / name).mkdir(parents=True)
    main = ROOT / "app" / "src" / "main"
    run(tool("aapt2"), "compile", "--dir", main / "res", "-o", build / "resources.zip")
    run(tool("aapt2"), "link", "-o", build / "base.apk", "-I", android,
        "--manifest", main / "AndroidManifest.xml", "--java", build / "generated",
        build / "resources.zip")
    sources = [f for base in (main / "java", build / "generated")
               for f in base.rglob("*.java") if not any(p.startswith(".") for p in f.parts)]
    run(jtool("javac"), "-encoding", "UTF-8", "--release", "8",
        "-classpath", android, "-d", build / "classes", *sources)
    jar = build / "classes.jar"
    with zipfile.ZipFile(jar, "w") as z:
        for f in (build / "classes").rglob("*.class"):
            if any(p.startswith(".") for p in f.parts): continue
            z.write(f, f.relative_to(build / "classes"))
    run(jtool("java"), "-cp", bt / "lib" / "d8.jar", "com.android.tools.r8.D8",
        "--lib", android, "--min-api", "26", "--output", build / "dex", jar)
    shutil.copyfile(build / "base.apk", build / "unsigned.apk")
    with zipfile.ZipFile(build / "unsigned.apk", "a", zipfile.ZIP_DEFLATED) as z:
        for f in (build / "dex").glob("*.dex"): z.write(f, f.name)
    run(tool("zipalign"), "-f", "4", build / "unsigned.apk", build / "aligned.apk")
    signing = ROOT / "signing"
    signing.mkdir(exist_ok=True)
    key = signing / "quiettv.jks"
    # Personal test certificate, not a commercial publisher identity.
    if not key.exists():
        run(jtool("keytool"), "-genkeypair", "-keystore", key, "-storepass", "quiettv-local",
            "-keypass", "quiettv-local", "-alias", "quiettv", "-keyalg", "RSA",
            "-keysize", "2048", "-validity", "10000", "-dname", "CN=Quiet TV Personal")
    output = ROOT / "QuietTV-0.5.1-dev.apk"
    run(jtool("java"), "-jar", bt / "lib" / "apksigner.jar", "sign",
        "--ks", key, "--ks-key-alias", "quiettv", "--ks-pass", "pass:quiettv-local",
        "--key-pass", "pass:quiettv-local", "--out", output, build / "aligned.apk")
    run(jtool("java"), "-jar", bt / "lib" / "apksigner.jar", "verify", "--verbose", output)
    print("APK:", output)

if __name__ == "__main__":
    main()
