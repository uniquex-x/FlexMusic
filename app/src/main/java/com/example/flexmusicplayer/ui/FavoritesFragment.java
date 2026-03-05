package com.example.flexmusicplayer.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.flexmusicplayer.R;
import com.example.flexmusicplayer.model.Song;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class FavoritesFragment extends Fragment {

    private RecyclerView favoritesRecycler;
    private View emptyState;
    private MaterialButton discoverButton;
    private SongVerticalAdapter favoritesAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_favorites, container, false);

        initViews(view);
        setupClickListeners();
        loadFavorites();

        return view;
    }

    private void initViews(View view) {
        favoritesRecycler = view.findViewById(R.id.favorites_recycler);
        emptyState = view.findViewById(R.id.empty_state);
        discoverButton = view.findViewById(R.id.discover_button);

        // Setup RecyclerView
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        favoritesRecycler.setLayoutManager(layoutManager);

        // Initialize adapter
        favoritesAdapter = new SongVerticalAdapter(new ArrayList<>());
        favoritesRecycler.setAdapter(favoritesAdapter);
    }

    private void setupClickListeners() {
        discoverButton.setOnClickListener(v -> {
            // TODO: Navigate to home
        });
    }

    private void loadFavorites() {
        // TODO: Load actual favorites from database
        List<Song> favorites = createMockFavorites();

        if (favorites.isEmpty()) {
            favoritesRecycler.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            favoritesRecycler.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
            favoritesAdapter.setSongs(favorites);
        }
    }

    private List<Song> createMockFavorites() {
        List<Song> favorites = new ArrayList<>();
        // Add some mock favorites
        Song song1 = new Song(1, "Favorite Song 1", "Artist 1", "Album 1", (4 * 60 + 20) * 1000, "url1.mp3");
        song1.setFavorite(true);
        favorites.add(song1);

        Song song2 = new Song(2, "Favorite Song 2", "Artist 2", "Album 2", (3 * 60 + 45) * 1000, "url2.mp3");
        song2.setFavorite(true);
        favorites.add(song2);

        Song song3 = new Song(3, "Favorite Song 3", "Artist 3", "Album 3", (5 * 60 + 10) * 1000, "url3.mp3");
        song3.setFavorite(true);
        favorites.add(song3);

        return favorites;
    }

    public void refreshFavorites() {
        loadFavorites();
    }

    // Adapter for favorite songs list
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
            private final android.widget.TextView songTitle;
            private final android.widget.TextView artistName;
            private final android.widget.TextView duration;
            private final android.widget.ImageButton favoriteButton;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                songTitle = itemView.findViewById(R.id.song_title);
                artistName = itemView.findViewById(R.id.artist_name);
                duration = itemView.findViewById(R.id.duration);

                // Use the options button area for favorite in this context
                favoriteButton = itemView.findViewById(R.id.options_button);
                favoriteButton.setImageResource(R.drawable.ic_favorite);
            }

            public void bind(Song song) {
                songTitle.setText(song.getTitle());
                artistName.setText(song.getArtist());
                duration.setText(song.getFormattedDuration());

                itemView.setOnClickListener(v -> {
                    // TODO: Play song
                });

                favoriteButton.setOnClickListener(v -> {
                    // TODO: Toggle favorite
                    song.setFavorite(!song.isFavorite());
                });
            }
        }
    }
}
