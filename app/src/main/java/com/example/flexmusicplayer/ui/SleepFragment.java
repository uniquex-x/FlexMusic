package com.example.flexmusicplayer.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
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
import com.example.flexmusicplayer.sleep.SleepRecentEntry;
import com.example.flexmusicplayer.sleep.SleepSound;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.List;

public class SleepFragment extends Fragment implements SleepPlaybackController.Listener {

    private SleepPlaybackController sleepPlaybackController;

    private EditText searchInput;
    private TextView timerValueText;
    private TextView featureTitleText;
    private TextView featureSubtitleText;
    private ImageButton featurePlayButton;
    private SwitchCompat fadeOutSwitch;
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
    private MaterialCardView radioCategoryLightMusic;
    private MaterialCardView radioCategoryMeditation;
    private MaterialCardView radioCategoryAsmr;
    private LinearLayout recentSessionsContainer;
    private LinearLayout radioResultsContainer;
    private View radioResultsEmpty;

    private String selectedCategory = "";
    private String lastErrorMessage = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sleep, container, false);
        sleepPlaybackController = SleepPlaybackController.getInstance(requireContext());

        initViews(view);
        setupClickListeners();
        renderRecents();
        renderStations();
        onSleepStateChanged(sleepPlaybackController.getState());

        return view;
    }

    private void initViews(@NonNull View view) {
        view.findViewById(R.id.btn_settings).setOnClickListener(v -> openSettings());
        view.findViewById(R.id.reset_all_button).setOnClickListener(v -> resetAll());

        searchInput = view.findViewById(R.id.search_input);
        timerValueText = view.findViewById(R.id.timer_value_text);
        featureTitleText = view.findViewById(R.id.sleep_feature_title_text);
        featureSubtitleText = view.findViewById(R.id.sleep_feature_subtitle_text);
        featurePlayButton = view.findViewById(R.id.feature_play_button);
        fadeOutSwitch = view.findViewById(R.id.fade_out_switch);
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

        radioCategoryLightMusic = view.findViewById(R.id.radio_category_light_music);
        radioCategoryMeditation = view.findViewById(R.id.radio_category_meditation);
        radioCategoryAsmr = view.findViewById(R.id.radio_category_asmr);

        recentSessionsContainer = view.findViewById(R.id.recent_sessions_container);
        radioResultsContainer = view.findViewById(R.id.radio_results_container);
        radioResultsEmpty = view.findViewById(R.id.radio_results_empty);
    }

    private void setupClickListeners() {
        featurePlayButton.setOnClickListener(v -> {
            SleepPlaybackState state = sleepPlaybackController.getState();
            if (state.isPlaying() || state.isLoading()) {
                sleepPlaybackController.stop();
            } else {
                sleepPlaybackController.playDefaultMix();
            }
        });

        trackRainCard.setOnClickListener(v -> toggleSound(SleepSound.RAIN));
        trackOceanCard.setOnClickListener(v -> toggleSound(SleepSound.OCEAN));
        trackWindCard.setOnClickListener(v -> toggleSound(SleepSound.WIND));
        trackForestCard.setOnClickListener(v -> toggleSound(SleepSound.FOREST));

        radioCategoryLightMusic.setOnClickListener(v -> toggleCategory(getString(R.string.sleep_radio_light_music)));
        radioCategoryMeditation.setOnClickListener(v -> toggleCategory(getString(R.string.sleep_radio_meditation)));
        radioCategoryAsmr.setOnClickListener(v -> toggleCategory(getString(R.string.sleep_radio_asmr)));

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
                renderStations();
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
        selectedCategory = "";
        updateCategorySelection();
        renderStations();
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

    private void toggleCategory(@NonNull String category) {
        if (category.equals(selectedCategory)) {
            selectedCategory = "";
        } else {
            selectedCategory = category;
        }
        updateCategorySelection();
        renderStations();
    }

    private void updateCategorySelection() {
        updateCategoryCard(radioCategoryLightMusic, getString(R.string.sleep_radio_light_music).equals(selectedCategory));
        updateCategoryCard(radioCategoryMeditation, getString(R.string.sleep_radio_meditation).equals(selectedCategory));
        updateCategoryCard(radioCategoryAsmr, getString(R.string.sleep_radio_asmr).equals(selectedCategory));
    }

    private void updateCategoryCard(@NonNull MaterialCardView cardView, boolean selected) {
        int strokeColor = ContextCompat.getColor(requireContext(), selected ? R.color.primary_500 : R.color.card_stroke);
        int backgroundColor = ContextCompat.getColor(requireContext(), selected ? R.color.primary_50 : R.color.card_background_light);
        cardView.setStrokeColor(strokeColor);
        cardView.setCardBackgroundColor(backgroundColor);
        cardView.setStrokeWidth(selected ? dpToPx(2) : dpToPx(1));
    }

    private void renderStations() {
        if (radioResultsContainer == null) {
            return;
        }
        String query = searchInput != null ? searchInput.getText().toString().trim() : "";
        List<SleepRadioStation> stations = sleepPlaybackController.searchStations(query, selectedCategory);
        radioResultsContainer.removeAllViews();
        radioResultsEmpty.setVisibility(stations.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        SleepPlaybackState state = sleepPlaybackController.getState();
        for (SleepRadioStation station : stations) {
            View item = inflater.inflate(R.layout.item_sleep_entry, radioResultsContainer, false);
            TextView title = item.findViewById(R.id.entry_title);
            TextView subtitle = item.findViewById(R.id.entry_subtitle);
            TextView action = item.findViewById(R.id.entry_action);

            title.setText(station.getName());
            String sourceTag = station.isOfficial() ? getString(R.string.sleep_source_official) : getString(R.string.sleep_source_sample);
            subtitle.setText(station.getSubtitle() + " · " + sourceTag);
            boolean isCurrent = state.getCurrentStation() != null
                    && station.getId().equals(state.getCurrentStation().getId())
                    && state.getSessionType() == SleepPlaybackState.SessionType.RADIO
                    && state.isPlaying();
            action.setText(isCurrent ? getString(R.string.sleep_playing) : getString(R.string.sleep_play));

            item.setOnClickListener(v -> {
                SleepPlaybackState currentState = sleepPlaybackController.getState();
                boolean alreadyPlaying = currentState.getCurrentStation() != null
                        && station.getId().equals(currentState.getCurrentStation().getId())
                        && currentState.getSessionType() == SleepPlaybackState.SessionType.RADIO
                        && currentState.isPlaying();
                if (alreadyPlaying) {
                    sleepPlaybackController.stop();
                } else {
                    sleepPlaybackController.playRadio(station);
                }
            });
            radioResultsContainer.addView(item);
        }
    }

    private void renderRecents() {
        if (recentSessionsContainer == null) {
            return;
        }
        recentSessionsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        List<SleepRecentEntry> entries = sleepPlaybackController.getRecentEntries();
        if (entries.isEmpty()) {
            View item = inflater.inflate(R.layout.item_sleep_entry, recentSessionsContainer, false);
            TextView title = item.findViewById(R.id.entry_title);
            TextView subtitle = item.findViewById(R.id.entry_subtitle);
            TextView action = item.findViewById(R.id.entry_action);
            title.setText(getString(R.string.sleep_recent_title));
            subtitle.setText(getString(R.string.sleep_recent_empty));
            action.setText("");
            item.setOnClickListener(null);
            recentSessionsContainer.addView(item);
            return;
        }

        for (SleepRecentEntry entry : entries) {
            View item = inflater.inflate(R.layout.item_sleep_entry, recentSessionsContainer, false);
            TextView title = item.findViewById(R.id.entry_title);
            TextView subtitle = item.findViewById(R.id.entry_subtitle);
            TextView action = item.findViewById(R.id.entry_action);

            title.setText(entry.getTitle());
            CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(
                    entry.getPlayedAt(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS);
            subtitle.setText(entry.getSubtitle() + " · " + relativeTime);
            action.setText(getString(R.string.sleep_play));
            item.setOnClickListener(v -> sleepPlaybackController.playRecent(entry));
            recentSessionsContainer.addView(item);
        }
    }

    @Override
    public void onSleepStateChanged(@NonNull SleepPlaybackState state) {
        if (!isAdded()) {
            return;
        }

        fadeOutSwitch.setChecked(state.isFadeOutEnabled());
        renderFeature(state);
        renderTracks(state);
        renderTimer(state);
        renderRecents();
        renderStations();
        maybeShowError(state.getErrorMessage());
    }

    private void renderFeature(@NonNull SleepPlaybackState state) {
        String title = getString(R.string.sleep_default_mix_title);
        String subtitle = getString(R.string.sleep_session_idle);

        if (state.getSessionType() == SleepPlaybackState.SessionType.AMBIENCE && state.getCurrentSound() != null) {
            title = resolveSoundTitle(state.getCurrentSound());
            subtitle = resolveSoundSubtitle(state.getCurrentSound());
        } else if (state.getSessionType() == SleepPlaybackState.SessionType.RADIO && state.getCurrentStation() != null) {
            title = state.getCurrentStation().getName();
            subtitle = state.getCurrentStation().getSubtitle();
        }

        if (state.getTimerRemainingSeconds() > 0) {
            subtitle = getString(
                    R.string.sleep_session_timer,
                    SleepPlaybackController.formatRemainingTime(state.getTimerRemainingSeconds()));
            if (state.getVolumeScale() < 1f) {
                subtitle = subtitle + " · " + getString(
                        R.string.sleep_session_volume,
                        Math.round(state.getVolumeScale() * 100f));
            }
        }

        if (state.isLoading()) {
            subtitle = getString(R.string.loading);
        }

        featureTitleText.setText(title);
        featureSubtitleText.setText(subtitle);
        featurePlayButton.setImageResource((state.isPlaying() || state.isLoading()) ? R.drawable.ic_pause : R.drawable.ic_play);
        featurePlayButton.setContentDescription(getString((state.isPlaying() || state.isLoading()) ? R.string.sleep_stop : R.string.play));
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

    @NonNull
    private String resolveSoundTitle(@NonNull SleepSound sound) {
        if (sound == SleepSound.DEFAULT_MIX) {
            return getString(R.string.sleep_default_mix_title);
        }
        if (sound == SleepSound.RAIN) {
            return getString(R.string.sleep_track_rain);
        }
        if (sound == SleepSound.OCEAN) {
            return getString(R.string.sleep_track_ocean);
        }
        if (sound == SleepSound.WIND) {
            return getString(R.string.sleep_track_wind);
        }
        return getString(R.string.sleep_track_forest);
    }

    @NonNull
    private String resolveSoundSubtitle(@NonNull SleepSound sound) {
        if (sound == SleepSound.DEFAULT_MIX) {
            return getString(R.string.sleep_default_mix_subtitle);
        }
        return getString(R.string.sleep_offline_ready);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }
}
