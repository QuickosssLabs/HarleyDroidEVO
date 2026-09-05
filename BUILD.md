HarleyDroid EVO 3.0
===================

Open-source Harley-Davidson J1850 / CAN analyser for Android (ELM327 / HDI).
Evolution of the original HarleyDroid — "EVO" refers to this continued
line of development and nods to Harley-Davidson's Evolution engine.

Copyright and licence
---------------------

Copyright (C) 2010-2012 Stelian Pop <stelian@popies.net>
  — original HarleyDroid

Copyright (C) 2026 Quickosss
  — HarleyDroid EVO (maintenance, Android modernization, Material UI,
    dual-bus J1850/CAN, documentation)

Released under the GNU GPL v3 or later (see COPYING).  Quickosss
continues the project under the same licence while preserving Stelian
Pop's authorship and protocol work.

Original project: https://github.com/stelian42/HarleyDroid

Télécharger l’APK (GitHub Releases)
-----------------------------------

Les APK ne sont **pas** dans git. Ils sont publiés sur la page Releases :

    https://github.com/QuickosssLabs/HarleyDroidEVO/releases

Publier une nouvelle version :

1. Mettre à jour `versionName` / `versionCode` dans `app/build.gradle` si besoin.
2. Commit + push sur `main`.
3. Créer et pousser un tag sémantique :

       git tag v3.0-EVO
       git push origin v3.0-EVO

4. Le workflow `.github/workflows/release.yml` compile l’APK et crée
   automatiquement la Release GitHub avec le fichier joint
   (`HarleyDroidEVO-<tag>-debug.apk`).

Compiler localement
-------------------

Prérequis : JDK 17+, Android SDK (API 35).

    gradlew.bat assembleDebug
    gradlew.bat test

APK :

    app\build\outputs\apk\debug\app-debug.apk

    adb install -r app\build\outputs\apk\debug\app-debug.apk

Fonctionnalités EVO
-------------------

- Modernisation Android : targetSdk / compileSdk 35, minSdk 21, JDK 17
- UI Material 3 (toolbar, thème, réglages PreferenceFragmentCompat)
- Jauges graphiques historiques conservées
- Bluetooth : SPP UUID puis fallback réflexion
- Permissions différées (BT à la connexion, GPS si logging GPS)
- Export / partage des logs (.log.gz) via FileProvider
- Notification enrichie (RPM / vitesse)
- Mode simulation : option dans Préférences (données J1850 ou CAN factices, sans moto)
- Bus dual : J1850 (4 pins) ou CAN / HDLAN (6 pins) — choix dans Préférences
- Strings FR / EN pour les nouvelles options
- Détails protocole et changelog : voir README

Bus J1850 / CAN
---------------

Dans Préférences → **Type de bus** :

- **J1850 (4 pins)** — comportement historique (ELM327 `ATSP2` + `ATMA`)
- **CAN / HDLAN (6 pins)** — lecture passive (ELM327 `ATSP6` + `CAF0` + `ATMA`)

CAN V1 = télémétrie tableau de bord uniquement (vitesse, odo, température, etc.).
Les diagnostics actifs (DTC / VIN) restent réservés au J1850.

Matériel CAN : ELM327 **compatible CAN 500 kbit/s** + câble Deutsch 6 pins.
L’interface HDI est J1850-only ; en mode CAN l’app bascule automatiquement sur ELM327.

Les layouts d’octets CAN varient selon modèles / années — calibrer via logs bruts.

Permissions / confidentialité
-----------------------------

HarleyDroid EVO utilise Bluetooth pour parler à l’interface ELM327.
La localisation n’est demandée que si le logging GPS est activé.
Aucune donnée n’est envoyée sur Internet.

Trames J1850 connues (extrait)
------------------------------

    28 1b 10 02 xx xx : RPM (xxxx / 4)
    48 29 10 02 xx xx : vitesse (xxxx / 128 km/h)
    a8 3b 10 03 xx    : rapport
    a8 69 10 06 xx xx : odomètre
    a8 83 10 0a xx xx : conso carburant

Voir README pour la liste complète et le format de log CSV.

Licence : GPL v3 (COPYING).
Auteur d’origine : Stelian Pop.
HarleyDroid EVO : Quickosss.
