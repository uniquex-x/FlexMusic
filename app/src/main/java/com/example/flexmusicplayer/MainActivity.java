package com.example.flexmusicplayer;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.flexmusicplayer.databinding.ActivityMainBinding;
import com.example.flexmusicplayer.model.PlayerState;
import com.example.flexmusicplayer.model.Song;
import com.example.flexmusicplayer.player.PlaybackController;
import com.example.flexmusicplayer.ui.FavoritesFragment;
import com.example.flexmusicplayer.ui.LocalFragment;
import com.example.flexmusicplayer.ui.MainPageFragment;
import com.example.flexmusicplayer.ui.MyFragment;
import com.example.flexmusicplayer.ui.PlaylistDetailFragment;
import com.example.flexmusicplayer.ui.RecentFragment;
import com.example.flexmusicplayer.ui.SettingsFragment;
import com.example.flexmusicplayer.ui.SleepFragment;
import com.example.flexmusicplayer.ui.TranscodeFragment;
import com.example.feature_search.ISearchHost;
import com.example.feature_search.ui.SearchFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import com.example.core_domain.player.PlaybackRequest;
import com.example.core_domain.search.SearchResultPage;
import com.example.core_domain.search.SearchTrack;

public class MainActivity extends AppCompatActivity implements MyFragment.NavigationCallback, ISearchHost {

    private static final String PREFS_NAME = "FlexMusicPrefs";
    private ActivityMainBinding binding;
    private PlaybackController playbackController;
    private final PlaybackController.Listener playbackListener = this::renderMiniPlayer;
    private final androidx.fragment.app.FragmentManager.OnBackStackChangedListener backStackChangedListener = () ->
            updateChromeForFragment(getSupportFragmentManager().findFragmentById(R.id.fragment_container));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applySavedTheme();

        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        playbackController = PlaybackController.getInstance(this);
        getSupportFragmentManager().addOnBackStackChangedListener(backStackChangedListener);

        setupBottomNavigation();
        setupMiniPlayer();

        if (savedInstanceState == null) {
            binding.bottomNavigation.setSelectedItemId(R.id.nav_main_page);
            loadRootFragment(new MainPageFragment());
        } else {
            updateChromeForFragment(getSupportFragmentManager().findFragmentById(R.id.fragment_container));
        }
    }

    private void applySavedTheme() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, 0);
        String theme = prefs.getString("theme", "light");
        if ("dark".equals(theme)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_main_page) {
                loadRootFragment(new MainPageFragment());
                return true;
            }
            if (itemId == R.id.nav_my) {
                loadRootFragment(createMyFragment());
                return true;
            }
            if (itemId == R.id.nav_sleep) {
                loadRootFragment(new SleepFragment());
                return true;
            }
            return false;
        });
    }

    private void setupMiniPlayer() {
        renderMiniPlayer(playbackController.getPlayerState());
        binding.playerPlayPause.setOnClickListener(v -> playbackController.togglePlayPause());
        binding.playerPrevious.setOnClickListener(v -> playbackController.skipPrevious());
        binding.playerNext.setOnClickListener(v -> playbackController.skipNext());
        binding.playerMiniBar.setOnClickListener(v -> openPlayerScreen());
    }

    public void onSongPlaybackRequested(@NonNull Song song) {
        playbackController.playSong(song);
    }

    public void onSongPlaybackRequested(@NonNull Song song, @NonNull java.util.List<Song> queue, int startIndex) {
        playbackController.playQueue(queue, startIndex);
    }

    private void renderMiniPlayer(@NonNull PlayerState state) {
        if (binding == null) {
            return;
        }
        Song currentSong = state.getCurrentSong();
        binding.playerSongTitle.setText(currentSong != null ? currentSong.getTitle() : getString(R.string.mock_player_title));
        binding.playerArtistName.setText(currentSong != null ? currentSong.getArtist() : getString(R.string.mock_player_artist));
        binding.playerPlayPause.setImageResource(state.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
        binding.playerPlayPause.setContentDescription(getString(state.isPlaying() ? R.string.player_pause : R.string.player_play));
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        updateChromeForFragment(currentFragment);
    }

    private MyFragment createMyFragment() {
        MyFragment fragment = new MyFragment();
        fragment.setNavigationCallback(this);
        return fragment;
    }

    private void loadRootFragment(@NonNull Fragment fragment) {
        replaceFragment(fragment);
    }

    private void loadSecondaryFragment(@NonNull Fragment fragment) {
        replaceFragment(fragment);
    }

    private void replaceFragment(@NonNull Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.commit();
        updateChromeForFragment(fragment);
    }

    private void restoreSelectedRoot() {
        int selectedId = binding.bottomNavigation.getSelectedItemId();
        if (selectedId == R.id.nav_my) {
            loadRootFragment(createMyFragment());
        } else if (selectedId == R.id.nav_sleep) {
            loadRootFragment(new SleepFragment());
        } else {
            loadRootFragment(new MainPageFragment());
        }
    }

    private void updateChromeForFragment(Fragment fragment) {
        boolean showMiniPlayer = fragment instanceof MainPageFragment
                || fragment instanceof MyFragment
                || fragment instanceof SleepFragment
                || fragment instanceof FavoritesFragment
                || fragment instanceof LocalFragment
                || fragment instanceof RecentFragment
                || fragment instanceof PlaylistDetailFragment
                || fragment instanceof SearchFragment;
        boolean showBottomNavigation = !(fragment instanceof SearchFragment
                || fragment instanceof PlaylistDetailFragment);
        boolean hasSong = playbackController.getPlayerState().getCurrentSong() != null;
        binding.bottomNavigation.setVisibility(showBottomNavigation ? View.VISIBLE : View.GONE);
        binding.playerMiniBar.setVisibility(showMiniPlayer && hasSong ? View.VISIBLE : View.GONE);
    }

    private void openPlayerScreen() {
        if (playbackController.getPlayerState().getCurrentSong() != null) {
            startActivity(PlayerActivity.createIntent(this));
        }
    }

    public void openSearch() {
        SearchFragment fragment = new SearchFragment();
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.addToBackStack("search");
        transaction.commit();
        updateChromeForFragment(fragment);
    }

    public void openSettings() {
        loadSecondaryFragment(new SettingsFragment());
    }

    @Override
    public void onBackPressed() {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment instanceof SettingsFragment
                || currentFragment instanceof FavoritesFragment
                || currentFragment instanceof RecentFragment
                || currentFragment instanceof LocalFragment
                || currentFragment instanceof PlaylistDetailFragment
                || currentFragment instanceof TranscodeFragment) {
            restoreSelectedRoot();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void navigateToFavorites() {
        loadSecondaryFragment(new FavoritesFragment());
    }

    @Override
    public void navigateToRecent() {
        loadSecondaryFragment(new RecentFragment());
    }

    @Override
    public void navigateToLocal() {
        loadSecondaryFragment(new LocalFragment());
    }

    @Override
    public void navigateToTranscode() {
        loadSecondaryFragment(new TranscodeFragment());
    }

    @Override
    public void navigateToPlaylistDetail(long playlistId) {
        loadSecondaryFragment(PlaylistDetailFragment.newInstance(playlistId));
    }

    @Override
    public void onSearchPlaybackRequested(@NonNull SearchResultPage resultPage,
                                          int startIndex,
                                          @NonNull PlaybackRequest playbackRequest) {
        playbackController.playSearchResultPage(resultPage, startIndex, playbackRequest);
    }

    @Override
    public void onSearchPlayNextRequested(@NonNull SearchTrack track,
                                          @NonNull PlaybackRequest playbackRequest) {
        playbackController.addSearchTrackNext(track, playbackRequest);
    }

    @Override
    public void onSearchAddToQueueRequested(@NonNull SearchTrack track,
                                            @NonNull PlaybackRequest playbackRequest) {
        playbackController.addSearchTrackToQueue(track, playbackRequest);
    }

    @Override
    public void onSearchQueuePlaybackRequested(@NonNull SearchResultPage resultPage,
                                               int startIndex,
                                               @NonNull PlaybackRequest playbackRequest) {
        playbackController.playSearchResultPage(resultPage, startIndex, playbackRequest);
    }

    @Override
    protected void onDestroy() {
        getSupportFragmentManager().removeOnBackStackChangedListener(backStackChangedListener);
        super.onDestroy();
        binding = null;
    }

    @Override
    protected void onStart() {
        super.onStart();
        playbackController.addListener(playbackListener);
    }

    @Override
    protected void onStop() {
        playbackController.removeListener(playbackListener);
        super.onStop();
    }
}
