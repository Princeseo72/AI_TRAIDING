package com.kplay.horseracing.gumvit;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

public class IntroActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro);

        ImageView logo = findViewById(R.id.introLogo);
        logo.setScaleX(0.62f);
        logo.setScaleY(0.62f);
        logo.setAlpha(0.18f);
        logo.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(1.0f)
                .setDuration(1200)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        handler.postDelayed(() -> {
            startActivity(new Intent(IntroActivity.this, MainActivity.class));
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }, 1450);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
