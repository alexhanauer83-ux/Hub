# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Was das ist

"Hub" – native Android-App (Kotlin/Jetpack Compose), die das BlackBerry-Hub-Konzept nachbaut:
ein zentraler, priorisierbarer Feed aller Nachrichten des Geräts, **lokal, ohne Cloud-Backend**.
Antworten in der App gelten für UI-Texte auf Deutsch (die App-Sprache und Commit-Historie sind Deutsch).

## Build & Release

- Bauen/Testen laufen **nicht in dieser Umgebung** (kein lokales JDK/Android SDK). Gebaut wird über
  **Android Studio** (Run ▶) oder – zuverlässiger für Verifikation – über die **GitHub Actions**.
- `minSdk 29`, `compileSdk/targetSdk 35`. Kein Test-Setup vorhanden (keine Unit-/Instrumented-Tests).
- **Release-/Verifikations-Workflow** (so wird in diesem Repo faktisch „gebaut & geprüft"):
  1. `versionCode` (muss steigen) und `versionName` in `app/build.gradle.kts` erhöhen.
  2. Commit auf `main`, dann Tag `vX.Y.Z` pushen → `.github/workflows/release.yml` baut eine
     **signierte Release-APK** und legt automatisch ein GitHub-Release an.
  3. Build prüfen: `gh run list --workflow="Release APK" --limit 1`, dann
     `gh run watch <id> --exit-status`. Bei Fehler: `gh run view --log-failed --job=<jobId>`.
- Ein CI-Build ist die verlässlichste Kompilier-Prüfung, da hier nicht lokal gebaut werden kann.
  Grüner `assembleRelease` = Code kompiliert.
- Verteilung/Auto-Update: siehe `DISTRIBUTION.md`. Signatur-Secrets (`KEYSTORE_BASE64`,
  `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) liegen als GitHub-Secrets; der Keystore
  `hub-release.jks` ist per `.gitignore` (`*.jks`) ausgeschlossen. **Wichtig:** Text-Secrets mit
  `printf '%s'` setzen, nie `echo` (Zeilenumbruch bricht die Signatur → „Tag number over 30").

## Zentrale Architektur

Alle Quellen speisen in **denselben Feed** ein – die UI weiß nie, woher eine Nachricht stammt:

- **`data/source/MessageSource`** – gemeinsamer Vertrag für jede Quelle. Zwei Arten:
  1. *Push*: `NotificationMessageSource` (der `NotificationListenerService` schreibt selbst).
  2. *Pull*: API-Connectoren (`TelegramBotConnector`, `MatrixConnector`, `ImapConnector`) pollen in `start()`.
  Alle rufen `MessageIngestSink.ingest(IncomingMessage)`.
- **`data/repository/MessageRepository`** – einzige Quelle der Wahrheit für die UI **und** der
  `MessageIngestSink`. `ingest()` bewahrt bei erneutem Einlesen derselben Nachricht (gleiche
  `stableId = "sourceKey:externalId"`) den Nutzerzustand (`isRead`/`isArchived`/`priority`) – sonst
  würden Re-Deliveries Swipes überschreiben.
- **`data/local`** – Room, **SQLCipher-verschlüsselt** (`ServiceLocator.buildDatabase`, lädt native
  Lib via `System.loadLibrary("sqlcipher")`). DB ist reiner Cache → `fallbackToDestructiveMigration`;
  bei Schemaänderung nur `version` in `HubDatabase` erhöhen, keine Migration schreiben.
- **`di/ServiceLocator`** – bewusst **kein DI-Framework**. Manuelle Singletons (DB, Repository,
  Connectoren, `ConnectorRegistry`). Vom System instanziierte Klassen (Listener, Receiver, Widget)
  greifen hierüber statisch zu.
- **`connectors/ConnectorRegistry`** – startet/stoppt Pull-Connectoren als Coroutines. Neue
  Connectoren: `MessageSource` implementieren, in `ServiceLocator.connectorRegistry` registrieren,
  in `HubApplication` bei `isConfigured()` starten. Connector-Sync läuft nur im App-Prozess
  (kein Foreground-Service).

## UI-Konventionen (Compose)

- **`ui/hub/HubViewModel`** ist das Herz der Feed-Logik: kombiniert `FeedFilter` (Tab, Quellenfilter,
  Konversationsfilter, Gruppierung, Suchbegriff) via `flatMapLatest` zu einem Nachrichten-Flow.
  Beim Erweitern der Feed-Ansicht hier ansetzen. `combine` maximal 5-armig – bei mehr Flows
  in ein Datenobjekt bündeln (Muster: `FeedFilter`, verschachtelte `combine` mit Pair).
- **Posteingang-Semantik:** gelesene *Notification*-Nachrichten verschwinden (Triage), aber
  Nachrichten von API-Connectoren (`isNativeConnector = 1`) bleiben wie Chats (siehe `observeInbox`-JOIN).
- **Gruppenschlüssel** einer Nachricht = `conversationId` (falls gesetzt) sonst `sender`
  (`MessageEntity.groupValue()` / SQL `COALESCE(NULLIF(conversationId,''), sender)`).
- **Antwort-Routing** (`HubViewModel.replySourceFor`): nach `sourceKey` an Matrix/Telegram/SMS-Quelle
  oder den Notification-RemoteInput-Weg. Dieselbe Logik ViewModel-unabhängig in
  `notification/MessageReplyRouter` (für den Broadcast aus Android Auto).
- **Gesten der Feed-Zeile:** kurz = Antworten, doppelt = App öffnen, lang = Peek-Menü;
  Swipe rechts = gelesen, links = archivieren.
- **Theme:** `ui/theme/HubTheme(themeMode)` folgt `ThemeSettings.mode` (System/Hell/Dunkel, live).
- Deferred Compose-/Material-APIs brauchen `@OptIn(ExperimentalMaterial3Api::class)` bzw.
  `ExperimentalFoundationApi`. `by remember`/`collectAsState` brauchen `runtime.getValue`/`setValue`-Imports.

## Plattform-Fallstricke, die hier schon relevant waren

- **PendingIntents fremder Apps sind nicht persistierbar** – Quick-Reply-Actions (`QuickReplyRegistry`)
  und contentIntents (`ContentIntentRegistry`) sind rein in-memory; nach Notification-Entfernung/Neustart weg.
- **Notification-Trampolines (Android 12+):** ein fremder `contentIntent`, der über Broadcast/Service
  läuft, darf von uns keine Activity starten → `openMessage` nutzt ihn nur bei `isActivity`, sonst
  Launch-Intent der App.
- **„Nur Hub anzeigen"-Modus:** Listener entfernt Fremd-Notifications (`cancelNotification`) und postet
  eigene via `HubNotifier` (MessagingStyle + Reply/Read-Action → auch **Android Auto**). Beim
  Selbst-Entfernen (`reason == REASON_LISTENER_CANCEL`) die Registry **nicht** leeren, sonst stirbt
  Quick Reply. Ton hängt am Channel (Importance nachträglich nicht änderbar → neuer Channel bei
  eigenem Ton, siehe `SoundSettings`).
- **Standard-SMS-Rolle** verlangt **alle vier** Pflichtkomponenten (siehe Manifest: `SmsReceiver`,
  `MmsReceiver`, `HeadlessSmsSendService`, `ComposeSmsActivity`), sonst erscheint die App nicht in
  der Auswahl. Zum reinen **Senden** genügt `SEND_SMS` (nicht Standard-SMS-App sein).
- **Widget:** scrollbares Collection-Widget (`HubWidgetProvider` + `HubWidgetService`) – auf manchen
  One-UI-Versionen zickig. Der linke „–1"-Bildschirm (Discover/Samsung Free) ist für Fremd-Apps
  gesperrt – kein API-Weg, nicht nachrüstbar.

## Bewusste Grenzen (nicht „Bugs")

- **Matrix-E2EE** wird ohne Krypto-SDK nicht entschlüsselt → Platzhalter für `m.room.encrypted`.
- **IMAP** pollt (kein IDLE), kein OAuth2, Vordergrund-Abruf.
- Redigierte Notification-Inhalte (Sperrbildschirm-Einstellung) liefern nur Platzhalter – wird als
  `isContentRedacted` markiert statt als echte Nachricht ausgegeben.
