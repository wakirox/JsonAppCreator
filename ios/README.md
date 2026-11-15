# Appkin for iOS

Native SwiftUI iOS app built on top of the `:shared` Kotlin Multiplatform engine (`AppkinShared.xcframework`).

## Build & Run (macOS)

1. Build the KMP XCFramework from the repository root:
   ```sh
   ./gradlew :shared:assembleAppkinSharedDebugXCFramework
   ```
2. Generate `Appkin.xcodeproj` with [XcodeGen](https://github.com/yonaskolb/XcodeGen):
   ```sh
   cd ios && xcodegen
   ```
3. Open `ios/Appkin.xcodeproj` in Xcode (iOS 26.0+ simulator or device) and run the `Appkin` scheme, or run unit tests via the `AppkinTests` target.

> On Linux, `:shared`'s iOS Kotlin/Native sources can still be type-checked and compiled without Xcode via `./gradlew :shared:compileKotlinIosArm64`.

## Debug Launch Arguments

In `DEBUG` builds, `UserDefaults` / scheme launch arguments can open screens directly for testing and screenshots:
- `-screen run -example v3_masterpieces` (or `v3_app_ideas`, `v3_the_movie_db`)
- `-screen editor` / `-screen ask` / `-screen about`
- `-openFile /path/to/app.json`
