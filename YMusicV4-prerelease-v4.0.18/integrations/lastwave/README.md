# Skeleton × LastWave integration

Skeleton uses YMusic as the base Android/KMP application. This integration area is reserved for the LastWave GPLv3 audio/lyrics components that are being ported behind Skeleton's existing playback interfaces.

The first integrated layer is the LastWave-inspired player chrome and lyrics entry point. The native DSP/output engine remains isolated until its Media3/AudioTrack bridge is wired and tested on Android.
