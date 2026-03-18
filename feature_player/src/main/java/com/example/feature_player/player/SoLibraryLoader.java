package com.example.feature_player.player;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class SoLibraryLoader {

    public interface LibraryLoadDelegate {
        boolean loadLibrary(@NonNull String libraryName);
    }

    private static final int LOAD_LIBRARY_STATUS_IDLE = 0;
    private static final int LOAD_LIBRARY_STATUS_SUCCESS = 1;
    private static final int LOAD_LIBRARY_STATUS_FAIL = -1;

    // Register independent third-party .so names here before loading JNI bridge libs.
    private static final String[] THIRD_PARTY_LIBS = {
            "avutil",
            "swresample",
            "swscale",
            "avcodec",
            "avformat"
    };
    private static final String[] JNI_LIBS = {
            "flexmusic_player"
    };

    private static volatile int loadLibraryStatus = LOAD_LIBRARY_STATUS_IDLE;
    @Nullable
    private static volatile LibraryLoadDelegate libraryLoadDelegate;

    private SoLibraryLoader() {
    }

    public static void setLibraryLoadDelegate(@Nullable LibraryLoadDelegate delegate) {
        libraryLoadDelegate = delegate;
    }

    public static boolean isLoaded() {
        return loadLibraryStatus == LOAD_LIBRARY_STATUS_SUCCESS;
    }

    public static boolean loadLibrary(@NonNull Context context) {
        if (loadLibraryStatus == LOAD_LIBRARY_STATUS_SUCCESS) {
            return true;
        }
        synchronized (SoLibraryLoader.class) {
            if (loadLibraryStatus != LOAD_LIBRARY_STATUS_SUCCESS) {
                try {
                    loadLibraries(THIRD_PARTY_LIBS);
                    loadLibraries(JNI_LIBS);
                    loadLibraryStatus = LOAD_LIBRARY_STATUS_SUCCESS;
                } catch (UnsatisfiedLinkError e) {
                    loadLibraryStatus = LOAD_LIBRARY_STATUS_FAIL;
                } catch (RuntimeException e) {
                    loadLibraryStatus = LOAD_LIBRARY_STATUS_FAIL;
                }
            }
            return loadLibraryStatus == LOAD_LIBRARY_STATUS_SUCCESS;
        }
    }

    private static void loadLibraries(@NonNull String[] libraries) {
        for (String library : libraries) {
            loadSingleLibrary(library);
        }
    }

    private static void loadSingleLibrary(@NonNull String libraryName) {
        LibraryLoadDelegate delegate = libraryLoadDelegate;
        boolean loaded;
        if (delegate != null) {
            loaded = delegate.loadLibrary(libraryName);
            if (!loaded) {
                throw new UnsatisfiedLinkError("Delegate failed to load " + libraryName);
            }
        } else {
            System.loadLibrary(libraryName);
            loaded = true;
        }
    }
}
