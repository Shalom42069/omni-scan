# OmniScan

Ein **Umgebungs-Scanner für Android** (getestet als Ziel: OnePlus 15) — bündelt alles,
was **ohne Root** über die offiziellen Android-APIs erfasst werden kann:

| Tab | Was gescannt wird | Nötige Freigabe |
|-----|-------------------|-----------------|
| **WLAN** | Access Points: SSID, BSSID, RSSI, Band, Kanal, Frequenz, Security (WEP/WPA/WPA2/WPA3), rohe Capabilities | Standort bzw. `NEARBY_WIFI_DEVICES` |
| **Bluetooth** | Classic-Discovery **+** BLE-Advertising, zusammengeführt: Name, MAC, RSSI, Typ, Bindung, Hersteller-Daten, Service-UUIDs | `BLUETOOTH_SCAN` |
| **NFC** | Tags bei Antippen (13,56 MHz): UID, Tech-Liste, NDEF-Records (Text/URI/MIME), Kapazität, beschreibbar | `NFC` (System) |
| **Mobilfunk** | Dienende + Nachbarzellen: NR(5G)/LTE/WCDMA/GSM/CDMA, MCC/MNC/TAC/CI/PCI/ARFCN, Signalpegel | `READ_PHONE_STATE` + Standort |
| **Standort** | Position (Lat/Lon/Höhe/Genauigkeit/Speed) **+** Live-GNSS-Satelliten (GPS, GLONASS, Galileo, BeiDou, QZSS, SBAS, NavIC) mit C/N0, Elevation, Azimut, im-Fix | `ACCESS_FINE_LOCATION` |
| **USB** | Per USB-OTG angeschlossene Geräte: VID/PID, Hersteller/Produkt, Geräteklasse, Interfaces | keine |
| **Sensoren** | Alle verbauten Sensoren: Name, Hersteller, Typ, Messbereich, Auflösung, Stromaufnahme, Wake-Up | keine |
| **Ports** | TCP-Connect-Portscanner (Common-Ports oder eigener Bereich), Banner-Grabbing | `INTERNET` |
| **GATT** | BLE-GATT-Explorer: verbinden per MAC, Services/Characteristics auslesen, lesen/schreiben/Notify wo erlaubt | `BLUETOOTH_CONNECT` |
| **Security** | Hash (MD5/SHA1/SHA256/SHA512), Base64/Hex/URL-Encoding, JWT-Decoder, TLS-Zertifikats-Inspektor, App-Permission-Auditor | `INTERNET`, `QUERY_ALL_PACKAGES` |
| **VPN** | Per-App-Traffic-Monitor: welche App verbindet wohin (IP:Port, Bytes) — läuft als lokales VPN | `BIND_VPN_SERVICE` (Nutzer-Dialog) |

## Ehrliche Grenzen (Hardware, nicht Software)

- **125-kHz-RFID** (EM4100, HID Prox, viele alte Zutritts-Fobs) kann ein Handy **nicht** lesen —
  das NFC-Frontend arbeitet ausschließlich auf **13,56 MHz**. Dafür bräuchtest du externe
  Hardware (z. B. PN532/Proxmark über USB/BLE) — genau der Punkt, an dem deine ESP32-/Flipper-
  Projekte ansetzen würden.
- **WLAN-Scans** sind ab Android 9 systemseitig gedrosselt (wenige Scans pro 2 Minuten).
  Die App zeigt deshalb bei häufigem Tippen die zuletzt gecachten Ergebnisse.
- **BT-MAC-Adressen** sind bei modernen Geräten oft randomisiert (Privacy).
- Ohne Root gibt es **kein** Monitor-Mode/Packet-Capture, kein Deauth, kein Raw-802.11.
- Der **Portscanner** ist ein TCP-Connect-Scan (kein SYN-Scan) — langsamer und leichter erkennbar, aber
  root-frei möglich, weil er normale `Socket`-Verbindungen statt Raw-Sockets nutzt.
- Der **VPN-Traffic-Monitor** implementiert einen vereinfachten User-Space-TCP/UDP-NAT-Stack
  (kein root, kein Kernel-Modul) — UDP wird vollständig weitergeleitet, TCP ohne eigene
  Retransmission-Timer/Congestion-Control. Für normale Mobil-/WLAN-Verbindungen funktioniert das
  gut, ist aber kein RFC-vollständiger TCP-Stack (kein Ersatz für z. B. lwIP/tun2socks). Nur ein
  VPN kann gleichzeitig aktiv sein — andere VPN-Apps müssen während der Nutzung deaktiviert sein.
- Der **App-Permission-Auditor** zeigt nur eine kuratierte Liste bekannter "dangerous"-Permissions,
  keine vollständige Android-Referenzliste (die sich je Version ändert).

## Bauen

**Variante A — Android Studio (empfohlen):**
1. Ordner in Android Studio öffnen (*Open*).
2. Gradle-Sync abwarten (lädt SDK-Komponenten + Gradle-Wrapper automatisch).
3. Grünes *Run* auf verbundenem Gerät, oder *Build → Build Bundle(s)/APK(s) → Build APK(s)*.

**Variante B — Kommandozeile:**
```bash
# einmalig, falls kein gradlew vorhanden:
gradle wrapper --gradle-version 8.9
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```
Voraussetzungen: JDK 17, Android SDK (Platform 35 + Build-Tools 35), `local.properties`
mit `sdk.dir=...` (legt Android Studio automatisch an).

## Installieren
`app-debug.apk` aufs Handy kopieren und öffnen (Installation aus unbekannten Quellen erlauben),
oder `adb install -r app-debug.apk`.

## Projektstruktur
```
app/src/main/java/com/omniscan/app/
├─ MainActivity.kt          Tabs, Permission-Flow, NFC-Reader-Lifecycle
├─ model/Models.kt          Datenklassen aller Scan-Ergebnisse
├─ scan/                    je ein Scanner pro Domäne (StateFlow-basiert)
│   ├─ Permissions.kt       versionsabhängige Runtime-Permissions
│   ├─ WifiScanner.kt  BluetoothScanner.kt  NfcReader.kt
│   ├─ CellScanner.kt  LocationScanner.kt   UsbScanner.kt  SensorScanner.kt
└─ ui/                      Compose (Material3): Theme, Components, Screens, ViewModel
```

Min SDK 30 (Android 11), Target SDK 34 (Android 14), Kotlin 2.0 + Jetpack Compose (Material2 — Material3
war offline nicht vollständig cachebar, siehe Build-Historie).

## Pentesting-/Security-Features (root-frei)

Zusätzlich zu den reinen Scannern enthält OmniScan vier root-freie Security-Werkzeuge:

1. **Port-Scanner** (`Ports`-Tab) — TCP-Connect-Scan gegen einen Host, Common-Ports oder eigener
   Bereich, mit einfachem Banner-Grabbing.
2. **BLE-GATT-Explorer** (`GATT`-Tab) — verbindet sich zu einem BLE-Gerät per MAC-Adresse, listet
   alle Services/Characteristics mit ihren Properties und erlaubt Lesen/Schreiben/Notify-Abo.
3. **Security-Toolbox** (`Security`-Tab) — Hash-Rechner (MD5/SHA1/SHA256/SHA512), Base64/Hex/URL-
   Encoding, JWT-Decoder (Header/Payload, unverifiziert), TLS-Zertifikats-Inspektor (Kette, Gültigkeit,
   Fingerprint), App-Permission-Auditor (installierte Apps + ihre gewährten/verweigerten Dangerous-
   Permissions).
4. **VPN-Traffic-Monitor** (`VPN`-Tab) — protokolliert pro App, wohin sie verbindet (IP:Port,
   übertragene Bytes), über einen root-freien lokalen VpnService.
