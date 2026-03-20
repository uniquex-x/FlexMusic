package com.example.core_network.auth;

import androidx.annotation.NonNull;

public class SupabaseStorageObjectDto {

    private final String objectPath;
    private final String publicUrl;

    public SupabaseStorageObjectDto(@NonNull String objectPath,
                                    @NonNull String publicUrl) {
        this.objectPath = objectPath;
        this.publicUrl = publicUrl;
    }

    @NonNull
    public String getObjectPath() {
        return objectPath;
    }

    @NonNull
    public String getPublicUrl() {
        return publicUrl;
    }
}
