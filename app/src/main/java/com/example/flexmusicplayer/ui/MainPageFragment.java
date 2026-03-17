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
import com.example.flexmusicplayer.model.Song;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

public class MainPageFragment extends Fragment {

    private RecyclerView resultsRecycler;
    private SearchResultAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main_page, container, false);

        ImageButton settingsButton = view.findViewById(R.id.btn_settings);
        ImageButton avatarButton = view.findViewById(R.id.btn_avatar);
        resultsRecycler = view.findViewById(R.id.results_recycler);

        settingsButton.setOnClickListener(v -> openSettings());
        avatarButton.setOnClickListener(v -> {
            BottomNavigationView navigationView = requireActivity().findViewById(R.id.bottom_navigation);
            navigationView.setSelectedItemId(R.id.nav_my);
        });

        resultsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        resultsRecycler.setNestedScrollingEnabled(false);
        List<Song> songs = createMockSongs();
        adapter = new SearchResultAdapter(songs, this::onSongSelected);
        resultsRecycler.setAdapter(adapter);

        return view;
    }

    private void openSettings() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openSettings();
        }
    }

    private List<Song> createMockSongs() {
        List<Song> songs = new ArrayList<>();
        songs.add(new Song(1, "Midnight Melodies", "The Echo Collective", "Afterglow", 225000, ""));
        songs.add(new Song(2, "Neon Avenue", "Sora Lane", "City Lights", 198000, ""));
        return songs;
    }

    private void onSongSelected(Song song, List<Song> queue, int position) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).onSongPlaybackRequested(song, queue, position);
        }
    }

    private static class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.ViewHolder> {
        private final List<Song> songs;
        private final OnSongClickListener onSongClickListener;

        SearchResultAdapter(List<Song> songs, OnSongClickListener onSongClickListener) {
            this.songs = songs;
            this.onSongClickListener = onSongClickListener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_search_result, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(songs.get(position), songs, position, onSongClickListener);
        }

        @Override
        public int getItemCount() {
            return songs.size();
        }

        interface OnSongClickListener {
            void onSongClick(Song song, List<Song> queue, int position);
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView title;
            private final TextView subtitle;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.song_title);
                subtitle = itemView.findViewById(R.id.song_subtitle);
            }

            void bind(Song song, List<Song> queue, int position, OnSongClickListener onSongClickListener) {
                title.setText(song.getTitle());
                subtitle.setText(song.getArtist() + " • " + song.getAlbum());
                itemView.setOnClickListener(v -> onSongClickListener.onSongClick(song, queue, position));
            }
        }
    }
}
