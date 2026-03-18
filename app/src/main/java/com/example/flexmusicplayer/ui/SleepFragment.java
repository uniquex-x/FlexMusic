package com.example.flexmusicplayer.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.flexmusicplayer.MainActivity;
import com.example.flexmusicplayer.R;
import com.example.flexmusicplayer.sleep.SleepPlaybackController;
import com.example.flexmusicplayer.sleep.SleepPlaybackState;
import com.example.flexmusicplayer.sleep.SleepRadioStation;
import com.example.flexmusicplayer.sleep.SleepSound;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.List;
import java.util.ArrayList;

public class SleepFragment extends Fragment implements SleepPlaybackController.Listener {

    private SleepPlaybackController sleepPlaybackController;

    private EditText searchInput;
    private TextView timerValueText;
    private SwitchCompat fadeOutSwitch;
    private MaterialButton defaultMixButton;
    private MaterialButtonToggleGroup timerToggleGroup;
    private MaterialCardView trackRainCard;
    private MaterialCardView trackOceanCard;
    private MaterialCardView trackWindCard;
    private MaterialCardView trackForestCard;
    private TextView trackRainValue;
    private TextView trackOceanValue;
    private TextView trackWindValue;
    private TextView trackForestValue;
    private LinearProgressIndicator trackRainProgress;
    private LinearProgressIndicator trackOceanProgress;
    private LinearProgressIndicator trackWindProgress;
    private LinearProgressIndicator trackForestProgress;
    private LinearLayout radioResultsContainer;
    private View radioResultsEmpty;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final List<SleepRadioStation> displayedStations = new ArrayList<>();
    private int searchRequestVersion = 0;
    private boolean searching = false;
    private final Runnable searchRunnable = this::requestStations;
    private String lastErrorMessage = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sleep, container, false);
        sleepPlaybackController = SleepPlaybackController.getInstance(requireContext());

        initViews(view);
        setupClickListeners();
        scheduleStationSearch(0L);
        onSleepStateChanged(sleepPlaybackController.getState());

        return view;
    }

    private void initViews(@NonNull View view) {
        view.findViewById(R.id.btn_settings).setOnClickListener(v -> openSettings());
        view.findViewById(R.id.reset_all_button).setOnClickListener(v -> resetAll());

        searchInput = view.findViewById(R.id.search_input);
        timerValueText = view.findViewById(R.id.timer_value_text);
        fadeOutSwitch = view.findViewById(R.id.fade_out_switch);
        defaultMixButton = view.findViewById(R.id.default_mix_button);
        timerToggleGroup = view.findViewById(R.id.timer_toggle_group);

        trackRainCard = view.findViewById(R.id.track_rain_card);
        trackOceanCard = view.findViewById(R.id.track_ocean_card);
        trackWindCard = view.findViewById(R.id.track_wind_card);
        trackForestCard = view.findViewById(R.id.track_forest_card);
        trackRainValue = view.findViewById(R.id.track_rain_value);
        trackOceanValue = view.findViewById(R.id.track_ocean_value);
        trackWindValue = view.findViewById(R.id.track_wind_value);
        trackForestValue = view.findViewById(R.id.track_forest_value);
        trackRainProgress = view.findViewById(R.id.track_rain_progress);
        trackOceanProgress = view.findViewById(R.id.track_ocean_progress);
        trackWindProgress = view.findViewById(R.id.track_wind_progress);
        trackForestProgress = view.findViewById(R.id.track_forest_progress);

        radioResultsContainer = view.findViewById(R.id.radio_results_container);
        radioResultsEmpty = view.findViewById(R.id.radio_results_empty);
    }

    private void setupClickListeners() {
        defaultMixButton.setOnClickListener(v -> {
            SleepPlaybackState state = sleepPlaybackController.getState();
            if (state.getSessionType() == SleepPlaybackState.SessionType.AMBIENCE
                    && state.getCurrentSound() == SleepSound.DEFAULT_MIX
                    && (state.isPlaying() || state.isLoading())) {
                sleepPlaybackController.stop();
            } else {
                sleepPlaybackController.playDefaultMix();
            }
        });

        trackRainCard.setOnClickListener(v -> toggleSound(SleepSound.RAIN));
        trackOceanCard.setOnClickListener(v -> toggleSound(SleepSound.OCEAN));
        trackWindCard.setOnClickListener(v -> toggleSound(SleepSound.WIND));
        trackForestCard.setOnClickListener(v -> toggleSound(SleepSound.FOREST));

        fadeOutSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> sleepPlaybackController.setFadeOutEnabled(isChecked));
        timerToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            if (checkedId == R.id.timer_button_15) {
                sleepPlaybackController.setTimerMinutes(15);
            } else if (checkedId == R.id.timer_button_30) {
                sleepPlaybackController.setTimerMinutes(30);
            } else if (checkedId == R.id.timer_button_60) {
                sleepPlaybackController.setTimerMinutes(60);
            }
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                scheduleStationSearch(300L);
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        sleepPlaybackController.addListener(this);
    }

    @Override
    public void onStop() {
        searchHandler.removeCallbacks(searchRunnable);
        sleepPlaybackController.removeListener(this);
        super.onStop();
    }

    private void openSettings() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openSettings();
        }
    }

    private void resetAll() {
        sleepPlaybackController.resetAll();
        timerToggleGroup.clearChecked();
        searchInput.setText("");
        scheduleStationSearch(0L);
    }

    private void toggleSound(@NonNull SleepSound sound) {
        SleepPlaybackState state = sleepPlaybackController.getState();
        if (state.getSessionType() == SleepPlaybackState.SessionType.AMBIENCE
                && state.getCurrentSound() == sound
                && state.isPlaying()) {
            sleepPlaybackController.stop();
            return;
        }
        sleepPlaybackController.playAmbience(sound);
    }

    private void renderStations() {
        if (radioResultsContainer == null) {
            return;
        }
        radioResultsContainer.removeAllViews();
        TextView emptyText = (TextView) radioResultsEmpty;
        if (searching && displayedStations.isEmpty()) {
            emptyText.setText(R.string.loading);
            radioResultsEmpty.setVisibility(View.VISIBLE);
            return;
        }
        emptyText.setText(R.string.sleep_fm_no_results);
        radioResultsEmpty.setVisibility(displayedStations.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        SleepPlaybackState state = sleepPlaybackController.getState();
        for (SleepRadioStation station : displayedStations) {
            View item = inflater.inflate(R.layout.item_sleep_entry, radioResultsContainer, false);
            TextView title = item.findViewById(R.id.entry_title);
            TextView subtitle = item.findViewById(R.id.entry_subtitle);
            TextView action = item.findViewById(R.id.entry_action);

            title.setText(station.getName());
            String sourceTag = station.isOfficial() ? getString(R.string.sleep_source_official) : getString(R.string.sleep_source_sample);
            subtitle.setText(station.getFrequency() + " · " + station.getSubtitle() + " · " + sourceTag);
            boolean isCurrent = state.getCurrentStation() != null
                    && station.getId().equals(state.getCurrentStation().getId())
                    && state.getSessionType() == SleepPlaybackState.SessionType.RADIO
                    && state.isPlaying();
            action.setText(isCurrent ? getString(R.string.sleep_playing) : getString(R.string.sleep_play));

            item.setOnClickListener(v -> sleepPlaybackController.toggleRadio(station));
            radioResultsContainer.addView(item);
        }
    }

    private void scheduleStationSearch(long delayMs) {
        searchHandler.removeCallbacks(searchRunnable);
        searchHandler.postDelayed(searchRunnable, delayMs);
    }

    private void requestStations() {
        if (!isAdded()) {
            return;
        }
        int requestVersion = ++searchRequestVersion;
        searching = true;
        renderStations();
        String query = searchInput != null ? searchInput.getText().toString().trim() : "";
        sleepPlaybackController.searchStations(query, (stations, errorMessage) -> {
            if (!isAdded() || requestVersion != searchRequestVersion) {
                return;
            }
            searching = false;
            displayedStations.clear();
            displayedStations.addAll(stations);
            renderStations();
            if (errorMessage != null) {
                maybeShowError(errorMessage);
            }
        });
    }

    @Override
    public void onSleepStateChanged(@NonNull SleepPlaybackState state) {
        if (!isAdded()) {
            return;
        }

        fadeOutSwitch.setChecked(state.isFadeOutEnabled());
        renderDefaultMixButton(state);
        renderTracks(state);
        renderTimer(state);
        renderStations();
        maybeShowError(state.getErrorMessage());
    }

    private void renderDefaultMixButton(@NonNull SleepPlaybackState state) {
        boolean active = state.getSessionType() == SleepPlaybackState.SessionType.AMBIENCE
                && state.getCurrentSound() == SleepSound.DEFAULT_MIX
                && (state.isPlaying() || state.isLoading());
        defaultMixButton.setText(active ? R.string.sleep_default_mix_stop : R.string.sleep_default_mix_play);
    }

    private void renderTracks(@NonNull SleepPlaybackState state) {
        updateTrackCard(trackRainCard, trackRainValue, trackRainProgress, state, SleepSound.RAIN, 65);
        updateTrackCard(trackOceanCard, trackOceanValue, trackOceanProgress, state, SleepSound.OCEAN, 40);
        updateTrackCard(trackWindCard, trackWindValue, trackWindProgress, state, SleepSound.WIND, 20);
        updateTrackCard(trackForestCard, trackForestValue, trackForestProgress, state, SleepSound.FOREST, 0);
    }

    private void updateTrackCard(@NonNull MaterialCardView card,
                                 @NonNull TextView valueText,
                                 @NonNull LinearProgressIndicator progressIndicator,
                                 @NonNull SleepPlaybackState state,
                                 @NonNull SleepSound sound,
                                 int defaultPercent) {
        boolean active = state.getSessionType() == SleepPlaybackState.SessionType.AMBIENCE
                && state.getCurrentSound() == sound
                && state.isPlaying();
        card.setStrokeColor(ContextCompat.getColor(requireContext(), active ? R.color.primary_500 : R.color.card_stroke));
        card.setStrokeWidth(active ? dpToPx(2) : dpToPx(1));
        card.setCardBackgroundColor(ContextCompat.getColor(requireContext(),
                active ? R.color.primary_50 : R.color.card_background_light));
        valueText.setText(active ? getString(R.string.sleep_playing) : defaultPercent + "%");
        progressIndicator.setProgress(active ? 100 : defaultPercent);
    }

    private void renderTimer(@NonNull SleepPlaybackState state) {
        if (state.getTimerRemainingSeconds() > 0) {
            timerValueText.setText(SleepPlaybackController.formatRemainingTime(state.getTimerRemainingSeconds()));
        } else {
            timerValueText.setText(R.string.sleep_timer_not_set);
            if (timerToggleGroup.getCheckedButtonId() != View.NO_ID) {
                timerToggleGroup.clearChecked();
            }
        }
    }

    private void maybeShowError(@Nullable String errorMessage) {
        if (errorMessage == null || errorMessage.equals(lastErrorMessage)) {
            return;
        }
        lastErrorMessage = errorMessage;
        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }
}
