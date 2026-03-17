package com.example.flexmusicplayer;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.flexmusicplayer.databinding.ActivityPlayerBinding;
import com.example.flexmusicplayer.model.PlayerState;
import com.example.flexmusicplayer.model.Song;
import com.example.flexmusicplayer.player.LyricsLine;
import com.example.flexmusicplayer.player.LyricsRepository;
import com.example.flexmusicplayer.player.PlaybackController;
import com.example.flexmusicplayer.storage.FavoriteSongsStore;

import java.util.ArrayList;
import java.util.List;

public class PlayerActivity extends AppCompatActivity implements PlaybackController.Listener {

    public static Intent createIntent(@NonNull android.content.Context context) {
        return new Intent(context, PlayerActivity.class);
    }

    private ActivityPlayerBinding binding;
    private PlaybackController playbackController;
    private LyricsAdapter lyricsAdapter;
    private boolean showingLyrics = false;
    private boolean userSeeking = false;
    private final List<LyricsLine> currentLyrics = new ArrayList<>();
    private long currentLyricsSongId = Long.MIN_VALUE;
    private int currentActiveLyricIndex = RecyclerView.NO_POSITION;
    private String currentFavoriteSongKey = "";
    private FavoriteSongsStore favoriteSongsStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        playbackController = PlaybackController.getInstance(this);
        favoriteSongsStore = new FavoriteSongsStore(this);
        lyricsAdapter = new LyricsAdapter(this::showNowPlayingScreen);

        binding.lyricsRecycler.setLayoutManager(new LinearLayoutManager(this));
        binding.lyricsRecycler.setAdapter(lyricsAdapter);
        if (binding.lyricsRecycler.getItemAnimator() instanceof DefaultItemAnimator) {
            ((DefaultItemAnimator) binding.lyricsRecycler.getItemAnimator()).setSupportsChangeAnimations(false);
        }

        binding.btnCollapseNowPlaying.setOnClickListener(v -> finish());
        binding.btnCollapseLyrics.setOnClickListener(v -> showNowPlayingScreen());
        binding.btnMoreNowPlaying.setOnClickListener(v -> showPlaybackQueueDialog());
        binding.btnMoreLyrics.setOnClickListener(v -> showPlaybackQueueDialog());

        binding.actionLike.setOnClickListener(v -> toggleFavorite());
        binding.actionSave.setOnClickListener(v -> showToast(getString(R.string.player_save_placeholder)));
        binding.actionLyrics.setOnClickListener(v -> togglePlayerSurface());
        binding.albumDisc.setOnClickListener(v -> showLyricsScreen());
        binding.lyricsContent.setOnClickListener(v -> showNowPlayingScreen());
        binding.lyricsSongTitle.setOnClickListener(v -> showNowPlayingScreen());
        binding.lyricsSongSubtitle.setOnClickListener(v -> showNowPlayingScreen());

        binding.btnShuffle.setOnClickListener(v -> playbackController.toggleShuffle());
        binding.btnPreviousLarge.setOnClickListener(v -> playbackController.skipPrevious());
        binding.btnPlayPauseLarge.setOnClickListener(v -> playbackController.togglePlayPause());
        binding.btnNextLarge.setOnClickListener(v -> playbackController.skipNext());
        binding.btnSecondaryAction.setOnClickListener(v -> {
            if (showingLyrics) {
                playbackController.toggleRepeat();
            } else {
                showPlaybackQueueDialog();
            }
        });
        binding.playerSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int target = (int) ((progress / 1000f) * Math.max(playbackController.getPlayerState().getDuration(), 1));
                    binding.playerElapsedTime.setText(formatDuration(target));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                userSeeking = false;
                PlayerState state = playbackController.getPlayerState();
                int target = (int) ((seekBar.getProgress() / 1000f) * Math.max(state.getDuration(), 1));
                playbackController.seekTo(target);
            }
        });

        if (savedInstanceState != null) {
            showingLyrics = savedInstanceState.getBoolean("showing_lyrics", false);
        }
        updateScreenMode(playbackController.getPlayerState());
    }

    @Override
    protected void onStart() {
        super.onStart();
        playbackController.addListener(this);
        if (playbackController.getPlayerState().getCurrentSong() == null) {
            finish();
        }
    }

    @Override
    protected void onStop() {
        playbackController.removeListener(this);
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("showing_lyrics", showingLyrics);
    }

    @Override
    public void onPlaybackStateChanged(@NonNull PlayerState state) {
        Song currentSong = state.getCurrentSong();
        if (currentSong == null) {
            return;
        }
        String songKey = buildSongKey(currentSong);
        if (!songKey.equals(currentFavoriteSongKey)) {
            currentFavoriteSongKey = songKey;
            currentSong.setFavorite(favoriteSongsStore.isFavorite(currentSong));
        }

        String artist = !TextUtils.isEmpty(currentSong.getArtist())
                ? currentSong.getArtist()
                : getString(R.string.player_unknown_artist);
        String album = !TextUtils.isEmpty(currentSong.getAlbum())
                ? currentSong.getAlbum()
                : getString(R.string.player_unknown_album);

        binding.playerScreenTitle.setText(currentSong.getTitle());
        binding.playerScreenSubtitle.setText(artist);
        binding.lyricsSongTitle.setText(currentSong.getTitle());
        binding.lyricsSongSubtitle.setText(artist + " • " + album);

        binding.playerElapsedTime.setText(state.getFormattedCurrentPosition());
        binding.playerTotalTime.setText(state.getFormattedDuration());
        if (!userSeeking) {
            int progress = state.getDuration() > 0 ? (int) ((state.getCurrentPosition() / (float) state.getDuration()) * 1000) : 0;
            binding.playerSeekBar.setProgress(progress);
        }

        binding.btnPlayPauseLarge.setImageResource(state.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
        binding.btnPlayPauseLarge.setContentDescription(getString(state.isPlaying() ? R.string.player_pause : R.string.player_play));
        binding.btnPreviousLarge.setAlpha(playbackController.hasPrevious() ? 1f : 0.45f);
        binding.btnNextLarge.setAlpha(playbackController.hasNext() ? 1f : 0.45f);
        binding.iconLike.setImageTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(
                this,
                currentSong.isFavorite() ? R.color.player_bar_background : R.color.gray_400)));

        updateControlChrome(state);
        updateLyrics(state);
    }

    private void updateControlChrome(@NonNull PlayerState state) {
        binding.btnShuffle.setImageTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(
                this,
                state.isShuffleEnabled() ? R.color.player_bar_background : R.color.gray_400)));

        if (showingLyrics) {
            binding.btnSecondaryAction.setImageResource(R.drawable.ic_repeat);
            int tintRes = state.getRepeatMode() == PlayerState.RepeatMode.OFF ? R.color.gray_400 : R.color.player_bar_background;
            binding.btnSecondaryAction.setImageTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, tintRes)));
        } else {
            binding.btnSecondaryAction.setImageResource(R.drawable.ic_playlist);
            binding.btnSecondaryAction.setImageTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.gray_400)));
        }
    }

    private void updateLyrics(@NonNull PlayerState state) {
        Song currentSong = state.getCurrentSong();
        if (currentSong == null) {
            return;
        }
        if (currentSong.getId() != currentLyricsSongId) {
            currentLyricsSongId = currentSong.getId();
            currentLyrics.clear();
            currentLyrics.addAll(LyricsRepository.getLyrics(this, currentSong));
            lyricsAdapter.submitLyrics(currentLyrics);
            currentActiveLyricIndex = RecyclerView.NO_POSITION;
        }

        int activeIndex = resolveActiveLyricIndex(currentLyrics, state.getCurrentPosition());
        if (activeIndex != currentActiveLyricIndex) {
            int previousIndex = currentActiveLyricIndex;
            currentActiveLyricIndex = activeIndex;
            lyricsAdapter.updateActiveIndex(previousIndex, activeIndex);
            if (showingLyrics) {
                scrollActiveLyricIntoView(activeIndex);
            }
        }
    }

    private int resolveActiveLyricIndex(List<LyricsLine> lyrics, int currentPosition) {
        int activeIndex = -1;
        for (int i = 0; i < lyrics.size(); i++) {
            if (currentPosition >= lyrics.get(i).getTimestampMs()) {
                activeIndex = i;
            } else {
                break;
            }
        }
        return activeIndex < 0 ? 0 : activeIndex;
    }

    private void toggleFavorite() {
        Song currentSong = playbackController.getPlayerState().getCurrentSong();
        if (currentSong == null) {
            return;
        }
        boolean isFavorite = favoriteSongsStore.toggleFavorite(currentSong);
        currentSong.setFavorite(isFavorite);
        currentFavoriteSongKey = buildSongKey(currentSong);
        onPlaybackStateChanged(playbackController.getPlayerState());
        showToast(getString(isFavorite ? R.string.added_to_favorites : R.string.removed_from_favorites));
    }

    private void showPlaybackQueueDialog() {
        List<Song> queue = playbackController.getQueueSnapshot();
        if (queue.isEmpty()) {
            showToast(getString(R.string.player_queue_empty));
            return;
        }

        String[] items = new String[queue.size()];
        for (int i = 0; i < queue.size(); i++) {
            Song song = queue.get(i);
            String artist = TextUtils.isEmpty(song.getArtist())
                    ? getString(R.string.player_unknown_artist)
                    : song.getArtist();
            items[i] = song.getTitle() + " • " + artist;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.player_queue_title)
                .setSingleChoiceItems(items, playbackController.getCurrentIndex(), (dialog, which) -> {
                    playbackController.playQueueIndex(which);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showLyricsScreen() {
        showingLyrics = true;
        updateScreenMode(playbackController.getPlayerState());
        if (currentActiveLyricIndex != RecyclerView.NO_POSITION) {
            scrollActiveLyricIntoView(currentActiveLyricIndex);
        }
    }

    private void showNowPlayingScreen() {
        showingLyrics = false;
        updateScreenMode(playbackController.getPlayerState());
    }

    private void togglePlayerSurface() {
        if (showingLyrics) {
            showNowPlayingScreen();
        } else {
            showLyricsScreen();
        }
    }

    private void updateScreenMode(@NonNull PlayerState state) {
        binding.headerNowPlaying.setVisibility(showingLyrics ? android.view.View.GONE : android.view.View.VISIBLE);
        binding.nowPlayingContent.setVisibility(showingLyrics ? android.view.View.GONE : android.view.View.VISIBLE);
        binding.headerLyrics.setVisibility(showingLyrics ? android.view.View.VISIBLE : android.view.View.GONE);
        binding.lyricsContent.setVisibility(showingLyrics ? android.view.View.VISIBLE : android.view.View.GONE);
        updateControlChrome(state);
    }

    private void scrollActiveLyricIntoView(int activeIndex) {
        binding.lyricsRecycler.post(() -> {
            RecyclerView.LayoutManager layoutManager = binding.lyricsRecycler.getLayoutManager();
            if (layoutManager instanceof LinearLayoutManager) {
                ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(
                        activeIndex,
                        Math.max(binding.lyricsRecycler.getHeight() / 3, 0));
            }
        });
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private String formatDuration(int durationMs) {
        int seconds = Math.max(durationMs, 0) / 1000;
        int minutes = seconds / 60;
        return minutes + ":" + String.format(java.util.Locale.getDefault(), "%02d", seconds % 60);
    }

    private String buildSongKey(@NonNull Song song) {
        if (!TextUtils.isEmpty(song.getAudioUrl())) {
            return song.getAudioUrl();
        }
        return song.getTitle() + "|" + song.getArtist() + "|" + song.getAlbum();
    }

    private static class LyricsAdapter extends RecyclerView.Adapter<LyricsAdapter.ViewHolder> {
        interface OnLyricTapListener {
            void onLyricTap();
        }

        private final List<LyricsLine> lyrics = new ArrayList<>();
        private final OnLyricTapListener onLyricTapListener;
        private int activeIndex = RecyclerView.NO_POSITION;

        LyricsAdapter(@NonNull OnLyricTapListener onLyricTapListener) {
            this.onLyricTapListener = onLyricTapListener;
        }

        void submitLyrics(@NonNull List<LyricsLine> lines) {
            lyrics.clear();
            lyrics.addAll(lines);
            notifyDataSetChanged();
        }

        void updateActiveIndex(int previousIndex, int newIndex) {
            if (previousIndex >= 0 && previousIndex < lyrics.size()) {
                notifyItemChanged(previousIndex);
            }
            if (newIndex >= 0 && newIndex < lyrics.size()) {
                notifyItemChanged(newIndex);
            }
            activeIndex = newIndex;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            android.view.View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_lyric_line, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(lyrics.get(position), Math.abs(position - activeIndex), onLyricTapListener);
        }

        @Override
        public int getItemCount() {
            return lyrics.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView lyricText;

            ViewHolder(@NonNull android.view.View itemView) {
                super(itemView);
                lyricText = itemView.findViewById(R.id.lyric_text);
            }

            void bind(LyricsLine line, int distanceFromActive, OnLyricTapListener onLyricTapListener) {
                lyricText.setText(line.getText());
                if (distanceFromActive == 0) {
                    lyricText.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.player_bar_background));
                    lyricText.setTextSize(16);
                } else if (distanceFromActive == 1) {
                    lyricText.setTextColor(0xFF8E97AD);
                    lyricText.setTextSize(13);
                } else {
                    lyricText.setTextColor(0xFFC5CAD5);
                    lyricText.setTextSize(12);
                }
                itemView.setOnClickListener(v -> onLyricTapListener.onLyricTap());
            }
        }
    }
}
