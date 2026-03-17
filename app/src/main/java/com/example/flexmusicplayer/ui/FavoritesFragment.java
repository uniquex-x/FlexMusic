package com.example.flexmusicplayer.ui;

import android.os.Bundle;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.flexmusicplayer.MainActivity;
import com.example.flexmusicplayer.R;
import com.example.flexmusicplayer.model.Song;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class FavoritesFragment extends Fragment {

    private static final int[] ART_COLORS = {
            Color.parseColor("#2B203F"),
            Color.parseColor("#5A214F"),
            Color.parseColor("#123C4C"),
            Color.parseColor("#EBC8B3"),
            Color.parseColor("#A55906"),
            Color.parseColor("#C99BC9")
    };

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
        ImageButton backButton = view.findViewById(R.id.btn_back);
        favoritesRecycler = view.findViewById(R.id.favorites_recycler);
        emptyState = view.findViewById(R.id.empty_state);
        discoverButton = view.findViewById(R.id.discover_button);
        backButton.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());

        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        favoritesRecycler.setLayoutManager(layoutManager);
        favoritesAdapter = new SongVerticalAdapter(new ArrayList<>());
        favoritesRecycler.setAdapter(favoritesAdapter);
    }

    private void setupClickListeners() {
        discoverButton.setOnClickListener(v -> {
            BottomNavigationView navigationView = requireActivity().findViewById(R.id.bottom_navigation);
            navigationView.setSelectedItemId(R.id.nav_main_page);
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
        favorites.add(createFavoriteSong(1, "Midnight City", "M83", "Hurry Up, We're Dreaming"));
        favorites.add(createFavoriteSong(2, "Levitating", "Dua Lipa", "Future Nostalgia"));
        favorites.add(createFavoriteSong(3, "Starboy", "The Weeknd", "Starboy"));
        favorites.add(createFavoriteSong(4, "Blinding Lights", "The Weeknd", "After Hours"));
        favorites.add(createFavoriteSong(5, "Good Days", "SZA", "SOS"));
        favorites.add(createFavoriteSong(6, "Watermelon Sugar", "Harry Styles", "Fine Line"));

        return favorites;
    }

    private Song createFavoriteSong(long id, String title, String artist, String album) {
        Song song = new Song(id, title, artist, album, (3 * 60 + 20) * 1000, "");
        song.setFavorite(true);
        return song;
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
                    .inflate(R.layout.item_favorite_song, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Song song = songs.get(position);
            holder.bind(song, songs, position);
        }

        @Override
        public int getItemCount() {
            return songs.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            private final MaterialCardView albumArtCard;
            private final android.widget.TextView songTitle;
            private final android.widget.TextView artistName;
            private final android.widget.ImageButton favoriteButton;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                albumArtCard = itemView.findViewById(R.id.album_art_card);
                songTitle = itemView.findViewById(R.id.song_title);
                artistName = itemView.findViewById(R.id.artist_name);
                favoriteButton = itemView.findViewById(R.id.favorite_button);
            }

            public void bind(Song song, List<Song> queue, int position) {
                songTitle.setText(song.getTitle());
                artistName.setText(song.getArtist());
                albumArtCard.setCardBackgroundColor(ART_COLORS[position % ART_COLORS.length]);
                favoriteButton.setImageTintList(ContextCompat.getColorStateList(
                        itemView.getContext(),
                        song.isFavorite() ? R.color.player_bar_background : R.color.gray_400));

                itemView.setOnClickListener(v -> {
                    if (itemView.getContext() instanceof MainActivity) {
                        ((MainActivity) itemView.getContext()).onSongPlaybackRequested(song, queue, position);
                    }
                });

                favoriteButton.setOnClickListener(v -> {
                    song.setFavorite(!song.isFavorite());
                    favoriteButton.setImageTintList(ContextCompat.getColorStateList(
                            itemView.getContext(),
                            song.isFavorite() ? R.color.player_bar_background : R.color.gray_400));
                });
            }
        }
    }
}
