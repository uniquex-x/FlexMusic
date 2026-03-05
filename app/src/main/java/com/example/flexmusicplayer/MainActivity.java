package com.example.flexmusicplayer;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.flexmusicplayer.databinding.ActivityMainBinding;
import com.example.flexmusicplayer.ui.FavoritesFragment;
import com.example.flexmusicplayer.ui.HomeFragment;
import com.example.flexmusicplayer.ui.LocalFragment;
import com.example.flexmusicplayer.ui.PlaylistsFragment;
import com.example.flexmusicplayer.ui.RecentFragment;
import com.example.flexmusicplayer.ui.SettingsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupBottomNavigation();
        setupToolbar();

        // Load home fragment by default
        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
            binding.bottomNavigation.setSelectedItemId(R.id.nav_home);
        }
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.setOnNavigationItemSelectedListener(
                new BottomNavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                        int itemId = item.getItemId();

                        if (itemId == R.id.nav_home) {
                            loadFragment(new HomeFragment());
                            return true;
                        } else if (itemId == R.id.nav_favorites) {
                            loadFragment(new FavoritesFragment());
                            return true;
                        } else if (itemId == R.id.nav_recent) {
                            loadFragment(new RecentFragment());
                            return true;
                        } else if (itemId == R.id.nav_local) {
                            loadFragment(new LocalFragment());
                            return true;
                        } else if (itemId == R.id.nav_playlists) {
                            loadFragment(new PlaylistsFragment());
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
        // Hide bottom navigation when in settings
        binding.bottomNavigation.setVisibility(android.view.View.GONE);
    }

    @Override
    public void onBackPressed() {
        // If in settings, show bottom navigation and go to home
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment instanceof SettingsFragment) {
            binding.bottomNavigation.setVisibility(android.view.View.VISIBLE);
            loadFragment(new HomeFragment());
            binding.bottomNavigation.setSelectedItemId(R.id.nav_home);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
