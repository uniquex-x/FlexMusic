package com.example.flexmusicplayer.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.flexmusicplayer.MainActivity;
import com.example.flexmusicplayer.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainPageFragment extends Fragment {

    private View searchBarContainer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main_page, container, false);

        ImageButton settingsButton = view.findViewById(R.id.btn_settings);
        ImageButton avatarButton = view.findViewById(R.id.btn_avatar);
        searchBarContainer = view.findViewById(R.id.search_bar_container);

        settingsButton.setOnClickListener(v -> openSettings());
        avatarButton.setOnClickListener(v -> {
            BottomNavigationView navigationView = requireActivity().findViewById(R.id.bottom_navigation);
            navigationView.setSelectedItemId(R.id.nav_my);
        });
        searchBarContainer.setOnClickListener(v -> openSearch());

        return view;
    }

    private void openSettings() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openSettings();
        }
    }

    private void openSearch() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openSearch();
        }
    }
}
