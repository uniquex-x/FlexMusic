package com.example.flexmusicplayer.sleep;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.core_data.radio.RadioRepository;
import com.example.core_data.sleep.SleepAudioRepository;
import com.example.core_domain.radio.RadioStation;
import com.example.core_domain.sleep.SleepAudioPlaybackSource;
import com.example.flexmusicplayer.R;
import com.example.flexmusicplayer.model.PlayerState;
import com.example.flexmusicplayer.model.Song;
import com.example.flexmusicplayer.player.PlaybackController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SleepPlaybackController implements PlaybackController.Listener {

    private static final String TAG = "SleepPlaybackController";
    private static final String SLEEP_AMBIENCE_SOURCE_PREFIX = "sleep_ambience:";

    public interface Listener {
        void onSleepStateChanged(@NonNull SleepPlaybackState state);
    }

    public interface SearchCallback {
        void onSearchResult(@NonNull List<SleepRadioStation> stations, @Nullable String errorMessage);
    }

    private static SleepPlaybackController instance;

    private final Context appContext;
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Set<Listener> listeners = new LinkedHashSet<>();
    private final PlaybackController playbackController;
    private final RadioRepository radioRepository;
    private final SleepAudioRepository sleepAudioRepository;
    private final ExecutorService radioSearchExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService ambienceExecutor = Executors.newSingleThreadExecutor();
    private final SleepPlaybackState state = new SleepPlaybackState();
    @Nullable
    private SleepPlaybackState lastDispatchedState;
    private long ambiencePrepareGeneration = 0L;
    @NonNull
    private PlayerState.RepeatMode repeatModeBeforeSleep = PlayerState.RepeatMode.OFF;
    private boolean sleepManagedRepeatMode;
    private final Runnable timerTicker = new Runnable() {
        @Override
        public void run() {
            synchronized (SleepPlaybackController.this) {
                long timerEndAtMs = state.getTimerEndAtMs();
                if (timerEndAtMs <= 0L) {
                    state.setTimerRemainingSeconds(0);
                    state.setVolumeScale(1f);
                    applyVolume();
                    dispatchState();
                    return;
                }

                long remainingMs = Math.max(0L, timerEndAtMs - System.currentTimeMillis());
                int remainingSeconds = (int) Math.ceil(remainingMs / 1000f);
                state.setTimerRemainingSeconds(remainingSeconds);
                float volumeScale = 1f;
                if (state.isFadeOutEnabled() && remainingSeconds <= 30) {
                    volumeScale = Math.max(remainingSeconds / 30f, 0f);
                }
                state.setVolumeScale(volumeScale);
                applyVolume();

                if (remainingSeconds <= 0) {
                    Log.d(TAG, "timer expired sessionType=" + state.getSessionType());
                    stopCurrentLocked();
                    state.setTimerEndAtMs(0L);
                    state.setTimerRemainingSeconds(0);
                    state.setVolumeScale(1f);
                    state.setErrorMessage(null);
                    dispatchState();
                    return;
                }

                dispatchState();
                mainHandler.postDelayed(this, 1000L);
            }
        }
    };

    private SleepPlaybackController(@NonNull Context context) {
        appContext = context.getApplicationContext();
        playbackController = PlaybackController.getInstance(appContext);
        radioRepository = new RadioRepository();
        sleepAudioRepository = new SleepAudioRepository(appContext);
        playbackController.addListener(this);
    }

    public static synchronized SleepPlaybackController getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new SleepPlaybackController(context);
        }
        return instance;
    }

    public synchronized void addListener(@NonNull Listener listener) {
        listeners.add(listener);
        notifyListener(listener, snapshotState());
    }

    public synchronized void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    @NonNull
    public synchronized SleepPlaybackState getState() {
        return snapshotState();
    }

    @NonNull
    public synchronized List<SleepRadioStation> getFeaturedStations() {
        return SleepRadioCatalog.featuredStations();
    }

    public void searchStations(@Nullable String query, @NonNull SearchCallback callback) {
        String trimmedQuery = query == null ? "" : query.trim();
        radioSearchExecutor.execute(() -> {
            List<SleepRadioStation> localMatches = SleepRadioCatalog.search(trimmedQuery);
            if (trimmedQuery.isEmpty()) {
                mainHandler.post(() -> callback.onSearchResult(SleepRadioCatalog.featuredStations(), null));
                return;
            }

            try {
                List<RadioStation> stations = radioRepository.search(trimmedQuery);
                List<SleepRadioStation> mergedStations = mergeStations(mapToSleepRadioStations(stations), localMatches);
                mainHandler.post(() -> callback.onSearchResult(mergedStations, null));
            } catch (IOException e) {
                Log.e(TAG, "searchStations failed query=" + trimmedQuery, e);
                String errorMessage = localMatches.isEmpty()
                        ? appContext.getString(R.string.sleep_radio_search_error)
                        : null;
                mainHandler.post(() -> callback.onSearchResult(localMatches, errorMessage));
            }
        });
    }

    @Nullable
    public synchronized SleepRadioStation findStationById(@Nullable String stationId) {
        RadioStation station = radioRepository.findById(stationId);
        if (station != null) {
            return mapToSleepRadioStation(station);
        }
        return SleepRadioCatalog.findById(stationId);
    }

    public synchronized void playDefaultMix() {
        startAmbienceLocked(SleepSound.DEFAULT_MIX);
    }

    public synchronized void playAmbience(@NonNull SleepSound sound) {
        startAmbienceLocked(sound);
    }

    public synchronized void playRadio(@NonNull SleepRadioStation station) {
        Log.d(TAG, "playRadio stationId=" + station.getId() + " stream=" + station.getStreamUrl());
        stopCurrentLocked();

        state.setSessionType(SleepPlaybackState.SessionType.RADIO);
        state.setCurrentSound(null);
        state.setCurrentStation(station);
        state.setPlaying(false);
        state.setLoading(true);
        state.setErrorMessage(null);
        dispatchState();
        playbackController.setVolume(state.getVolumeScale());
        playbackController.playSong(station.toSong());
        radioSearchExecutor.execute(() -> radioRepository.registerClick(mapFromSleepRadioStation(station)));
        ensureTimerTicker();
    }

    public synchronized void toggleRadio(@NonNull SleepRadioStation station) {
        SleepRadioStation currentStation = state.getCurrentStation();
        boolean isCurrent = currentStation != null && currentStation.getId().equals(station.getId())
                && state.getSessionType() == SleepPlaybackState.SessionType.RADIO;
        if (!isCurrent) {
            playRadio(station);
            return;
        }
        if (state.isPlaying() || state.isLoading()) {
            playbackController.pause();
            return;
        }
        playbackController.togglePlayPause();
    }

    public synchronized void playRecent(@NonNull SleepRecentEntry entry) {
        if (entry.getType() == SleepRecentEntry.Type.AMBIENCE) {
            try {
                SleepSound sound = SleepSound.valueOf(entry.getReferenceId());
                playAmbience(sound);
            } catch (IllegalArgumentException ignored) {
            }
            return;
        }
        SleepRadioStation station = SleepRadioCatalog.findById(entry.getReferenceId());
        if (station != null) {
            playRadio(station);
        }
    }

    public synchronized void stop() {
        stopCurrentLocked();
        clearTimerLocked();
        dispatchState();
    }

    public synchronized void resetAll() {
        stopCurrentLocked();
        clearTimerLocked();
        dispatchState();
    }

    public synchronized void setTimerMinutes(int minutes) {
        long timerEndAt = System.currentTimeMillis() + Math.max(minutes, 0) * 60_000L;
        state.setTimerEndAtMs(timerEndAt);
        state.setTimerRemainingSeconds(minutes * 60);
        dispatchState();
        ensureTimerTicker();
    }

    public synchronized void clearTimer() {
        clearTimerLocked();
        dispatchState();
    }

    public synchronized void setFadeOutEnabled(boolean enabled) {
        state.setFadeOutEnabled(enabled);
        if (!enabled) {
            state.setVolumeScale(1f);
            applyVolume();
        }
        dispatchState();
    }

    private void startAmbienceLocked(@NonNull SleepSound sound) {
        pauseMainMusic();
        stopCurrentLocked();

        state.setSessionType(SleepPlaybackState.SessionType.AMBIENCE);
        state.setCurrentSound(sound);
        state.setCurrentStation(null);
        state.setPlaying(false);
        state.setLoading(true);
        state.setErrorMessage(null);
        dispatchState();

        long prepareGeneration = ++ambiencePrepareGeneration;
        ambienceExecutor.execute(() -> prepareAmbienceSource(sound, prepareGeneration));
    }

    private void prepareAmbienceSource(@NonNull SleepSound sound, long prepareGeneration) {
        String assetId = sound.getAssetId();
        try {
            if (!sleepAudioRepository.isCached(assetId)) {
                Log.d(TAG, "cache miss for ambience sound=" + sound.name()
                        + " assetId=" + assetId
                        + ", downloading before playback");
                sleepAudioRepository.cacheForOffline(assetId);
            }
            SleepAudioPlaybackSource playbackSource = sleepAudioRepository.resolvePlaybackSource(assetId);
            Log.d(TAG, "prepareAmbience sound=" + sound.name()
                    + " assetId=" + assetId
                    + " source=" + playbackSource.getSource()
                    + " cached=" + playbackSource.isCached());
            Song ambienceSong = buildAmbienceSong(sound, playbackSource.getSource());
            mainHandler.post(() -> startPreparedAmbience(sound, ambienceSong, prepareGeneration));
        } catch (IOException e) {
            Log.e(TAG, "prepareAmbience failed sound=" + sound.name() + " assetId=" + assetId, e);
            mainHandler.post(() -> handleAmbiencePrepareFailed(prepareGeneration, sound));
        }
    }

    private void startPreparedAmbience(@NonNull SleepSound sound,
                                       @NonNull Song ambienceSong,
                                       long prepareGeneration) {
        synchronized (this) {
            if (prepareGeneration != ambiencePrepareGeneration
                    || state.getSessionType() != SleepPlaybackState.SessionType.AMBIENCE
                    || state.getCurrentSound() != sound) {
                return;
            }
            rememberAndForceRepeatOneLocked();
            playbackController.setVolume(state.getVolumeScale());
            playbackController.playSong(ambienceSong);
            ensureTimerTicker();
        }
    }

    private void handleAmbiencePrepareFailed(long prepareGeneration, @NonNull SleepSound sound) {
        synchronized (this) {
            if (prepareGeneration != ambiencePrepareGeneration
                    || state.getSessionType() != SleepPlaybackState.SessionType.AMBIENCE
                    || state.getCurrentSound() != sound) {
                return;
            }
            state.setLoading(false);
            state.setPlaying(false);
            state.setErrorMessage(appContext.getString(R.string.sleep_sound_error));
            dispatchState();
        }
    }

    private void pauseMainMusic() {
        playbackController.pause();
    }

    @NonNull
    private Song buildAmbienceSong(@NonNull SleepSound sound, @NonNull String localPath) {
        Song song = new Song(Math.abs((long) (SLEEP_AMBIENCE_SOURCE_PREFIX + sound.name()).hashCode()),
                resolveSoundTitle(sound),
                appContext.getString(R.string.sleep_title),
                resolveSoundSubtitle(sound),
                0,
                localPath);
        song.setLocal(true);
        song.setDownloaded(true);
        song.setRadioStream(false);
        song.setSourceId(buildAmbienceSourceId(sound));
        return song;
    }

    @NonNull
    private String buildAmbienceSourceId(@NonNull SleepSound sound) {
        return SLEEP_AMBIENCE_SOURCE_PREFIX + sound.name();
    }

    @Nullable
    private SleepSound resolveSleepSound(@Nullable Song song) {
        if (song == null || song.getSourceId() == null) {
            return null;
        }
        String sourceId = song.getSourceId();
        if (!sourceId.startsWith(SLEEP_AMBIENCE_SOURCE_PREFIX)) {
            return null;
        }
        String enumName = sourceId.substring(SLEEP_AMBIENCE_SOURCE_PREFIX.length());
        try {
            return SleepSound.valueOf(enumName);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean isSleepAmbienceSong(@Nullable Song song) {
        return resolveSleepSound(song) != null;
    }

    private void rememberAndForceRepeatOneLocked() {
        if (!sleepManagedRepeatMode) {
            repeatModeBeforeSleep = playbackController.getPlayerState().getRepeatMode();
            sleepManagedRepeatMode = true;
        }
        playbackController.setRepeatMode(PlayerState.RepeatMode.ONE);
    }

    private void restoreRepeatModeIfNeededLocked() {
        if (!sleepManagedRepeatMode) {
            return;
        }
        sleepManagedRepeatMode = false;
        if (playbackController.getPlayerState().getRepeatMode() == PlayerState.RepeatMode.ONE) {
            playbackController.setRepeatMode(repeatModeBeforeSleep);
        }
    }

    @NonNull
    private List<SleepRadioStation> mapToSleepRadioStations(@NonNull List<RadioStation> stations) {
        List<SleepRadioStation> mapped = new ArrayList<>();
        for (RadioStation station : stations) {
            mapped.add(mapToSleepRadioStation(station));
        }
        return mapped;
    }

    @NonNull
    private SleepRadioStation mapToSleepRadioStation(@NonNull RadioStation station) {
        return new SleepRadioStation(
                station.getId(),
                station.getName(),
                station.getSubtitle(),
                station.getFrequency(),
                station.getStreamUrl(),
                station.isOfficial());
    }

    @NonNull
    private RadioStation mapFromSleepRadioStation(@NonNull SleepRadioStation station) {
        return new RadioStation(
                station.getId(),
                station.getName(),
                station.getSubtitle(),
                station.getFrequency(),
                station.getStreamUrl(),
                station.isOfficial());
    }

    @NonNull
    private List<SleepRadioStation> mergeStations(@NonNull List<SleepRadioStation> primary,
                                                  @NonNull List<SleepRadioStation> fallback) {
        Map<String, SleepRadioStation> merged = new LinkedHashMap<>();
        for (SleepRadioStation station : primary) {
            merged.put(station.getId(), station);
        }
        for (SleepRadioStation station : fallback) {
            merged.put(station.getId(), station);
        }
        return new ArrayList<>(merged.values());
    }

    private void stopCurrentLocked() {
        ambiencePrepareGeneration++;
        Song currentSong = playbackController.getPlayerState().getCurrentSong();
        boolean currentIsSleepAmbience = isSleepAmbienceSong(currentSong);
        if ((state.getSessionType() == SleepPlaybackState.SessionType.RADIO
                && currentSong != null
                && currentSong.isRadioStream())
                || currentIsSleepAmbience) {
            playbackController.pause();
        }
        if (state.getSessionType() == SleepPlaybackState.SessionType.AMBIENCE || currentIsSleepAmbience) {
            restoreRepeatModeIfNeededLocked();
        }
        Log.d(TAG, "stopCurrent sessionType=" + state.getSessionType());
        state.setPlaying(false);
        state.setLoading(false);
        state.setSessionType(SleepPlaybackState.SessionType.NONE);
        state.setCurrentSound(null);
        state.setCurrentStation(null);
        state.setErrorMessage(null);
        state.setVolumeScale(1f);
        applyVolume();
    }

    private void clearTimerLocked() {
        mainHandler.removeCallbacks(timerTicker);
        state.setTimerEndAtMs(0L);
        state.setTimerRemainingSeconds(0);
        state.setVolumeScale(1f);
        applyVolume();
    }

    private void ensureTimerTicker() {
        mainHandler.removeCallbacks(timerTicker);
        if (state.getTimerEndAtMs() > 0L) {
            mainHandler.post(timerTicker);
        }
    }

    private void applyVolume() {
        float volume = Math.max(0f, Math.min(1f, state.getVolumeScale()));
        Song currentSong = playbackController.getPlayerState().getCurrentSong();
        if (currentSong != null && (currentSong.isRadioStream() || isSleepAmbienceSong(currentSong))) {
            playbackController.setVolume(volume);
        }
    }

    @NonNull
    private SleepPlaybackState snapshotState() {
        SleepPlaybackState snapshot = new SleepPlaybackState();
        snapshot.setSessionType(state.getSessionType());
        snapshot.setPlaying(state.isPlaying());
        snapshot.setLoading(state.isLoading());
        snapshot.setFadeOutEnabled(state.isFadeOutEnabled());
        snapshot.setTimerEndAtMs(state.getTimerEndAtMs());
        snapshot.setTimerRemainingSeconds(state.getTimerRemainingSeconds());
        snapshot.setVolumeScale(state.getVolumeScale());
        snapshot.setCurrentSound(state.getCurrentSound());
        snapshot.setCurrentStation(state.getCurrentStation());
        snapshot.setErrorMessage(state.getErrorMessage());
        return snapshot;
    }

    private void dispatchState() {
        SleepPlaybackState snapshot = snapshotState();
        if (lastDispatchedState != null && sameState(lastDispatchedState, snapshot)) {
            return;
        }
        lastDispatchedState = snapshot;
        for (Listener listener : listeners) {
            notifyListener(listener, snapshot);
        }
    }

    private void notifyListener(@NonNull Listener listener, @NonNull SleepPlaybackState snapshot) {
        mainHandler.post(() -> {
            synchronized (SleepPlaybackController.this) {
                if (!listeners.contains(listener)) {
                    return;
                }
            }
            listener.onSleepStateChanged(snapshot);
        });
    }

    private boolean sameState(@NonNull SleepPlaybackState left, @NonNull SleepPlaybackState right) {
        return left.getSessionType() == right.getSessionType()
                && left.isPlaying() == right.isPlaying()
                && left.isLoading() == right.isLoading()
                && left.isFadeOutEnabled() == right.isFadeOutEnabled()
                && left.getTimerEndAtMs() == right.getTimerEndAtMs()
                && left.getTimerRemainingSeconds() == right.getTimerRemainingSeconds()
                && Math.abs(left.getVolumeScale() - right.getVolumeScale()) < 0.0001f
                && left.getCurrentSound() == right.getCurrentSound()
                && sameStation(left.getCurrentStation(), right.getCurrentStation())
                && sameNullableString(left.getErrorMessage(), right.getErrorMessage());
    }

    private boolean sameStation(@Nullable SleepRadioStation left, @Nullable SleepRadioStation right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return sameNullableString(left.getId(), right.getId())
                && sameNullableString(left.getStreamUrl(), right.getStreamUrl());
    }

    private boolean sameNullableString(@Nullable String left, @Nullable String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    @Override
    public synchronized void onPlaybackStateChanged(@NonNull PlayerState playerState) {
        Song currentSong = playerState.getCurrentSong();
        SleepSound currentSleepSound = resolveSleepSound(currentSong);
        if (currentSleepSound != null) {
            if (state.isLoading()
                    && state.getSessionType() == SleepPlaybackState.SessionType.RADIO
                    && state.getCurrentStation() != null) {
                Log.d(TAG, "ignore stale ambience callback while switching to radio callbackSound="
                        + currentSleepSound.name()
                        + " requestedStation=" + state.getCurrentStation().getId()
                        + " playerState=" + playerState.getState());
                return;
            }
            if (state.getSessionType() == SleepPlaybackState.SessionType.AMBIENCE
                    && state.isLoading()
                    && state.getCurrentSound() != null
                    && state.getCurrentSound() != currentSleepSound) {
                Log.d(TAG, "ignore stale ambience callback callbackSound=" + currentSleepSound.name()
                        + " requestedSound=" + state.getCurrentSound().name()
                        + " playerState=" + playerState.getState());
                return;
            }
            state.setSessionType(SleepPlaybackState.SessionType.AMBIENCE);
            state.setCurrentSound(currentSleepSound);
            state.setCurrentStation(null);
            state.setPlaying(playerState.isPlaying());
            state.setLoading(playerState.isLoading());
            state.setErrorMessage(playerState.getState() == PlayerState.State.ERROR
                    ? appContext.getString(R.string.sleep_sound_error)
                    : null);
            dispatchState();
            return;
        }

        if (currentSong != null && currentSong.isRadioStream()) {
            if (state.isLoading()
                    && state.getSessionType() == SleepPlaybackState.SessionType.AMBIENCE
                    && state.getCurrentSound() != null) {
                Log.d(TAG, "ignore stale radio callback while switching to ambience callbackStation="
                        + (currentSong.getSourceId() == null ? currentSong.getTitle() : currentSong.getSourceId())
                        + " requestedSound=" + state.getCurrentSound().name()
                        + " playerState=" + playerState.getState());
                return;
            }
            if (state.isLoading()
                    && state.getSessionType() == SleepPlaybackState.SessionType.RADIO
                    && state.getCurrentStation() != null
                    && !sameNullableString(state.getCurrentStation().getId(), currentSong.getSourceId())) {
                Log.d(TAG, "ignore stale radio callback callbackStation="
                        + (currentSong.getSourceId() == null ? currentSong.getTitle() : currentSong.getSourceId())
                        + " requestedStation=" + state.getCurrentStation().getId()
                        + " playerState=" + playerState.getState());
                return;
            }
            SleepRadioStation station = findStationById(currentSong.getSourceId());
            if (station == null) {
                station = new SleepRadioStation(
                        currentSong.getSourceId() == null ? currentSong.getTitle() : currentSong.getSourceId(),
                        currentSong.getTitle(),
                        currentSong.getArtist(),
                        currentSong.getArtist(),
                        currentSong.getAudioUrl(),
                        false);
            }
            state.setSessionType(SleepPlaybackState.SessionType.RADIO);
            state.setCurrentSound(null);
            state.setCurrentStation(station);
            state.setPlaying(playerState.isPlaying());
            state.setLoading(playerState.isLoading());
            state.setErrorMessage(playerState.getState() == PlayerState.State.ERROR
                    ? appContext.getString(R.string.sleep_radio_error)
                    : null);
            dispatchState();
            return;
        }

        if (state.getSessionType() == SleepPlaybackState.SessionType.RADIO
                || state.getSessionType() == SleepPlaybackState.SessionType.AMBIENCE) {
            playbackController.setVolume(1f);
            clearTimerLocked();
            restoreRepeatModeIfNeededLocked();
            state.setSessionType(SleepPlaybackState.SessionType.NONE);
            state.setCurrentSound(null);
            state.setCurrentStation(null);
            state.setPlaying(false);
            state.setLoading(false);
            state.setErrorMessage(null);
            dispatchState();
        }
    }

    @NonNull
    private String resolveSoundTitle(@NonNull SleepSound sound) {
        if (sound == SleepSound.DEFAULT_MIX) {
            return appContext.getString(R.string.sleep_default_mix_title);
        }
        if (sound == SleepSound.RAIN) {
            return appContext.getString(R.string.sleep_track_rain);
        }
        if (sound == SleepSound.OCEAN) {
            return appContext.getString(R.string.sleep_track_ocean);
        }
        if (sound == SleepSound.WIND) {
            return appContext.getString(R.string.sleep_track_wind);
        }
        return appContext.getString(R.string.sleep_track_forest);
    }

    @NonNull
    private String resolveSoundSubtitle(@NonNull SleepSound sound) {
        if (sound == SleepSound.DEFAULT_MIX) {
            return appContext.getString(R.string.sleep_default_mix_subtitle);
        }
        return appContext.getString(R.string.sleep_offline_ready);
    }

    @NonNull
    public static String formatRemainingTime(int totalSeconds) {
        int safeSeconds = Math.max(totalSeconds, 0);
        int hours = safeSeconds / 3600;
        int minutes = (safeSeconds % 3600) / 60;
        int seconds = safeSeconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
    }
}
