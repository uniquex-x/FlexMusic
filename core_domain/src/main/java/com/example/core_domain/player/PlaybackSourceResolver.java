package com.example.core_domain.player;

import androidx.annotation.NonNull;

import java.io.IOException;

public interface PlaybackSourceResolver {

    @NonNull
    ResolvedPlayableSource resolve(@NonNull PlaybackRequest request) throws IOException;
}
