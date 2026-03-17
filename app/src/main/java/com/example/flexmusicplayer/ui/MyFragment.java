package com.example.flexmusicplayer.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.flexmusicplayer.MainActivity;
import com.example.flexmusicplayer.R;
import com.example.flexmusicplayer.model.Playlist;

import java.util.ArrayList;
import java.util.List;

public class MyFragment extends Fragment {

    public interface NavigationCallback {
        void navigateToFavorites();
        void navigateToRecent();
        void navigateToLocal();
        void navigateToTranscode();
    }

    private NavigationCallback navigationCallback;
    private RecyclerView playlistsRecycler;
    private PlaylistAdapter playlistAdapter;

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

        settingsButton.setOnClickListener(v -> openSettings());
        favoritesCard.setOnClickListener(v -> navigateFavorites());
        recentCard.setOnClickListener(v -> navigateRecent());
        localCard.setOnClickListener(v -> navigateLocal());
        transcodeCard.setOnClickListener(v -> navigateTranscode());
        createButton.setOnClickListener(v -> { /* UI-only placeholder. */ });

        playlistsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        playlistsRecycler.setNestedScrollingEnabled(false);
        playlistAdapter = new PlaylistAdapter(createMockPlaylists());
        playlistsRecycler.setAdapter(playlistAdapter);

        return view;
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

    private List<Playlist> createMockPlaylists() {
        List<Playlist> playlists = new ArrayList<>();
        playlists.add(createPlaylist(1, "Late Night Vibes", "Created by you • 24 songs"));
        playlists.add(createPlaylist(2, "Focus Flow", "Created by you • 56 songs"));
        playlists.add(createPlaylist(3, "Starry Night", "Created by you • 18 songs"));
        return playlists;
    }

    private Playlist createPlaylist(long id, String name, String description) {
        Playlist playlist = new Playlist(id, name, description);
        playlist.setDescription(description);
        return playlist;
    }

    private static class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.ViewHolder> {
        private final List<Playlist> playlists;

        PlaylistAdapter(List<Playlist> playlists) {
            this.playlists = playlists;
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

        static class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView playlistName;
            private final TextView playlistMeta;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                playlistName = itemView.findViewById(R.id.playlist_name);
                playlistMeta = itemView.findViewById(R.id.song_count);
            }

            void bind(Playlist playlist) {
                playlistName.setText(playlist.getName());
                playlistMeta.setText(playlist.getDescription());
            }
        }
    }
}
