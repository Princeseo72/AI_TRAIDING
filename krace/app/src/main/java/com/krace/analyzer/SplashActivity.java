package com.krace.analyzer;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Entry point: show the supplied NobleEthan emblem, then open existing KRace features. */
public final class SplashActivity extends Activity {
 private final Handler handler=new Handler(Looper.getMainLooper());
 private Runnable launch;
 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  getWindow().setStatusBarColor(Color.rgb(10,10,10));
  getWindow().setNavigationBarColor(Color.rgb(10,10,10));
  LinearLayout root=new LinearLayout(this);
  root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER);
  root.setBackgroundColor(Color.rgb(10,10,10));
  root.setPadding(12,12,12,12);
  ImageView emblem=new ImageView(this);
  emblem.setImageResource(R.drawable.nobleethan);
  emblem.setScaleType(ImageView.ScaleType.FIT_CENTER);
  emblem.setContentDescription("NobleEthan Notes 로고");
  root.addView(emblem,new LinearLayout.LayoutParams(-1,0,1));
  TextView label=new TextView(this);
  label.setGravity(Gravity.CENTER);
  label.setTextColor(0xffffc693);label.setTextSize(14);
  label.setTypeface(Typeface.create("serif",Typeface.NORMAL));
  label.setText("KRACE  ·  RACE ANALYZER");
  root.addView(label,new LinearLayout.LayoutParams(-1,60));
  setContentView(root);
  launch=()->{
   if(isFinishing()||isDestroyed())return;
   startActivity(new Intent(this,MainActivity.class));
   finish();
  };
  handler.postDelayed(launch,1550);
 }
 @Override protected void onDestroy(){
  if(launch!=null)handler.removeCallbacks(launch);
  super.onDestroy();
 }
}