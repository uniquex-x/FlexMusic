# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FlexMusic is a free music player for Android that supports online music playback, search, download, and audio transcoding (MP3, Vorbis, FLAC). The project follows Clean Architecture with native C++ media engine shared between player and transcoding features.

## Build Commands

### Gradle Build
```bash
# Build all modules
./gradlew build

# Build specific module
./gradlew :feature_player:build
./gradlew :core_player_sdk:build

# Clean and rebuild
./gradlew clean build

# Build debug APK
./gradlew :app:assembleDebug

# Build release APK
./gradlew :app:assembleRelease

# Run tests
./gradlew test
./gradlew :feature_player:test

# Android instrumented tests
./gradlew connectedAndroidTest
```

### Native Build
```bash
# CMake will be invoked automatically during Gradle build
# To clean native build only:
./gradlew clean
rm -rf app/.cxx
```

## Architecture

### Module Dependency Graph
```
app (仅依赖 feature 模块)
    |
     +-------------+-------------+-------------+
     |             |             |             |
feature-player  feature-transcode  feature-search  feature-download
     |             |             |             |
     +-------------+-------------+-------------+
                        |
              core-player-sdk (仅依赖 core-domain)
                        |
           +------------+------------+-----------+
           |            |            |           |
      core-domain   core-data    core-network  core-database
           |            |
           +------------+
                        |
                  native (.so 库)
```

### Android Modules

| Module | Purpose | Dependencies |
|--------|---------|--------------|
| app | Application shell (assembly only) | feature_* modules |
| feature_player | Player UI functionality | core_player_sdk, core_domain |
| feature_transcode | Transcode UI functionality | core_player_sdk, core_domain |
| feature_search | Search UI functionality | core_domain, core_network |
| feature_download | Download UI functionality | core_domain, core_data, core_network |
| core_player_sdk | Native SDK wrapper (pure JNI) | core_domain, native |
| core_domain | Domain layer (UseCase + Entity + Repository interfaces) | None |
| core_data | Data layer (Repository implementations + local storage) | core_domain, core_database |
| core_network | Network layer (API + network requests) | None |
| core_database | Database layer (Room) | None |

### Native Architecture

The native layer (app/src/main/native/) is organized with clear separation of concerns:

```
app/src/main/native/
├── core/                          # Infrastructure layer
│   ├── error/                     # Error handling (error_code.h)
│   ├── logger/                    # Logging system (logger.h)
│   ├── thread/                    # Thread pool and task queue (thread_pool.h)
│   ├── memory/                    # Memory management
│   └── utils/                     # Utility classes (time, string)
├── media/                         # Media abstraction layer
│   ├── demux/                     # Demuxing (MP3/FLAC/OGG)
│   ├── format/                    # Container format detection
│   ├── codec/                     # Codec interface (codec_interface.h)
│   └── packet/                    # Audio frame data (audio_packet.h)
├── player/                        # Player engine
│   ├── state/                     # State management (player_state.h)
│   ├── event/                     # Event bus
│   ├── audio_output/              # Audio output (AAudio/OpenSL ES)
│   ├── sync/                      # Synchronization control
│   └── playlist/                  # Playlist management
├── transcoder/                    # Transcoder engine
│   ├── pipeline/                  # Transcoding pipeline
│   ├── format/                    # Output format encapsulation
│   └── progress/                  # Transcoding progress
├── io/                            # I/O abstraction
│   ├── file/                      # File input
│   ├── network/                   # Network streaming (HTTP/HLS)
│   └── cache/                     # Caching mechanism
├── audio/                         # Audio processing
│   ├── resample/                  # Resampling
│   ├── filter/                    # Audio filters (EQ/gain)
│   └── mixer/                     # Mixing (reserved)
├── jni/                           # JNI bridge layer
│   ├── bridge/                    # Java interface implementations (player_bridge.h)
│   ├── manager/                   # Instance lifecycle management
│   ├── callback/                  # Callback mechanism
│   ├── jni_utils/                 # JNI utilities (reference management)
│   ├── adapter/                   # Object adaptation
│   └── version/                   # Version management (native_version.h)
└── third_party/                   # Third-party libraries
    ├── ffmpeg/                    # FFmpeg
    └── soxr/                      # High-quality resampling
```

## Key Design Principles

1. **Shared Native Media Core**: Player and transcoder share the same native media kernel (demux/decode/encode)
2. **JNI Abstraction**: Java layer does not directly depend on FFmpeg - all native interactions go through `core_player_sdk`
3. **Logical Separation**: Transcoder and player modules are logically isolated but share demuxing/decoding capabilities
4. **Replaceable Components**: Network, download, and player components can be independently replaced
5. **Extensibility**: Prepared for future features (streaming protocols, EQ, audio effects, visualization)

## Important Files

### Configuration
- `settings.gradle` - Module definitions
- `app/build.gradle` - App-level build configuration (minSdk 21, targetSdk 28, NDK 25.2.9519653)
- `app/src/main/native/CMakeLists.txt` - Native build configuration

### Core Native Headers
- `app/src/main/native/core/error/error_code.h` - Unified error codes
- `app/src/main/native/core/logger/logger.h` - Logging system (Android logcat integration)
- `app/src/main/native/core/thread/thread_pool.h` - Thread pool for async tasks
- `app/src/main/native/media/codec/codec_interface.h` - Codec abstraction
- `app/src/main/native/media/packet/audio_packet.h` - Audio frame data structure
- `app/src/main/native/player/state/player_state.h` - Player state machine
- `app/src/main/native/jni/version/native_version.h` - Native versioning
- `app/src/main/native/jni/bridge/player_bridge.h` - JNI player bridge

## Working with Native Code

### When to Modify Native Code
- Implementing audio processing features (filters, resampling, EQ)
- Fixing performance issues in player/transcoder
- Adding new audio format support
- Optimizing memory usage

### JNI Layer Rules
- JNI layer should ONLY bridge Java/Kotlin and C++
- No business logic in JNI - keep it in native `player/` or `transcoder/`
- All JNI code lives in `app/src/main/native/jni/`
- Always check version compatibility in `app/src/main/native/jni/version/native_version.h`

### Adding New Features

1. **New Audio Format**: Add format detection in `media/format/`, codec support in `media/codec/`
2. **New Audio Filter**: Implement in `audio/filter/`
3. **New Streaming Protocol**: Add to `io/network/`
4. **New Player Feature**: Add to `player/` with proper state management in `player/state/`

## Common Issues

- **Thread Pool**: Use `core/thread/thread_pool.h` for async operations in player/transcoder
- **Error Handling**: Always use error codes from `core/error/error_code.h`
- **Logging**: Use LOG macros from `core/logger/logger.h` for native logging
- **State Management**: Player state transitions must be handled through `player/state/`
- **Memory Management**: Audio frames use `media/packet/audio_packet.h` - don't allocate raw buffers for PCM data

## Testing

Run tests with `./gradlew test` or module-specific tests. Native unit tests should be added as the project grows (consider using Google Test framework).
