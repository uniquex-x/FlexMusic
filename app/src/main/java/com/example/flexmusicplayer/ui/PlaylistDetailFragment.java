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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.flexmusicplayer.MainActivity;
import com.example.flexmusicplayer.R;
import com.example.flexmusicplayer.model.Playlist;
import com.example.flexmusicplayer.model.Song;
import com.example.flexmusicplayer.storage.PlaylistStore;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class PlaylistDetailFragment extends Fragment {

    private static final String ARG_PLAYLIST_ID = "playlist_id";
    private static final int[] ART_COLORS = {
            Color.parseColor("#254C45"),
            Color.parseColor("#4B3A63"),
            Color.parseColor("#7E684B"),
            Color.parseColor("#5A214F"),
            Color.parseColor("#0D5474")
    };

    public static PlaylistDetailFragment newInstance(long playlistId) {
        PlaylistDetailFragment fragment = new PlaylistDetailFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_PLAYLIST_ID, playlistId);
        fragment.setArguments(args);
        return fragment;
    }

    private long playlistId;
    private PlaylistStore playlistStore;
    private Playlist currentPlaylist;
    private TextView playlistNameView;
    private TextView playlistDescriptionView;
    private TextView playlistStatsView;
    private RecyclerView songsRecycler;
    private TextView emptySongsView;
    private SongAdapter songAdapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        playlistId = getArguments() == null ? -1L : getArguments().getLong(ARG_PLAYLIST_ID, -1L);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_playlist_detail, container, false);
        playlistStore = new PlaylistStore(requireContext());
        initViews(view);
        setupRecycler();
        setupListeners(view);
        loadPlaylist();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadPlaylist();
    }

    private void initViews(@NonNull View view) {
        playlistNameView = view.findViewById(R.id.playlist_detail_title);
        playlistDescriptionView = view.findViewById(R.id.playlist_detail_description);
        playlistStatsView = view.findViewById(R.id.playlist_detail_stats);
        songsRecycler = view.findViewById(R.id.playlist_songs_recycler);
        emptySongsView = view.findViewById(R.id.playlist_songs_empty);
    }

    private void setupRecycler() {
        songsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        songsRecycler.setNestedScrollingEnabled(false);
        songAdapter = new SongAdapter();
        songsRecycler.setAdapter(songAdapter);
    }

    private void setupListeners(@NonNull View view) {
        ImageButton backButton = view.findViewById(R.id.btn_back);
        ImageButton moreButton = view.findViewById(R.id.btn_more);
        MaterialButton playAllButton = view.findViewById(R.id.btn_play_all);
        backButton.setOnClickListener(v -> navigateBack());
        moreButton.setOnClickListener(v -> showPlaylistOptions());
        playAllButton.setOnClickListener(v -> playAllSongs());
    }

    private void loadPlaylist() {
        currentPlaylist = playlistStore.findPlaylistById(playlistId);
        if (currentPlaylist == null) {
            navigateBack();
            return;
        }
        playlistNameView.setText(currentPlaylist.getName());
        if (TextUtils.isEmpty(currentPlaylist.getDescription())) {
            playlistDescriptionView.setText(R.string.my_playlists_empty);
        } else {
            playlistDescriptionView.setText(currentPlaylist.getDescription());
        }
        playlistStatsView.setText(getString(
                R.string.playlist_detail_summary,
                currentPlaylist.getSongCount(),
                currentPlaylist.getFormattedTotalDuration()));
        List<Song> songs = currentPlaylist.getSongs() == null
                ? new ArrayList<>()
                : new ArrayList<>(currentPlaylist.getSongs());
        songAdapter.setSongs(songs);
        boolean isEmpty = songs.isEmpty();
        songsRecycler.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        emptySongsView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    private void playAllSongs() {
        if (currentPlaylist == null || currentPlaylist.getSongs() == null || currentPlaylist.getSongs().isEmpty()) {
            Toast.makeText(requireContext(), R.string.playlist_detail_empty_songs, Toast.LENGTH_SHORT).show();
            return;
        }
        if (getActivity() instanceof MainActivity) {
            List<Song> queue = new ArrayList<>(currentPlaylist.getSongs());
            ((MainActivity) getActivity()).onSongPlaybackRequested(queue.get(0), queue, 0);
        }
    }

    private void showPlaylistOptions() {
        if (currentPlaylist == null) {
            return;
        }
        String[] options = {getString(R.string.edit_playlist), getString(R.string.delete_playlist)};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(currentPlaylist.getName())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showEditPlaylistDialog();
                    } else if (which == 1) {
                        showDeletePlaylistDialog();
                    }
                })
                .show();
    }

    private void showEditPlaylistDialog() {
        if (currentPlaylist == null) {
            return;
        }
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_playlist, null);
        EditText nameInput = dialogView.findViewById(R.id.playlist_name_input);
        EditText descriptionInput = dialogView.findViewById(R.id.playlist_description_input);
        nameInput.setText(currentPlaylist.getName());
        descriptionInput.setText(currentPlaylist.getDescription());

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.edit_playlist)
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
            currentPlaylist.setName(name);
            currentPlaylist.setDescription(description);
            currentPlaylist.setModifiedDate(System.currentTimeMillis());
            playlistStore.updatePlaylist(currentPlaylist);
            loadPlaylist();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void showDeletePlaylistDialog() {
        if (currentPlaylist == null) {
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_delete_playlist_title)
                .setMessage(R.string.dialog_delete_playlist_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    playlistStore.deletePlaylist(currentPlaylist.getId());
                    navigateBack();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void navigateBack() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).onBackPressed();
            return;
        }
        requireActivity().finish();
    }

    private void showSongOptions(@NonNull Song song) {
        String[] options = {
                getString(R.string.playlist_detail_add_to_other_playlist),
                getString(R.string.playlist_detail_remove_song),
                getString(R.string.player_menu_share)
        };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(song.getTitle())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showAddToOtherPlaylistDialog(song);
                    } else if (which == 1) {
                        removeSongFromCurrentPlaylist(song);
                    } else if (which == 2) {
                        shareSong(song);
                    }
                })
                .show();
    }

    private void showAddToOtherPlaylistDialog(@NonNull Song song) {
        List<Playlist> allPlaylists = playlistStore.loadPlaylists();
        List<Playlist> targetPlaylists = new ArrayList<>();
        for (Playlist playlist : allPlaylists) {
            if (playlist.getId() != playlistId) {
                targetPlaylists.add(playlist);
            }
        }
        if (targetPlaylists.isEmpty()) {
            Toast.makeText(requireContext(), R.string.playlist_detail_no_other_playlists, Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence[] items = new CharSequence[targetPlaylists.size()];
        for (int index = 0; index < targetPlaylists.size(); index++) {
            Playlist playlist = targetPlaylists.get(index);
            items[index] = playlist.getName() + "  (" + getString(R.string.songs_count, playlist.getSongCount()) + ")";
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.playlist_choose_target)
                .setItems(items, (dialog, which) -> {
                    Playlist targetPlaylist = targetPlaylists.get(which);
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

    private void removeSongFromCurrentPlaylist(@NonNull Song song) {
        if (playlistStore.removeSongFromPlaylist(playlistId, song)) {
            Toast.makeText(requireContext(), R.string.playlist_detail_removed, Toast.LENGTH_SHORT).show();
            loadPlaylist();
        }
    }

    private void shareSong(@NonNull Song song) {
        String artist = TextUtils.isEmpty(song.getArtist())
                ? getString(R.string.player_unknown_artist)
                : song.getArtist();
        String shareText = getString(R.string.player_share_text, song.getTitle(), artist);
        if (!TextUtils.isEmpty(song.getAudioUrl())) {
            shareText = shareText + "\n" + song.getAudioUrl();
        }
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.player_share_chooser)));
    }

    private class SongAdapter extends RecyclerView.Adapter<SongAdapter.ViewHolder> {
        private final List<Song> songs = new ArrayList<>();

        void setSongs(@NonNull List<Song> updatedSongs) {
            songs.clear();
            songs.addAll(updatedSongs);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_playlist_detail_song, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(songs.get(position), position);
        }

        @Override
        public int getItemCount() {
            return songs.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final MaterialCardView albumArtCard;
            private final TextView songTitle;
            private final TextView artistName;
            private final TextView durationView;
            private final ImageButton moreButton;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                albumArtCard = itemView.findViewById(R.id.album_art_card);
                songTitle = itemView.findViewById(R.id.song_title);
                artistName = itemView.findViewById(R.id.artist_name);
                durationView = itemView.findViewById(R.id.song_duration);
                moreButton = itemView.findViewById(R.id.song_more_button);
            }

            void bind(@NonNull Song song, int position) {
                songTitle.setText(song.getTitle());
                artistName.setText(TextUtils.isEmpty(song.getArtist())
                        ? getString(R.string.player_unknown_artist)
                        : song.getArtist());
                durationView.setText(song.getFormattedDuration());
                albumArtCard.setCardBackgroundColor(ART_COLORS[position % ART_COLORS.length]);
                itemView.setOnClickListener(v -> {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).onSongPlaybackRequested(song, songs, position);
                    }
                });
                moreButton.setOnClickListener(v -> showSongOptions(song));
            }
        }
    }
}
