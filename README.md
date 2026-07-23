# Reader

An Android e-book (EPUB) reader with sentence-level text-to-speech, built on the
[Readium Kotlin toolkit](https://github.com/readium/kotlin-toolkit).

## Features

- Import EPUB files into a personal library
- Chapter and page navigation, table of contents
- Reading position is remembered between sessions
- Sentence-by-sentence text-to-speech (Android TTS), speed 0.5×–3.0×
- Background playback, including with the screen off (media session + notification)
- Dark theme for the book text

## Tech stack

Kotlin, Jetpack Compose, Hilt, Room, DataStore, Readium Kotlin toolkit 3.3.0, AndroidX Media3.

## Building

Open the project in Android Studio (a version supporting AGP 9.0 / Kotlin 2.3.20) — Studio
will automatically fill in `local.properties` with your Android SDK path. The app supports
Android 8.0 (API 26) and up.
