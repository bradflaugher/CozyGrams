# CozyGrams for Android TV

A cozy, endless nonogram game made for the couch. Play solo, or hand someone a second
controller and solve every picture together with independent Rose and Sky cursors.

## Controls

| | gamepad | bare TV remote |
|---|---|---|
| move | D-pad or left stick (wraps around) | D-pad |
| fill a square | **A** | **OK** cycles fill → cross → clear |
| cross a square out | **B** or **X** | (part of the OK cycle) |
| reveal one square | **Y** | hold **OK** |
| cozy corner | **Menu** / **Start** | **Menu** |
| back | **B** in menus, **Back** | **Back** |

A remote has no face buttons, so the centre key does more work there and the on-screen
legend changes to match whatever is actually in the room. The first controller to send
input becomes Rose, the second becomes Sky, and both keep their identity for the session —
including across a controller falling asleep and waking up with a new device id.

## Game features

- **Every board is uniquely line-solvable.** A real constraint solver checks each puzzle,
  so the clues alone are always enough — you never have to guess, and there is never a
  second valid answer to feel cheated by.
- Deterministic endless play from 5×5 to 20×20, with 24 cozy subjects (hearts, sleepy
  cats, cocoa, moons, rain on the window, teapots, mittens, sleeping foxes, pie…) each
  drawn in up to 12 variations.
- An 18-chapter handcrafted Story Book that grows from 5×5 through 10×10 to 15×15.
- Local couch co-op with two independent cursors, shared credit and no scoreboard.
- Runtime-synthesized score — a 3½-minute evolving music box in F pentatonic with five
  layers that fade between sections, plus ten distinctly-synthesized sound effects and a
  12-voice mixer so both players are always heard.
- Illustrated living-room and moonlit-garden scenes that stay visible while you play.
- Comfort options: larger text, extra contrast, colour-blind-friendly player identity,
  bolder cursors, calmer animation, gentle mistake checking, and put-everything-back.
- Progress that survives anything — a versioned save with a fingerprint of the hidden
  picture, so a stale or mismatched save deals a fresh board instead of corrupting one.
- Native landscape TV interface. No touchscreen required, nothing below the safe area.

## Build and test

Install JDK 17 and the Android SDK, then run:

```sh
./gradlew test lint assembleDebug
```

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions runs
tests, lint and a complete debug build on every push and pull request.

## Previewing the interface without a device

The entire UI is drawn onto one `Canvas`, and the rendering code is deliberately free of
`View`, `Context` and input handling. That makes it possible to render real frames on a
desktop JVM, with no emulator:

```sh
tools/preview/render.sh [outputDir] [width] [height]     # 15 scenarios, default 1920x1080
```

It compiles a Java2D-backed set of `android.graphics` stubs together with the app's own
rendering sources and writes deterministic PNGs — the same input always produces the same
bytes, so frames can be compared across changes. `tools/legacy-preview/render-legacy.sh`
does the same for the pre-2.0 interface, for side-by-side comparison.

## Release

Push a version tag such as `v1.0.0`, or manually run **Latest APK Release** in GitHub
Actions. The workflow tests the project, builds a release APK, removes older GitHub
Releases, and publishes a single latest release containing `CozyGrams-latest.apk`. The APK
is suitable for sideloading on NVIDIA Shield TV Pro (enable installation from unknown
sources first).

Release APKs use the project's stable sideloading certificate, so subsequent GitHub
Releases install as in-place upgrades. The bundled certificate is intentionally for this
open-source personal game — not for Play Store identity or security-sensitive
distribution.
