# Verteilung & Auto-Update (GitHub Releases + Obtainium)

Hub wird als **signierte Release-APK** über **GitHub Releases** verteilt. Die App
[Obtainium](https://github.com/ImranR98/Obtainium) prüft dein Repo und installiert Updates.

Der GitHub-Actions-Workflow (`.github/workflows/release.yml`) baut die APK bei jedem
Versions-Tag automatisch und hängt sie ans Release.

## Einmalige Einrichtung

### 1. Release-Keystore erzeugen
**Wichtig:** Immer denselben Keystore verwenden – Android installiert ein Update nur über
die alte Version, wenn die Signatur identisch ist. Keystore sicher aufbewahren (Verlust =
kein Update mehr möglich, nur Neuinstallation).

```bash
keytool -genkey -v -keystore hub-release.jks -keyalg RSA -keysize 2048 \
  -validity 10000 -alias hub
```

### 2. Als base64 kodieren (für das GitHub-Secret)
```bash
base64 -w0 hub-release.jks > hub-release.jks.base64
```

### 3. GitHub-Secrets anlegen
Per `gh` (wichtig: `printf` statt `echo`, sonst landet ein **Zeilenumbruch** im Secret
und die Signatur schlägt mit „Tag number over 30 is not supported" fehl):

```bash
base64 -w0 hub-release.jks           | gh secret set KEYSTORE_BASE64
printf '%s' 'DEIN_STORE_PASSWORT'    | gh secret set KEYSTORE_PASSWORD
printf '%s' 'DEIN_KEY_PASSWORT'      | gh secret set KEY_PASSWORD
printf '%s' 'hub'                    | gh secret set KEY_ALIAS
```

(Alternativ im Web: Repo → Settings → Secrets and variables → Actions → New repository
secret. Dann darauf achten, keine Leerzeichen/Zeilenumbrüche mitzukopieren.)

## Neue Version veröffentlichen
1. In `app/build.gradle.kts` **`versionCode` erhöhen** (Ganzzahl, muss steigen) und
   `versionName` anpassen (z. B. `0.2.0`).
2. Commit + Tag pushen:
   ```bash
   git commit -am "Release 0.2.0"
   git tag v0.2.0
   git push origin main --tags
   ```
3. GitHub Actions baut die signierte APK und legt automatisch das Release `v0.2.0`
   mit `hub-v0.2.0.apk` an.

## Obtainium einrichten (auf dem Telefon)
1. Obtainium installieren (F-Droid oder GitHub-Release von Obtainium).
2. „App hinzufügen" → die URL deines Hub-Repos eintragen.
3. Obtainium findet die Releases; Erstinstallation bestätigen.
4. Künftig meldet Obtainium neue Versionen und installiert sie mit einem Tipp
   (der System-Installationsdialog erscheint sideloading-bedingt immer).

## Hinweise
- **Lokaler Release-Build** (optional): dieselben Werte als `KEYSTORE_FILE`,
  `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` in `~/.gradle/gradle.properties` oder als
  Umgebungsvariablen setzen, dann `./gradlew assembleRelease`.
- Ohne Signatur-Werte bleibt der Release-Build **unsigniert** und ist nicht installierbar –
  für die Verteilung sind die Secrets also Pflicht.
- Der `versionCode` **muss** bei jeder Veröffentlichung steigen, sonst erkennt weder Android
  noch Obtainium das Update.
