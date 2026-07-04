package com.mynk.hlsplayer.player;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import com.mynk.hlsplayer.R;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.common.Player;

public class HlsPlayerController extends FrameLayout {
    
    public interface ControllerListener {
        void onQualityClicked();
        void onSubtitleClicked();
        void onFullscreenClicked();
        void onPipClicked();
    }
    
    private boolean subtitlesAvailable = false;
    private boolean subtitlesEnabled = false;
    private boolean isLiveTvMode = false;
    
    private ExoPlayer player;
    private ControllerListener controllerListener;
    private ImageView playPauseButton;
    private ImageView bottomPlayPauseButton;
    private ImageView rewindButton;
    private ImageView forwardButton;
    private ImageView backButton;
    private ImageView pipButton;
    private ImageView qualityButton;
    private ImageView subtitleButton;
    private ImageView fullscreenButton;
    private TextView titleText;
    private SeekBar progressBar;
    private TextView currentTime;
    private TextView totalTime;
    private View controlsContainer;
    
    // New UI elements
    private LinearLayout brightnessControl;
    private LinearLayout volumeControl;
    private ImageView brightnessIcon;
    private ImageView volumeIcon;
    private View brightnessProgress;
    private View volumeProgress;
    private TextView subtitleText;
    private LinearLayout seekPopup;
    private LinearLayout backwardSeekPopup;
    private LinearLayout forwardSeekPopup;
    private TextView backwardSeekText;
    private TextView forwardSeekText;
    private LinearLayout loadingView;
    private android.widget.ProgressBar bufferingProgress;
    private LinearLayout liveIndicator;
    
    private boolean isControlsVisible = true;
    private Runnable hideControlsRunnable;
    private Runnable hideGestureRunnable;
    
    public HlsPlayerController(Context context) {
        super(context);
        init();
    }
    
    public HlsPlayerController(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.easyplex_player_controls, this, true);
        
        playPauseButton = findViewById(R.id.btn_play_pause);
        bottomPlayPauseButton = findViewById(R.id.btn_bottom_play_pause);
        rewindButton = findViewById(R.id.btn_rewind);
        forwardButton = findViewById(R.id.btn_forward);
        backButton = findViewById(R.id.btn_back);
        pipButton = findViewById(R.id.btn_pip);
        qualityButton = findViewById(R.id.btn_quality);
        subtitleButton = findViewById(R.id.btn_subtitle);
        fullscreenButton = findViewById(R.id.btn_fullscreen);
        titleText = findViewById(R.id.tv_title);
        progressBar = findViewById(R.id.progress_bar);
        currentTime = findViewById(R.id.tv_current_time);
        totalTime = findViewById(R.id.tv_total_time);
        controlsContainer = findViewById(R.id.controls_container);
        
        // Initialize new UI elements
        brightnessControl = findViewById(R.id.brightness_control_independent);
        volumeControl = findViewById(R.id.volume_control_independent);
        brightnessIcon = findViewById(R.id.brightness_icon_independent);
        volumeIcon = findViewById(R.id.volume_icon_independent);
        brightnessProgress = findViewById(R.id.brightness_progress_independent);
        volumeProgress = findViewById(R.id.volume_progress_independent);
        subtitleText = findViewById(R.id.subtitle_text);
        seekPopup = findViewById(R.id.seek_popup);
        backwardSeekPopup = findViewById(R.id.backward_seek_popup);
        forwardSeekPopup = findViewById(R.id.forward_seek_popup);
        backwardSeekText = findViewById(R.id.backward_seek_text);
        forwardSeekText = findViewById(R.id.forward_seek_text);
        loadingView = findViewById(R.id.loading_view);
        liveIndicator = findViewById(R.id.live_indicator);
        
        // Create buffering progress bar
        bufferingProgress = new android.widget.ProgressBar(getContext());
        FrameLayout.LayoutParams bufferingParams = new FrameLayout.LayoutParams(
            (int) (60 * getContext().getResources().getDisplayMetrics().density),
            (int) (60 * getContext().getResources().getDisplayMetrics().density),
            android.view.Gravity.CENTER
        );
        bufferingProgress.setLayoutParams(bufferingParams);
        bufferingProgress.setVisibility(GONE);
        addView(bufferingProgress);
        
        // Initialize brightness and volume progress bars
        // Both brightnessProgress and volumeProgress are now Views, not ProgressBars
        
        setupClickListeners();
        
        hideControlsRunnable = () -> hideControls();
        hideGestureRunnable = () -> {
            if (brightnessControl != null) brightnessControl.setVisibility(GONE);
            if (volumeControl != null) volumeControl.setVisibility(GONE);
        };
        
        // Force show controls initially
        if (controlsContainer != null) {
            controlsContainer.setVisibility(VISIBLE);
            isControlsVisible = true;
            Log.d("HlsPlayerController", "Controls forced to show in init");
        }
        
        // Delayed show to ensure proper initialization
        postDelayed(() -> {
            showControls();
            // Initialize brightness display with current system brightness
            initializeBrightnessDisplay();
            Log.d("HlsPlayerController", "Controls shown with delay");
        }, 500);
    }
    
    private void setupClickListeners() {
        if (playPauseButton != null) {
            playPauseButton.setOnClickListener(v -> togglePlayPause());
        }
        if (bottomPlayPauseButton != null) {
            bottomPlayPauseButton.setOnClickListener(v -> togglePlayPause());
        }
        if (rewindButton != null) {
            rewindButton.setOnClickListener(v -> rewind());
        }
        if (forwardButton != null) {
            forwardButton.setOnClickListener(v -> forward());
        }
        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                if (getContext() instanceof android.app.Activity) {
                    ((android.app.Activity) getContext()).finish();
                }
            });
        }
        if (pipButton != null) {
            pipButton.setOnClickListener(v -> {
                try {
                    Log.d("HlsPlayerController", "PiP button clicked in controller");
                    if (controllerListener != null) {
                        controllerListener.onPipClicked();
                    } else {
                        Log.w("HlsPlayerController", "Controller listener is null");
                    }
                } catch (Exception e) {
                    Log.e("HlsPlayerController", "Error in PiP button click: " + e.getMessage());
                }
            });
        }
        if (qualityButton != null) {
            qualityButton.setOnClickListener(v -> {
                try {
                    Log.d("HlsPlayerController", "Quality button clicked in controller");
                    if (controllerListener != null) {
                        controllerListener.onQualityClicked();
                    } else {
                        Log.w("HlsPlayerController", "Controller listener is null");
                    }
                } catch (Exception e) {
                    Log.e("HlsPlayerController", "Error in quality button click: " + e.getMessage());
                }
            });
        }
        if (subtitleButton != null) {
            subtitleButton.setOnClickListener(v -> {
                try {
                    Log.d("HlsPlayerController", "Subtitle button clicked in controller");
                    if (!subtitlesAvailable) {
                        android.widget.Toast.makeText(getContext(), "Subtitles not available", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (controllerListener != null) {
                        controllerListener.onSubtitleClicked();
                    } else {
                        Log.w("HlsPlayerController", "Controller listener is null");
                    }
                } catch (Exception e) {
                    Log.e("HlsPlayerController", "Error in subtitle button click: " + e.getMessage());
                }
            });
        }
        if (fullscreenButton != null) {
            fullscreenButton.setOnClickListener(v -> {
                try {
                    Log.d("HlsPlayerController", "Fullscreen button clicked - content only");
                    
                    // Only toggle video content resize mode, not system UI
                    if (getParent() instanceof HlsPlayerView) {
                        HlsPlayerView playerView = (HlsPlayerView) getParent();
                        
                        // Check current resize mode to toggle
                        boolean isCurrentlyZoomed = playerView.getPlayerView().getResizeMode() == 
                            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
                        
                        // Toggle between FIT and ZOOM modes
                        playerView.setFullscreenMode(!isCurrentlyZoomed);
                        
                        Log.d("HlsPlayerController", "Video content fullscreen toggled: " + !isCurrentlyZoomed);
                        android.widget.Toast.makeText(getContext(), 
                            !isCurrentlyZoomed ? "Video expanded" : "Video fit to screen", 
                            android.widget.Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e("HlsPlayerController", "Error in content fullscreen toggle: " + e.getMessage());
                }
            });
        }
        
        if (progressBar != null) {
            progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && player != null && player.getDuration() > 0) {
                        long duration = player.getDuration();
                        long position = (duration * progress) / 100;
                        player.seekTo(position);
                    }
                }
                
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
        
        setOnTouchListener(new OnTouchListener() {
            private float startY;
            private float startX;
            private boolean isScrolling = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startY = event.getY();
                        startX = event.getX();
                        isScrolling = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float deltaY = event.getY() - startY;
                        if (Math.abs(deltaY) > 30) {
                            isScrolling = true;
                            int screenWidth = getWidth();
                            if (startX < screenWidth / 2f) {
                                handleBrightnessGesture(deltaY);
                                startY = event.getY();
                            } else {
                                handleVolumeGesture(deltaY);
                                startY = event.getY();
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!isScrolling) toggleControlsVisibility();
                        else {
                            removeCallbacks(hideGestureRunnable);
                            postDelayed(hideGestureRunnable, 1500);
                        }
                        return true;
                }
                return false;
            }

            private void handleBrightnessGesture(float deltaY) {
                if (getContext() instanceof android.app.Activity) {
                    android.app.Activity activity = (android.app.Activity) getContext();
                    android.view.WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
                    float cur = lp.screenBrightness < 0 ? 0.5f : lp.screenBrightness;
                    float newB = Math.max(0.01f, Math.min(1.0f, cur + (-deltaY * 0.003f)));
                    lp.screenBrightness = newB;
                    activity.getWindow().setAttributes(lp);
                    setBrightnessLevel(newB);
                    if (brightnessControl != null) brightnessControl.setVisibility(VISIBLE);
                }
            }

            private void handleVolumeGesture(float deltaY) {
                android.media.AudioManager am = (android.media.AudioManager)
                    getContext().getSystemService(android.content.Context.AUDIO_SERVICE);
                if (am == null) return;
                int max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
                int cur = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
                int newVol = Math.max(0, Math.min(max, cur + (int)(-deltaY * 0.05f)));
                am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVol, 0);
                setVolumeLevel((float) newVol / max, newVol == 0);
                if (volumeControl != null) volumeControl.setVisibility(VISIBLE);
            }
        });
    }
    
    public void setPlayer(ExoPlayer player) {
        this.player = player;
        if (player != null) {
            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    updatePlayPauseButton();
                    
                    // Show buffering progress when buffering
                    if (playbackState == Player.STATE_BUFFERING) {
                        showBufferingProgress(true);
                    } else {
                        showBufferingProgress(false);
                        if (playbackState == Player.STATE_READY) {
                            updateProgress();
                        }
                    }
                }
                
                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    updatePlayPauseButton();
                    if (isPlaying) {
                        updateProgress();
                    }
                }
            });
            updatePlayPauseButton();
        }
    }
    
    private void togglePlayPause() {
        if (player != null) {
            if (player.isPlaying()) {
                player.pause();
            } else {
                player.play();
            }
        }
    }
    
    private void rewind() {
        if (player != null) {
            long currentPosition = player.getCurrentPosition();
            long seekPosition = Math.max(0, currentPosition - 10000);
            player.seekTo(seekPosition);
            showSeekLoader();
        }
    }
    
    private void forward() {
        if (player != null) {
            long currentPosition = player.getCurrentPosition();
            long duration = player.getDuration();
            long seekPosition = Math.min(duration, currentPosition + 10000);
            player.seekTo(seekPosition);
            showSeekLoader();
        }
    }
    
    private android.widget.ProgressBar seekLoader;
    
    private void showSeekLoader() {
        // Create loading indicator if not exists
        if (seekLoader == null) {
            seekLoader = new android.widget.ProgressBar(getContext());
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                80, 80, android.view.Gravity.CENTER);
            addView(seekLoader, params);
        }
        seekLoader.setVisibility(android.view.View.VISIBLE);
        
        // Hide after 2 seconds
        postDelayed(() -> {
            if (seekLoader != null) {
                seekLoader.setVisibility(android.view.View.GONE);
            }
        }, 2000);
    }
    
    private void updatePlayPauseButton() {
        if (player != null) {
            int iconRes = player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play;
            if (playPauseButton != null) {
                playPauseButton.setImageResource(iconRes);
            }
            if (bottomPlayPauseButton != null) {
                bottomPlayPauseButton.setImageResource(iconRes);
            }
        }
    }
    
    private void updateProgress() {
        if (player != null && progressBar != null && currentTime != null && totalTime != null) {
            long duration = player.getDuration();
            long position = player.getCurrentPosition();
            
            if (duration > 0) {
                int progress = (int) ((position * 100) / duration);
                progressBar.setProgress(progress);
                
                currentTime.setText(formatTime(position));
                totalTime.setText(formatTime(duration));
            }
            
            // Schedule next update only if player is playing
            if (player.isPlaying()) {
                postDelayed(this::updateProgress, 1000);
            }
        }
    }
    
    private String formatTime(long timeMs) {
        long totalSeconds = timeMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }
    
    private void toggleControlsVisibility() {
        if (isControlsVisible) {
            hideControls();
        } else {
            showControls();
        }
    }
    
    public void showControls() {
        if (controlsContainer != null) {
            controlsContainer.setVisibility(VISIBLE);
            isControlsVisible = true;
            removeCallbacks(hideControlsRunnable);
            postDelayed(hideControlsRunnable, 5000);
        }
    }
    
    public void hideControls() {
        if (controlsContainer != null) {
            controlsContainer.setVisibility(GONE);
            isControlsVisible = false;
            if (brightnessControl != null) brightnessControl.setVisibility(GONE);
            if (volumeControl != null) volumeControl.setVisibility(GONE);
            removeCallbacks(hideControlsRunnable);
        }
    }
    
    public void setTitle(String title) {
        if (titleText != null) {
            titleText.setText(title);
        }
    }
    
    public void startProgressUpdates() {
        removeCallbacks(this::updateProgress);
        updateProgress();
    }
    
    public void stopProgressUpdates() {
        removeCallbacks(this::updateProgress);
    }
    
    public void setControllerListener(ControllerListener listener) {
        this.controllerListener = listener;
    }
    
    // New methods for enhanced UI features
    public void showBrightnessControl(boolean show) {
        Log.d("HlsPlayerController", "showBrightnessControl: " + show + ", brightnessControl: " + (brightnessControl != null));
        if (brightnessControl != null) {
            // Show brightness control only when main controls are visible
            brightnessControl.setVisibility((show && isControlsVisible) ? VISIBLE : GONE);
        }
    }
    
    public void showVolumeControl(boolean show) {
        Log.d("HlsPlayerController", "showVolumeControl: " + show + ", volumeControl: " + (volumeControl != null));
        if (volumeControl != null) {
            // Show volume control only when main controls are visible
            volumeControl.setVisibility((show && isControlsVisible) ? VISIBLE : GONE);
        }
    }
    
    public void setBrightnessLevel(float level) {
        // Update brightness progress bar height based on level
        if (brightnessProgress != null) {
            android.widget.LinearLayout.LayoutParams params = 
                (android.widget.LinearLayout.LayoutParams) brightnessProgress.getLayoutParams();
            if (params != null) {
                // Scale height from 20dp (minimum) to 120dp (maximum)
                int minHeight = (int) (20 * getContext().getResources().getDisplayMetrics().density);
                int maxHeight = (int) (120 * getContext().getResources().getDisplayMetrics().density);
                int newHeight = minHeight + (int) ((maxHeight - minHeight) * level);
                params.height = newHeight;
                brightnessProgress.setLayoutParams(params);
            }
        }
        
        // Update brightness icon based on level
        if (brightnessIcon != null) {
            int iconRes;
            if (level < 0.33f) {
                iconRes = R.drawable.ic_brightness_medium;
            } else if (level < 0.66f) {
                iconRes = R.drawable.ic_brightness_medium;
            } else {
                iconRes = R.drawable.ic_brightness_medium;
            }
            brightnessIcon.setImageResource(iconRes);
        }
    }
    
    public void setVolumeLevel(float level, boolean isMuted) {
        // Update volume progress bar height based on level
        if (volumeProgress != null) {
            android.widget.LinearLayout.LayoutParams params = 
                (android.widget.LinearLayout.LayoutParams) volumeProgress.getLayoutParams();
            if (params != null) {
                // Scale height from 20dp (minimum) to 120dp (maximum)
                int minHeight = (int) (20 * getContext().getResources().getDisplayMetrics().density);
                int maxHeight = (int) (120 * getContext().getResources().getDisplayMetrics().density);
                int newHeight = minHeight + (int) ((maxHeight - minHeight) * level);
                params.height = newHeight;
                volumeProgress.setLayoutParams(params);
            }
        }
        
        // Update volume icon based on level and mute state
        if (volumeIcon != null) {
            volumeIcon.setImageResource(isMuted ? R.drawable.ic_mute : R.drawable.ic_unmute);
        }
    }
    
    public void showSubtitle(String text) {
        if (subtitleText != null) {
            if (text != null && !text.isEmpty()) {
                subtitleText.setText(text);
                subtitleText.setVisibility(VISIBLE);
            } else {
                subtitleText.setVisibility(GONE);
            }
        }
    }
    
    public void showSeekPopup(boolean isForward, String seekText) {
        if (seekPopup != null) {
            seekPopup.setVisibility(VISIBLE);
            
            if (isForward) {
                if (forwardSeekPopup != null && forwardSeekText != null) {
                    forwardSeekPopup.setVisibility(VISIBLE);
                    forwardSeekText.setText(seekText);
                }
                if (backwardSeekPopup != null) {
                    backwardSeekPopup.setVisibility(GONE);
                }
            } else {
                if (backwardSeekPopup != null && backwardSeekText != null) {
                    backwardSeekPopup.setVisibility(VISIBLE);
                    backwardSeekText.setText(seekText);
                }
                if (forwardSeekPopup != null) {
                    forwardSeekPopup.setVisibility(GONE);
                }
            }
            
            // Auto-hide after 2 seconds
            postDelayed(() -> {
                if (seekPopup != null) {
                    seekPopup.setVisibility(GONE);
                }
            }, 2000);
        }
    }
    
    public void showLoading(boolean show) {
        if (loadingView != null) {
            loadingView.setVisibility(show ? VISIBLE : GONE);
        }
    }
    
    public void showBufferingProgress(boolean show) {
        if (bufferingProgress != null) {
            bufferingProgress.setVisibility(show ? VISIBLE : GONE);
            Log.d("HlsPlayerController", "Buffering progress: " + (show ? "shown" : "hidden"));
        }
    }
    
    public void hideAllGestureControls() {
        showBrightnessControl(false);
        showVolumeControl(false);
        if (seekPopup != null) {
            seekPopup.setVisibility(GONE);
        }
    }
    
    public void setSubtitlesAvailable(boolean available) {
        subtitlesAvailable = available;
        updateSubtitleButtonState();
    }
    
    public void setSubtitlesEnabled(boolean enabled) {
        subtitlesEnabled = enabled;
        updateSubtitleButtonState();
    }
    
    private void updateSubtitleButtonState() {
        if (subtitleButton != null) {
            if (!subtitlesAvailable) {
                // Subtitle not available - dim the button
                subtitleButton.setAlpha(0.5f);
                subtitleButton.setImageResource(R.drawable.ic_subtitle);
            } else if (subtitlesEnabled) {
                // Subtitle enabled - highlight the button
                subtitleButton.setAlpha(1.0f);
                subtitleButton.setImageResource(R.drawable.ic_subtitle);
                subtitleButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    getContext().getResources().getColor(R.color.app_color)));
            } else {
                // Subtitle available but disabled - normal state
                subtitleButton.setAlpha(1.0f);
                subtitleButton.setImageResource(R.drawable.ic_subtitle);
                subtitleButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    getContext().getResources().getColor(R.color.app_black_30)));
            }
        }
    }
    
    public boolean isSubtitlesAvailable() {
        return subtitlesAvailable;
    }
    
    public boolean isSubtitlesEnabled() {
        return subtitlesEnabled;
    }
    
    public void setLiveTvMode(boolean isLiveTV) {
        this.isLiveTvMode = isLiveTV;
        updateLiveTvUI();
    }
    
    public boolean isLiveTvMode() {
        return isLiveTvMode;
    }
    
    private void updateLiveTvUI() {
        if (isLiveTvMode) {
            // Hide center play/pause and seek buttons for live TV
            if (playPauseButton != null) {
                playPauseButton.setVisibility(GONE);
            }
            if (rewindButton != null) {
                rewindButton.setVisibility(GONE);
            }
            if (forwardButton != null) {
                forwardButton.setVisibility(GONE);
            }
            
            // Hide entire center controls container
            View centerControls = findViewById(R.id.center_controls);
            if (centerControls != null) {
                centerControls.setVisibility(GONE);
            }
            
            // Show LIVE indicator with blinking dot
            if (liveIndicator != null) {
                liveIndicator.setVisibility(VISIBLE);
                View dot = liveIndicator.getChildAt(0);
                if (dot != null) {
                    android.view.animation.Animation blink = android.view.animation.AnimationUtils
                        .loadAnimation(getContext(), R.anim.blink);
                    dot.startAnimation(blink);
                }
            }
            
            // Keep bottom play/pause button but remove functionality
            if (bottomPlayPauseButton != null) {
                bottomPlayPauseButton.setVisibility(GONE);
            }
            
            Log.d("HlsPlayerController", "Live TV mode enabled - center controls hidden, seek bar visible");
        } else {
            // Show normal controls for regular content
            if (playPauseButton != null) {
                playPauseButton.setVisibility(VISIBLE);
            }
            if (rewindButton != null) {
                rewindButton.setVisibility(VISIBLE);
            }
            if (forwardButton != null) {
                forwardButton.setVisibility(VISIBLE);
            }
            
            // Show entire center controls container
            View centerControls = findViewById(R.id.center_controls);
            if (centerControls != null) {
                centerControls.setVisibility(VISIBLE);
            }
            
            // Show seek bar for regular content
            if (progressBar != null) {
                progressBar.setVisibility(VISIBLE);
            }
            
            // Hide LIVE indicator and stop animation
            if (liveIndicator != null) {
                liveIndicator.setVisibility(GONE);
                View dot = liveIndicator.getChildAt(0);
                if (dot != null) dot.clearAnimation();
            }
            
            // Show normal play/pause button
            if (bottomPlayPauseButton != null) {
                bottomPlayPauseButton.setVisibility(VISIBLE);
                updatePlayPauseButton();
            }
            
            Log.d("HlsPlayerController", "Normal mode enabled - center controls shown");
        }
    }
    
    private void initializeBrightnessDisplay() {
        if (getContext() instanceof android.app.Activity) {
            android.app.Activity activity = (android.app.Activity) getContext();
            android.view.WindowManager.LayoutParams attributes = activity.getWindow().getAttributes();
            float currentBrightness = attributes.screenBrightness;
            if (currentBrightness < 0) {
                // Get system brightness if not set
                try {
                    currentBrightness = android.provider.Settings.System.getInt(
                        getContext().getContentResolver(),
                        android.provider.Settings.System.SCREEN_BRIGHTNESS
                    ) / 255.0f;
                } catch (android.provider.Settings.SettingNotFoundException e) {
                    currentBrightness = 0.5f; // Default to 50%
                }
            }
            setBrightnessLevel(currentBrightness);
        }
    }
}