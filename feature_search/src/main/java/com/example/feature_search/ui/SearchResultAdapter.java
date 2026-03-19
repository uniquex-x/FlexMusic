package com.example.feature_search.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.core_domain.search.SearchTrack;
import com.example.feature_search.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    private static final class ViewHolder {
        private final FrameLayout artFrameView;
        private final ImageView artIconView;
        private final TextView titleView;
        private final TextView subtitleView;
        private final TextView qualityView;
        private final TextView metaView;
        private final ImageButton moreButton;

        private ViewHolder(@NonNull View itemView) {
            artFrameView = itemView.findViewById(R.id.search_track_art_frame);
            artIconView = itemView.findViewById(R.id.search_track_art_icon);
            titleView = itemView.findViewById(R.id.search_track_title);
            subtitleView = itemView.findViewById(R.id.search_track_subtitle);
            qualityView = itemView.findViewById(R.id.search_track_quality);
            metaView = itemView.findViewById(R.id.search_track_meta);
            moreButton = itemView.findViewById(R.id.search_track_more);
        }

        private void bind(@NonNull SearchTrack track, int position) {
            titleView.setText(track.getTitle());
            subtitleView.setText(track.getSubtitle());
            qualityView.setText(resolveQualityLabel(track.getQualitySummary()));
            metaView.setText(track.getDurationMs() > 0L ? formatDuration(track.getDurationMs()) : "");
            bindArtwork(position);
            moreButton.setOnClickListener(v -> { });
            artIconView.setAlpha(0.96f);
        }

        @NonNull
        private String formatDuration(long durationMs) {
            long totalSeconds = durationMs / 1000L;
            long minutes = totalSeconds / 60L;
            long seconds = totalSeconds % 60L;
            return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
        }

        @NonNull
        private String resolveQualityLabel(@NonNull String qualitySummary) {
            if (TextUtils.isEmpty(qualitySummary)) {
                return "ONLINE";
            }
            String normalized = qualitySummary.toUpperCase();
            if (normalized.contains("FLAC")) {
                return "LOSSLESS";
            }
            if (normalized.contains("VBR")) {
                return "MP3 VBR";
            }
            if (normalized.contains("OGG")) {
                return "OGG";
            }
            if (normalized.contains("MP3")) {
                return "MP3 320";
            }
            return qualitySummary.toUpperCase();
        }

        private void bindArtwork(int position) {
            int mod = position % 3;
            if (mod == 1) {
                artFrameView.setBackgroundResource(R.drawable.bg_feature_search_track_art_b);
            } else if (mod == 2) {
                artFrameView.setBackgroundResource(R.drawable.bg_feature_search_track_art_c);
            } else {
                artFrameView.setBackgroundResource(R.drawable.bg_feature_search_track_art_a);
            }
        }
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
        viewHolder.bind(getItem(position), position);
        return convertView;
    }
}
