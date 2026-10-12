# After the Storm — Hurricane Relief Crew

A self-contained, offline Android prototype. Procedurally generated storm intensity and damage, four response tools, touchscreen joystick, hold-to-clear interactions, map, reward loop, a fresh mission button, procedural 2D art, and synthesized sound. No Unity dependency and no network permissions.

## Controls

- Left joystick: walk or drive.
- Bottom tools: chainsaw (trees), skid steer (rubble), tow truck (abandoned vehicles), excavator (collapsed structures).
- Get close to a debris site and hold **CLEAR** until completed.
- Clear all sites to win; start a new random hurricane.

## Install

Build the APK with Gradle 8.9 and JDK 17: `gradle assembleDebug` from the project directory. The unsigned-for-release but Android-debug-signed installable APK will be `app/build/outputs/apk/debug/app-debug.apk`.

An included GitHub Actions workflow builds and uploads an APK artifact on pushes to the `hurricane-relief-prototype` branch in the hosting repository. The action assumes this project lives in the `after-the-storm` directory.

## Browser preview

Open `app/src/main/assets/index.html` in a modern browser. Mobile portrait screens and desktop arrow/WASD controls are both supported.

## Notes

This is an arcade-style simulation, not an operational emergency-response or safety training tool. No real-world response instructions are represented.