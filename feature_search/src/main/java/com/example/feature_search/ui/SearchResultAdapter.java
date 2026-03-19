package com.example.feature_search.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.core_domain.search.SearchTrack;
import com.example.feature_search.R;

import java.util.ArrayList;
import java.util.List;

public final class SearchResultAdapter extends BaseAdapter {

    private final LayoutInflater layoutInflater;
    private final List<SearchTrack> tracks = new ArrayList<>();

    public SearchResultAdapter(@NonNull Context context) {
        this.layoutInflater = LayoutInflater.from(context);
    }

    public void submitTracks(@NonNull List<SearchTrack> newTracks) {
        tracks.clear();
        tracks.addAll(newTracks);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return tracks.size();
    }

    @Override
    public SearchTrack getItem(int position) {
        return tracks.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder;
        if (convertView == null) {
            convertView = layoutInflater.inflate(R.layout.item_online_search_track, parent, false);
            viewHolder = new ViewHolder(convertView);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }
        viewHolder.bind(getItem(position));
        return convertView;
    }

    private static final class ViewHolder {
        private final TextView titleView;
        private final TextView subtitleView;
        private final TextView metaView;

        private ViewHolder(@NonNull View itemView) {
            titleView = itemView.findViewById(R.id.search_track_title);
            subtitleView = itemView.findViewById(R.id.search_track_subtitle);
            metaView = itemView.findViewById(R.id.search_track_meta);
        }

        private void bind(@NonNull SearchTrack track) {
            titleView.setText(track.getTitle());
            subtitleView.setText(track.getSubtitle());
            List<String> metaParts = new ArrayList<>();
            if (!TextUtils.isEmpty(track.getQualitySummary())) {
                metaParts.add(track.getQualitySummary());
            }
            if (track.getDurationMs() > 0L) {
                metaParts.add(formatDuration(track.getDurationMs()));
            }
            metaView.setText(TextUtils.join("  ·  ", metaParts));
        }

        @NonNull
        private String formatDuration(long durationMs) {
            long totalSeconds = durationMs / 1000L;
            long minutes = totalSeconds / 60L;
            long seconds = totalSeconds % 60L;
            return String.format("%d:%02d", minutes, seconds);
        }
    }
}
