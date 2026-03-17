package com.example.flexmusicplayer.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.flexmusicplayer.MainActivity;
import com.example.flexmusicplayer.R;
import com.example.flexmusicplayer.model.Album;
import com.example.flexmusicplayer.model.Artist;
import com.example.flexmusicplayer.model.Song;
import com.example.flexmusicplayer.storage.FavoriteSongsStore;
import com.example.flexmusicplayer.storage.LocalMusicStore;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

public class LocalFragment extends Fragment {

    private static final int TAB_ARTIST = 0;
    private static final int TAB_ALBUM = 1;
    private static final int TAB_FOLDER = 2;
    private static final int TAB_PLAYLISTS = 3;
    private static final int[] ART_COLORS = {
            Color.parseColor("#5A21C9"),
            Color.parseColor("#7E684B"),
            Color.parseColor("#0D5474"),
            Color.parseColor("#6A461B"),
            Color.parseColor("#16232D")
    };

    private TabLayout tabLayout;
    private View scanButton;
    private MaterialButton uploadButton;
    private RecyclerView songsRecycler;
    private RecyclerView albumsRecycler;
    private RecyclerView artistsRecycler;
    private View emptyState;
    private View loadingState;

    private SongVerticalAdapter songsAdapter;
    private AlbumGridAdapter albumsAdapter;
    private ArtistGridAdapter artistsAdapter;

    private int currentTab = TAB_ARTIST;

    private ActivityResultLauncher<String[]> filePickerLauncher;
    private LocalMusicStore localMusicStore;
    private FavoriteSongsStore favoriteSongsStore;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(),
                uris -> {
                    if (uris != null && !uris.isEmpty()) {
                        handleUploadedFiles(uris);
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_local, container, false);

        initViews(view);
        setupClickListeners();
        setupTabLayout();
        loadLocalMusic();

        return view;
    }

    private void initViews(View view) {
        ImageButton backButton = view.findViewById(R.id.btn_back);
        tabLayout = view.findViewById(R.id.tab_layout);
        scanButton = view.findViewById(R.id.scan_button);
        uploadButton = view.findViewById(R.id.upload_button);
        songsRecycler = view.findViewById(R.id.songs_recycler);
        albumsRecycler = view.findViewById(R.id.albums_recycler);
        artistsRecycler = view.findViewById(R.id.artists_recycler);
        emptyState = view.findViewById(R.id.empty_state);
        loadingState = view.findViewById(R.id.loading_state);
        backButton.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        localMusicStore = new LocalMusicStore(requireContext());
        favoriteSongsStore = new FavoriteSongsStore(requireContext());

        songsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        albumsRecycler.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        artistsRecycler.setLayoutManager(new GridLayoutManager(requireContext(), 2));

        songsAdapter = new SongVerticalAdapter(new ArrayList<>());
        albumsAdapter = new AlbumGridAdapter(new ArrayList<>());
        artistsAdapter = new ArtistGridAdapter(new ArrayList<>());

        songsRecycler.setAdapter(songsAdapter);
        albumsRecycler.setAdapter(albumsAdapter);
        artistsRecycler.setAdapter(artistsAdapter);
    }

    private void setupClickListeners() {
        scanButton.setOnClickListener(v -> scanForMusic());

        uploadButton.setOnClickListener(v -> filePickerLauncher.launch(new String[]{"audio/*"}));
    }

    private void handleUploadedFiles(java.util.List<android.net.Uri> uris) {
        for (android.net.Uri uri : uris) {
            try {
                requireContext().getContentResolver().takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
            }
        }
        localMusicStore.addSongs(uris);
        Snackbar.make(requireView(),
                getString(R.string.local_upload_success) + " (" + uris.size() + " files)",
                Snackbar.LENGTH_SHORT).show();
        loadLocalMusic();
    }

    private void setupTabLayout() {
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                switchTab(currentTab);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    private void switchTab(int tab) {
        songsRecycler.setVisibility(tab == TAB_ARTIST || tab == TAB_PLAYLISTS ? View.VISIBLE : View.GONE);
        albumsRecycler.setVisibility(tab == TAB_ALBUM ? View.VISIBLE : View.GONE);
        artistsRecycler.setVisibility(tab == TAB_FOLDER ? View.VISIBLE : View.GONE);
    }

    private void loadLocalMusic() {
        List<Song> storedSongs = localMusicStore.loadSongs();
        List<Song> songs = storedSongs.isEmpty() ? createMockSongs() : storedSongs;
        favoriteSongsStore.applyFavoriteFlags(songs);
        List<Album> albums = createMockAlbums();
        List<Artist> artists = createMockArtists();

        if (songs.isEmpty() && albums.isEmpty() && artists.isEmpty()) {
            showEmptyState();
        } else {
            showContent();
            songsAdapter.setSongs(songs);
            albumsAdapter.setAlbums(albums);
            artistsAdapter.setArtists(artists);
        }
    }

    private void scanForMusic() {
        showLoading();
        songsRecycler.postDelayed(this::loadLocalMusic, 1200);
    }

    private void showEmptyState() {
        emptyState.setVisibility(View.VISIBLE);
        loadingState.setVisibility(View.GONE);
        songsRecycler.setVisibility(View.GONE);
        albumsRecycler.setVisibility(View.GONE);
        artistsRecycler.setVisibility(View.GONE);
    }

    private void showLoading() {
        loadingState.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
        songsRecycler.setVisibility(View.GONE);
        albumsRecycler.setVisibility(View.GONE);
        artistsRecycler.setVisibility(View.GONE);
    }

    private void showContent() {
        loadingState.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        switchTab(currentTab);
    }

    private List<Song> createMockSongs() {
        List<Song> songs = new ArrayList<>();
        songs.add(createLocalSong(1, "Starlight", "Muse", "Will of the People", 242000, false, true));
        songs.add(createLocalSong(2, "Midnight City", "M83", "Hurry Up, We're Dreaming", 243000, true, true));
        songs.add(createLocalSong(3, "Blinding Lights", "The Weeknd", "After Hours", 200000, false, true));
        songs.add(createLocalSong(4, "Do I Wanna Know?", "Arctic Monkeys", "AM", 272000, false, false));
        songs.add(createLocalSong(5, "Lateralus", "Tool", "Lateralus", 564000, false, true));
        return songs;
    }

    private Song createLocalSong(long id,
                                 String title,
                                 String artist,
                                 String album,
                                 int duration,
                                 boolean favorite,
                                 boolean downloaded) {
        Song song = new Song(id, title, artist, album, duration, "file:///sdcard/Music/" + title + ".mp3");
        song.setLocal(true);
        song.setFavorite(favorite);
        song.setDownloaded(downloaded);
        return song;
    }

    private List<Album> createMockAlbums() {
        List<Album> albums = new ArrayList<>();
        albums.add(new Album(1, "After Hours", "The Weeknd"));
        albums.add(new Album(2, "Future Nostalgia", "Dua Lipa"));
        albums.add(new Album(3, "AM", "Arctic Monkeys"));
        albums.add(new Album(4, "Fine Line", "Harry Styles"));
        return albums;
    }

    private List<Artist> createMockArtists() {
        List<Artist> artists = new ArrayList<>();
        artists.add(new Artist(1, "Muse"));
        artists.add(new Artist(2, "The Weeknd"));
        artists.add(new Artist(3, "Arctic Monkeys"));
        artists.add(new Artist(4, "Tool"));
        return artists;
    }

    private static class SongVerticalAdapter extends RecyclerView.Adapter<SongVerticalAdapter.ViewHolder> {
        private List<Song> songs;

        SongVerticalAdapter(List<Song> songs) {
            this.songs = songs;
        }

        void setSongs(List<Song> songs) {
            this.songs = songs;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_local_track, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(songs.get(position), songs, position);
        }

        @Override
        public int getItemCount() {
            return songs.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            private final MaterialCardView albumArtCard;
            private final android.widget.TextView songTitle;
            private final android.widget.TextView songMeta;
            private final android.widget.ImageButton favoriteButton;
            private final android.widget.ImageButton downloadButton;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                albumArtCard = itemView.findViewById(R.id.album_art_card);
                songTitle = itemView.findViewById(R.id.song_title);
                songMeta = itemView.findViewById(R.id.song_meta);
                favoriteButton = itemView.findViewById(R.id.favorite_button);
                downloadButton = itemView.findViewById(R.id.download_button);
            }

            void bind(Song song, List<Song> queue, int position) {
                songTitle.setText(song.getTitle());
                songMeta.setText(song.getArtist() + " • " + song.getFormattedDuration());
                albumArtCard.setCardBackgroundColor(ART_COLORS[position % ART_COLORS.length]);
                updateFavorite(song);
                updateDownload(song);

                itemView.setOnClickListener(v -> {
                    if (itemView.getContext() instanceof MainActivity) {
                        ((MainActivity) itemView.getContext()).onSongPlaybackRequested(song, queue, position);
                    }
                });

                favoriteButton.setOnClickListener(v -> {
                    song.setFavorite(new FavoriteSongsStore(itemView.getContext()).toggleFavorite(song));
                    updateFavorite(song);
                });

                downloadButton.setOnClickListener(v -> {
                    song.setDownloaded(!song.isDownloaded());
                    updateDownload(song);
                });
            }

            private void updateFavorite(Song song) {
                int tint = ContextCompat.getColor(itemView.getContext(),
                        song.isFavorite() ? R.color.player_bar_background : R.color.gray_400);
                favoriteButton.setImageTintList(android.content.res.ColorStateList.valueOf(tint));
            }

            private void updateDownload(Song song) {
                int tint = ContextCompat.getColor(itemView.getContext(),
                        song.isDownloaded() ? R.color.gray_500 : R.color.gray_300);
                downloadButton.setImageResource(song.isDownloaded() ? R.drawable.ic_check_small : R.drawable.ic_download);
                downloadButton.setImageTintList(android.content.res.ColorStateList.valueOf(tint));
            }
        }
    }

    private static class AlbumGridAdapter extends RecyclerView.Adapter<AlbumGridAdapter.ViewHolder> {
        private List<Album> albums;

        AlbumGridAdapter(List<Album> albums) {
            this.albums = albums;
        }

        void setAlbums(List<Album> albums) {
            this.albums = albums;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_album_grid, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Album album = albums.get(position);
            holder.bind(album);
        }

        @Override
        public int getItemCount() {
            return albums.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            android.widget.TextView albumName;
            android.widget.TextView artistName;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                albumName = itemView.findViewById(R.id.album_name);
                artistName = itemView.findViewById(R.id.artist_name);
            }

            void bind(Album album) {
                albumName.setText(album.getName());
                artistName.setText(album.getArtist());
            }
        }
    }

    private static class ArtistGridAdapter extends RecyclerView.Adapter<ArtistGridAdapter.ViewHolder> {
        private List<Artist> artists;

        ArtistGridAdapter(List<Artist> artists) {
            this.artists = artists;
        }

        void setArtists(List<Artist> artists) {
            this.artists = artists;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_artist_grid, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Artist artist = artists.get(position);
            holder.bind(artist);
        }

        @Override
        public int getItemCount() {
            return artists.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            android.widget.TextView artistName;
            android.widget.TextView songCount;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                artistName = itemView.findViewById(R.id.artist_name);
                songCount = itemView.findViewById(R.id.song_count);
            }

            void bind(Artist artist) {
                artistName.setText(artist.getName());
                songCount.setText(artist.getSongCount() + " songs");
            }
        }
    }
}
