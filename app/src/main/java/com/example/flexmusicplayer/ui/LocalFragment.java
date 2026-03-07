package com.example.flexmusicplayer.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.flexmusicplayer.R;
import com.example.flexmusicplayer.model.Album;
import com.example.flexmusicplayer.model.Artist;
import com.example.flexmusicplayer.model.Song;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

public class LocalFragment extends Fragment {

    private static final int TAB_SONGS = 0;
    private static final int TAB_ALBUMS = 1;
    private static final int TAB_ARTISTS = 2;

    private TabLayout tabLayout;
    private MaterialButton scanButton;
    private MaterialButton uploadButton;
    private RecyclerView songsRecycler;
    private RecyclerView albumsRecycler;
    private RecyclerView artistsRecycler;
    private View emptyState;
    private View loadingState;

    private SongVerticalAdapter songsAdapter;
    private AlbumGridAdapter albumsAdapter;
    private ArtistGridAdapter artistsAdapter;

    private int currentTab = TAB_SONGS;

    // File picker launcher for uploading offline songs
    private ActivityResultLauncher<String[]> filePickerLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_local, container, false);

        // Register file picker before view is created
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(),
                uris -> {
                    if (uris != null && !uris.isEmpty()) {
                        handleUploadedFiles(uris);
                    }
                });

        initViews(view);
        setupClickListeners();
        setupTabLayout();
        loadLocalMusic();

        return view;
    }

    private void initViews(View view) {
        tabLayout = view.findViewById(R.id.tab_layout);
        scanButton = view.findViewById(R.id.scan_button);
        uploadButton = view.findViewById(R.id.upload_button);
        songsRecycler = view.findViewById(R.id.songs_recycler);
        albumsRecycler = view.findViewById(R.id.albums_recycler);
        artistsRecycler = view.findViewById(R.id.artists_recycler);
        emptyState = view.findViewById(R.id.empty_state);
        loadingState = view.findViewById(R.id.loading_state);

        // Setup RecyclerViews
        LinearLayoutManager songsLayoutManager = new LinearLayoutManager(requireContext());
        songsRecycler.setLayoutManager(songsLayoutManager);

        GridLayoutManager albumsLayoutManager = new GridLayoutManager(requireContext(), 2);
        albumsRecycler.setLayoutManager(albumsLayoutManager);

        GridLayoutManager artistsLayoutManager = new GridLayoutManager(requireContext(), 2);
        artistsRecycler.setLayoutManager(artistsLayoutManager);

        // Initialize adapters
        songsAdapter = new SongVerticalAdapter(new ArrayList<>());
        albumsAdapter = new AlbumGridAdapter(new ArrayList<>());
        artistsAdapter = new ArtistGridAdapter(new ArrayList<>());

        songsRecycler.setAdapter(songsAdapter);
        albumsRecycler.setAdapter(albumsAdapter);
        artistsRecycler.setAdapter(artistsAdapter);
    }

    private void setupClickListeners() {
        scanButton.setOnClickListener(v -> {
            scanForMusic();
        });

        uploadButton.setOnClickListener(v -> {
            // Open file picker for audio files
            filePickerLauncher.launch(new String[]{"audio/*"});
        });
    }

    private void handleUploadedFiles(java.util.List<android.net.Uri> uris) {
        // TODO: Implement actual file import logic (copy/index the selected audio files)
        Snackbar.make(requireView(),
                getString(R.string.local_upload_success) + " (" + uris.size() + " files)",
                Snackbar.LENGTH_SHORT).show();
        // Reload list after import
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
        songsRecycler.setVisibility(tab == TAB_SONGS ? View.VISIBLE : View.GONE);
        albumsRecycler.setVisibility(tab == TAB_ALBUMS ? View.VISIBLE : View.GONE);
        artistsRecycler.setVisibility(tab == TAB_ARTISTS ? View.VISIBLE : View.GONE);
    }

    private void loadLocalMusic() {
        // TODO: Load actual local music from database
        List<Song> songs = createMockSongs();
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

        // TODO: Implement actual music scanning
        // For now, just simulate loading
        songsRecycler.postDelayed(() -> {
            loadLocalMusic();
        }, 2000);
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
        for (int i = 1; i <= 10; i++) {
            Song song = new Song(
                    i,
                    "Local Song " + i,
                    "Local Artist " + i,
                    "Local Album " + i,
                    (3 * 60 + i * 10) * 1000,
                    "file:///sdcard/Music/song" + i + ".mp3"
            );
            song.setLocal(true);
            songs.add(song);
        }
        return songs;
    }

    private List<Album> createMockAlbums() {
        List<Album> albums = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            Album album = new Album(i, "Local Album " + i, "Local Artist " + i);
            albums.add(album);
        }
        return albums;
    }

    private List<Artist> createMockArtists() {
        List<Artist> artists = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            Artist artist = new Artist(i, "Local Artist " + i);
            artists.add(artist);
        }
        return artists;
    }

    // Adapter for songs
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
            android.widget.TextView songTitle;
            android.widget.TextView artistName;
            android.widget.TextView duration;

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

    // Adapter for albums
    private static class AlbumGridAdapter extends RecyclerView.Adapter<AlbumGridAdapter.ViewHolder> {
        private List<Album> albums;

        public AlbumGridAdapter(List<Album> albums) {
            this.albums = albums;
        }

        public void setAlbums(List<Album> albums) {
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

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                albumName = itemView.findViewById(R.id.album_name);
                artistName = itemView.findViewById(R.id.artist_name);
            }

            public void bind(Album album) {
                albumName.setText(album.getName());
                artistName.setText(album.getArtist());

                itemView.setOnClickListener(v -> {
                    // TODO: Open album
                });
            }
        }
    }

    // Adapter for artists
    private static class ArtistGridAdapter extends RecyclerView.Adapter<ArtistGridAdapter.ViewHolder> {
        private List<Artist> artists;

        public ArtistGridAdapter(List<Artist> artists) {
            this.artists = artists;
        }

        public void setArtists(List<Artist> artists) {
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

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                artistName = itemView.findViewById(R.id.artist_name);
                songCount = itemView.findViewById(R.id.song_count);
            }

            public void bind(Artist artist) {
                artistName.setText(artist.getName());
                songCount.setText(artist.getSongCount() + " songs");

                itemView.setOnClickListener(v -> {
                    // TODO: Open artist
                });
            }
        }
    }
}
