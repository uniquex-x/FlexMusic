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
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class RecentFragment extends Fragment {

    private RecyclerView recentRecycler;
    private View emptyState;
    private MaterialButton browseButton;
    private RecentAdapter recentAdapter;
    private List<SongWithDate> recentSongs;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_recent, container, false);

        initViews(view);
        setupClickListeners();
        loadRecentSongs();

        return view;
    }

    private void initViews(View view) {
        recentRecycler = view.findViewById(R.id.recent_recycler);
        emptyState = view.findViewById(R.id.empty_state);
        browseButton = view.findViewById(R.id.browse_button);

        // Setup RecyclerView
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        recentRecycler.setLayoutManager(layoutManager);

        // Initialize adapter
        recentSongs = new ArrayList<>();
        recentAdapter = new RecentAdapter(recentSongs);
        recentRecycler.setAdapter(recentAdapter);
    }

    private void setupClickListeners() {
        browseButton.setOnClickListener(v -> {
            // TODO: Navigate to home
        });
    }

    private void loadRecentSongs() {
        // TODO: Load actual recent songs from database
        List<Song> songs = createMockRecentSongs();

        if (songs.isEmpty()) {
            recentRecycler.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            recentRecycler.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
            groupSongsByDate(songs);
        }
    }

    private void groupSongsByDate(List<Song> songs) {
        recentSongs.clear();

        Map<Long, List<Song>> groupedByDate = new TreeMap<>((o1, o2) -> o2.compareTo(o1));

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        Calendar cal = Calendar.getInstance();

        for (Song song : songs) {
            if (song.getLastPlayedDate() > 0) {
                Date playDate = new Date(song.getLastPlayedDate());
                long dateKey = Long.parseLong(sdf.format(playDate));

                if (!groupedByDate.containsKey(dateKey)) {
                    groupedByDate.put(dateKey, new ArrayList<>());
                }
                groupedByDate.get(dateKey).add(song);
            }
        }

        // Convert to display format
        for (Map.Entry<Long, List<Song>> entry : groupedByDate.entrySet()) {
            String dateLabel = getDateLabel(entry.getKey());
            for (Song song : entry.getValue()) {
                recentSongs.add(new SongWithDate(song, dateLabel));
            }
        }

        recentAdapter.notifyDataSetChanged();
    }

    private String getDateLabel(long dateKey) {
        Calendar cal = Calendar.getInstance();
        int today = Integer.parseInt(new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(cal.getTime()));

        cal.add(Calendar.DAY_OF_YEAR, -1);
        int yesterday = Integer.parseInt(new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(cal.getTime()));

        cal = Calendar.getInstance();
        cal.add(Calendar.WEEK_OF_YEAR, -1);
        int weekAgo = Integer.parseInt(new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(cal.getTime()));

        int dateInt = (int) dateKey;

        if (dateInt == today) {
            return getString(R.string.recent_today);
        } else if (dateInt == yesterday) {
            return getString(R.string.recent_yesterday);
        } else if (dateInt >= weekAgo) {
            return getString(R.string.recent_this_week);
        } else {
            return getString(R.string.recent_earlier);
        }
    }

    private List<Song> createMockRecentSongs() {
        List<Song> songs = new ArrayList<>();
        Calendar cal = Calendar.getInstance();

        // Today's songs
        Song song1 = new Song(1, "Recent Song 1", "Artist 1", "Album 1", (3 * 60 + 30) * 1000, "url1.mp3");
        song1.setLastPlayedDate(cal.getTimeInMillis());
        songs.add(song1);

        song1 = new Song(2, "Recent Song 2", "Artist 2", "Album 2", (4 * 60 + 15) * 1000, "url2.mp3");
        song1.setLastPlayedDate(cal.getTimeInMillis() - 3600000); // 1 hour ago
        songs.add(song1);

        // Yesterday's song
        cal.add(Calendar.DAY_OF_YEAR, -1);
        Song song2 = new Song(3, "Recent Song 3", "Artist 3", "Album 3", (5 * 60 + 20) * 1000, "url3.mp3");
        song2.setLastPlayedDate(cal.getTimeInMillis());
        songs.add(song2);

        return songs;
    }

    private static class SongWithDate {
        Song song;
        String dateLabel;

        public SongWithDate(Song song, String dateLabel) {
            this.song = song;
            this.dateLabel = dateLabel;
        }
    }

    private class RecentAdapter extends RecyclerView.Adapter<RecentAdapter.ViewHolder> {
        private List<SongWithDate> items;

        public RecentAdapter(List<SongWithDate> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_song_with_date, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SongWithDate item = items.get(position);
            holder.bind(item);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView dateLabel;
            TextView songTitle;
            TextView artistName;
            TextView duration;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                dateLabel = itemView.findViewById(R.id.date_label);
                songTitle = itemView.findViewById(R.id.song_title);
                artistName = itemView.findViewById(R.id.artist_name);
                duration = itemView.findViewById(R.id.duration);
            }

            public void bind(SongWithDate item) {
                dateLabel.setText(item.dateLabel);
                songTitle.setText(item.song.getTitle());
                artistName.setText(item.song.getArtist());
                duration.setText(item.song.getFormattedDuration());

                itemView.setOnClickListener(v -> {
                    // TODO: Play song
                });
            }
        }
    }
}
