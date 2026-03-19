package com.example.flexmusicplayer.ui;

import android.text.TextUtils;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.flexmusicplayer.MainActivity;
import com.example.flexmusicplayer.R;
import com.example.flexmusicplayer.model.Playlist;
import com.example.flexmusicplayer.storage.PlaylistStore;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class MyFragment extends Fragment {

    public interface NavigationCallback {
        void navigateToFavorites();
        void navigateToRecent();
        void navigateToLocal();
        void navigateToTranscode();
        void navigateToPlaylistDetail(long playlistId);
    }

    private NavigationCallback navigationCallback;
    private RecyclerView playlistsRecycler;
    private PlaylistAdapter playlistAdapter;
    private TextView playlistsEmptyText;
    private PlaylistStore playlistStore;

    public void setNavigationCallback(NavigationCallback callback) {
        this.navigationCallback = callback;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_my, container, false);

        ImageButton settingsButton = view.findViewById(R.id.btn_settings);
        View favoritesCard = view.findViewById(R.id.card_favorites);
        View recentCard = view.findViewById(R.id.card_recent);
        View localCard = view.findViewById(R.id.card_local);
        View transcodeCard = view.findViewById(R.id.card_transcode);
        View createButton = view.findViewById(R.id.btn_create_playlist);
        playlistsRecycler = view.findViewById(R.id.playlists_recycler);
        playlistsEmptyText = view.findViewById(R.id.playlists_empty_text);
        playlistStore = new PlaylistStore(requireContext());

        settingsButton.setOnClickListener(v -> openSettings());
        favoritesCard.setOnClickListener(v -> navigateFavorites());
        recentCard.setOnClickListener(v -> navigateRecent());
        localCard.setOnClickListener(v -> navigateLocal());
        transcodeCard.setOnClickListener(v -> navigateTranscode());
        createButton.setOnClickListener(v -> showCreatePlaylistDialog());

        playlistsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        playlistsRecycler.setNestedScrollingEnabled(false);
        playlistAdapter = new PlaylistAdapter();
        playlistsRecycler.setAdapter(playlistAdapter);
        loadPlaylists();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadPlaylists();
    }

    private void openSettings() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openSettings();
        }
    }

    private void navigateFavorites() {
        if (navigationCallback != null) {
            navigationCallback.navigateToFavorites();
        }
    }

    private void navigateRecent() {
        if (navigationCallback != null) {
            navigationCallback.navigateToRecent();
        }
    }

    private void navigateLocal() {
        if (navigationCallback != null) {
            navigationCallback.navigateToLocal();
        }
    }

    private void navigateTranscode() {
        if (navigationCallback != null) {
            navigationCallback.navigateToTranscode();
        }
    }

    private void loadPlaylists() {
        if (playlistStore == null) {
            return;
        }
        List<Playlist> playlists = playlistStore.loadPlaylists();
        playlistAdapter.setPlaylists(playlists);
        boolean isEmpty = playlists.isEmpty();
        playlistsRecycler.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        playlistsEmptyText.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    private void showCreatePlaylistDialog() {
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
            playlistStore.createPlaylist(name, description);
            loadPlaylists();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void showPlaylistOptions(@NonNull Playlist playlist) {
        String[] options = {getString(R.string.edit_playlist), getString(R.string.delete_playlist)};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(playlist.getName())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showEditPlaylistDialog(playlist);
                    } else if (which == 1) {
                        showDeletePlaylistDialog(playlist);
                    }
                })
                .show();
    }

    private void showEditPlaylistDialog(@NonNull Playlist playlist) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_playlist, null);
        EditText nameInput = dialogView.findViewById(R.id.playlist_name_input);
        EditText descriptionInput = dialogView.findViewById(R.id.playlist_description_input);
        nameInput.setText(playlist.getName());
        descriptionInput.setText(playlist.getDescription());

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
            playlist.setName(name);
            playlist.setDescription(description);
            playlist.setModifiedDate(System.currentTimeMillis());
            playlistStore.updatePlaylist(playlist);
            loadPlaylists();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void showDeletePlaylistDialog(@NonNull Playlist playlist) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_delete_playlist_title)
                .setMessage(R.string.dialog_delete_playlist_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    playlistStore.deletePlaylist(playlist.getId());
                    loadPlaylists();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.ViewHolder> {
        private final List<Playlist> playlists = new ArrayList<>();

        void setPlaylists(@NonNull List<Playlist> updatedPlaylists) {
            playlists.clear();
            playlists.addAll(updatedPlaylists);
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
            holder.bind(playlists.get(position));
        }

        @Override
        public int getItemCount() {
            return playlists.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView playlistName;
            private final TextView playlistMeta;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                playlistName = itemView.findViewById(R.id.playlist_name);
                playlistMeta = itemView.findViewById(R.id.song_count);
            }

            void bind(Playlist playlist) {
                playlistName.setText(playlist.getName());
                if (TextUtils.isEmpty(playlist.getDescription())) {
                    playlistMeta.setText(getString(R.string.songs_count, playlist.getSongCount()));
                } else {
                    playlistMeta.setText(getString(
                            R.string.playlist_meta_with_description,
                            playlist.getDescription(),
                            playlist.getSongCount()));
                }
                itemView.setOnClickListener(v -> {
                    if (navigationCallback != null) {
                        navigationCallback.navigateToPlaylistDetail(playlist.getId());
                    }
                });
                itemView.findViewById(R.id.options_button).setOnClickListener(v -> showPlaylistOptions(playlist));
            }
        }
    }
}
