package com.example.flexmusicplayer.model;

import com.example.core_domain.player.AudioEffectProfile;

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

    public enum PlaybackMode {
        SHUFFLE,
        ORDER,
        SINGLE_LOOP,
        SINGLE_LOOP_COUNT
    }

    private State state;
    private Song currentSong;
    private int currentPosition; // in milliseconds
    private int duration; // in milliseconds
    private boolean isShuffleEnabled;
    private RepeatMode repeatMode;
    private PlaybackMode playbackMode;
    private int singleLoopCount;
    private int remainingSingleLoopCount;
    private float volume;
    private float playbackSpeed;
    private AudioEffectProfile audioEffectProfile;
    private boolean playWhenReadyRequested;

    public PlayerState() {
        this.state = State.IDLE;
        this.currentPosition = 0;
        this.duration = 0;
        this.isShuffleEnabled = false;
        this.repeatMode = RepeatMode.OFF;
        this.playbackMode = PlaybackMode.ORDER;
        this.singleLoopCount = 2;
        this.remainingSingleLoopCount = 2;
        this.volume = 1.0f;
        this.playbackSpeed = 1.0f;
        this.audioEffectProfile = AudioEffectProfile.OFF;
        this.playWhenReadyRequested = false;
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
        if (shuffleEnabled) {
            playbackMode = PlaybackMode.SHUFFLE;
            repeatMode = RepeatMode.OFF;
        } else if (playbackMode == PlaybackMode.SHUFFLE) {
            playbackMode = PlaybackMode.ORDER;
            repeatMode = RepeatMode.OFF;
        }
    }

    public RepeatMode getRepeatMode() {
        return repeatMode;
    }

    public void setRepeatMode(RepeatMode repeatMode) {
        this.repeatMode = repeatMode;
        if (repeatMode == RepeatMode.ONE) {
            if (playbackMode != PlaybackMode.SINGLE_LOOP_COUNT) {
                playbackMode = PlaybackMode.SINGLE_LOOP;
            }
            isShuffleEnabled = false;
        } else if (playbackMode != PlaybackMode.SHUFFLE) {
            playbackMode = PlaybackMode.ORDER;
            isShuffleEnabled = false;
        }
    }

    public PlaybackMode getPlaybackMode() {
        return playbackMode;
    }

    public void setPlaybackMode(PlaybackMode playbackMode) {
        this.playbackMode = playbackMode;
        switch (playbackMode) {
            case SHUFFLE:
                isShuffleEnabled = true;
                repeatMode = RepeatMode.OFF;
                break;
            case SINGLE_LOOP:
            case SINGLE_LOOP_COUNT:
                isShuffleEnabled = false;
                repeatMode = RepeatMode.ONE;
                break;
            case ORDER:
            default:
                isShuffleEnabled = false;
                repeatMode = RepeatMode.OFF;
                break;
        }
    }

    public int getSingleLoopCount() {
        return singleLoopCount;
    }

    public void setSingleLoopCount(int singleLoopCount) {
        this.singleLoopCount = Math.max(2, singleLoopCount);
    }

    public int getRemainingSingleLoopCount() {
        return remainingSingleLoopCount;
    }

    public void setRemainingSingleLoopCount(int remainingSingleLoopCount) {
        this.remainingSingleLoopCount = Math.max(1, remainingSingleLoopCount);
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

    public AudioEffectProfile getAudioEffectProfile() {
        return audioEffectProfile;
    }

    public void setAudioEffectProfile(AudioEffectProfile audioEffectProfile) {
        this.audioEffectProfile = audioEffectProfile == null ? AudioEffectProfile.OFF : audioEffectProfile;
    }

    public boolean isPlayWhenReadyRequested() {
        return playWhenReadyRequested;
    }

    public void setPlayWhenReadyRequested(boolean playWhenReadyRequested) {
        this.playWhenReadyRequested = playWhenReadyRequested;
    }

    public boolean isPlaying() {
        return state == State.PLAYING;
    }

    public boolean isPaused() {
        return state == State.PAUSED;
    }

    public boolean isStopped() {
        return state == State.STOPPED || (state == State.IDLE && currentSong == null);
    }

    public boolean isLoading() {
        return state == State.LOADING;
    }

    public void toggleShuffle() {
        setPlaybackMode(playbackMode == PlaybackMode.SHUFFLE
                ? PlaybackMode.ORDER
                : PlaybackMode.SHUFFLE);
    }

    public void toggleRepeat() {
        setPlaybackMode(playbackMode == PlaybackMode.SINGLE_LOOP
                ? PlaybackMode.ORDER
                : PlaybackMode.SINGLE_LOOP);
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
