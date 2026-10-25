# Ringenbeheer Android 1.0

Native Android-app in Kotlin. Geen internetrechten en geen externe backend.

## Functies
- Meerdere collecties, hernoemen en verwijderen wanneer leeg.
- Ringen met referentie, foto, gewicht, materiaal, aankoopprijs/-datum en opmerkingen.
- Verkoopstatus, verkoopprijs/-datum, gerealiseerde winst en omzet.
- Dashboard en overzicht per collectie, zoeken en sorteren.
- Lokale SQLite-opslag; JSON-back-up inclusief foto's via Android-bestandskiezer.

## APK bouwen
1. Installeer **Android Studio** met Android SDK 35 en JDK 17.
2. Open deze map als project.
3. Laat Gradle synchroniseren (internet nodig voor de eerste download van buildtools).
4. Kies **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. APK staat normaal in `app/build/outputs/apk/debug/app-debug.apk`.
6. Kopieer die APK naar de Android-telefoon en installeer hem.

De app zelf werkt offline. Er is in deze omgeving geen Android SDK/Gradle beschikbaar; de APK is daarom niet hier gecompileerd of op een toestel getest.

## Aandachtspunten
- De fotokiezer kan camera-apps aanbieden afhankelijk van het Android-toestel; de app gebruikt geen ingebouwde camerafunctie.
- Bewaar regelmatig een JSON-back-up buiten het toestel.
- Bij het wissen van appgegevens of deïnstalleren verdwijnen lokale gegevens.
- Winst = verkoopprijs minus aankoopprijs; geen verwerking van kosten, btw of belastingen.
