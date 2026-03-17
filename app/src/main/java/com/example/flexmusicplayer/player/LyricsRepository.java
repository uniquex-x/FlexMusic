package com.example.flexmusicplayer.player;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.flexmusicplayer.R;
import com.example.flexmusicplayer.model.Song;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LyricsRepository {

    private LyricsRepository() {
    }

    @NonNull
    public static List<LyricsLine> getLyrics(@NonNull Context context, Song song) {
        String title = song != null && song.getTitle() != null ? song.getTitle().trim().toLowerCase(Locale.US) : "";
        if (title.contains("midnight city")) {
            return createMidnightCityLyrics(context);
        }
        return createGenericLyrics(context, song);
    }

    private static List<LyricsLine> createMidnightCityLyrics(Context context) {
        List<LyricsLine> lines = new ArrayList<>();
        lines.add(new LyricsLine(0, context.getString(R.string.player_demo_line_one)));
        lines.add(new LyricsLine(12000, context.getString(R.string.player_demo_line_two)));
        lines.add(new LyricsLine(26000, context.getString(R.string.player_demo_line_three)));
        lines.add(new LyricsLine(40000, context.getString(R.string.player_demo_line_four)));
        lines.add(new LyricsLine(54000, context.getString(R.string.player_demo_line_five)));
        lines.add(new LyricsLine(70000, context.getString(R.string.player_demo_line_two)));
        lines.add(new LyricsLine(88000, context.getString(R.string.player_demo_line_three)));
        return lines;
    }

    private static List<LyricsLine> createGenericLyrics(Context context, Song song) {
        String title = song != null && song.getTitle() != null ? song.getTitle() : context.getString(R.string.player_title);
        String artist = song != null && song.getArtist() != null ? song.getArtist() : context.getString(R.string.player_unknown_artist);
        List<LyricsLine> lines = new ArrayList<>();
        lines.add(new LyricsLine(0, title));
        lines.add(new LyricsLine(18000, artist));
        lines.add(new LyricsLine(36000, context.getString(R.string.player_no_lyrics)));
        lines.add(new LyricsLine(54000, title + " • " + artist));
        return lines;
    }
}
