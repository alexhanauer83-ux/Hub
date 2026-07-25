# Hub

Ein moderner Nachbau des BlackBerry-Hub-Konzepts als native Android-App: ein zentraler,
priorisierbarer Feed aller Nachrichten des Geräts, lokal, ohne Cloud-Backend.

## Build

Diese Umgebung hat kein JDK/Android SDK, daher wurde das Projekt ohne Build-Verifikation
geschrieben. Zum Bauen:

1. Projekt in **Android Studio** (Koala/2024.1 oder neuer) öffnen. Android Studio erzeugt
   beim ersten Öffnen automatisch den fehlenden `gradle/wrapper/gradle-wrapper.jar`
   (bzw. führe manuell `gradle wrapper --gradle-version 8.7` aus, falls du die
   Kommandozeile bevorzugst).
2. `minSdk 29` / `compileSdk 35` – Zielgerät laut Vorgabe: Samsung Galaxy S25 Ultra.
3. `./gradlew assembleDebug` bzw. Run ▶ in Android Studio.

## Architektur

- **`data/local`** – Room-Entities/DAOs/Datenbank (ab Phase 7 per SQLCipher verschlüsselt).
- **`data/source`** – `MessageSource`-Interface: gemeinsamer Vertrag für den
  `NotificationListenerService` und alle API-Connectoren (Telegram, IMAP, Matrix).
- **`data/repository`** – `MessageRepository`, einzige Quelle der Wahrheit für die UI,
  implementiert gleichzeitig `MessageIngestSink` für alle Quellen.
- **`notification`** – Kernfunktion 1 (Notification-Abgriff, Quick Reply).
- **`sms`** – optionale Standard-SMS-App-Anbindung.
- **`connectors`** – Kernfunktion 2 (offene APIs als Bonus-Anbindung).
- **`ui`** – Jetpack Compose, Material 3, eigenes dunkles Theme.
- **`security`** – Verschlüsselung, App-Lock.

## Phasen (siehe Commit-Historie)

1. Grundgerüst (dieses Commit)
2. NotificationListenerService + Onboarding
3. Hub-UI (Swipe, Filter, Priority Hub)
4. Quick Reply (RemoteInput)
5. SMS als Standard-App (optional)
6. API-Connectoren (Telegram PoC, IMAP/Matrix als Stub)
7. Verschlüsselung, App-Lock, Politur

## Berechtigungen (kumulativ, wächst mit den Phasen)

| Berechtigung | Ab Phase | Warum |
|---|---|---|
| `BIND_NOTIFICATION_LISTENER_SERVICE` | 2 | Notification-Abgriff (Kernfunktion 1) |
| `POST_NOTIFICATIONS` | 2 | Eigene Status-/Fehlermeldungen der App (Android 13+) |
| SMS-Set (`RECEIVE_SMS`, `SEND_SMS`, `READ_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH`) | 5 | Standard-SMS-App-Rolle |
| `USE_BIOMETRIC` | 7 | App-Lock |
| Internet/Netzwerk | 6 | Telegram-Bot-API-Polling, IMAP |

Details jeweils im Commit der entsprechenden Phase.
