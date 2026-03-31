# RFID Inventory App (Chainway C72)

Android RFID inventory application for **Chainway C72** devices, built with Kotlin and AndroidX.

This project is designed as a practical base for inventory operations using UHF RFID scanning, provider-backed sync flows, diagnostics, and role-based feature access.

## Author

- **Akash Arora**

## License

This project is licensed under the Apache License 2.0. See the `LICENSE` file for full terms.

If you use this project or publish derivatives, please preserve author attribution for **Akash Arora** in notices, credits, and documentation.

## Features

- Chainway UHF integration via `RFIDWithUHFUART` wrapper (`ChainwayUhfReaderGateway`)
- Physical-device compatibility spike activity (`CompatibilitySpikeActivity`)
- RFID scan workflows (inventory sync, anti-theft session support)
- Provider selection and authentication flow (Custom Node adapter path included)
- API path templates for provider integrations
- Offline-aware architecture with local Room DB + sync outbox
- Reader/network/auth/outbox diagnostics screen
- Role-based dashboard navigation (Admin/User)
- UHF config screen for region/power save-and-apply

## Tech Stack

- Kotlin + Android SDK
- Android Gradle Plugin `8.2.2`
- Kotlin plugin `1.9.22`
- Compile/Target SDK `35`
- Min SDK `24`
- Java/Kotlin target `17`
- AndroidX Navigation, Lifecycle, RecyclerView, Material
- Room (local persistence)
- OkHttp + Gson (network and JSON)
- WorkManager (background sync)
- JUnit, Mockito, Robolectric, Room testing, JaCoCo

## Project Structure

- `app/src/main/java/com/rfidsoftwares/` - main app source
- `app/src/main/res/` - XML layouts, navigation, resources
- `app/src/main/res/navigation/nav_graph.xml` - app flow graph
- `app/src/main/java/com/rfidsoftwares/rfid/` - RFID gateway and reader diagnostics
- `app/src/main/java/com/rfidsoftwares/integration/` - backend adapter and API integration logic
- `app/src/main/java/com/rfidsoftwares/common/config/` - feature and app-level config

## Prerequisites

1. **Windows** machine (recommended for Chainway SDK path setup)
2. **Android Studio** (latest stable recommended)
3. **JDK 17** installed  
   - Current project expects:
   - `C:\Program Files\Java\jdk-17`
4. **Android SDK** with API 35
5. **Chainway C72 hardware** for real UHF tests
6. Chainway demo SDK JARs available locally (see Setup section)

## Setup

### 1) Clone

```bash
git clone <your-repo-url>
cd rfid_inventory_app_phase0
```

### 2) Open in Android Studio

Open the project root folder and let Gradle sync.

### 3) Configure JDK 17

The project uses `gradle.properties` with:

`org.gradle.java.home=C\:\\Program Files\\Java\\jdk-17`

If your JDK path is different, update this value.

### 4) Provide Chainway SDK JAR dependencies

The app currently references local JARs in `app/build.gradle`:

```gradle
def demoLibRoot = 'D:/code backup/RFID Inventory/demo-uhf_example2/libs'
implementation files("$demoLibRoot/cw-deviceapi20191022.jar")
implementation files("$demoLibRoot/xUtils-2.5.5.jar")
implementation files("$demoLibRoot/jxl.jar")
implementation files("$demoLibRoot/IGLBarDecoder.jar")
```

You have two options:

- **Option A (quick start):** keep this path and place JARs there.
- **Option B (recommended for portability):** copy JARs into `app/libs/` and update `app/build.gradle` to use local `app/libs`.

### 5) Sync and build

```bash
./gradlew assembleDebug
```

On Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

## Running the App

1. Connect a Chainway C72 device (or run debug checks in emulator where applicable).
2. Install debug build:

```powershell
.\gradlew.bat installDebug
```

3. Launch the app from device launcher.

## Validating Chainway C72 Compatibility

The project includes `CompatibilitySpikeActivity` for early hardware validation of:

`getInstance()` -> `init()` -> `startInventoryTag()` -> `stopInventory()` -> `free()`

To test:

1. Run app on **physical C72**.
2. Launch compatibility flow/activity in your test build flow.
3. Verify no lifecycle crashes and successful start/stop/free behavior.
4. Use logcat tag `UHF_COMPAT_SPIKE` for diagnostics.

## UHF Reader Configuration

From the app UI, open **UHF Configuration** to set:

- Region code
- Power (dBm)

These values are saved via preferences and applied on reader init.

## Backend/API Integration

- Custom Node API paths are centralized in:
  - `app/src/main/java/com/rfidsoftwares/integration/config/ApiPathCustom.kt`
- App/provider feature behavior is configured in:
  - `app/src/main/java/com/rfidsoftwares/common/config/AppConfig.kt`

Adjust endpoint paths and provider behavior according to your backend contract.

## Testing

Run unit tests:

```powershell
.\gradlew.bat testDebugUnitTest
```

Run instrumentation tests:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Generate JaCoCo report:

```powershell
.\gradlew.bat jacocoTestReport
```

## Build Variants / ABI Notes

- Debug ABI filters: `arm64-v8a`, `armeabi-v7a`, `x86_64`
- Release ABI filters: `arm64-v8a`, `armeabi-v7a`

## Open Source Contribution Notes

Contributions are welcome.

- Keep changes focused and documented.
- Add/adjust tests when behavior changes.
- Preserve project and author attribution in derived works.
- For major changes, open an issue first to discuss approach.

## Troubleshooting

- **Gradle sync fails (JDK):** verify JDK 17 path in `gradle.properties`.
- **RFID classes unresolved:** ensure Chainway JAR files are present and path is valid.
- **Reader init fails on device:** verify hardware permissions, SDK compatibility, and test on physical C72.
- **No network-dependent features:** check API provider setup and connectivity.

---

Built for real-world RFID operations on Chainway C72.  
Maintained by **Akash Arora**.
