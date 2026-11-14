# Sepia Music

**Sepia Music** is a free, open-source, ad-free music player for Android. It finds and streams full songs
from YouTube Music in any language, plays in the background, downloads for offline listening and keeps
your playlists and likes - with a warm sepia look.

## Features

- Full-length songs in any language you can find on YouTube Music
- Search suggestions as you type, plus a **Top result** card and songs that only exist as videos
- **Radio**: tap a song and Sepia keeps playing similar songs (not the same song again), like YT Music
- Swipe the mini player (left = next, right = previous, down = close) and the artwork in the full player
- Five full-screen player styles: Sepia, Apple Music, Wavy, YT Music and Spotify
- Light, dark or match-the-phone theme, with a liquid-glass navigation bar and mini player
- Downloads (Wi-Fi only option), stream cache, export and import of playlists
- Notification and lock-screen controls
- No ads and no account

Built with Kotlin, Jetpack Compose, Media3 (ExoPlayer), Room, WorkManager and the NewPipe extractor.

## Download

Ready-made APK files are on the **Releases** page of this repository.
Download `Sepia.apk` (the optimised version) and allow "install unknown apps" when Android asks.

## Build it yourself

Open this folder in Android Studio (Ladybug or newer), let it sync, and press Run.
Or: `gradle :app:assembleRelease` (Gradle 8.10, JDK 17). The APK appears in `app/build/outputs/apk/`.

## If a song stops playing

YouTube changes things from time to time. Raise the version of
`com.github.TeamNewPipe:NewPipeExtractor` in `app/build.gradle.kts` to the newest release and rebuild.
You can also paste a Piped-compatible server address in Settings as a backup source.

## License

GPL-3.0-or-later, because Sepia Music uses NewPipe Extractor (GPL-3.0). See the `LICENSE` file.

## Disclaimer

Sepia Music is not affiliated with YouTube or Google. Streaming and downloading from YouTube is against
YouTube's terms, so apps like this are shared through GitHub or F-Droid rather than the Play Store.
Music belongs to its artists - please use downloads for personal listening only.

## Made by

Instagram: [@_ur_._adii_](https://instagram.com/_ur_._adii_)

Keywords: sepia music, music player, android music app, youtube music, newpipe, offline music, open source.
