# War Gold Scanner (WGS)

A production-quality Android app for Clash of Clans clan war scouting.
Records each enemy player's gold storage value using screen capture + ML Kit OCR.

---

## Requirements

- Android Studio Ladybug (2024.2.1) or newer
- Android SDK 35
- Kotlin 2.0+
- Java 17

---

## Build Instructions

1. Open Android Studio
2. File → Open → select the `war-gold-scanner/` folder
3. Wait for Gradle sync to complete (~2–3 min first time)
4. Connect a physical Android device (Android 8.0+, 2–3 GB RAM)
5. Run → Run 'app'

> **Note:** MediaProjection and floating overlay require a **real device**.
> They do NOT work in the Android emulator.

---

## First Run — Permission Flow

1. Open the app → tap **Start Scan**
2. Grant **Display over other apps** permission (Settings opens automatically)
3. Return to the app → tap **Start Scan** again
4. Grant **Screen capture** permission in the system dialog
5. The floating **SCAN** button appears over your screen

---

## How to Use

### Scanning a Player

1. Open Clash of Clans → open an enemy base in war map
2. Tap the floating **SCAN** button → reads Base Number + Player Name
3. Tap an enemy **Gold Storage** building
4. Tap **SCAN** again → reads gold value, saves the record
5. Repeat for the next player

### Records
- Tap **Records** to view all saved data
- Long-press a record → Edit / Copy / Delete
- Tap **Share** icon → export CSV
- Tap **Copy** icon → copy all as plain text

### Export Formats
**CSV:** `Base,Player,Gold,Date`
**Plain text:** `1. PlayerName - 35,800`

---

## Architecture

```
app/
├── data/
│   ├── db/          — Room entities + DAO + Database
│   └── repository/  — ScanRepository (single source of truth)
├── di/              — Hilt modules (DB, DataStore)
├── service/
│   ├── ScreenCaptureService.kt   — MediaProjection foreground service
│   └── ScanStateManager.kt      — Shared state machine (StateFlow)
├── overlay/
│   └── FloatingOverlayService.kt — System overlay SCAN button
├── ocr/
│   └── OcrProcessor.kt          — ML Kit crop + normalize + extract
├── export/
│   └── ExportHelper.kt          — CSV + plain text + FileProvider
└── ui/
    ├── theme/   — Dark gold theme (Compose Material3)
    ├── home/    — HomeScreen + HomeViewModel (scan flow)
    ├── records/ — RecordsScreen + RecordsViewModel (CRUD + search)
    └── settings/— SettingsScreen (War ID + region info)
```

### Scan State Machine

```
IDLE → WaitingForBase → BaseCapturing → WaitingForGold → GoldCapturing → Saving → Success → WaitingForBase
                                                                             ↓
                                                                         Duplicate → (YES → overwrite / NO → skip)
```

---

## OCR Crop Regions (% of screen)

| Target        | Left  | Top   | Right | Bottom |
|---------------|-------|-------|-------|--------|
| Base Number   | 55%   | 3%    | 95%   | 12%    |
| Player Name   | 5%    | 12%   | 85%   | 21%    |
| Gold Storage  | 30%   | 40%   | 80%   | 55%    |

Adjust in `OcrProcessor.kt → OcrRegions` if your device's CoC UI differs.

---

## OCR Normalization

- `O` → `0`, `I/l` → `1`
- Remove commas and dots
- Split by `/` → keep only the value before `/` (current gold, not capacity)
- Trim whitespace

---

## Data Model

| Field       | Type   | Notes                            |
|-------------|--------|----------------------------------|
| id          | Long   | Auto-generated                   |
| warId       | String | Groups records by war session    |
| baseNumber  | Int    | Enemy base position (#1, #2, …)  |
| playerName  | String | Scanned from screen              |
| goldValue   | Long   | Current gold (before `/`)        |
| date        | String | yyyy-MM-dd                       |
| timestamp   | Long   | Unix millis                      |

---

## Extending the App (Future Resources)

The architecture is ready for:
- **Elixir scanning** → add `elixirValue` to `ScanRecord`, add crop region to `OcrRegions`
- **Dark Elixir scanning** → same pattern
- **Town Hall detection** → add `townHallLevel` field + dedicated crop region
- **Hero/Clan Castle detection** → separate `ScanPhase` states

No major refactoring needed — extend `OcrRegions`, add fields to `ScanRecord`, update DAO, re-run migration.

---

## Permissions Used

| Permission                        | Why                                      |
|-----------------------------------|------------------------------------------|
| `FOREGROUND_SERVICE`              | Keep capture service alive               |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Required for MediaProjection on Android 14+ |
| `SYSTEM_ALERT_WINDOW`             | Floating SCAN button overlay             |
| `POST_NOTIFICATIONS`              | Foreground service notification          |
| `WRITE_EXTERNAL_STORAGE` (≤28)    | CSV export on older devices              |

---

## Performance Notes

- OCR only runs **on demand** (when SCAN is tapped) — never continuously
- `ImageReader` with 2-frame buffer minimizes memory pressure
- ML Kit Latin text recognizer is ~5 MB — much lighter than full model
- Room + Flow for reactive UI with no polling
- Bitmaps are recycled immediately after OCR
