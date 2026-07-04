package com.mynk.hlsplayer.player;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HlsPlayer {

    private final Context context;
    private ExoPlayer player;
    private final HlsPlayerView playerView;
    private HlsPlayerListener listener;
    private HlsPlayerController controller;

    private String currentVideoUrl;
    private String masterPlaylistBaseUrl;
    private List<String> availableQualities = new ArrayList<>();
    private final Map<String, String> qualityUrlMap = new HashMap<>();

    public interface HlsPlayerListener {
        void onPlayerReady();
        void onPlayerError(String error);
        void onPlaybackStateChanged(int state);
        default void onLiveDetected(boolean isLive) {}
    }

    public interface HlsPlayerExtendedListener extends HlsPlayerListener {
        void onQualityClicked();
        void onSubtitleClicked();
        void onFullscreenClicked();
        void onPipClicked();
    }

    public HlsPlayer(Context context, HlsPlayerView playerView) {
        this.context = context;
        this.playerView = playerView;
        initializePlayer();
    }

    private void initializePlayer() {
        try {
            player = new ExoPlayer.Builder(context).build();
            playerView.setPlayer(player);

            controller = new HlsPlayerController(context);
            controller.setPlayer(player);
            controller.setControllerListener(new HlsPlayerController.ControllerListener() {
                @Override public void onQualityClicked() {
                    if (listener instanceof HlsPlayerExtendedListener)
                        ((HlsPlayerExtendedListener) listener).onQualityClicked();
                }
                @Override public void onSubtitleClicked() {
                    if (listener instanceof HlsPlayerExtendedListener)
                        ((HlsPlayerExtendedListener) listener).onSubtitleClicked();
                }
                @Override public void onFullscreenClicked() {
                    if (listener instanceof HlsPlayerExtendedListener)
                        ((HlsPlayerExtendedListener) listener).onFullscreenClicked();
                }
                @Override public void onPipClicked() {
                    if (listener instanceof HlsPlayerExtendedListener)
                        ((HlsPlayerExtendedListener) listener).onPipClicked();
                }
            });
            playerView.addUserInteractionView(controller);
            playerView.setPlayerController(controller);

            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int state) {
                    if (listener != null) {
                        listener.onPlaybackStateChanged(state);
                        if (state == Player.STATE_READY && player.getPlayWhenReady()) {
                            listener.onPlayerReady();
                            if (controller != null) controller.startProgressUpdates();
                        }
                    }
                }
                @Override
                public void onPlayerError(androidx.media3.common.PlaybackException error) {
                    if (!retriedWithProgressive && currentVideoUrl != null) {
                        retriedWithProgressive = true;
                        Log.w("HlsPlayer", "HLS failed, retrying with Progressive: " + error.getMessage());
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                            playDirectUrl(currentVideoUrl, "mp4"));
                    } else {
                        if (listener != null) listener.onPlayerError(error.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            Log.e("HlsPlayer", "Init error: " + e.getMessage());
        }
    }

    private boolean retriedWithProgressive = false;

    public void playVideo(String url, String type) {
        try {
            currentVideoUrl = url;
            retriedWithProgressive = false;
            masterPlaylistBaseUrl = extractBaseUrl(url);
            playDirectUrl(url, "hls");
        } catch (Exception e) {
            if (listener != null) listener.onPlayerError(e.getMessage());
        }
    }

    private void playDirectUrl(String url, String type) {
        try {
            MediaSource mediaSource = createMediaSource(url, type);
            player.setMediaSource(mediaSource);
            player.prepare();
            player.setPlayWhenReady(true);
        } catch (Exception e) {
            if (listener != null) listener.onPlayerError(e.getMessage());
        }
    }

    private MediaSource createMediaSource(String url, String type) {
        Uri uri = Uri.parse(url);

        // Dynamically derive Referer and Origin from the URL itself
        String origin = extractOrigin(url);
        String referer = origin + "/";

        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/91.0 Mobile Safari/537.36");
        headers.put("Referer", referer);
        headers.put("Origin", origin);
        headers.put("Accept", "*/*");

        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/91.0 Mobile Safari/537.36")
                .setConnectTimeoutMs(30000)
                .setReadTimeoutMs(30000)
                .setDefaultRequestProperties(headers);

        DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(context, httpFactory);

        boolean isHls = (type != null && (type.equalsIgnoreCase("hls") || type.equalsIgnoreCase("m3u8")))
                || url.toLowerCase().contains(".m3u8");

        if (isHls) {
            fetchAndParseQualities(url, dataSourceFactory);
            return new HlsMediaSource.Factory(dataSourceFactory)
                    .setAllowChunklessPreparation(true)
                    .createMediaSource(MediaItem.fromUri(uri));
        } else {
            return new ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(uri));
        }
    }

    // Fetch master playlist in background to parse quality tracks
    private void fetchAndParseQualities(String url, DefaultDataSource.Factory unused) {
        new Thread(() -> {
            try {
                String finalUrl = url;
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(finalUrl).openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/91.0 Mobile Safari/537.36");
                conn.setRequestProperty("Referer", extractOrigin(finalUrl) + "/");
                int status = conn.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String redirectUrl = conn.getHeaderField("Location");
                    conn.disconnect();
                    if (redirectUrl != null) {
                        finalUrl = redirectUrl;
                        conn = (java.net.HttpURLConnection) new java.net.URL(finalUrl).openConnection();
                        conn.setConnectTimeout(10000);
                        conn.setReadTimeout(10000);
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/91.0 Mobile Safari/537.36");
                        status = conn.getResponseCode();
                    }
                }
                if (status == 200) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line).append("\n");
                    reader.close();
                    parseQualities(sb.toString(), finalUrl);
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.w("HlsPlayer", "Could not fetch qualities: " + e.getMessage());
            }
        }).start();
    }

    private void parseQualities(String playlist, String masterUrl) {
        availableQualities.clear();
        qualityUrlMap.clear();
        availableQualities.add("Auto");

        // Detect live stream — live playlists don't have #EXT-X-ENDLIST
        boolean isLive = !playlist.contains("#EXT-X-ENDLIST") && playlist.contains("#EXTINF");
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            if (listener != null) listener.onLiveDetected(isLive);
        });

        if (!playlist.contains("#EXT-X-STREAM-INF")) return;

        String base = masterUrl.substring(0, masterUrl.lastIndexOf('/') + 1);
        String origin = extractOrigin(masterUrl);
        String[] lines = playlist.split("\n");
        Pattern resPattern = Pattern.compile("RESOLUTION=\\d+x(\\d+)");

        for (int i = 0; i < lines.length - 1; i++) {
            String line = lines[i].trim();
            if (!line.startsWith("#EXT-X-STREAM-INF")) continue;
            String next = lines[i + 1].trim();
            Matcher m = resPattern.matcher(line);
            if (m.find()) {
                String quality = m.group(1) + "p";
                String streamUrl;
                if (next.startsWith("http://") || next.startsWith("https://")) {
                    streamUrl = next;
                } else if (next.startsWith("/")) {
                    streamUrl = origin + next;
                } else {
                    streamUrl = base + next;
                }
                if (!qualityUrlMap.containsKey(quality)) {
                    availableQualities.add(quality);
                    qualityUrlMap.put(quality, streamUrl);
                }
            }
        }

        if (availableQualities.size() > 1) {
            List<Integer> vals = new ArrayList<>();
            for (int i = 1; i < availableQualities.size(); i++) {
                try { vals.add(Integer.parseInt(availableQualities.get(i).replace("p", ""))); } catch (Exception ignored) {}
            }
            Collections.sort(vals, Collections.reverseOrder());
            List<String> sorted = new ArrayList<>();
            sorted.add("Auto");
            for (int v : vals) sorted.add(v + "p");
            availableQualities = sorted;
        }
    }

    // Extract scheme://host from any URL dynamically
    private String extractOrigin(String url) {
        try {
            java.net.URL u = new java.net.URL(url);
            return u.getProtocol() + "://" + u.getHost();
        } catch (Exception e) {
            return "";
        }
    }

    private String extractBaseUrl(String url) {
        try {
            java.net.URL u = new java.net.URL(url);
            return u.getProtocol() + "://" + u.getHost();
        } catch (Exception e) {
            return "";
        }
    }

    public void changeQuality(String quality) {
        if (player == null || currentVideoUrl == null) return;
        long pos = player.getCurrentPosition();
        boolean wasPlaying = player.isPlaying();
        String targetUrl = quality.equals("Auto") ? currentVideoUrl : qualityUrlMap.get(quality);
        if (targetUrl == null) return;
        playDirectUrl(targetUrl, "hls");
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (player != null) {
                player.seekTo(pos);
                if (wasPlaying) player.play();
            }
        }, 1000);
    }

public void setListener(HlsPlayerListener listener) { this.listener = listener; }
    public void pause() { if (player != null) player.pause(); }
    public void resume() { if (player != null) player.play(); }
    public void seekTo(long position) { if (player != null) player.seekTo(position); }
    public long getCurrentPosition() { return player != null ? player.getCurrentPosition() : 0; }
    public long getDuration() { return player != null ? player.getDuration() : 0; }
    public boolean isPlaying() { return player != null && player.isPlaying(); }
    public ExoPlayer getPlayer() { return player; }
    public List<String> getAvailableQualities() { return availableQualities; }

    public void release() {
        if (player != null) { player.release(); player = null; }
    }
}
