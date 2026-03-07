package com.example.flexmusicplayer;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.flexmusicplayer.databinding.ActivityMainBinding;
import com.example.flexmusicplayer.ui.FavoritesFragment;
import com.example.flexmusicplayer.ui.HomeFragment;
import com.example.flexmusicplayer.ui.LocalFragment;
import com.example.flexmusicplayer.ui.MyFragment;
import com.example.flexmusicplayer.ui.RecentFragment;
import com.example.flexmusicplayer.ui.SettingsFragment;
import com.example.flexmusicplayer.ui.TranscodeFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity implements MyFragment.NavigationCallback {

    private static final String PREFS_NAME = "FlexMusicPrefs";
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 恢复已保存的主题
        applySavedTheme();

        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupBottomNavigation();
        setupToolbar();

        // 默认加载首页
        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
            binding.bottomNavigation.setSelectedItemId(R.id.nav_main_page);
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

    private MyFragment createMyFragment() {
        MyFragment fragment = new MyFragment();
        fragment.setNavigationCallback(this);
        return fragment;
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.setOnNavigationItemSelectedListener(
                new BottomNavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                        int itemId = item.getItemId();

                        if (itemId == R.id.nav_main_page) {
                            loadFragment(new HomeFragment());
                            return true;
                        } else if (itemId == R.id.nav_my) {
                            loadFragment(createMyFragment());
                            return true;
                        }

                        return false;
                    }
                });
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
    }

    private void loadFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.settings_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            openSettings();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void openSettings() {
        loadFragment(new SettingsFragment());
        binding.bottomNavigation.setVisibility(android.view.View.GONE);
    }

    @Override
    public void onBackPressed() {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment instanceof SettingsFragment) {
            binding.bottomNavigation.setVisibility(android.view.View.VISIBLE);
            loadFragment(new HomeFragment());
            binding.bottomNavigation.setSelectedItemId(R.id.nav_main_page);
        } else if (!(currentFragment instanceof HomeFragment)) {
            // 非首页时，返回键回到首页
            binding.bottomNavigation.setVisibility(android.view.View.VISIBLE);
            loadFragment(new HomeFragment());
            binding.bottomNavigation.setSelectedItemId(R.id.nav_main_page);
        } else {
            super.onBackPressed();
        }
    }

    // ==================== MyFragment.NavigationCallback ====================

    @Override
    public void navigateToFavorites() {
        loadFragment(new FavoritesFragment());
        binding.bottomNavigation.setVisibility(android.view.View.VISIBLE);
    }

    @Override
    public void navigateToRecent() {
        loadFragment(new RecentFragment());
        binding.bottomNavigation.setVisibility(android.view.View.VISIBLE);
    }

    @Override
    public void navigateToLocal() {
        loadFragment(new LocalFragment());
        binding.bottomNavigation.setVisibility(android.view.View.VISIBLE);
    }

    @Override
    public void navigateToTranscode() {
        loadFragment(new TranscodeFragment());
        binding.bottomNavigation.setVisibility(android.view.View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
