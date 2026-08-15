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
- **Toolchain:** Kotlin `2.0.21` + `org.jetbrains.kotlin.plugin.compose` (kein
  `kotlinCompilerExtensionVersion` mehr), AGP `8.5.2`, Compose-BOM `2024.09.03` (Compose 1.7).
  Moderne APIs verfügbar: `Modifier.animateItem()`, `LinkAnnotation`/`withLink`, `PullToRefreshBox`.
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
- **In-App-Updater** (`update/UpdateManager`): prüft das neueste GitHub-Release, lädt die APK über den
  **System-DownloadManager** (überlebt Sperre/App-Wechsel, kein Timeout) und installiert per
  `UpdateDownloadReceiver` + FileProvider. Braucht `REQUEST_INSTALL_PACKAGES` (Nutzer-Freigabe).
- **Installation aus dem Hintergrund (Android 12+):** Ein `startActivity` aus dem
  `UpdateDownloadReceiver` ist gesperrt (Background-Activity-Launch). Deshalb postet
  `installDownloaded` eine **tippbare „Update bereit"-Benachrichtigung** *und* setzt ein
  „ausstehend"-Flag; `MainActivity.onResume`/nach dem Entsperren startet den Installer aus dem
  Vordergrund. Das Flag setzt sich per Versionsvergleich selbst zurück (kein erneutes Aufpoppen).

## Zentrale Architektur

Alle Quellen speisen in **denselben Feed** ein – die UI weiß nie, woher eine Nachricht stammt:

- **`data/source/MessageSource`** – gemeinsamer Vertrag für jede Quelle. Zwei Arten:
  1. *Push*: `NotificationMessageSource` (der `NotificationListenerService` schreibt selbst).
  2. *Pull*: API-Connectoren (`TelegramBotConnector`, `MatrixConnector`, `ImapConnector`) pollen in `start()`.
  Alle rufen `MessageIngestSink.ingest(IncomingMessage)`.
- **`data/repository/MessageRepository`** – einzige Quelle der Wahrheit für die UI **und** der
  `MessageIngestSink`. `ingest()` bewahrt bei gleicher `stableId = "sourceKey:externalId"` den
  Nutzerzustand (`isRead`/`isArchived`/`snoozeUntil`) **nur bei unverändertem Inhalt** (Re-Delivery);
  bei geändertem Inhalt gilt es als echte neue Nachricht → wieder ungelesen/aktiv. **Wichtig:** Der
  Notification-`externalId` ist bewusst nur `sbn.key` (ohne `postTime`) – sonst erzeugt jede
  Notification-Aktualisierung eine neue, ungelesene Zeile („gelesene tauchen wieder auf"-Bug).
  Gesendete Antworten werden via `recordOutgoing()` als `isOutgoing`-Nachricht im selben Verlauf
  abgelegt (rechtsbündige Blase). **Auto-Aufräumen gegen unbegrenztes Feed-Wachstum:**
  `pruneRead(retentionDays)` löscht **gelesene** Nachrichten älter als N Tage (ungelesene bleiben
  immer); Aufruf aus `MainActivity.onCreate` und bei Änderung von `NotificationSettings.retentionDays`
  (Default 7 Tage, 0 = aus). `clearAllMessages()` leert den kompletten Cache (Button in
  Einstellungen → Aufbewahrung) – behebt „alte Nachrichten bleiben nach Quelle-Deaktivieren stehen".
- **`data/local`** – Room, **SQLCipher-verschlüsselt** (`ServiceLocator.buildDatabase`, lädt native
  Lib via `System.loadLibrary("sqlcipher")`). DB ist reiner Cache → `fallbackToDestructiveMigration`;
  bei Schemaänderung nur `version` in `HubDatabase` erhöhen, keine Migration schreiben.
- **`di/ServiceLocator`** – bewusst **kein DI-Framework**. Manuelle Singletons (DB, Repository,
  Connectoren, `ConnectorRegistry`). Vom System instanziierte Klassen (Listener, Receiver, Widget)
  greifen hierüber statisch zu.
- **`connectors/ConnectorRegistry`** – startet/stoppt Pull-Connectoren als Coroutines (Registrierung
  per `sourceKey`). Neue Connectoren: `MessageSource` implementieren + registrieren.
- **`connectors/NotifyingIngestSink`** – dekoriert **nur** den Ingest-Weg der `ConnectorRegistry`
  (nicht das Repository direkt) und postet für **neue, frische, ungelesene** Connector-Nachrichten
  eine Hub-Benachrichtigung via `HubNotifier`. Fremd-App-Notifications laufen über den Listener und
  **nicht** hier durch → keine Doppel-Alarme. Frische-Fenster (10 min) verhindert einen
  Benachrichtigungs-Sturm beim IMAP-Backfill.
- **`connectors/ConnectorSyncService`** – Foreground-Service (Typ `dataSync`), der die Connector-
  Sync-Loops auch bei geschlossener App am Leben hält (leise „Hub läuft"-Notification, Android-Pflicht).
  Start aus `MainActivity`/nach Setup/`BootReceiver`, abschaltbar (`NotificationSettings.backgroundSyncEnabled`).
- **Mehrere IMAP-Konten:** ein `ImapConnector` **pro Konto**, `sourceKey = "imap:<accountId>"`;
  `ImapCredentialStore` hält mehrere Konten; `ServiceLocator.imapConnector(ctx, accountId)` registriert
  sie dynamisch.

## UI-Konventionen (Compose)

- **`ui/hub/HubViewModel`** ist das Herz der Feed-Logik: kombiniert `FeedFilter` (Tab, Quellenfilter,
  Konversationsfilter, Gruppierung, Suchbegriff) via `flatMapLatest` zu einem Nachrichten-Flow.
  Beim Erweitern der Feed-Ansicht hier ansetzen. `combine` maximal 5-armig – bei mehr Flows
  in ein Datenobjekt bündeln (Muster: `FeedFilter`, verschachtelte `combine` mit Pair).
- **Posteingang-Semantik:** gelesene *Notification*-Nachrichten verschwinden (Triage), aber
  Nachrichten von API-Connectoren (`isNativeConnector = 1`) bleiben wie Chats (siehe `observeInbox`-JOIN).
- **Gruppierung/Reiter:** Der Posteingang **und** jede Quellen-Auswahl (Matrix/Telegram/E-Mail als
  eigener Reiter, `HubFilterBar` + `SourceDrawer`) werden zu Unterhaltungen gruppiert
  (`showConversations`, `ConversationSummary`). Gruppenschlüssel = `conversationId` sonst `sender`
  (`MessageEntity.groupValue()` / SQL `COALESCE(NULLIF(conversationId,''), sender)`). Anzeigetitel:
  `conversationTitle` (z. B. via `MatrixConnector` aufgelöster Raumname) > 1:1-Absender > Gruppenschlüssel.
  Reiter haben Ungelesen-Badges (`sourceCounts`) und sind über `NotificationSettings.pinnedSources` anpinnbar.
- **Antwort-Routing** (`HubViewModel.replySourceFor`): nach `sourceKey` an Matrix/Telegram/SMS-Quelle
  oder den Notification-RemoteInput-Weg. Dieselbe Logik ViewModel-unabhängig in
  `notification/MessageReplyRouter` (für den Broadcast aus Android Auto).
- **Gesten der Feed-Zeile:** kurz = Antworten, doppelt = App öffnen, lang = Peek-Menü;
  Swipe rechts/links = **konfigurierbar** (`SwipeAction` Gelesen/Archivieren/Löschen/Nichts;
  Default rechts=gelesen, links=archivieren; mit Bestätigungs-Haptik). Config in
  `NotificationSettings.rightSwipeAction/leftSwipeAction`, live via `HubViewModel.swipeConfig`
  (`refreshSwipeConfig()` in einem `LifecycleResumeEffect`). Im Archiv wird „Archivieren" zu
  „Wiederherstellen". E-Mail-Zeilen
  (`sourceKey` `imap:*`) öffnen statt Antwort/App den `EmailReaderSheet`. In der gruppierten
  Ansicht: Konversationszeile **lang drücken = anpinnen/lösen** (`pinnedConversations`, pinned-first).
- **Theme:** `ui/theme/HubTheme(themeMode, dynamicColor)` folgt `ThemeSettings.mode` **und**
  `ThemeSettings.dynamicColor` (Material You / Wallpaper-Farben ab Android 12, beides live/geteilte
  `StateFlow`s, in `MainActivity` gesammelt).
- **Undo:** triage-/destruktive Aktionen (Archivieren/Löschen/Gelesen/Sammelaktionen, „Alle gelesen")
  laufen über `HubViewModel.undoEvents: SharedFlow<UndoRequest>` → „Rückgängig"-Snackbar in `HubScreen`
  (Löschen wird über eine Entity-Kopie via `restore()` wiederhergestellt). „Alle gelesen" gilt für die
  **aktuell gefilterte Ansicht** (`markVisibleRead()` über `uiState.messages`).
- **Weitere Aktionen:** Snooze (`snoozeUntil` + `SnoozeScheduler`/`SnoozeReceiver` via AlarmManager;
  smarte Ziele Heute-Abend/Morgen-früh/Nächste-Woche), Mehrfachauswahl (`SelectionState` im ViewModel,
  Sammelaktionen), pro-Absender-Ton, Konversation stummschalten – alles im Peek/Topbar.
- **Zeilen-Einfärbung:** ganze Zeile dezent in Quellenfarbe (`colorForSource` + `SOURCE_TINT_ALPHA`),
  statt buntem Punkt. E-Mail: `subject` separat vom `content` (voller Mailtext).
- **Matrix-Medien:** eingehende `m.audio`/`m.image` werden via `resolveMxc` (mxc:// → HTTPS-Download-URL
  mit Access-Token) auf `audioUri`/`imageUri` gemappt; Voice **senden** nur bei Matrix
  (`MatrixConnector.sendVoice`: Upload + `m.audio`), Aufnahme via `VoiceRecorder`.
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
  eigenem Ton, siehe `SoundSettings`). **`HubNotifier.post` ist der einzige Choke-Point** für Hubs
  eigene Benachrichtigungen (Ersetzen-Modus **und** `NotifyingIngestSink`) und erzwingt dort
  Quellen-Mute, **Konversations-Mute** und **Ruhezeiten** (stiller `IMPORTANCE_LOW`-Channel).
- **`notification/NotificationSettings`** ist der zentrale Prefs-Speicher für nicht-sensible Optionen:
  `mutedSources`, `mutedConversations`, `pinnedConversations` (Schlüssel `sourceKey\u0001groupValue`),
  `pinnedSources`, Ruhezeiten, `retentionDays`, `rightSwipeAction`/`leftSwipeAction`,
  `gestureHintDismissed`, `backgroundSyncEnabled`, `replaceOtherNotifications`. Such-Verlauf
  separat in `notification/SearchHistory`.
- **Standard-SMS-Rolle** verlangt **alle vier** Pflichtkomponenten (siehe Manifest: `SmsReceiver`,
  `MmsReceiver`, `HeadlessSmsSendService`, `ComposeSmsActivity`), sonst erscheint die App nicht in
  der Auswahl. Zum reinen **Senden** genügt `SEND_SMS` (nicht Standard-SMS-App sein).
- **Widget:** bewusst **kein** Collection-Widget (RemoteViewsService blieb auf One UI leer) – der
  `HubWidgetProvider` füllt feste Zeilen per `RemoteViews.addView` (nicht scrollbar, dafür zuverlässig).
  Der linke „–1"-Bildschirm (Discover/Samsung Free) ist für Fremd-Apps gesperrt – kein API-Weg.

## Bewusste Grenzen (nicht „Bugs")

- **Matrix-E2EE** wird ohne Krypto-SDK nicht entschlüsselt → Platzhalter für `m.room.encrypted`.
- **IMAP** pollt (kein IDLE), kein OAuth2, Vordergrund-Abruf.
- Redigierte Notification-Inhalte (Sperrbildschirm-Einstellung) liefern nur Platzhalter – wird als
  `isContentRedacted` markiert statt als echte Nachricht ausgegeben.
