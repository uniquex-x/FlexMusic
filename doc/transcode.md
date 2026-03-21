  ## Transcode Module Overview

The transcode feature is split across three layers:

- `app/src/main/java/com/example/flexmusicplayer/ui/TranscodeFragment.java`
  UI only. It handles file picking, result-state rendering, and post-success refresh of the local library.
- `feature_transcode/src/main/java/com/example/feature_transcode/`
  Java-side transcode domain for source staging, output-path selection, and JNI invocation.
- `native/transcode/` and `native/jni/bridge/TranscodeBridge.cpp`
  Native decode/encode pipeline and JNI bridge. The JNI layer stays thin and delegates to `AudioTranscoder`.

Do not move core transcode logic back into `app/`. `app/` should remain a UI shell that consumes `feature_transcode`.

## Current Flow

1. The user picks one audio file from `TranscodeFragment`.
2. `feature_transcode.TranscodeRepository` copies the source into `cache/transcode_input/` as a staged local file.
3. `feature_transcode.TranscodeBridge` calls the native transcoder.
4. The native pipeline decodes the staged source and encodes to the requested target format.
5. The output file is written to the app external music directory under `Music/transcoded/`.
6. On success, `TranscodeFragment` imports the output file into `LocalMusicStore` so it appears in the local library.

The result screen currently shows the active task only. Old placeholder cards under `Completed` and `Failed` were removed.

## Supported Output Formats

The UI exposes these target formats:

- `MP3`
- `FLAC`
- `OGG`
- `WAV`

Implementation notes:

- `OGG` is the Vorbis-in-Ogg output path.
- `WAV` is written directly from PCM output.
- `MP3` and `FLAC` depend on matching native third-party encoder libraries under `native/third_party/`.

If a target ABI does not have a compatible native encoder library, that format must be treated as unavailable for that ABI until the correct library is supplied. Do not document or expose a format as universally supported unless the corresponding native library set is verified for every shipped ABI.

## Storage and Local Library Semantics

- Transcoded outputs are stored under the app-scoped external music directory, not a public shared root.
- `LocalMusicStore` persists imported local songs by URI.
- Deleting a song from the Local page is a real file deletion:
  - `file://` URIs delete via the filesystem.
  - `content://` URIs delete via `DocumentsContract.deleteDocument()` with a `ContentResolver.delete()` fallback.
- A successful delete also removes the song from the local library record.

Keep Android scoped-storage behavior in mind when changing any part of this flow.

## Logging Expectations

Transcode-sensitive changes should preserve logs at these boundaries:

- transcode request start: source URI, target path, output format
- transcode success: final output path
- transcode failure: first failing stage and native error

Native transcode stage logs live in `native/transcode/AudioTranscoder.cpp`. Java-side repository logs live in `feature_transcode.TranscodeRepository`.
