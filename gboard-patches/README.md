# gboard-patches

Morphe patches that turn stock Gboard into **"Gboard with Pesto"** — a side-installed clone whose
microphone key runs Pesto dictation. This is the plan's path (ε) (RESEARCH §4, §9).

Licensed **GPLv3**, as an academic, not-for-profit project. Only the patches are shared; the Gboard
APKs are never committed (RESEARCH §5).

## What the patches do

| Patch | Effect |
|---|---|
| Install as Gboard with Pesto clone | Renames the package so it installs next to stock Gboard; prefixes authorities and custom permissions. |
| Gboard signature bypass | Makes Gboard's internal signature check accept the locally re-signed APK. |
| Microphone to Pesto | Routes the `LAUNCH_VOICE_IME` mic key to Pesto dictation, with Gboard's own voice input as fallback. |
| Seed keyboard languages | Bundles `assets/pesto_locales.txt` (option `pestoLocales`, written as given) and applies those languages on first start. The default is the S2 example: `en-GB:qwerty;el-GR:greek`, `enabled_ime_language_tags=en-GB;el`. |
| Hide from launcher | Removes the launcher icon. |
| Pesto manifest wiring | Declares Pesto package visibility, the keyboard label and `RECORD_AUDIO`. |

While Pesto listens, a minimal panel ("Listening…", "Done", "Cancel") covers the keys, sized
from `onComputeInsets().visibleTopInsets` like the S6 spike's; its design is a TODO for phase 6.
"Cancel", or a hidden keyboard (back, or the keyboard closing), cancels and drops the result.

The microphone and language logic lives in the `pestobridge` extension (Java, merged as a DEX), so
the patches stay small and the obfuscation-sensitive parts (reading the key event, the handler's
context, the input connection) are done with reflection rather than pinned to obfuscated names
(RESEARCH §11).

## Building

Needs the Android SDK (`sdk.dir` in `local.properties`) and the Morphe toolchain in the local Maven
repository. Bootstrap the toolchain once:

```bash
tools/bootstrap-morphe.sh
```

Then build the patches bundle (`patches/build/libs/patches-*.mpp`):

```bash
./gradlew :patches:buildAndroid
```

## Tests

```bash
./gradlew :patches:test
```

- Pure unit tests (`PestoLocalesTest`, `PatchSetTest`) always run.
- `RealGboardApkTest` runs the patches against real Gboard APKs and is **skipped** unless their
  paths are set in `local.properties` (see `local.properties.example`). It confirms every
  fingerprint matches the real bytecode.
