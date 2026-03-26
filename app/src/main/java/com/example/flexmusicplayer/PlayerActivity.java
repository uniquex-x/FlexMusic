package com.example.flexmusicplayer;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.core_data.lyrics.OnlineLyricsRepository;
import com.example.core_domain.lyrics.ILyricsRepository;
import com.example.core_domain.lyrics.LyricsLineData;
import com.example.core_domain.lyrics.LyricsQuery;
import com.example.core_domain.lyrics.LyricsResult;
import com.example.flexmusicplayer.databinding.ActivityPlayerBinding;
import com.example.flexmusicplayer.model.Playlist;
import com.example.flexmusicplayer.model.PlayerState;
import com.example.flexmusicplayer.model.Song;
import com.example.flexmusicplayer.player.LyricsLine;
import com.example.flexmusicplayer.player.PlaybackController;
import com.example.flexmusicplayer.settings.AppLocaleManager;
import com.example.flexmusicplayer.storage.FavoriteRadioStore;
import com.example.flexmusicplayer.storage.FavoriteSongsStore;
import com.example.flexmusicplayer.storage.PlaylistStore;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlayerActivity extends AppCompatActivity implements PlaybackController.Listener {

    private static final String TAG = "PlayerActivity";

    public static Intent createIntent(@NonNull android.content.Context context) {
        return new Intent(context, PlayerActivity.class);
    }

    private ActivityPlayerBinding binding;
    private PlaybackController playbackController;
    private LyricsAdapter lyricsAdapter;
    private ExecutorService lyricsExecutorService;
    private Handler mainHandler;
    private ILyricsRepository lyricsRepository;
    private boolean showingLyrics = false;
    private boolean userSeeking = false;
    private boolean showSeekBufferingMessage = false;
    private final List<LyricsLine> currentLyrics = new ArrayList<>();
    private String currentLyricsSongKey = "";
    private long lyricsRequestGeneration = 0L;
    private int currentActiveLyricIndex = RecyclerView.NO_POSITION;
    private String currentFavoriteSongKey = "";
    private FavoriteSongsStore favoriteSongsStore;
    private FavoriteRadioStore favoriteRadioStore;
    private PlaylistStore playlistStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppLocaleManager.applyStoredLocale(this);
        super.onCreate(savedInstanceState);
        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        playbackController = PlaybackController.getInstance(this);
        favoriteSongsStore = new FavoriteSongsStore(this);
        favoriteRadioStore = new FavoriteRadioStore(this);
        playlistStore = new PlaylistStore(this);
        mainHandler = new Handler(Looper.getMainLooper());
        lyricsExecutorService = Executors.newSingleThreadExecutor();
        lyricsRepository = new OnlineLyricsRepository();
        lyricsAdapter = new LyricsAdapter(this::showNowPlayingScreen);

        binding.lyricsRecycler.setLayoutManager(new LinearLayoutManager(this));
        binding.lyricsRecycler.setAdapter(lyricsAdapter);
        if (binding.lyricsRecycler.getItemAnimator() instanceof DefaultItemAnimator) {
            ((DefaultItemAnimator) binding.lyricsRecycler.getItemAnimator()).setSupportsChangeAnimations(false);
        }
        binding.lyricsRecycler.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> updateLyricsRecyclerInsets());

        binding.btnCollapseNowPlaying.setOnClickListener(v -> finish());
        binding.btnCollapseLyrics.setOnClickListener(v -> showNowPlayingScreen());
        binding.btnMoreNowPlaying.setOnClickListener(this::showPlayerOptionsMenu);
        binding.btnMoreLyrics.setOnClickListener(this::showPlayerOptionsMenu);

        binding.actionLike.setOnClickListener(v -> toggleFavorite());
        binding.actionSave.setOnClickListener(v -> showToast(getString(R.string.player_save_placeholder)));
        binding.actionLyrics.setOnClickListener(v -> togglePlayerSurface());
        binding.albumDisc.setOnClickListener(v -> showLyricsScreen());
        binding.lyricsContent.setOnClickListener(v -> showNowPlayingScreen());
        binding.lyricsSongTitle.setOnClickListener(v -> showNowPlayingScreen());
        binding.lyricsSongSubtitle.setOnClickListener(v -> showNowPlayingScreen());

        binding.btnShuffle.setOnClickListener(this::showPlaybackModeMenu);
        binding.btnPreviousLarge.setOnClickListener(v -> playbackController.skipPrevious());
        binding.btnPlayPauseLarge.setOnClickListener(v -> {
            showSeekBufferingMessage = false;
            playbackController.togglePlayPause();
            PlayerState updatedState = playbackController.getPlayerState();
            updatePlayPauseButton(updatedState);
            updateBufferStatus(updatedState);
        });
        binding.btnNextLarge.setOnClickListener(v -> playbackController.skipNext());
        binding.btnSecondaryAction.setOnClickListener(v -> showPlaybackQueueDialog());
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
                showSeekBufferingMessage = state.isPlayWhenReadyRequested();
                playbackController.seekTo(target);
                updateBufferStatus(playbackController.getPlayerState());
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
    protected void onDestroy() {
        if (playbackController != null) {
            playbackController.removeListener(this);
        }
        if (lyricsExecutorService != null) {
            lyricsExecutorService.shutdownNow();
            lyricsExecutorService = null;
        }
        binding = null;
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("showing_lyrics", showingLyrics);
    }

    @Override
    public void onPlaybackStateChanged(@NonNull PlayerState state) {
        if (binding == null || isFinishing() || isDestroyed()) {
            return;
        }
        Song currentSong = state.getCurrentSong();
        if (currentSong == null) {
            return;
        }
        String songKey = buildSongKey(currentSong);
        if (!songKey.equals(currentFavoriteSongKey)) {
            currentFavoriteSongKey = songKey;
            currentSong.setFavorite(currentSong.isRadioStream()
                    ? favoriteRadioStore.isFavorite(currentSong)
                    : favoriteSongsStore.isFavorite(currentSong));
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

        if (state.isPlaying()) {
            showSeekBufferingMessage = false;
        } else if (state.isPaused() || state.isStopped() || state.getState() == PlayerState.State.ERROR) {
            showSeekBufferingMessage = false;
        }

        updatePlayPauseButton(state);
        updateBufferStatus(state);
        binding.btnPreviousLarge.setAlpha(playbackController.hasPrevious() ? 1f : 0.45f);
        binding.btnNextLarge.setAlpha(playbackController.hasNext() ? 1f : 0.45f);
        binding.iconLike.setImageTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(
                this,
                currentSong.isFavorite() ? R.color.player_bar_background : R.color.gray_400)));

        updateMediaActions(currentSong);
        updateControlChrome(state);
        updateLyrics(state);
    }

    private void updateMediaActions(@NonNull Song currentSong) {
        boolean isRadio = currentSong.isRadioStream();
        binding.actionSave.setVisibility(isRadio ? android.view.View.GONE : android.view.View.VISIBLE);
        binding.actionLyrics.setVisibility(isRadio ? android.view.View.GONE : android.view.View.VISIBLE);
        binding.playerSeekBar.setEnabled(!isRadio);
        binding.playerSeekBar.setAlpha(isRadio ? 0.4f : 1f);
        binding.playerElapsedTime.setVisibility(isRadio ? android.view.View.INVISIBLE : android.view.View.VISIBLE);
        binding.playerTotalTime.setVisibility(isRadio ? android.view.View.INVISIBLE : android.view.View.VISIBLE);
        binding.albumDisc.setEnabled(!isRadio);
        if (isRadio && showingLyrics) {
            showNowPlayingScreen();
        }
    }

    private void updateControlChrome(@NonNull PlayerState state) {
        binding.btnShuffle.setImageResource(resolvePlaybackModeIcon(state));
        binding.btnShuffle.setImageTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(
                this,
                R.color.player_bar_background)));
        binding.btnShuffle.setContentDescription(resolvePlaybackModeLabel(state));
        binding.btnSecondaryAction.setImageResource(R.drawable.ic_playlist);
        binding.btnSecondaryAction.setImageTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.gray_400)));
        binding.btnSecondaryAction.setContentDescription(getString(R.string.player_queue));
    }

    private void showPlaybackModeMenu(@NonNull View anchorView) {
        PopupMenu popupMenu = new PopupMenu(this, anchorView);
        popupMenu.inflate(R.menu.menu_playback_mode);
        PlayerState state = playbackController.getPlayerState();
        popupMenu.getMenu().findItem(R.id.action_mode_single_loop_count)
                .setTitle(getString(R.string.player_mode_single_loop_count, state.getSingleLoopCount()));
        updatePlaybackModeMenuCheckState(popupMenu.getMenu(), state);
        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_mode_shuffle) {
                applyPlaybackMode(PlayerState.PlaybackMode.SHUFFLE);
                return true;
            }
            if (itemId == R.id.action_mode_order) {
                applyPlaybackMode(PlayerState.PlaybackMode.ORDER);
                return true;
            }
            if (itemId == R.id.action_mode_single_loop) {
                applyPlaybackMode(PlayerState.PlaybackMode.SINGLE_LOOP);
                return true;
            }
            if (itemId == R.id.action_mode_single_loop_count) {
                showSingleLoopCountDialog();
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void updatePlaybackModeMenuCheckState(@NonNull Menu menu, @NonNull PlayerState state) {
        int checkedItemId;
        switch (state.getPlaybackMode()) {
            case SHUFFLE:
                checkedItemId = R.id.action_mode_shuffle;
                break;
            case SINGLE_LOOP:
                checkedItemId = R.id.action_mode_single_loop;
                break;
            case SINGLE_LOOP_COUNT:
                checkedItemId = R.id.action_mode_single_loop_count;
                break;
            case ORDER:
            default:
                checkedItemId = R.id.action_mode_order;
                break;
        }
        menu.findItem(checkedItemId).setChecked(true);
    }

    private void applyPlaybackMode(@NonNull PlayerState.PlaybackMode playbackMode) {
        playbackController.setPlaybackMode(playbackMode);
        showToast(getString(R.string.player_mode_changed, resolvePlaybackModeLabel(playbackController.getPlayerState())));
    }

    private void showSingleLoopCountDialog() {
        PlayerState state = playbackController.getPlayerState();
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(state.getSingleLoopCount()));
        input.setSelection(input.getText().length());
        FrameLayout container = new FrameLayout(this);
        int horizontalMargin = dpToPx(24);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.setMarginStart(horizontalMargin);
        layoutParams.setMarginEnd(horizontalMargin);
        input.setLayoutParams(layoutParams);
        container.addView(input);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.player_mode_single_loop_count_title)
                .setMessage(R.string.player_mode_single_loop_count_message)
                .setView(container)
                .setPositiveButton(R.string.confirm, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String rawValue = input.getText().toString().trim();
            if (TextUtils.isEmpty(rawValue)) {
                input.setError(getString(R.string.player_mode_single_loop_count_error));
                return;
            }
            int count;
            try {
                count = Integer.parseInt(rawValue);
            } catch (NumberFormatException numberFormatException) {
                input.setError(getString(R.string.player_mode_single_loop_count_error));
                return;
            }
            if (count < 2) {
                input.setError(getString(R.string.player_mode_single_loop_count_error));
                return;
            }
            playbackController.setSingleLoopCount(count);
            playbackController.setPlaybackMode(PlayerState.PlaybackMode.SINGLE_LOOP_COUNT);
            showToast(getString(R.string.player_mode_changed, resolvePlaybackModeLabel(playbackController.getPlayerState())));
            dialog.dismiss();
        }));
        dialog.show();
    }

    private int resolvePlaybackModeIcon(@NonNull PlayerState state) {
        switch (state.getPlaybackMode()) {
            case SHUFFLE:
                return R.drawable.ic_shuffle;
            case SINGLE_LOOP:
            case SINGLE_LOOP_COUNT:
                return R.drawable.ic_repeat_one;
            case ORDER:
            default:
                return R.drawable.ic_play_order;
        }
    }

    @NonNull
    private String resolvePlaybackModeLabel(@NonNull PlayerState state) {
        switch (state.getPlaybackMode()) {
            case SHUFFLE:
                return getString(R.string.player_mode_shuffle);
            case SINGLE_LOOP:
                return getString(R.string.player_mode_single_loop);
            case SINGLE_LOOP_COUNT:
                return getString(R.string.player_mode_single_loop_count, state.getSingleLoopCount());
            case ORDER:
            default:
                return getString(R.string.player_mode_order);
        }
    }

    private void updateLyrics(@NonNull PlayerState state) {
        Song currentSong = state.getCurrentSong();
        if (currentSong == null || currentSong.isRadioStream()) {
            currentLyricsSongKey = "";
            currentLyrics.clear();
            lyricsAdapter.submitLyrics(currentLyrics);
            currentActiveLyricIndex = RecyclerView.NO_POSITION;
            return;
        }
        String lyricsSongKey = buildSongKey(currentSong);
        if (!lyricsSongKey.equals(currentLyricsSongKey)) {
            currentLyricsSongKey = lyricsSongKey;
            requestLyrics(currentSong, lyricsSongKey);
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
        if (lyrics.isEmpty()) {
            return RecyclerView.NO_POSITION;
        }
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
        boolean isFavorite = currentSong.isRadioStream()
                ? favoriteRadioStore.toggleFavorite(currentSong)
                : favoriteSongsStore.toggleFavorite(currentSong);
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

    private void showPlayerOptionsMenu(@NonNull View anchorView) {
        PopupMenu popupMenu = new PopupMenu(this, anchorView);
        popupMenu.inflate(R.menu.menu_player_more);
        popupMenu.getMenu()
                .findItem(R.id.action_playback_speed)
                .setTitle(getString(
                        R.string.player_menu_speed,
                        formatSpeed(playbackController.getPlayerState().getPlaybackSpeed())));
        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_add_to_playlist) {
                showAddToPlaylistDialog();
                return true;
            }
            if (itemId == R.id.action_share) {
                shareCurrentSong();
                return true;
            }
            if (itemId == R.id.action_playback_speed) {
                showPlaybackSpeedDialog();
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void showAddToPlaylistDialog() {
        Song currentSong = playbackController.getPlayerState().getCurrentSong();
        if (currentSong == null) {
            return;
        }
        List<Playlist> playlists = playlistStore.loadPlaylists();
        if (playlists.isEmpty()) {
            showCreatePlaylistDialog(currentSong);
            return;
        }
        CharSequence[] items = new CharSequence[playlists.size() + 1];
        items[0] = getString(R.string.playlist_create_and_add);
        for (int index = 0; index < playlists.size(); index++) {
            Playlist playlist = playlists.get(index);
            items[index + 1] = playlist.getName() + "  (" + getString(R.string.songs_count, playlist.getSongCount()) + ")";
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.playlist_choose_target)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        showCreatePlaylistDialog(currentSong);
                        return;
                    }
                    Playlist targetPlaylist = playlists.get(which - 1);
                    PlaylistStore.AddSongResult result =
                            playlistStore.addSongToPlaylist(targetPlaylist.getId(), currentSong);
                    if (result == PlaylistStore.AddSongResult.ADDED) {
                        showToast(getString(R.string.playlist_song_added));
                    } else if (result == PlaylistStore.AddSongResult.ALREADY_EXISTS) {
                        showToast(getString(R.string.playlist_song_exists));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showCreatePlaylistDialog(@NonNull Song currentSong) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_playlist, null);
        EditText nameInput = dialogView.findViewById(R.id.playlist_name_input);
        EditText descriptionInput = dialogView.findViewById(R.id.playlist_description_input);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.create_playlist)
                .setView(dialogView)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String description = descriptionInput.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                nameInput.setError(getString(R.string.playlist_name_required));
                return;
            }
            playlistStore.createPlaylistWithSong(name, description, currentSong);
            showToast(getString(R.string.playlist_song_added));
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void shareCurrentSong() {
        Song currentSong = playbackController.getPlayerState().getCurrentSong();
        if (currentSong == null) {
            return;
        }
        String artist = TextUtils.isEmpty(currentSong.getArtist())
                ? getString(R.string.player_unknown_artist)
                : currentSong.getArtist();
        String shareText = getString(R.string.player_share_text, currentSong.getTitle(), artist);
        if (!TextUtils.isEmpty(currentSong.getAudioUrl())) {
            shareText = shareText + "\n" + currentSong.getAudioUrl();
        }
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.player_share_chooser)));
    }

    private void showPlaybackSpeedDialog() {
        float[] speedValues = {0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
        String[] speedLabels = new String[speedValues.length];
        int checkedIndex = 1;
        float currentSpeed = playbackController.getPlayerState().getPlaybackSpeed();
        for (int index = 0; index < speedValues.length; index++) {
            speedLabels[index] = formatSpeed(speedValues[index]) + "x";
            if (Math.abs(speedValues[index] - currentSpeed) < 0.001f) {
                checkedIndex = index;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.player_speed_title)
                .setSingleChoiceItems(speedLabels, checkedIndex, (dialog, which) -> {
                    float selectedSpeed = speedValues[which];
                    playbackController.setPlaybackSpeed(selectedSpeed);
                    showToast(getString(R.string.player_speed_applied, formatSpeed(selectedSpeed)));
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @NonNull
    private String formatSpeed(float speed) {
        if (Math.abs(speed - Math.round(speed)) < 0.001f) {
            return String.format(Locale.getDefault(), "%d", Math.round(speed));
        }
        return String.format(Locale.getDefault(), "%.2f", speed).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private void showLyricsScreen() {
        Song currentSong = playbackController.getPlayerState().getCurrentSong();
        if (currentSong != null && currentSong.isRadioStream()) {
            return;
        }
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
        if (showingLyrics && currentActiveLyricIndex != RecyclerView.NO_POSITION) {
            scrollActiveLyricIntoView(currentActiveLyricIndex);
        }
    }

    private void scrollActiveLyricIntoView(int activeIndex) {
        ActivityPlayerBinding currentBinding = binding;
        if (currentBinding == null) {
            return;
        }
        RecyclerView lyricsRecycler = currentBinding.lyricsRecycler;
        lyricsRecycler.post(() -> {
            RecyclerView.LayoutManager layoutManager = lyricsRecycler.getLayoutManager();
            if (layoutManager instanceof LinearLayoutManager) {
                RecyclerView.ViewHolder viewHolder = lyricsRecycler.findViewHolderForAdapterPosition(activeIndex);
                int itemHeight = viewHolder == null ? dpToPx(56) : viewHolder.itemView.getHeight();
                ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(
                        activeIndex,
                        Math.max((lyricsRecycler.getHeight() - itemHeight) / 2, 0));
            }
        });
    }

    private void requestLyrics(@NonNull Song song, @NonNull String lyricsSongKey) {
        currentLyrics.clear();
        currentLyrics.add(new LyricsLine(0L, getString(R.string.player_loading_lyrics)));
        lyricsAdapter.submitLyrics(currentLyrics);
        currentActiveLyricIndex = 0;
        if (showingLyrics) {
            scrollActiveLyricIntoView(0);
        }
        if (lyricsExecutorService == null) {
            return;
        }
        long requestGeneration = ++lyricsRequestGeneration;
        LyricsQuery query = new LyricsQuery(
                TextUtils.isEmpty(song.getSourceId()) ? lyricsSongKey : song.getSourceId(),
                song.getTitle() == null ? "" : song.getTitle(),
                song.getArtist() == null ? "" : song.getArtist(),
                song.getAlbum() == null ? "" : song.getAlbum(),
                song.getDuration());
        lyricsExecutorService.execute(() -> {
            try {
                LyricsResult lyricsResult = lyricsRepository.loadLyrics(query);
                List<LyricsLine> lyricsLines = mapLyricsLines(lyricsResult, song);
                mainHandler.post(() -> applyLyricsResult(requestGeneration, lyricsSongKey, lyricsLines));
            } catch (IOException ioException) {
                Log.e(TAG, "requestLyrics failed sourceId=" + query.getSourceId()
                        + " title=" + query.getTitle(), ioException);
                List<LyricsLine> fallbackLines = createFallbackLyrics(song);
                mainHandler.post(() -> applyLyricsResult(requestGeneration, lyricsSongKey, fallbackLines));
            }
        });
    }

    private void applyLyricsResult(long requestGeneration,
                                   @NonNull String lyricsSongKey,
                                   @NonNull List<LyricsLine> lyricsLines) {
        if (binding == null || requestGeneration != lyricsRequestGeneration) {
            return;
        }
        if (!lyricsSongKey.equals(currentLyricsSongKey)) {
            return;
        }
        currentLyrics.clear();
        currentLyrics.addAll(lyricsLines);
        lyricsAdapter.submitLyrics(currentLyrics);
        currentActiveLyricIndex = RecyclerView.NO_POSITION;
        PlayerState state = playbackController.getPlayerState();
        int activeIndex = resolveActiveLyricIndex(currentLyrics, state.getCurrentPosition());
        currentActiveLyricIndex = activeIndex;
        lyricsAdapter.updateActiveIndex(RecyclerView.NO_POSITION, activeIndex);
        if (showingLyrics && activeIndex != RecyclerView.NO_POSITION) {
            scrollActiveLyricIntoView(activeIndex);
        }
    }

    @NonNull
    private List<LyricsLine> mapLyricsLines(@NonNull LyricsResult lyricsResult, @NonNull Song song) {
        List<LyricsLine> lines = new ArrayList<>();
        for (LyricsLineData lineData : lyricsResult.getLines()) {
            String text = lineData.getText() == null ? "" : lineData.getText().trim();
            if (text.isEmpty()) {
                continue;
            }
            lines.add(new LyricsLine(lineData.getTimestampMs(), text));
        }
        if (lines.isEmpty()) {
            return createFallbackLyrics(song);
        }
        return lines;
    }

    @NonNull
    private List<LyricsLine> createFallbackLyrics(@NonNull Song song) {
        List<LyricsLine> lines = new ArrayList<>();
        String title = TextUtils.isEmpty(song.getTitle()) ? getString(R.string.player_title) : song.getTitle();
        String artist = TextUtils.isEmpty(song.getArtist()) ? getString(R.string.player_unknown_artist) : song.getArtist();
        lines.add(new LyricsLine(0L, title));
        lines.add(new LyricsLine(8_000L, artist));
        lines.add(new LyricsLine(16_000L, getString(R.string.player_no_lyrics)));
        return lines;
    }

    private void updatePlayPauseButton(@NonNull PlayerState state) {
        boolean showPause = state.isPlayWhenReadyRequested()
                && state.getCurrentSong() != null
                && state.getState() != PlayerState.State.ERROR;
        binding.btnPlayPauseLarge.setImageResource(showPause ? R.drawable.ic_pause : R.drawable.ic_play);
        int contentDescriptionResId = showPause && state.isLoading()
                ? R.string.player_stop
                : (showPause ? R.string.player_pause : R.string.player_play);
        binding.btnPlayPauseLarge.setContentDescription(getString(contentDescriptionResId));
    }

    private void updateBufferStatus(@NonNull PlayerState state) {
        boolean showSeekBuffer = state.isLoading() && showSeekBufferingMessage;
        boolean showInitialLoading = state.isLoading()
                && state.isPlayWhenReadyRequested()
                && !showSeekBuffer;
        boolean showBuffer = showSeekBuffer || showInitialLoading;
        binding.playerBufferStatus.setVisibility(showBuffer ? android.view.View.VISIBLE : android.view.View.GONE);
        if (showBuffer) {
            binding.playerBufferStatus.setText(showSeekBuffer
                    ? R.string.player_buffering_after_seek
                    : R.string.player_loading_status);
        }
    }

    private void updateLyricsRecyclerInsets() {
        ActivityPlayerBinding currentBinding = binding;
        if (currentBinding == null) {
            return;
        }
        RecyclerView lyricsRecycler = currentBinding.lyricsRecycler;
        int horizontalPadding = lyricsRecycler.getPaddingLeft();
        int verticalInset = Math.max((lyricsRecycler.getHeight() / 2) - dpToPx(28), dpToPx(24));
        if (lyricsRecycler.getPaddingTop() == verticalInset
                && lyricsRecycler.getPaddingBottom() == verticalInset) {
            return;
        }
        lyricsRecycler.setPadding(horizontalPadding, verticalInset, horizontalPadding, verticalInset);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
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
        if (!TextUtils.isEmpty(song.getSourceId())) {
            return song.getSourceId();
        }
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
                    lyricText.setTextSize(20);
                } else if (distanceFromActive == 1) {
                    lyricText.setTextColor(0xFF8E97AD);
                    lyricText.setTextSize(16);
                } else {
                    lyricText.setTextColor(0xFFC5CAD5);
                    lyricText.setTextSize(14);
                }
                itemView.setOnClickListener(v -> onLyricTapListener.onLyricTap());
            }
        }
    }
}
