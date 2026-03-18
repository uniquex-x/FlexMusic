package com.example.feature_player.player;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.core_domain.player.PlayerKernel;
import com.example.feature_player.coreplayer.PlayerJNI;

public final class FeaturePlayerFactory {

    private FeaturePlayerFactory() {
    }

    @NonNull
    public static PlayerKernel create(@NonNull Context context) {
        SoLibraryLoader.loadLibrary(context.getApplicationContext());
        PlayerJNI.initializeNativeRuntime(context.getApplicationContext().getFilesDir().getAbsolutePath());
        return new NativeBackedPlayerKernel(context.getApplicationContext());
    }
}
