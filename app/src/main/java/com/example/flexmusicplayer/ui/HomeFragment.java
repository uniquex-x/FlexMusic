package com.example.flexmusicplayer.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.flexmusicplayer.R;
import com.example.flexmusicplayer.model.Genre;
import com.example.flexmusicplayer.model.Song;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private MaterialCardView searchBarContainer;
    private RecyclerView trendingRecycler;
    private RecyclerView newReleasesRecycler;
    private RecyclerView recommendedRecycler;
    private RecyclerView topPlaylistsRecycler;
    private com.google.android.material.chip.ChipGroup genresChipGroup;

    private SongHorizontalAdapter trendingAdapter;
    private SongHorizontalAdapter newReleasesAdapter;
    private SongVerticalAdapter recommendedAdapter;
    private PlaylistHorizontalAdapter topPlaylistsAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        initViews(view);
        setupClickListeners();
        loadMockData();

        return view;
    }

    private void initViews(View view) {
        searchBarContainer = view.findViewById(R.id.search_bar_container);
        trendingRecycler = view.findViewById(R.id.trending_recycler);
        newReleasesRecycler = view.findViewById(R.id.new_releases_recycler);
        recommendedRecycler = view.findViewById(R.id.recommended_recycler);
        topPlaylistsRecycler = view.findViewById(R.id.top_playlists_recycler);
        genresChipGroup = view.findViewById(R.id.genres_chip_group);

        // Setup horizontal RecyclerViews
        LinearLayoutManager horizontalLayoutManager = new LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false);

        trendingRecycler.setLayoutManager(horizontalLayoutManager);
        newReleasesRecycler.setLayoutManager(new LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false));
        topPlaylistsRecycler.setLayoutManager(new LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false));

        // Setup vertical RecyclerView
        LinearLayoutManager verticalLayoutManager = new LinearLayoutManager(requireContext());
        recommendedRecycler.setLayoutManager(verticalLayoutManager);

        // Initialize adapters
        trendingAdapter = new SongHorizontalAdapter(new ArrayList<>());
        newReleasesAdapter = new SongHorizontalAdapter(new ArrayList<>());
        recommendedAdapter = new SongVerticalAdapter(new ArrayList<>());
        topPlaylistsAdapter = new PlaylistHorizontalAdapter(new ArrayList<>());

        trendingRecycler.setAdapter(trendingAdapter);
        newReleasesRecycler.setAdapter(newReleasesAdapter);
        recommendedRecycler.setAdapter(recommendedAdapter);
        topPlaylistsRecycler.setAdapter(topPlaylistsAdapter);
    }

    private void setupClickListeners() {
        searchBarContainer.setOnClickListener(v -> {
            // TODO: Navigate to search activity
        });
    }

    private void loadMockData() {
        // Load mock trending songs
        List<Song> trendingSongs = createMockSongs(8);
        trendingAdapter.setSongs(trendingSongs);

        // Load mock new releases
        List<Song> newReleases = createMockSongs(8);
        newReleasesAdapter.setSongs(newReleases);

        // Load mock recommended songs
        List<Song> recommendedSongs = createMockSongs(5);
        recommendedAdapter.setSongs(recommendedSongs);

        // Load mock top playlists
        List<String> topPlaylists = createMockPlaylists(6);
        topPlaylistsAdapter.setPlaylists(topPlaylists);

        // Load mock genres
        List<Genre> genres = createMockGenres();
        loadGenresChips(genres);
    }

    private List<Song> createMockSongs(int count) {
        List<Song> songs = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Song song = new Song(
                    i,
                    "Song " + i,
                    "Artist " + i,
                    "Album " + i,
                    (3 * 60 + 45) * 1000, // 3:45
                    "https://example.com/audio" + i + ".mp3"
            );
            songs.add(song);
        }
        return songs;
    }

    private List<String> createMockPlaylists(int count) {
        List<String> playlists = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            playlists.add("Playlist " + i);
        }
        return playlists;
    }

    private List<Genre> createMockGenres() {
        List<Genre> genres = new ArrayList<>();
        genres.add(new Genre(1, "Pop"));
        genres.add(new Genre(2, "Rock"));
        genres.add(new Genre(3, "Hip-Hop"));
        genres.add(new Genre(4, "R&B"));
        genres.add(new Genre(5, "Electronic"));
        genres.add(new Genre(6, "Jazz"));
        genres.add(new Genre(7, "Classical"));
        genres.add(new Genre(8, "Country"));
        return genres;
    }

    private void loadGenresChips(List<Genre> genres) {
        genresChipGroup.removeAllViews();
        for (Genre genre : genres) {
            Chip chip = new Chip(requireContext());
            chip.setText(genre.getName());
            chip.setCheckable(false);
            chip.setClickable(true);
            chip.setOnClickListener(v -> {
                // TODO: Navigate to genre detail
            });
            genresChipGroup.addView(chip);
        }
    }

    // Adapter for horizontal song list
    private static class SongHorizontalAdapter extends RecyclerView.Adapter<SongHorizontalAdapter.ViewHolder> {
        private List<Song> songs;

        public SongHorizontalAdapter(List<Song> songs) {
            this.songs = songs;
        }

        public void setSongs(List<Song> songs) {
            this.songs = songs;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_song_horizontal, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Song song = songs.get(position);
            holder.bind(song);
        }

        @Override
        public int getItemCount() {
            return songs.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView songTitle, artistName;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                songTitle = itemView.findViewById(R.id.song_title);
                artistName = itemView.findViewById(R.id.artist_name);
            }

            public void bind(Song song) {
                songTitle.setText(song.getTitle());
                artistName.setText(song.getArtist());

                itemView.setOnClickListener(v -> {
                    // TODO: Play song
                });
            }
        }
    }

    // Adapter for vertical song list
    private static class SongVerticalAdapter extends RecyclerView.Adapter<SongVerticalAdapter.ViewHolder> {
        private List<Song> songs;

        public SongVerticalAdapter(List<Song> songs) {
            this.songs = songs;
        }

        public void setSongs(List<Song> songs) {
            this.songs = songs;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_song_vertical, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Song song = songs.get(position);
            holder.bind(song);
        }

        @Override
        public int getItemCount() {
            return songs.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView songTitle, artistName, duration;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                songTitle = itemView.findViewById(R.id.song_title);
                artistName = itemView.findViewById(R.id.artist_name);
                duration = itemView.findViewById(R.id.duration);
            }

            public void bind(Song song) {
                songTitle.setText(song.getTitle());
                artistName.setText(song.getArtist());
                duration.setText(song.getFormattedDuration());

                itemView.setOnClickListener(v -> {
                    // TODO: Play song
                });
            }
        }
    }

    // Adapter for horizontal playlist list
    private static class PlaylistHorizontalAdapter extends RecyclerView.Adapter<PlaylistHorizontalAdapter.ViewHolder> {
        private List<String> playlists;

        public PlaylistHorizontalAdapter(List<String> playlists) {
            this.playlists = playlists;
        }

        public void setPlaylists(List<String> playlists) {
            this.playlists = playlists;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_playlist_horizontal, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String playlistName = playlists.get(position);
            holder.bind(playlistName, position);
        }

        @Override
        public int getItemCount() {
            return playlists.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView playlistName, songCount;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                playlistName = itemView.findViewById(R.id.playlist_name);
                songCount = itemView.findViewById(R.id.song_count);
            }

            public void bind(String name, int position) {
                playlistName.setText(name);
                songCount.setText(String.format("%d songs", (position + 1) * 5 + 10));

                itemView.setOnClickListener(v -> {
                    // TODO: Open playlist
                });
            }
        }
    }
}
