package com.example.flexmusicplayer.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.flexmusicplayer.MainActivity;
import com.example.flexmusicplayer.R;

public class SleepFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sleep, container, false);
        view.findViewById(R.id.btn_settings).setOnClickListener(v -> openSettings());
        view.findViewById(R.id.reset_all_button).setOnClickListener(v -> { /* UI-only placeholder. */ });
        return view;
    }

    private void openSettings() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openSettings();
        }
    }
}
