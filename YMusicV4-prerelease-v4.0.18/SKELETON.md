# Skeleton Music

Skeleton is the merged direction built on YMusic V4, with a LastWave-inspired player experience.

## Current integration
- YMusic V4 remains the playback/download/library foundation.
- App branding is now Skeleton.
- AMOLED-friendly purple/black accent system.
- Skeleton player chrome in the Now Playing bar.
- Direct lyrics entry from the Now Playing screen using the current track metadata.
- Existing YMusic download pipeline is preserved.
- LastWave integration boundary is documented under `integrations/lastwave`.

## Next native integration boundary
LastWave's native Oboe/DSP/output layer is intentionally kept isolated until it is connected to YMusic's Media3 playback path with an Android-tested audio sink. This avoids shipping an unverified second playback engine or breaking background playback.

Both upstream projects are GPLv3; keep their license and attribution files with redistributed source.
