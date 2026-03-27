package com.example.flexmusicplayer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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

import com.example.core_data.auth.SupabaseUserAccountRepository;
import com.example.core_domain.auth.IUserAccountRepository;
import com.example.core_domain.player.PlaybackRequest;
import com.example.core_domain.search.SearchResultPage;
import com.example.core_domain.search.SearchTrack;
import com.example.flexmusicplayer.auth.AuthProcessSessionState;
import com.example.flexmusicplayer.config.AppConfig;
import com.example.flexmusicplayer.settings.AppLocaleManager;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements MyFragment.NavigationCallback, ISearchHost {

    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "FlexMusicPrefs";
    private ActivityMainBinding binding;
    private PlaybackController playbackController;
    private IUserAccountRepository userAccountRepository;
    private final ExecutorService authExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final PlaybackController.Listener playbackListener = this::renderMiniPlayer;
    private final androidx.fragment.app.FragmentManager.OnBackStackChangedListener backStackChangedListener = () ->
            updateChromeForFragment(getSupportFragmentManager().findFragmentById(R.id.fragment_container));
    private String lastMiniPlayerSongKey = "";
    private boolean lastMiniPlayerShowingActiveControl = false;
    private boolean lastMiniPlayerHasSong = false;
    private int lastBottomNavigationVisibility = View.VISIBLE;
    private int lastMiniBarVisibility = View.GONE;
    private boolean mainUiInitialized = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppLocaleManager.applyStoredLocale(this);
        applySavedTheme();

        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        if (!AppConfig.Features.isAuthEnabled()) {
            Log.d(TAG, "onCreate auth disabled, skip startup authentication");
            initializeUi(savedInstanceState);
            return;
        }
        if (AuthProcessSessionState.isAuthenticatedInProcess()) {
            Log.d(TAG, "onCreate auth fast path, skip session restore");
            initializeUi(savedInstanceState);
            return;
        }
        binding.getRoot().setVisibility(View.INVISIBLE);
        userAccountRepository = new SupabaseUserAccountRepository(this);
        ensureAuthenticatedThenInit(savedInstanceState);
    }

    private void ensureAuthenticatedThenInit(Bundle savedInstanceState) {
        authExecutor.execute(() -> {
            try {
                if (userAccountRepository.loadCurrentProfile() == null) {
                    AuthProcessSessionState.markUnauthenticated();
                    Log.d(TAG, "ensureAuthenticatedThenInit no active session, redirecting");
                    runOnMainIfActive(this::openAuthGate);
                    return;
                }
                AuthProcessSessionState.markAuthenticated();
                Log.d(TAG, "ensureAuthenticatedThenInit session restored");
                runOnMainIfActive(() -> initializeUi(savedInstanceState));
            } catch (IOException ioException) {
                AuthProcessSessionState.markUnauthenticated();
                Log.w(TAG, "ensureAuthenticatedThenInit failed, redirecting", ioException);
                runOnMainIfActive(this::openAuthGate);
            }
        });
    }

    private void initializeUi(Bundle savedInstanceState) {
        if (mainUiInitialized || binding == null || isFinishing() || isDestroyed()) {
            return;
        }
        mainUiInitialized = true;
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
        binding.getRoot().setVisibility(View.VISIBLE);
        if (getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            playbackController.addListener(playbackListener);
        }
    }

    private void openAuthGate() {
        if (!AppConfig.Features.isAuthEnabled()) {
            Log.d(TAG, "openAuthGate skipped because auth is disabled");
            initializeUi(null);
            return;
        }
        AuthProcessSessionState.markUnauthenticated();
        if (isFinishing() || isDestroyed()) {
            return;
        }
        Intent intent = AuthActivity.createIntent(this);
        startActivity(intent);
        finish();
    }

    private void applySavedTheme() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, 0);
        String theme = prefs.getString("theme", "light");
        int targetNightMode = "dark".equals(theme)
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO;
        if (AppCompatDelegate.getDefaultNightMode() != targetNightMode) {
            AppCompatDelegate.setDefaultNightMode(targetNightMode);
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
        binding.playerPlayPause.setOnClickListener(v -> {
            playbackController.togglePlayPause();
            renderMiniPlayer(playbackController.getPlayerState());
        });
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
        if (binding == null || !mainUiInitialized) {
            return;
        }
        Song currentSong = state.getCurrentSong();
        boolean hasSong = currentSong != null;
        String songKey = hasSong
                ? (currentSong.getSourceId() + "|" + currentSong.getTitle() + "|" + currentSong.getArtist())
                : "";
        if (!songKey.equals(lastMiniPlayerSongKey) || hasSong != lastMiniPlayerHasSong) {
            binding.playerSongTitle.setText(hasSong
                    ? currentSong.getTitle()
                    : getString(R.string.mock_player_title));
            binding.playerArtistName.setText(hasSong
                    ? currentSong.getArtist()
                    : getString(R.string.mock_player_artist));
            lastMiniPlayerSongKey = songKey;
            lastMiniPlayerHasSong = hasSong;
        }
        boolean showPause = state.isPlayWhenReadyRequested()
                && hasSong
                && state.getState() != PlayerState.State.ERROR;
        if (showPause != lastMiniPlayerShowingActiveControl || hasSong != lastMiniPlayerHasSong) {
            binding.playerPlayPause.setImageResource(showPause ? R.drawable.ic_pause : R.drawable.ic_play);
            int contentDescriptionResId = showPause && state.isLoading()
                    ? R.string.player_stop
                    : (showPause ? R.string.player_pause : R.string.player_play);
            binding.playerPlayPause.setContentDescription(getString(contentDescriptionResId));
            lastMiniPlayerShowingActiveControl = showPause;
        }
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
        if (binding == null || playbackController == null) {
            return;
        }
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
        int bottomNavigationVisibility = showBottomNavigation ? View.VISIBLE : View.GONE;
        int miniBarVisibility = showMiniPlayer && hasSong ? View.VISIBLE : View.GONE;
        if (lastBottomNavigationVisibility != bottomNavigationVisibility) {
            binding.bottomNavigation.setVisibility(bottomNavigationVisibility);
            lastBottomNavigationVisibility = bottomNavigationVisibility;
        }
        if (lastMiniBarVisibility != miniBarVisibility) {
            binding.playerMiniBar.setVisibility(miniBarVisibility);
            lastMiniBarVisibility = miniBarVisibility;
        }
    }

    private void openPlayerScreen() {
        if (playbackController == null) {
            return;
        }
        if (playbackController.getPlayerState().getCurrentSong() != null) {
            startActivity(PlayerActivity.createIntent(this));
        }
    }

    public void openSearch() {
        SearchFragment fragment = SearchFragment.newInstance(
                AppConfig.Features.isSpotifySearchEnabled());
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
        authExecutor.shutdownNow();
        if (mainUiInitialized) {
            getSupportFragmentManager().removeOnBackStackChangedListener(backStackChangedListener);
        }
        if (mainUiInitialized
                && playbackController != null
                && isFinishing()
                && !isChangingConfigurations()) {
            playbackController.clearPlaybackSessionCache();
            Log.d(TAG, "onDestroy clear playback session cache");
        }
        super.onDestroy();
        binding = null;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mainUiInitialized && playbackController != null) {
            playbackController.addListener(playbackListener);
        }
    }

    @Override
    protected void onStop() {
        if (mainUiInitialized && playbackController != null) {
            playbackController.removeListener(playbackListener);
        }
        super.onStop();
    }

    private void runOnMainIfActive(@NonNull Runnable action) {
        mainHandler.post(() -> {
            if (!isFinishing() && !isDestroyed()) {
                action.run();
            }
        });
    }
}
