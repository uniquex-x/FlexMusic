package com.example.flexmusicplayer.sleep;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.flexmusicplayer.R;
import com.example.flexmusicplayer.model.PlayerState;
import com.example.flexmusicplayer.model.Song;
import com.example.flexmusicplayer.player.PlaybackController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SleepPlaybackController implements PlaybackController.Listener {

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
    private final SleepRadioSearchRepository radioSearchRepository;
    private final ExecutorService radioSearchExecutor = Executors.newSingleThreadExecutor();
    private final SleepPlaybackState state = new SleepPlaybackState();
    private final AmbientEngine ambientEngine = new AmbientEngine();
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
        radioSearchRepository = new SleepRadioSearchRepository();
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

    public void searchStations(@Nullable String query, @NonNull SearchCallback callback) {
        String safeQuery = query == null ? "" : query;
        radioSearchExecutor.execute(() -> {
            try {
                List<SleepRadioStation> stations = radioSearchRepository.search(safeQuery);
                mainHandler.post(() -> callback.onSearchResult(stations, null));
            } catch (IOException e) {
                mainHandler.post(() -> callback.onSearchResult(new ArrayList<>(),
                        appContext.getString(R.string.sleep_radio_search_error)));
            }
        });
    }

    @Nullable
    public synchronized SleepRadioStation findStationById(@Nullable String stationId) {
        return SleepRadioCatalog.findById(stationId);
    }

    public synchronized void playDefaultMix() {
        startAmbienceLocked(SleepSound.DEFAULT_MIX);
    }

    public synchronized void playAmbience(@NonNull SleepSound sound) {
        startAmbienceLocked(sound);
    }

    public synchronized void playRadio(@NonNull SleepRadioStation station) {
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
        radioSearchExecutor.execute(() -> radioSearchRepository.registerClick(station));
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

        ambientEngine.start(sound, () -> {
            synchronized (SleepPlaybackController.this) {
                state.setLoading(false);
                state.setPlaying(true);
                state.setErrorMessage(null);
                dispatchState();
                ensureTimerTicker();
            }
        });
    }

    private void pauseMainMusic() {
        playbackController.pause();
    }

    private void stopCurrentLocked() {
        ambientEngine.stop();
        Song currentSong = playbackController.getPlayerState().getCurrentSong();
        if (state.getSessionType() == SleepPlaybackState.SessionType.RADIO
                && currentSong != null
                && currentSong.isRadioStream()) {
            playbackController.pause();
        }
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
        ambientEngine.setVolume(volume);
        Song currentSong = playbackController.getPlayerState().getCurrentSong();
        if (currentSong != null && currentSong.isRadioStream()) {
            playbackController.setVolume(volume);
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
        for (Listener listener : listeners) {
            notifyListener(listener, snapshot);
        }
    }

    private void notifyListener(@NonNull Listener listener, @NonNull SleepPlaybackState snapshot) {
        mainHandler.post(() -> listener.onSleepStateChanged(snapshot));
    }

    @Override
    public synchronized void onPlaybackStateChanged(@NonNull PlayerState playerState) {
        Song currentSong = playerState.getCurrentSong();
        if (currentSong != null && currentSong.isRadioStream()) {
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

        if (state.getSessionType() == SleepPlaybackState.SessionType.RADIO) {
            playbackController.setVolume(1f);
            clearTimerLocked();
            state.setSessionType(SleepPlaybackState.SessionType.NONE);
            state.setCurrentStation(null);
            state.setPlaying(false);
            state.setLoading(false);
            state.setErrorMessage(null);
            dispatchState();
        }
    }

    private static final class AmbientEngine {
        private static final int SAMPLE_RATE = 44_100;
        private static final int CHANNEL_COUNT = 2;
        private static final int BUFFER_FRAMES = 2048;

        private final Object lock = new Object();
        private volatile boolean running;
        private volatile float volume = 1f;
        @Nullable
        private AudioTrack audioTrack;
        @Nullable
        private Thread renderThread;

        void start(@NonNull SleepSound sound, @NonNull Runnable onStarted) {
            stop();
            int minBufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT);
            int bufferSize = Math.max(minBufferSize, BUFFER_FRAMES * CHANNEL_COUNT * 2);
            AudioTrack track = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STREAM);
            audioTrack = track;
            running = true;
            renderThread = new Thread(() -> renderLoop(sound, track, onStarted), "sleep-ambient-renderer");
            renderThread.start();
        }

        void stop() {
            Thread threadToJoin;
            AudioTrack trackToRelease;
            synchronized (lock) {
                running = false;
                threadToJoin = renderThread;
                renderThread = null;
                trackToRelease = audioTrack;
                audioTrack = null;
            }
            if (threadToJoin != null) {
                try {
                    threadToJoin.join(300L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            if (trackToRelease != null) {
                try {
                    trackToRelease.pause();
                    trackToRelease.flush();
                } catch (IllegalStateException ignored) {
                }
                trackToRelease.release();
            }
        }

        void setVolume(float volume) {
            this.volume = volume;
        }

        private void renderLoop(@NonNull SleepSound sound, @NonNull AudioTrack track, @NonNull Runnable onStarted) {
            AmbientSynth synth = new AmbientSynth();
            short[] pcm = new short[BUFFER_FRAMES * CHANNEL_COUNT];
            track.play();
            onStarted.run();

            while (running) {
                for (int frame = 0; frame < BUFFER_FRAMES; frame++) {
                    float sample = synth.next(sound) * volume;
                    short left = (short) (clamp(sample * (0.98f + synth.randomSpread())) * 32767);
                    short right = (short) (clamp(sample * (0.98f - synth.randomSpread())) * 32767);
                    int index = frame * CHANNEL_COUNT;
                    pcm[index] = left;
                    pcm[index + 1] = right;
                }
                track.write(pcm, 0, pcm.length);
            }
        }

        private float clamp(float value) {
            return Math.max(-1f, Math.min(1f, value));
        }

        private static final class AmbientSynth {
            private final Random random = new Random();
            private float rainLowPass;
            private float oceanLowPass;
            private float windLowPass;
            private float forestLowPass;
            private float swellPhase;
            private float gustPhase;
            private float chirpPhase;

            float next(@NonNull SleepSound sound) {
                if (sound == SleepSound.DEFAULT_MIX) {
                    return clamp(
                            0.45f * nextRain()
                                    + 0.25f * nextOcean()
                                    + 0.15f * nextWind()
                                    + 0.15f * nextForest());
                }
                if (sound == SleepSound.RAIN) {
                    return nextRain();
                }
                if (sound == SleepSound.OCEAN) {
                    return nextOcean();
                }
                if (sound == SleepSound.WIND) {
                    return nextWind();
                }
                return nextForest();
            }

            float randomSpread() {
                return (random.nextFloat() - 0.5f) * 0.04f;
            }

            private float nextRain() {
                float white = white();
                rainLowPass = rainLowPass * 0.82f + white * 0.18f;
                return clamp((white - rainLowPass) * 0.65f);
            }

            private float nextOcean() {
                float white = white();
                oceanLowPass = oceanLowPass * 0.985f + white * 0.015f;
                swellPhase += 0.00045f;
                float swell = 0.35f + 0.65f * ((float) Math.sin(swellPhase) * 0.5f + 0.5f);
                return clamp(oceanLowPass * swell * 0.85f);
            }

            private float nextWind() {
                float white = white();
                windLowPass = windLowPass * 0.96f + white * 0.04f;
                gustPhase += 0.00018f;
                float gust = 0.25f + 0.75f * ((float) Math.sin(gustPhase) * 0.5f + 0.5f);
                return clamp(windLowPass * gust * 0.8f);
            }

            private float nextForest() {
                float white = white();
                forestLowPass = forestLowPass * 0.98f + white * 0.02f;
                chirpPhase += 0.0016f;
                float chirpGate = Math.max(0f, (float) Math.sin(chirpPhase));
                float chirp = (float) Math.sin(chirpPhase * 11f) * chirpGate * 0.12f;
                return clamp(forestLowPass * 0.45f + chirp);
            }

            private float white() {
                return (random.nextFloat() * 2f) - 1f;
            }

            private float clamp(float value) {
                return Math.max(-1f, Math.min(1f, value));
            }
        }
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
