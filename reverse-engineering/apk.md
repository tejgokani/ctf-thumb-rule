# APK Analysis (Android)

## Fastest First-Pass Checklist (APK)

```bash
# Purpose: confirm it's a valid ZIP-based APK and spot anomalies in structure
file ./app.apk
unzip -l ./app.apk

# Purpose: quick strings sweep before any decompilation — sometimes the whole solve
strings -n 8 ./app.apk | grep -iE 'flag|wtf\{|secret|key|password|token'

# Purpose: decode resources + manifest (fast, no Java decompilation)
apktool d ./app.apk -o apk_out

# Purpose: full Java-ish source decompilation (slower, much more readable than smali)
jadx -d jadx_out ./app.apk

# Purpose: broad grep sweep across both outputs — the single highest-value command in APK triage
grep -RniE 'flag|secret|key|password|token|http|https' apk_out jadx_out
```

## The Standard APK Workflow, In Order — and Why

This is the canonical order for APK challenges. Each step is ordered by
cost vs information gained (see `../methodology/triage.md`'s Decision
Priorities):

1. **`file` / `unzip -l` / `strings` triage.**
   *Why first*: essentially free, and a meaningful fraction of "RE" APK
   challenges have the flag sitting in a plain string somewhere in the
   package — checking costs seconds and either solves the challenge outright
   or gives you search terms for every later step.

2. **`apktool d` (resources + manifest) and `jadx -d` (Java source) —
   run both, in parallel if possible.**
   *Why both, and why now*: `apktool` gives you the true `AndroidManifest.xml`
   (binary XML decoded) plus raw resources/assets exactly as packaged —
   fast and reliable. `jadx` gives you Java-like decompiled source, which
   is far more readable than raw smali for understanding *logic*, but can
   fail or produce partial output on obfuscated/edge-case bytecode. Running
   both means you have manifest/resource ground truth *and* a best-effort
   readable source tree, without waiting on one before starting the other.

3. **Inspect `AndroidManifest.xml`**: activities, services, receivers,
   providers, permissions, exported components, `intent-filter`s.
   *Why before diving into code*: the manifest tells you the *attack
   surface* and *entry points* before you've read a line of logic — which
   activity is launched, what's externally reachable (`exported="true"`),
   what permissions hint at capability (camera, storage, network,
   accounts), and which component names look deliberately suspicious
   (`SecretActivity`, `FlagProvider`, `DebugReceiver`). This tells you
   *where* in the decompiled source to start reading, instead of reading
   linearly from `MainActivity`.

4. **Inspect resources, assets, native libs**: `res/values/`, `res/raw/`,
   `assets/`, `lib/*.so`, `META-INF/`.
   *Why here*: these are non-code carriers that are cheap to check and
   commonly hold the payload directly (a flag in `res/raw/`, a key in
   `res/values/strings.xml`, an encoded blob in `assets/`) or hold material
   the code references (a native library the Java code calls into via
   JNI, which changes your next escalation step).

5. **Broad grep sweep**: `grep -RniE 'flag|secret|key|password|token|http|https'
   apk_out jadx_out`.
   *Why after manifest+resources, not before*: running it blind on a huge
   decompiled tree without first knowing which components matter produces a
   flood of irrelevant matches (library boilerplate, Android framework
   noise). Having already identified suspicious component names/resource
   paths from steps 3-4 lets you prioritize which grep hits are worth
   reading in full.

6. **Escalate only if steps 1-5 don't resolve it**: smali analysis of the
   specific suspicious method (when `jadx` decompilation is broken/
   obfuscated for that method), native `.so` analysis (when logic is in
   JNI, not Java — treat the `.so` as an ELF, see `elf.md`), Frida dynamic
   instrumentation, GDB, or runtime/logcat inspection.
   *Why last*: these are the most expensive techniques (require running
   the app, attaching a debugger/instrumentation framework, or reading
   assembly-level smali) — only justified once static reading of Java
   source and resources has been exhausted, per the "fastest plausible
   path" principle.

## Technique: SharedPreferences & SQLite Databases (Static + Dynamic)

**When to use it**: the app stores state (login tokens, flags unlocked at
runtime, settings) — common in APKs that require *running* the app to
reveal the flag.

**Fast path (static, if a device/emulator state is provided)**:
```bash
# Purpose: SharedPreferences are plain XML under this path on a device/emulator's data dir
adb shell run-as <package_name> cat shared_prefs/*.xml

# Purpose: pull and query an app's SQLite database directly
adb pull /data/data/<package_name>/databases/app.db
sqlite3 app.db ".tables" ".dump"
```
**Indicators**: a preference key or DB row containing a flag or an encoded
value that decodes to one.

## Technique: Native Library (.so) Analysis

**When to use it**: Java/Kotlin code calls a `native` method with no Java
implementation — logic lives in a bundled `.so` under `lib/<abi>/`.

**Fast path**:
```bash
# Purpose: treat the .so exactly as an ELF binary — see elf.md for the full workflow
file lib/arm64-v8a/libnative.so
strings -n 8 lib/arm64-v8a/libnative.so | grep -iE 'flag|key'
nm -D lib/arm64-v8a/libnative.so
```
**Next Step**: full ELF static/dynamic analysis per `elf.md` and
`static-analysis.md`; JNI function names typically follow
`Java_<package>_<Class>_<method>` and are a direct anchor point in
disassembly/decompilation (Ghidra resolves these well).

## Technique: Dynamic Instrumentation (Frida)

**When to use it**: the value you need is computed at runtime (e.g. a key
derived from device state, a check that short-circuits static reading) and
isn't recoverable from static source alone.

**Fast path**:
```bash
# Purpose: hook a specific method and print its arguments/return value at call time
frida -U -f <package_name> -l hook.js --no-pause
```
```javascript
// hook.js
// Purpose: intercept a suspected "check flag" or "decrypt" method and log its actual runtime values
Java.perform(function () {
    var target = Java.use("com.example.app.SecretActivity");
    target.checkFlag.implementation = function (input) {
        console.log("checkFlag called with: " + input);
        var result = this.checkFlag(input);
        console.log("checkFlag returned: " + result);
        return result;
    };
});
```
**Indicators**: logged runtime values reveal the actual comparison
target/derived key directly — often faster than statically deobfuscating
the same logic.

## Common Mistakes

- Running `jadx` alone and never checking the raw `AndroidManifest.xml`/
  resources via `apktool` — some data only survives in the raw resource
  form and jadx's manifest view can be incomplete for edge cases.
- Grepping before inspecting the manifest — wastes time triaging noise
  instead of targeted hits.
- Ignoring `exported="true"` components — these are reachable without the
  app's own UI (via `adb shell am start`), which is sometimes the intended
  solve path.
- Skipping native `.so` files because "the flag should be in Java" — many
  APK RE challenges deliberately push the interesting logic into JNI.
- Not checking permissions declared in the manifest as a hint toward what
  the app actually *does* (e.g. `CAMERA` permission on an app that's
  supposedly "just a calculator").
