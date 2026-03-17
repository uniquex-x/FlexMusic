package com.example.flexmusicplayer.sleep;

import androidx.annotation.Nullable;

public class SleepPlaybackState {

    public enum SessionType {
        NONE,
        AMBIENCE,
        RADIO
    }

    private SessionType sessionType = SessionType.NONE;
    private boolean playing;
    private boolean loading;
    private boolean fadeOutEnabled = true;
    private long timerEndAtMs;
    private int timerRemainingSeconds;
    private float volumeScale = 1f;
    @Nullable
    private SleepSound currentSound;
    @Nullable
    private SleepRadioStation currentStation;
    @Nullable
    private String errorMessage;

    public SessionType getSessionType() {
        return sessionType;
    }

    public void setSessionType(SessionType sessionType) {
        this.sessionType = sessionType;
    }

    public boolean isPlaying() {
        return playing;
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
    }

    public boolean isLoading() {
        return loading;
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
    }

    public boolean isFadeOutEnabled() {
        return fadeOutEnabled;
    }

    public void setFadeOutEnabled(boolean fadeOutEnabled) {
        this.fadeOutEnabled = fadeOutEnabled;
    }

    public long getTimerEndAtMs() {
        return timerEndAtMs;
    }

    public void setTimerEndAtMs(long timerEndAtMs) {
        this.timerEndAtMs = timerEndAtMs;
    }

    public int getTimerRemainingSeconds() {
        return timerRemainingSeconds;
    }

    public void setTimerRemainingSeconds(int timerRemainingSeconds) {
        this.timerRemainingSeconds = timerRemainingSeconds;
    }

    public float getVolumeScale() {
        return volumeScale;
    }

    public void setVolumeScale(float volumeScale) {
        this.volumeScale = volumeScale;
    }

    @Nullable
    public SleepSound getCurrentSound() {
        return currentSound;
    }

    public void setCurrentSound(@Nullable SleepSound currentSound) {
        this.currentSound = currentSound;
    }

    @Nullable
    public SleepRadioStation getCurrentStation() {
        return currentStation;
    }

    public void setCurrentStation(@Nullable SleepRadioStation currentStation) {
        this.currentStation = currentStation;
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(@Nullable String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
