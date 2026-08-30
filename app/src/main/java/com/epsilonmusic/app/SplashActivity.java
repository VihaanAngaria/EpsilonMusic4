package com.epsilonmusic.app;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.VideoView;

public class SplashActivity extends Activity {

    /**
     * Hard safety-net duration. The video itself is ~2.1s, but if for any
     * reason onPrepared / onCompletion never fires (rare codec issue,
     * low-end decoder, etc.) we still transition to MainActivity instead
     * of stranding the user on a black screen.
     */
    private static final long SPLASH_FALLBACK_MS = 2600L;

    private VideoView videoView;
    private boolean navigatedAway = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(android.graphics.Color.BLACK);
        window.setNavigationBarColor(android.graphics.Color.BLACK);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        setContentView(R.layout.activity_splash);

        videoView = findViewById(R.id.splash_video);

        // Suppress any audio that might sneak onto a future revision of the
        // splash clip; the supplied mp4 is video-only but we keep this for
        // safety.
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        Uri videoUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.epsilon_splash);
        videoView.setVideoURI(videoUri);
        videoView.setMediaController(null); // no transport controls on splash

        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mp.setLooping(false);
                // Mute defensively in case the asset gains an audio track later.
                mp.setVolume(0f, 0f);
                videoView.start();
            }
        });

        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                proceedToMain();
            }
        });

        // Fallback in case the video never signals completion.
        videoView.postDelayed(new Runnable() {
            @Override
            public void run() {
                proceedToMain();
            }
        }, SPLASH_FALLBACK_MS);
    }

    private void proceedToMain() {
        if (navigatedAway) {
            return;
        }
        navigatedAway = true;

        if (videoView != null) {
            try {
                if (videoView.isPlaying()) {
                    videoView.stopPlayback();
                }
            } catch (Exception ignored) {
            }
        }

        Intent intent = new Intent(SplashActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    public void onBackPressed() {
        // Prevent the launch screen from being skipped while it is showing.
    }

    @Override
    protected void onDestroy() {
        if (videoView != null) {
            try {
                videoView.stopPlayback();
            } catch (Exception ignored) {
            }
            videoView = null;
        }
        super.onDestroy();
    }
}
