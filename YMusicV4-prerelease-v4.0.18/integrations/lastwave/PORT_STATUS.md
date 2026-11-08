# LastWave port status

Copied from LastWave Native 5.1.2 into this Skeleton source tree:
- native audio/DSP/USB-output sources under `cpp/`
- lyrics models/providers/parsers under `lyrics/`
- LastWave's modern lyrics renderer under `ui/ModernLyricsView.kt`

These files are intentionally isolated from the YMusic build until their Android/KMP dependency surface is adapted. The active Skeleton player currently uses YMusic's playback service and a Skeleton-native lyrics surface backed by YMusic caption lines. This is the safe first bridge; the native output/DSP bridge is the next engineering stage.

Source is GPLv3; retain the upstream license and attribution when redistributing this tree.
