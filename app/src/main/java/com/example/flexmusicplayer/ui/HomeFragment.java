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
import com.example.flexmusicplayer.model.Song;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页 Fragment - 音乐发现页
 * 提供 trending / daily recommend / top list 三个跳转入口，以及最近播放列表
 */
public class HomeFragment extends Fragment {

    private MaterialCardView searchBarContainer;
    private MaterialCardView cardTrending;
    private MaterialCardView cardDailyRecommend;
    private MaterialCardView cardTopList;
    private RecyclerView recentlyPlayedRecycler;

    private RecentSongAdapter recentAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        initViews(view);
        setupClickListeners();
        loadRecentlyPlayed();
        return view;
    }

    private void initViews(View view) {
        searchBarContainer    = view.findViewById(R.id.search_bar_container);
        cardTrending          = view.findViewById(R.id.card_trending);
        cardDailyRecommend    = view.findViewById(R.id.card_daily_recommend);
        cardTopList           = view.findViewById(R.id.card_top_list);
        recentlyPlayedRecycler = view.findViewById(R.id.recently_played_recycler);

        recentAdapter = new RecentSongAdapter(new ArrayList<>());
        recentlyPlayedRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recentlyPlayedRecycler.setAdapter(recentAdapter);
    }

    private void setupClickListeners() {
        searchBarContainer.setOnClickListener(v ->
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new HomeFragment())
                        .addToBackStack(null)
                        .commit());

        cardTrending.setOnClickListener(v -> {
            // TODO: 跳转至 Trending 详情页
        });

        cardDailyRecommend.setOnClickListener(v -> {
            // TODO: 跳转至 Daily Recommend 详情页
        });

        cardTopList.setOnClickListener(v -> {
            // TODO: 跳转至 Top List 详情页
        });
    }

    private void loadRecentlyPlayed() {
        // TODO: 从数据库加载真实的最近播放数据
        List<Song> recentSongs = createMockSongs(6);
        recentAdapter.setSongs(recentSongs);
    }

    private List<Song> createMockSongs(int count) {
        List<Song> songs = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            songs.add(new Song(i, "Song " + i, "Artist " + i, "Album " + i,
                    (3 * 60 + 30) * 1000, ""));
        }
        return songs;
    }

    // -------------------- Inner Adapter --------------------

    private static class RecentSongAdapter extends RecyclerView.Adapter<RecentSongAdapter.ViewHolder> {
        private List<Song> songs;

        public RecentSongAdapter(List<Song> songs) {
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
            holder.bind(songs.get(position));
        }

        @Override
        public int getItemCount() {
            return songs.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView songTitle, artistName, duration;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                songTitle  = itemView.findViewById(R.id.song_title);
                artistName = itemView.findViewById(R.id.artist_name);
                duration   = itemView.findViewById(R.id.duration);
            }

            public void bind(Song song) {
                songTitle.setText(song.getTitle());
                artistName.setText(song.getArtist());
                if (duration != null) duration.setText(song.getFormattedDuration());
                itemView.setOnClickListener(v -> { /* TODO: Play song */ });
            }
        }
    }
}
