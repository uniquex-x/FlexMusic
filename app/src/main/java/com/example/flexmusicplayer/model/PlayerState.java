package com.example.flexmusicplayer.model;

import java.io.Serializable;

public class PlayerState implements Serializable {
    public enum State {
        IDLE,
        LOADING,
        PLAYING,
        PAUSED,
        STOPPED,
        ERROR
    }

    public enum RepeatMode {
        OFF,
        ALL,
        ONE
    }

    private State state;
    private Song currentSong;
    private int currentPosition; // in milliseconds
    private int duration; // in milliseconds
    private boolean isShuffleEnabled;
    private RepeatMode repeatMode;
    private float volume;
    private float playbackSpeed;

    public PlayerState() {
        this.state = State.IDLE;
        this.currentPosition = 0;
        this.duration = 0;
        this.isShuffleEnabled = false;
        this.repeatMode = RepeatMode.OFF;
        this.volume = 1.0f;
        this.playbackSpeed = 1.0f;
    }

    // Getters and Setters
    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public Song getCurrentSong() {
        return currentSong;
    }

    public void setCurrentSong(Song currentSong) {
        this.currentSong = currentSong;
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    public void setCurrentPosition(int currentPosition) {
        this.currentPosition = currentPosition;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public boolean isShuffleEnabled() {
        return isShuffleEnabled;
    }

    public void setShuffleEnabled(boolean shuffleEnabled) {
        isShuffleEnabled = shuffleEnabled;
    }

    public RepeatMode getRepeatMode() {
        return repeatMode;
    }

    public void setRepeatMode(RepeatMode repeatMode) {
        this.repeatMode = repeatMode;
    }

    public float getVolume() {
        return volume;
    }

    public void setVolume(float volume) {
        this.volume = volume;
    }

    public float getPlaybackSpeed() {
        return playbackSpeed;
    }

    public void setPlaybackSpeed(float playbackSpeed) {
        this.playbackSpeed = playbackSpeed;
    }

    public boolean isPlaying() {
        return state == State.PLAYING;
    }

    public boolean isPaused() {
        return state == State.PAUSED;
    }

    public boolean isStopped() {
        return state == State.STOPPED || state == State.IDLE;
    }

    public boolean isLoading() {
        return state == State.LOADING;
    }

    public void toggleShuffle() {
        this.isShuffleEnabled = !this.isShuffleEnabled;
    }

    public void toggleRepeat() {
        switch (repeatMode) {
            case OFF:
                repeatMode = RepeatMode.ALL;
                break;
            case ALL:
                repeatMode = RepeatMode.ONE;
                break;
            case ONE:
                repeatMode = RepeatMode.OFF;
                break;
        }
    }

    public String getFormattedCurrentPosition() {
        int seconds = (currentPosition / 1000) % 60;
        int minutes = (currentPosition / (1000 * 60)) % 60;
        int hours = (currentPosition / (1000 * 60 * 60));

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }

    public String getFormattedDuration() {
        int seconds = (duration / 1000) % 60;
        int minutes = (duration / (1000 * 60)) % 60;
        int hours = (duration / (1000 * 60 * 60));

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }

    public int getProgressPercentage() {
        if (duration > 0) {
            return (int) ((currentPosition * 100) / duration);
        }
        return 0;
    }
}
