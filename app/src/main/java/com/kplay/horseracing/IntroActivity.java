package com.kplay.horseracing.gumvit;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

public class IntroActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro);

        ImageView logo = findViewById(R.id.introLogo);

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        float scale = Math.min((dm.widthPixels * 0.80f) / 360f, (dm.heightPixels * 0.72f) / 308f);
        int targetW = Math.max(220, Math.round(360f * scale));
        int targetH = Math.max(188, Math.round(308f * scale));
        ViewGroup.LayoutParams lp = logo.getLayoutParams();
        lp.width = targetW;
        lp.height = targetH;
        logo.setLayoutParams(lp);

        logo.setScaleX(0.55f);
        logo.setScaleY(0.55f);
        logo.setAlpha(0.08f);
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
        }, 1500);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
