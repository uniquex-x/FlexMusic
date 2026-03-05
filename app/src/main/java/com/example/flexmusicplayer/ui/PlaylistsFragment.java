package com.example.flexmusicplayer.ui;

import androidx.appcompat.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
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
import com.example.flexmusicplayer.model.Playlist;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class PlaylistsFragment extends Fragment {

    private RecyclerView playlistsRecycler;
    private View emptyState;
    private MaterialButton createButton;
    private MaterialButton createEmptyButton;
    private PlaylistVerticalAdapter playlistsAdapter;
    private List<Playlist> playlists;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_playlists, container, false);

        initViews(view);
        setupClickListeners();
        loadPlaylists();

        return view;
    }

    private void initViews(View view) {
        playlistsRecycler = view.findViewById(R.id.playlists_recycler);
        emptyState = view.findViewById(R.id.empty_state);
        createButton = view.findViewById(R.id.create_button);
        createEmptyButton = view.findViewById(R.id.create_empty_button);

        // Setup RecyclerView
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        playlistsRecycler.setLayoutManager(layoutManager);

        // Initialize adapter
        playlists = new ArrayList<>();
        playlistsAdapter = new PlaylistVerticalAdapter(playlists);
        playlistsRecycler.setAdapter(playlistsAdapter);
    }

    private void setupClickListeners() {
        createButton.setOnClickListener(v -> showCreatePlaylistDialog());
        createEmptyButton.setOnClickListener(v -> showCreatePlaylistDialog());
    }

    private void loadPlaylists() {
        // TODO: Load actual playlists from database
        playlists = createMockPlaylists();

        if (playlists.isEmpty()) {
            playlistsRecycler.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            playlistsRecycler.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
            playlistsAdapter.setPlaylists(playlists);
        }
    }

    private List<Playlist> createMockPlaylists() {
        List<Playlist> playlists = new ArrayList<>();

        Playlist playlist1 = new Playlist(1, "My Favorites", "My favorite songs collection");
        playlist1.setCoverUrl("https://example.com/cover1.jpg");
        playlists.add(playlist1);

        Playlist playlist2 = new Playlist(2, "Workout Mix", "High energy songs for working out");
        playlist2.setCoverUrl("https://example.com/cover2.jpg");
        playlists.add(playlist2);

        Playlist playlist3 = new Playlist(3, "Chill Vibes", "Relaxing music for chilling");
        playlist3.setCoverUrl("https://example.com/cover3.jpg");
        playlists.add(playlist3);

        return playlists;
    }

    private void showCreatePlaylistDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_create_playlist, null);

        EditText nameInput = dialogView.findViewById(R.id.playlist_name_input);
        EditText descriptionInput = dialogView.findViewById(R.id.playlist_description_input);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.create_playlist)
                .setView(dialogView)
                .setPositiveButton(R.string.save, (d, which) -> {
                    String name = nameInput.getText().toString().trim();
                    String description = descriptionInput.getText().toString().trim();

                    if (!TextUtils.isEmpty(name)) {
                        createPlaylist(name, description);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.show();
    }

    private void createPlaylist(String name, String description) {
        Playlist newPlaylist = new Playlist(System.currentTimeMillis(), name, description);
        playlists.add(newPlaylist);

        if (playlists.size() == 1) {
            playlistsRecycler.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
        }

        playlistsAdapter.setPlaylists(playlists);

        // TODO: Save to database
    }

    private void showPlaylistOptions(Playlist playlist) {
        String[] options = {getString(R.string.edit_playlist), getString(R.string.delete_playlist)};

        new AlertDialog.Builder(requireContext())
                .setTitle(playlist.getName())
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            // Edit playlist
                            showEditPlaylistDialog(playlist);
                            break;
                        case 1:
                            // Delete playlist
                            showDeletePlaylistDialog(playlist);
                            break;
                    }
                })
                .show();
    }

    private void showEditPlaylistDialog(Playlist playlist) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_create_playlist, null);

        EditText nameInput = dialogView.findViewById(R.id.playlist_name_input);
        EditText descriptionInput = dialogView.findViewById(R.id.playlist_description_input);

        nameInput.setText(playlist.getName());
        descriptionInput.setText(playlist.getDescription());

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.edit_playlist)
                .setView(dialogView)
                .setPositiveButton(R.string.save, (d, which) -> {
                    String name = nameInput.getText().toString().trim();
                    String description = descriptionInput.getText().toString().trim();

                    if (!TextUtils.isEmpty(name)) {
                        playlist.setName(name);
                        playlist.setDescription(description);
                        playlist.setModifiedDate(System.currentTimeMillis());
                        playlistsAdapter.notifyDataSetChanged();

                        // TODO: Update in database
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showDeletePlaylistDialog(Playlist playlist) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_delete_playlist_title)
                .setMessage(R.string.dialog_delete_playlist_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    deletePlaylist(playlist);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deletePlaylist(Playlist playlist) {
        playlists.remove(playlist);
        playlistsAdapter.setPlaylists(playlists);

        if (playlists.isEmpty()) {
            playlistsRecycler.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        }

        // TODO: Delete from database
    }

    private class PlaylistVerticalAdapter extends RecyclerView.Adapter<PlaylistVerticalAdapter.ViewHolder> {
        private List<Playlist> items;

        public PlaylistVerticalAdapter(List<Playlist> items) {
            this.items = items;
        }

        public void setPlaylists(List<Playlist> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_playlist_vertical, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Playlist playlist = items.get(position);
            holder.bind(playlist);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView playlistName;
            TextView songCount;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                playlistName = itemView.findViewById(R.id.playlist_name);
                songCount = itemView.findViewById(R.id.song_count);
            }

            public void bind(Playlist playlist) {
                playlistName.setText(playlist.getName());
                songCount.setText(getString(R.string.songs_count, playlist.getSongCount()));

                itemView.setOnClickListener(v -> {
                    // TODO: Open playlist
                });

                itemView.findViewById(R.id.options_button).setOnClickListener(v -> {
                    showPlaylistOptions(playlist);
                });
            }
        }
    }
}
