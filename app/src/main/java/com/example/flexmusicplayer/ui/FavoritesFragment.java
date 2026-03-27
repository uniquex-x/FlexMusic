package com.example.flexmusicplayer.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.flexmusicplayer.MainActivity;
import com.example.flexmusicplayer.R;
import com.example.flexmusicplayer.download.SongDownloadManager;
import com.example.flexmusicplayer.model.PlayerState;
import com.example.flexmusicplayer.model.Song;
import com.example.flexmusicplayer.player.PlaybackController;
import com.example.flexmusicplayer.storage.FavoriteRadioStore;
import com.example.flexmusicplayer.storage.FavoriteSongsStore;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class FavoritesFragment extends Fragment implements PlaybackController.Listener {

    private enum FavoriteTab {
        SONGS,
        RADIOS
    }

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
    private FavoriteSongsStore favoriteSongsStore;
    private FavoriteRadioStore favoriteRadioStore;
    private SongDownloadManager downloadManager;
    private PlaybackController playbackController;
    private TextView emptyTitle;
    private TextView emptyDescription;
    private View songsTab;
    private View radiosTab;
    private TextView songsTabText;
    private TextView radiosTabText;
    private View songsTabIndicator;
    private View radiosTabIndicator;
    private FavoriteTab selectedTab = FavoriteTab.SONGS;
    private String currentPlayingSongKey = "";

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
        emptyTitle = view.findViewById(R.id.empty_title);
        emptyDescription = view.findViewById(R.id.empty_description);
        songsTab = view.findViewById(R.id.tab_songs);
        radiosTab = view.findViewById(R.id.tab_radios);
        songsTabText = view.findViewById(R.id.tab_songs_text);
        radiosTabText = view.findViewById(R.id.tab_radios_text);
        songsTabIndicator = view.findViewById(R.id.tab_songs_indicator);
        radiosTabIndicator = view.findViewById(R.id.tab_radios_indicator);
        backButton.setOnClickListener(v -> navigateBack());
        favoriteSongsStore = new FavoriteSongsStore(requireContext());
        favoriteRadioStore = new FavoriteRadioStore(requireContext());
        downloadManager = SongDownloadManager.getInstance(requireContext());
        playbackController = PlaybackController.getInstance(requireContext());

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
        songsTab.setOnClickListener(v -> switchTab(FavoriteTab.SONGS));
        radiosTab.setOnClickListener(v -> switchTab(FavoriteTab.RADIOS));
    }

    private void switchTab(@NonNull FavoriteTab tab) {
        if (selectedTab == tab) {
            return;
        }
        selectedTab = tab;
        loadFavorites();
    }

    private void loadFavorites() {
        updateTabUi();
        List<Song> favorites = selectedTab == FavoriteTab.SONGS
                ? favoriteSongsStore.loadFavorites()
                : favoriteRadioStore.loadFavorites();
        if (selectedTab == FavoriteTab.SONGS) {
            downloadManager.refreshDownloadStates(favorites);
        }

        if (favorites.isEmpty()) {
            favoritesRecycler.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
            emptyTitle.setText(selectedTab == FavoriteTab.SONGS
                    ? R.string.favorites_empty
                    : R.string.favorite_radios_empty);
            emptyDescription.setText(selectedTab == FavoriteTab.SONGS
                    ? R.string.favorites_empty_description
                    : R.string.favorite_radios_empty_description);
        } else {
            favoritesRecycler.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
            favoritesAdapter.setSongs(favorites, selectedTab);
            syncCurrentPlayingSong(playbackController.getPlayerState());
        }
    }

    private void navigateBack() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).onBackPressed();
            return;
        }
        requireActivity().finish();
    }

    private void updateTabUi() {
        boolean songsSelected = selectedTab == FavoriteTab.SONGS;
        songsTabText.setTextColor(ContextCompat.getColor(requireContext(),
                songsSelected ? R.color.player_bar_background : R.color.gray_500));
        radiosTabText.setTextColor(ContextCompat.getColor(requireContext(),
                songsSelected ? R.color.gray_500 : R.color.player_bar_background));
        songsTabIndicator.setVisibility(songsSelected ? View.VISIBLE : View.INVISIBLE);
        radiosTabIndicator.setVisibility(songsSelected ? View.INVISIBLE : View.VISIBLE);
    }

    @Override
    public void onStart() {
        super.onStart();
        playbackController.addListener(this);
        syncCurrentPlayingSong(playbackController.getPlayerState());
    }

    @Override
    public void onStop() {
        playbackController.removeListener(this);
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadFavorites();
    }

    @Override
    public void onPlaybackStateChanged(@NonNull PlayerState state) {
        if (!isAdded()) {
            return;
        }
        syncCurrentPlayingSong(state);
    }

    public void refreshFavorites() {
        loadFavorites();
    }

    private void syncCurrentPlayingSong(@NonNull PlayerState state) {
        String nextSongKey = buildSongKey(state.getCurrentSong());
        if (TextUtils.equals(currentPlayingSongKey, nextSongKey)) {
            return;
        }
        String previousSongKey = currentPlayingSongKey;
        currentPlayingSongKey = nextSongKey;
        int previousIndex = favoritesAdapter.findSongIndex(previousSongKey);
        int nextIndex = favoritesAdapter.findSongIndex(nextSongKey);
        if (previousIndex >= 0) {
            favoritesAdapter.notifyItemChanged(previousIndex);
        }
        if (nextIndex >= 0 && nextIndex != previousIndex) {
            favoritesAdapter.notifyItemChanged(nextIndex);
        }
    }

    @NonNull
    private String buildSongKey(@Nullable Song song) {
        if (song == null) {
            return "";
        }
        if (!TextUtils.isEmpty(song.getSourceId())) {
            return song.getSourceId();
        }
        if (!TextUtils.isEmpty(song.getAudioUrl())) {
            return song.getAudioUrl();
        }
        return song.getTitle() + "|" + song.getArtist() + "|" + song.getAlbum();
    }

    private int dpToPx(int valueDp) {
        return Math.round(valueDp * requireContext().getResources().getDisplayMetrics().density);
    }

    private class SongVerticalAdapter extends RecyclerView.Adapter<SongVerticalAdapter.ViewHolder> {
        private List<Song> songs;
        private FavoriteTab tab = FavoriteTab.SONGS;

        SongVerticalAdapter(List<Song> songs) {
            this.songs = songs;
        }

        void setSongs(List<Song> songs, @NonNull FavoriteTab tab) {
            this.songs = songs;
            this.tab = tab;
            notifyDataSetChanged();
        }

        int findSongIndex(@Nullable String songKey) {
            if (TextUtils.isEmpty(songKey)) {
                return -1;
            }
            for (int index = 0; index < songs.size(); index++) {
                if (songKey.equals(buildSongKey(songs.get(index)))) {
                    return index;
                }
            }
            return -1;
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
            holder.bind(song, position, tab);
        }

        @Override
        public int getItemCount() {
            return songs.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final MaterialCardView albumArtCard;
            private final TextView songTitle;
            private final TextView artistName;
            private final ImageButton downloadButton;
            private final ImageButton favoriteButton;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                albumArtCard = itemView.findViewById(R.id.album_art_card);
                songTitle = itemView.findViewById(R.id.song_title);
                artistName = itemView.findViewById(R.id.artist_name);
                downloadButton = itemView.findViewById(R.id.download_button);
                favoriteButton = itemView.findViewById(R.id.favorite_button);
            }

            void bind(Song song, int position, @NonNull FavoriteTab tab) {
                songTitle.setText(song.getTitle());
                artistName.setText(TextUtils.isEmpty(song.getArtist())
                        ? getString(R.string.player_unknown_artist)
                        : song.getArtist());
                albumArtCard.setCardBackgroundColor(ART_COLORS[position % ART_COLORS.length]);
                updateDownload(song, tab);
                updatePlayingHighlight(song);
                updateFavorite(song);

                itemView.setOnClickListener(v -> {
                    if (itemView.getContext() instanceof MainActivity) {
                        ((MainActivity) itemView.getContext()).onSongPlaybackRequested(song, songs, position);
                    }
                });

                downloadButton.setOnClickListener(v -> requestSongDownload(song));
                favoriteButton.setOnClickListener(v -> {
                    boolean isFavorite = tab == FavoriteTab.SONGS
                            ? favoriteSongsStore.toggleFavorite(song)
                            : favoriteRadioStore.toggleFavorite(song);
                    song.setFavorite(isFavorite);
                    updateFavorite(song);
                    if (!isFavorite) {
                        int adapterPosition = getAdapterPosition();
                        if (adapterPosition != RecyclerView.NO_POSITION) {
                            songs.remove(adapterPosition);
                            notifyItemRemoved(adapterPosition);
                            if (songs.isEmpty()) {
                                loadFavorites();
                            }
                        }
                        Toast.makeText(itemView.getContext(), R.string.removed_from_favorites, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            private void updatePlayingHighlight(@NonNull Song song) {
                boolean isCurrentSong = buildSongKey(song).equals(currentPlayingSongKey);
                int primaryColor = ContextCompat.getColor(
                        itemView.getContext(),
                        isCurrentSong ? R.color.player_bar_background : R.color.gray_900);
                int secondaryColor = ContextCompat.getColor(
                        itemView.getContext(),
                        isCurrentSong ? R.color.player_bar_background : R.color.gray_500);
                itemView.setBackgroundResource(isCurrentSong
                        ? R.drawable.bg_playlist_detail_song_active
                        : android.R.color.transparent);
                albumArtCard.setStrokeWidth(isCurrentSong ? dpToPx(1) : 0);
                albumArtCard.setStrokeColor(isCurrentSong
                        ? ColorStateList.valueOf(ContextCompat.getColor(itemView.getContext(), R.color.player_bar_background))
                        : ColorStateList.valueOf(Color.TRANSPARENT));
                songTitle.setTextColor(primaryColor);
                artistName.setTextColor(secondaryColor);
            }

            private void updateFavorite(@NonNull Song song) {
                int tint = ContextCompat.getColor(itemView.getContext(),
                        song.isFavorite() ? R.color.player_bar_background : R.color.gray_400);
                favoriteButton.setImageTintList(ColorStateList.valueOf(tint));
            }

            private void updateDownload(@NonNull Song song, @NonNull FavoriteTab tab) {
                if (tab != FavoriteTab.SONGS) {
                    downloadButton.setVisibility(View.GONE);
                    return;
                }
                downloadButton.setVisibility(View.VISIBLE);
                boolean inFlight = downloadManager.isDownloadInFlight(song);
                downloadButton.setImageResource(song.isDownloaded() ? R.drawable.ic_check_small : R.drawable.ic_download);
                int tint = ContextCompat.getColor(itemView.getContext(),
                        song.isDownloaded() || inFlight ? R.color.gray_500 : R.color.gray_300);
                downloadButton.setImageTintList(ColorStateList.valueOf(tint));
                downloadButton.setEnabled(!inFlight);
            }

            private void requestSongDownload(@NonNull Song song) {
                downloadManager.requestDownload(song, new SongDownloadManager.DownloadCallbacks() {
                    @Override
                    public void onDownloadStateChanged(@NonNull Song targetSong) {
                        updateDownload(targetSong, tab);
                    }

                    @Override
                    public void onDownloadSucceeded(@NonNull Song targetSong, boolean alreadyDownloaded) {
                        updateDownload(targetSong, tab);
                        Toast.makeText(
                                        itemView.getContext(),
                                        alreadyDownloaded ? R.string.download_already_exists_message : R.string.download_success_message,
                                        Toast.LENGTH_SHORT)
                                .show();
                    }

                    @Override
                    public void onDownloadFailed(@NonNull Song targetSong, @NonNull String message) {
                        updateDownload(targetSong, tab);
                        Toast.makeText(
                                        itemView.getContext(),
                                        message,
                                        Toast.LENGTH_SHORT)
                                .show();
                    }
                });
            }
        }
    }
}
