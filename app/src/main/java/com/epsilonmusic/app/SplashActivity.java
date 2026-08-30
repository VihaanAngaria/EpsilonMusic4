package com.epsilonmusic.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

public class SplashActivity extends Activity {

    private static final long SPLASH_DURATION_MS = 2100L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(android.graphics.Color.BLACK);
        window.setNavigationBarColor(android.graphics.Color.BLACK);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        setContentView(R.layout.activity_splash);

        ImageView logo = findViewById(R.id.splash_logo);
        Animation animation = AnimationUtils.loadAnimation(this, R.anim.logo_splash);
        logo.startAnimation(animation);

        logo.postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
            overridePendingTransition(0, 0);
        }, SPLASH_DURATION_MS);
    }

    @Override
    public void onBackPressed() {
        // Prevent the launch screen from being skipped while it is showing.
    }
}
