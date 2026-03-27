# SoX-derived Audio Filters

This directory contains the playback-time audio-effect processor derived from
SoX biquad filter design logic.

- Upstream project: `https://github.com/chirlu/sox`
- Upstream revision used for reference: `42b3557e13e0fe01a83465b672d89faddbe65f49`
- Referenced upstream sources: `src/biquads.c`, `src/biquad.c`, `src/biquad.h`
- Original license: `LGPL-2.1-or-later`

Scope in FlexMusic:

- only realtime PCM filter design and processing needed for playback presets
- no SoX CLI, file formats, or general effects-chain runtime

Current presets:

- `OFF`
- `BASS_BOOST`
- `VOCAL_BOOST`
- `TREBLE_BOOST`
- `WARM`
- `BRIGHT`
- `ACOUSTIC`
- `PODCAST`
- `NIGHT`
