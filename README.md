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

1. Grundgerüst
2. NotificationListenerService + Onboarding
3. Hub-UI (Swipe, Filter, Priority Hub, Peek)
4. Quick Reply (RemoteInput)
5. SMS als Standard-App (optional)
6. API-Connectoren (Telegram vollständig, IMAP/Matrix als Stub)
7. Verschlüsselung, App-Lock, Politur

## Berechtigungen

| Berechtigung | Phase | Warum |
|---|---|---|
| `BIND_NOTIFICATION_LISTENER_SERVICE` | 2 | Notification-Abgriff (Kernfunktion 1). Signatur-Permission: nur das System darf den Service binden. Vom Nutzer nur in den Systemeinstellungen erteilbar, nicht per Runtime-Dialog. |
| `POST_NOTIFICATIONS` | 2 | Eigene Hinweise der App (Android 13+). Nicht fürs Mitlesen fremder Notifications. |
| `INTERNET`, `ACCESS_NETWORK_STATE` | 6 | Nur für die API-Connectoren. Verbindungen gehen direkt zum Dienst – kein Hub-Backend. |
| SMS-Set (`RECEIVE_SMS`, `READ_SMS`, `SEND_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH`) | 5 | Nur relevant, wenn Hub bewusst zur Standard-SMS-App gemacht wird. |
| `USE_BIOMETRIC` | 7 | App-Lock. Geräte-PIN als Rückfallebene braucht keine eigene Berechtigung. |

`QUERY_ALL_PACKAGES` wird angefordert, damit App-Namen und -Icons der Quell-Apps aufgelöst
werden können (seit Android 11 sind Apps sonst füreinander unsichtbar, und im Feed stünde
nur der rohe Paketname). Die Daten werden ausschließlich lokal zur Anzeige genutzt.
Backup ist über `allowBackup=false` und `data_extraction_rules.xml` vollständig gesperrt,
sonst würden die aggregierten Nachrichten das Gerät verlassen.

## Was fertig ist – und was nicht

Ehrlich gehalten, damit niemand auf eine Fassade hereinfällt:

| Bereich | Status |
|---|---|
| Notification-Aggregation, Feed, Swipe/Filter/Priority/Peek | vollständig |
| Quick Reply via RemoteInput | vollständig |
| Telegram (Bot API) | vollständig, aber Bot-API-typisch begrenzt: ein Bot sieht **nur an ihn gerichtete** Nachrichten, nicht die privaten Chats des Nutzers |
| SMS | Lesen/Senden implementiert; eingehende SMS werden **nicht** in den System-Provider zurückgeschrieben → im UI als experimentell gekennzeichnet |
| Verschlüsselung (SQLCipher + Keystore), App-Lock | vollständig |
| IMAP | Gerüst lauffähig, aber Polling statt IDLE, Message-Nummer statt UID, Passwort im Klartext → TODOs im KDoc |
| Matrix | reiner Platzhalter, wirft `NotImplementedError` (Begründung im KDoc) |

IMAP und Matrix sind absichtlich **nicht** in der `ConnectorRegistry` registriert, damit sie
nicht als nutzbare Quellen in den Einstellungen erscheinen.

## Bekannte Plattform-Grenzen (nicht behebbar)

- **Redigierte Notification-Inhalte**: Blendet Android sensible Inhalte aus, liefert es an
  Listener nur Platzhaltertext. Hub erkennt das heuristisch und kennzeichnet es, statt den
  Platzhalter als echte Nachricht auszugeben. Abhilfe gibt es nur über einen echten
  API-Connector.
- **Quick Reply nach Neustart**: Die `PendingIntent` einer fremden App ist nicht
  persistierbar. Nach Geräteneustart oder Verwerfen der Notification bleibt die Nachricht
  im Hub, die Antwortmöglichkeit aber nicht.
- **Connector-Laufzeit**: Die Polling-Schleifen laufen im App-Prozess. Beendet Android den
  Prozess, enden sie – echter Hintergrundempfang bräuchte Foreground-Service/WorkManager.
