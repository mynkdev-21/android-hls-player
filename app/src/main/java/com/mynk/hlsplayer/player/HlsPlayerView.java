package com.mynk.hlsplayer.player;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import androidx.media3.ui.PlayerView;
import androidx.media3.exoplayer.ExoPlayer;

public class HlsPlayerView extends FrameLayout {
    
    private PlayerView playerView;
    private HlsPlayerController playerController;
    
    public HlsPlayerView(Context context) {
        super(context);
        init();
    }
    
    public HlsPlayerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    private void init() {
        playerView = new PlayerView(getContext());
        playerView.setUseController(false);
        playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setKeepScreenOn(true);
        addView(playerView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }
    
    public void setPlayer(ExoPlayer player) {
        playerView.setPlayer(player);
    }
    
    public void addUserInteractionView(View view) {
        if (view != null) {
            addView(view);
        }
    }
    
    public HlsPlayerController getPlayerController() {
        return playerController;
    }
    
    public void setPlayerController(HlsPlayerController controller) {
        this.playerController = controller;
    }
    
    public PlayerView getPlayerView() {
        return playerView;
    }
    
    public void setFullscreenMode(boolean isFullscreen) {
        if (playerView != null) {
            if (isFullscreen) {
                // Fill entire screen in fullscreen mode
                playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
            } else {
                // Fit content normally
                playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
            }
        }
    }
}