package com.example.flexmusicplayer.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.flexmusicplayer.R;
import com.example.flexmusicplayer.model.Playlist;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/**
 * 我的 Fragment - 个人导航中心
 * 提供喜欢、最近、本地、转码的快捷入口，以及收藏歌单列表
 */
public class MyFragment extends Fragment {

    public interface NavigationCallback {
        void navigateToFavorites();
        void navigateToRecent();
        void navigateToLocal();
        void navigateToTranscode();
    }

    private NavigationCallback navigationCallback;
    private MaterialCardView searchBarContainer;
    private MaterialButton btnFavorites;
    private MaterialButton btnRecent;
    private MaterialButton btnLocal;
    private MaterialButton btnTranscode;
    private RecyclerView playlistsRecycler;
    private PlaylistVerticalAdapter playlistsAdapter;

    public void setNavigationCallback(NavigationCallback callback) {
        this.navigationCallback = callback;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main_page, container, false);

        initViews(view);
        setupClickListeners();
        loadPlaylists();

        return view;
    }

    private void initViews(View view) {
        searchBarContainer = view.findViewById(R.id.search_bar_container);
        btnFavorites = view.findViewById(R.id.btn_favorites);
        btnRecent = view.findViewById(R.id.btn_recent);
        btnLocal = view.findViewById(R.id.btn_local);
        btnTranscode = view.findViewById(R.id.btn_transcode);
        playlistsRecycler = view.findViewById(R.id.playlists_recycler);

        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        playlistsRecycler.setLayoutManager(layoutManager);

        playlistsAdapter = new PlaylistVerticalAdapter(new ArrayList<>());
        playlistsRecycler.setAdapter(playlistsAdapter);
    }

    private void setupClickListeners() {
        searchBarContainer.setOnClickListener(v -> {
            // Navigate to search/home fragment
            if (getActivity() instanceof NavigationCallback) {
                // handled in activity
            }
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new HomeFragment())
                    .addToBackStack(null)
                    .commit();
        });

        btnFavorites.setOnClickListener(v -> {
            if (navigationCallback != null) {
                navigationCallback.navigateToFavorites();
            } else {
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new FavoritesFragment())
                        .addToBackStack(null)
                        .commit();
            }
        });

        btnRecent.setOnClickListener(v -> {
            if (navigationCallback != null) {
                navigationCallback.navigateToRecent();
            } else {
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new RecentFragment())
                        .addToBackStack(null)
                        .commit();
            }
        });

        btnLocal.setOnClickListener(v -> {
            if (navigationCallback != null) {
                navigationCallback.navigateToLocal();
            } else {
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new LocalFragment())
                        .addToBackStack(null)
                        .commit();
            }
        });

        btnTranscode.setOnClickListener(v -> {
            if (navigationCallback != null) {
                navigationCallback.navigateToTranscode();
            } else {
                // TODO: Navigate to transcode feature
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new TranscodeFragment())
                        .addToBackStack(null)
                        .commit();
            }
        });
    }

    private void loadPlaylists() {
        // TODO: Load actual playlists from database
        List<Playlist> playlists = createMockPlaylists();
        playlistsAdapter.setPlaylists(playlists);
    }

    private List<Playlist> createMockPlaylists() {
        List<Playlist> playlists = new ArrayList<>();
        playlists.add(new Playlist(1, "收藏歌单1", ""));
        playlists.add(new Playlist(2, "收藏歌单2", ""));
        playlists.add(new Playlist(3, "收藏歌单3", ""));
        return playlists;
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
            holder.bind(items.get(position));
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
                itemView.setOnClickListener(v -> { /* TODO: Open playlist */ });
            }
        }
    }
}
