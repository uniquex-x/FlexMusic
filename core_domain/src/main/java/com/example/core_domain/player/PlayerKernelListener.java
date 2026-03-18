package com.example.core_domain.player;

import androidx.annotation.NonNull;

public interface PlayerKernelListener {

    void onSnapshotChanged(@NonNull PlayerKernelSnapshot snapshot);
}
