package com.example.flexmusicplayer.ui;

import android.os.Bundle;
import android.text.format.DateUtils;
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
import com.example.flexmusicplayer.storage.RecentPlaybackStore;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class RecentFragment extends Fragment implements RecentPlaybackStore.Listener {

    private RecyclerView recentRecycler;
    private View emptyState;
    private MaterialButton browseButton;
    private RecentAdapter recentAdapter;
    private List<SongWithDate> recentSongs;
    private final RecentPlaybackStore recentPlaybackStore = RecentPlaybackStore.getInstance();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_recent, container, false);

        initViews(view);
        setupClickListeners();
        loadRecentSongs(recentPlaybackStore.getRecentSongs());

        return view;
    }

    private void initViews(View view) {
        ImageButton backButton = view.findViewById(R.id.btn_back);
        recentRecycler = view.findViewById(R.id.recent_recycler);
        emptyState = view.findViewById(R.id.empty_state);
        browseButton = view.findViewById(R.id.browse_button);
        backButton.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());

        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        recentRecycler.setLayoutManager(layoutManager);
        recentSongs = new ArrayList<>();
        recentAdapter = new RecentAdapter(recentSongs);
        recentRecycler.setAdapter(recentAdapter);
    }

    private void setupClickListeners() {
        browseButton.setOnClickListener(v -> {
            BottomNavigationView navigationView = requireActivity().findViewById(R.id.bottom_navigation);
            navigationView.setSelectedItemId(R.id.nav_main_page);
        });
    }

    private void loadRecentSongs(@NonNull List<Song> songs) {
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

    @Override
    public void onStart() {
        super.onStart();
        recentPlaybackStore.addListener(this);
    }

    @Override
    public void onStop() {
        recentPlaybackStore.removeListener(this);
        super.onStop();
    }

    @Override
    public void onRecentSongsChanged(@NonNull List<Song> songs) {
        if (!isAdded()) {
            return;
        }
        loadRecentSongs(songs);
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
                    .inflate(R.layout.item_recent_song, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SongWithDate item = items.get(position);
            boolean showDateLabel = position == 0
                    || !item.dateLabel.equals(items.get(position - 1).dateLabel);
            holder.bind(item, buildQueue(), position, showDateLabel);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView dateLabel;
            TextView songTitle;
            TextView artistName;
            TextView timeAgo;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                dateLabel = itemView.findViewById(R.id.date_label);
                songTitle = itemView.findViewById(R.id.song_title);
                artistName = itemView.findViewById(R.id.artist_name);
                timeAgo = itemView.findViewById(R.id.time_ago);
            }

            public void bind(SongWithDate item, List<Song> queue, int position, boolean showDateLabel) {
                dateLabel.setText(item.dateLabel);
                dateLabel.setVisibility(showDateLabel ? View.VISIBLE : View.GONE);
                songTitle.setText(item.song.getTitle());
                artistName.setText(item.song.getArtist());
                timeAgo.setText(getRelativeTimeLabel(item.song.getLastPlayedDate()));

                itemView.setOnClickListener(v -> {
                    if (itemView.getContext() instanceof MainActivity) {
                        ((MainActivity) itemView.getContext()).onSongPlaybackRequested(item.song, queue, position);
                    }
                });
            }
        }

        private List<Song> buildQueue() {
            List<Song> queue = new ArrayList<>();
            for (SongWithDate item : items) {
                queue.add(item.song);
            }
            return queue;
        }
    }

    private CharSequence getRelativeTimeLabel(long lastPlayedDate) {
        return DateUtils.getRelativeTimeSpanString(
                lastPlayedDate,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE);
    }
}
