# Quiet Ledger

Quiet Ledger is a native Android personal-finance tracker that works entirely on your phone. It turns selected bank and payment notifications into structured transactions, helps review uncertain entries, and keeps a calm monthly budget—without an account, cloud sync, ads, analytics, or an internet connection.

## Highlights

- **Offline and private.** All data stays on-device. The app declares no `INTERNET` permission and has no cloud, Firebase, analytics, API, or crash-reporting dependency.
- **Selective notification detection.** You choose which installed apps Quiet Ledger may inspect; every other app is ignored.
- **Conservative parsing.** Only completed money movement—paid, debited, credited, refunded, reversed, or received—is recorded. OTPs, authentication codes, failures, pending attempts, and future AutoPay/SIP/bill/recharge reminders are ignored.
- **Safe duplicate review.** Transactions are flagged only on an exact reference match or strong combined evidence. Nothing is silently deleted.
- **Local statement reconciliation.** Import a bank CSV through Android's file picker, preview its rows, and add only the selected new entries; the CSV is parsed in memory and not retained.
- **Quick manual entry.** Type or use the phone keyboard's voice button for text such as `Swiggy 340`, `paid 1200 to doctor`, or `Salary 45000`; the app fills the form locally for review.
- **Monthly budget.** Enter income and a savings goal to see spendable money, money spent, remaining money, percentage used, and a daily guide.
- **Optional local alert.** The Budget screen can request Android notification permission solely for an on-device daily overspend warning.
- **Security and control.** Optional biometric/device-lock gate, local CSV export, manual transactions, categories, and remembered merchant corrections.

## Privacy model

Notification text is processed in memory and discarded. The database stores only structured ledger data such as amount, category, timestamp, source app, optional final account digits, and reference ID. It never stores raw notification text, OTPs, PINs, passwords, or verification codes.

Exports are created through Android's document picker, so you choose a local destination. Nothing is uploaded.

## Requirements

- Android phone running Android 8.0 (API 26) or newer
- Android Studio with the Android SDK installed
- USB cable or configured wireless debugging
- Developer options and USB debugging enabled on the phone

The project targets Android 15 / API 35 and uses Kotlin, Jetpack Compose, Material 3, Room, DataStore, Navigation Compose, and Android's `NotificationListenerService`.

## Run on your Android phone

### Android Studio

1. On the phone, open **Settings → About phone** and tap **Build number** seven times.
2. Open **Settings → System → Developer options** and enable **USB debugging**.
3. Connect the phone by USB and accept its RSA debugging prompt.
4. Open this project folder in Android Studio.
5. Wait for Gradle sync. If prompted, install/select the required SDK and the JDK configured in `gradle.properties` (JDK 20).
6. Select the connected phone and the `app` run configuration.
7. Press the green **Run** button. Android Studio builds, installs, and opens Quiet Ledger.

### Command line

With `adb` available on your `PATH` and an authorized device connected:

```powershell
.\gradlew.bat :app:assembleDebug
adb devices
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

The `-r` option updates the debug app while preserving local data when Android permits it.

## First-run setup

1. Read the privacy explanation during onboarding.
2. Enable Android notification access when you want automatic tracking; Quiet Ledger cannot grant this access itself.
3. Select only financial apps you approve in **Settings → Financial sources**.
4. Add income and a savings target in **Settings → Monthly budget**. Enable the optional alert only if you want a daily local notification.
5. Review detected notifications from the **Review** tab. They affect totals only after you choose **Add**; use **Ignore** for an unwanted candidate.
6. Use **Settings → Import bank statement** to reconcile missed activity from a CSV exported by your bank. The app does not delete the original file.

## How notification detection works

1. Android delivers a notification from a user-approved source.
2. Quiet Ledger filters security codes, scheduled reminders, and non-completed notices in memory.
3. A local parser extracts completed amount, direction, optional merchant/person, payment method, account suffix, and reference ID.
4. The app categorizes the entry locally, checks strong duplicate evidence, and saves only the structured result in Room.
5. The raw notification text is not written to preferences or the database.

Because financial providers use different wording, the parser favors false negatives over accidentally recording a reminder as a payment. Use **+ Add** for a transaction it did not recognize.

## Project structure

```text
app/src/main/java/com/quietledger/
├── data/           # Local DataStore preferences
├── database/       # Room transaction database
├── models/         # Ledger and budget models
├── notification/   # Selected-source listener and local budget alert
├── parser/         # Conservative parser and categorizer
├── repository/     # Transaction, duplicate, summary, and budget logic
├── screens/        # Compose screens
└── ui/             # Navigation, lock gate, components, and theme
```

## Troubleshooting

- **Phone is missing from Android Studio:** reconnect it, accept the RSA prompt, then run `adb devices` to confirm it is authorized.
- **Automatic tracking is paused:** enable Quiet Ledger under **Settings → Notification access**, then select at least one financial source.
- **A payment is missing:** wording differs by provider. Add it manually rather than expanding app access unnecessarily.
- **No budget alert appears:** enable it in the Budget screen and accept Android's notification permission. System-level notification settings can also suppress alerts.
- **Gradle build fails:** install Android SDK API 35 and configure Android Studio to use the JDK selected in `gradle.properties`.

## Development principles

Contributions must preserve the offline-first model: no network permissions, external data collection, cloud synchronization, tracking SDKs, or raw-notification storage. Financial decisions must remain transparent and reviewable.

## License

No license has been selected. Add one before accepting external contributions or distributing the code under defined terms.
