# Reader

Android čtečka e-knih (EPUB) s předčítáním po větách (TTS), postavená na
[Readium Kotlin toolkit](https://github.com/readium/kotlin-toolkit).

## Funkce

- Import EPUB souborů a knihovna naimportovaných knih
- Čtení po kapitolách i stránkách, obsah (TOC)
- Zapamatování pozice čtení mezi otevřeními
- Předčítání textu po větách (Android TTS), rychlost 0.5×–3.0×
- Přehrávání na pozadí a při zhasnutém displeji (media session + notifikace)
- Tmavý motiv pro text knihy

## Tech stack

Kotlin, Jetpack Compose, Hilt, Room, DataStore, Readium Kotlin toolkit 3.3.0, AndroidX Media3.

## Sestavení

Otevřete projekt v Android Studiu (min. verze podporující AGP 9.0 / Kotlin 2.3.20) — Studio
si automaticky doplní `local.properties` s cestou k Android SDK. Minimální Android verze
podporovaná aplikací je Android 8.0 (API 26).
