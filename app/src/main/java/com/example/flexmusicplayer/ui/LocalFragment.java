package com.example.flexmusicplayer.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.flexmusicplayer.download.SongDownloadManager;
import com.example.flexmusicplayer.MainActivity;
import com.example.flexmusicplayer.R;
import com.example.flexmusicplayer.model.Album;
import com.example.flexmusicplayer.model.Artist;
import com.example.flexmusicplayer.model.Playlist;
import com.example.flexmusicplayer.model.Song;
import com.example.flexmusicplayer.storage.FavoriteSongsStore;
import com.example.flexmusicplayer.storage.LocalMusicStore;
import com.example.flexmusicplayer.storage.PlaylistStore;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
    private PlaylistStore playlistStore;
    private SongDownloadManager downloadManager;

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
        backButton.setOnClickListener(v -> navigateBack());
        localMusicStore = new LocalMusicStore(requireContext());
        favoriteSongsStore = new FavoriteSongsStore(requireContext());
        playlistStore = new PlaylistStore(requireContext());
        downloadManager = SongDownloadManager.getInstance(requireContext());

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

        uploadButton.setOnClickListener(v -> filePickerLauncher.launch(new String[]{"audioProcess/*"}));
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

    private void navigateBack() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).onBackPressed();
            return;
        }
        requireActivity().finish();
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
        List<Song> songs = new ArrayList<>(localMusicStore.loadSongs());
        favoriteSongsStore.applyFavoriteFlags(songs);
        downloadManager.refreshDownloadStates(songs);
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

    private void showSongOptions(@NonNull View anchorView, @NonNull Song song) {
        PopupMenu popupMenu = new PopupMenu(requireContext(), anchorView);
        popupMenu.inflate(R.menu.menu_local_song_more);
        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_add_to_playlist) {
                showAddToPlaylistDialog(song);
                return true;
            }
            if (itemId == R.id.action_delete_song) {
                showDeleteSongDialog(song);
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void requestSongDownload(@NonNull Song song, @NonNull Runnable onUiUpdated) {
        downloadManager.requestDownload(song, new SongDownloadManager.DownloadCallbacks() {
            @Override
            public void onDownloadStateChanged(@NonNull Song targetSong) {
                onUiUpdated.run();
            }

            @Override
            public void onDownloadSucceeded(@NonNull Song targetSong, boolean alreadyDownloaded) {
                onUiUpdated.run();
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(
                                requireContext(),
                                alreadyDownloaded ? R.string.download_already_exists_message : R.string.download_success_message,
                                Toast.LENGTH_SHORT)
                        .show();
            }

            @Override
            public void onDownloadFailed(@NonNull Song targetSong, @NonNull String message) {
                onUiUpdated.run();
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(
                                requireContext(),
                                message,
                                Toast.LENGTH_SHORT)
                        .show();
            }
        });
    }

    private void showAddToPlaylistDialog(@NonNull Song song) {
        List<Playlist> playlists = playlistStore.loadPlaylists();
        if (playlists.isEmpty()) {
            showCreatePlaylistDialog(song);
            return;
        }
        CharSequence[] items = new CharSequence[playlists.size() + 1];
        items[0] = getString(R.string.playlist_create_and_add);
        for (int index = 0; index < playlists.size(); index++) {
            Playlist playlist = playlists.get(index);
            items[index + 1] = playlist.getName() + "  (" + getString(R.string.songs_count, playlist.getSongCount()) + ")";
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.playlist_choose_target)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        showCreatePlaylistDialog(song);
                        return;
                    }
                    Playlist targetPlaylist = playlists.get(which - 1);
                    PlaylistStore.AddSongResult result =
                            playlistStore.addSongToPlaylist(targetPlaylist.getId(), song);
                    if (result == PlaylistStore.AddSongResult.ADDED) {
                        Toast.makeText(requireContext(), R.string.playlist_song_added, Toast.LENGTH_SHORT).show();
                    } else if (result == PlaylistStore.AddSongResult.ALREADY_EXISTS) {
                        Toast.makeText(requireContext(), R.string.playlist_song_exists, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showCreatePlaylistDialog(@NonNull Song song) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_playlist, null);
        EditText nameInput = dialogView.findViewById(R.id.playlist_name_input);
        EditText descriptionInput = dialogView.findViewById(R.id.playlist_description_input);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.create_playlist)
                .setView(dialogView)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String description = descriptionInput.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                nameInput.setError(getString(R.string.playlist_name_required));
                return;
            }
            playlistStore.createPlaylistWithSong(name, description, song);
            Toast.makeText(requireContext(), R.string.playlist_song_added, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void showDeleteSongDialog(@NonNull Song song) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_delete_song_title)
                .setMessage(R.string.dialog_delete_song_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> removeSong(song))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void removeSong(@NonNull Song song) {
        if (!localMusicStore.deleteSong(song)) {
            Toast.makeText(requireContext(), R.string.local_song_remove_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        Snackbar.make(requireView(), R.string.local_song_removed, Snackbar.LENGTH_SHORT).show();
        loadLocalMusic();
    }

    private class SongVerticalAdapter extends RecyclerView.Adapter<SongVerticalAdapter.ViewHolder> {
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

        class ViewHolder extends RecyclerView.ViewHolder {
            private final MaterialCardView albumArtCard;
            private final android.widget.TextView songTitle;
            private final android.widget.TextView songMeta;
            private final android.widget.ImageButton favoriteButton;
            private final android.widget.ImageButton downloadButton;
            private final android.widget.ImageButton moreButton;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                albumArtCard = itemView.findViewById(R.id.album_art_card);
                songTitle = itemView.findViewById(R.id.song_title);
                songMeta = itemView.findViewById(R.id.song_meta);
                favoriteButton = itemView.findViewById(R.id.favorite_button);
                downloadButton = itemView.findViewById(R.id.download_button);
                moreButton = itemView.findViewById(R.id.more_button);
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
                    song.setFavorite(favoriteSongsStore.toggleFavorite(song));
                    updateFavorite(song);
                });

                downloadButton.setOnClickListener(v -> {
                    requestSongDownload(song, () -> updateDownload(song));
                });

                moreButton.setOnClickListener(v -> showSongOptions(v, song));
            }

            private void updateFavorite(Song song) {
                int tint = ContextCompat.getColor(itemView.getContext(),
                        song.isFavorite() ? R.color.player_bar_background : R.color.gray_400);
                favoriteButton.setImageTintList(android.content.res.ColorStateList.valueOf(tint));
            }

            private void updateDownload(Song song) {
                boolean inFlight = downloadManager.isDownloadInFlight(song);
                int tint = ContextCompat.getColor(itemView.getContext(),
                        song.isDownloaded() || inFlight ? R.color.gray_500 : R.color.gray_300);
                downloadButton.setImageResource(song.isDownloaded() ? R.drawable.ic_check_small : R.drawable.ic_download);
                downloadButton.setImageTintList(android.content.res.ColorStateList.valueOf(tint));
                downloadButton.setEnabled(!inFlight);
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
