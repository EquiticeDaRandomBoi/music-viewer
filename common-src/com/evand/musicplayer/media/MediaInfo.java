// common-src/com/evand/musicplayer/media/MediaInfo.java
package com.evand.musicplayer.media;

public record MediaInfo(
    String title,
    String artist,
    boolean isPlaying,
    long positionMs,
    long durationMs,
    String thumbnailPath,  // null if unavailable
    String sourceApp       // e.g. "Spotify.exe"
) {
    public boolean hasThumbnail() { return thumbnailPath != null && !thumbnailPath.isEmpty(); }

    public float progress() {
        if (durationMs <= 0) return 0f;
        return (float) positionMs / durationMs;
    }

    public String elapsedFormatted()   { return formatMs(positionMs); }
    public String remainingFormatted() { return "-" + formatMs(durationMs - positionMs); }

    private static String formatMs(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
