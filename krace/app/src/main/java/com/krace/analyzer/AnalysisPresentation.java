package com.krace.analyzer;

import android.app.Activity;
import android.view.View;
import android.widget.*;
import android.graphics.*;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Typeface;

/** A genuinely separate UI for analysis progress and results. Main controls stay off result screen. */
final class AnalysisPresentation {
 private final Activity activity;
 private final View main;
 private View progressScreen, resultScreen;
 private TextView phase;
 private ArcMeter meter;
 private boolean busy=false, showingResult=false;
 private final int BG=0xff0c1421, FG=0xffedf4ff, MUTED=0xffb6c9e2, ACCENT=0xff4fbfdc;
 AnalysisPresentation(Activity a,View mainView){activity=a;main=mainView;}
 boolean isOverlay(){return busy||showingResult;}
 private LinearLayout column(){
   LinearLayout l=new LinearLayout(activity);
   l.setOrientation(LinearLayout.VERTICAL);
   l.setBackgroundColor(BG);
   l.setPadding(28,26,28,24);
   return l;
 }
 private TextView text(String content,int sp,int color){
   TextView t=new TextView(activity);
   t.setText(content);t.setTextSize(sp);t.setTextColor(color);
   t.setPadding(0,12,0,12);
   return t;
 }
 void progress(String title){
   busy=true;showingResult=false;
   LinearLayout body=column();
   TextView heading=text("KRACE · ANALYSIS",12,ACCENT);body.addView(heading);
   TextView titleText=text(title,22,FG);titleText.setTypeface(null,Typeface.BOLD);body.addView(titleText);
   meter=new ArcMeter(activity);
   body.addView(meter,new LinearLayout.LayoutParams(-1,300));
   phase=text("단계 1/6 · 분석 준비",16,MUTED);
   phase.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
   body.addView(phase);
   body.addView(text("경주 입력·마필 수집·기록 검증·착순 계산 단계만 표시합니다.\n이 게이지는 적중확률이 아닙니다.",12,MUTED));
   ScrollView scroll=new ScrollView(activity);scroll.setBackgroundColor(BG);scroll.setFillViewport(true);scroll.addView(body);
   progressScreen=scroll;activity.setContentView(progressScreen);
   meter.setProgress(4);
 }
 void stage(int pct,String detail){
   activity.runOnUiThread(()->{
    if(!busy||meter==null)return;
    meter.setProgress(Math.min(99,Math.max(0,pct)));
    phase.setText(detail);
   });
 }
 void result(String title,String result){
   busy=false;showingResult=true;
   LinearLayout layout=column();
   TextView eyebrow=text("KRACE / 분석 결과",13,ACCENT);layout.addView(eyebrow);
   TextView h=text(title,23,FG);h.setTypeface(null,Typeface.BOLD);layout.addView(h);
   View rule=new View(activity);rule.setBackgroundColor(0xff284860);
   layout.addView(rule,new LinearLayout.LayoutParams(-1,2));
   TextView data=text(result,17,FG);data.setTextIsSelectable(true);
   layout.addView(data);
   ScrollView scroll=new ScrollView(activity);
   scroll.addView(layout);
   LinearLayout root=column();root.setPadding(20,8,20,16);
   root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
   Button home=new Button(activity);home.setText("⌂  메인화면으로 복귀");
   home.setOnClickListener(v->home());
   root.addView(home,new LinearLayout.LayoutParams(-1,-2));
   resultScreen=root;meter=null;
   activity.setContentView(resultScreen);
 }
 void home(){
   busy=false;showingResult=false;meter=null;progressScreen=null;resultScreen=null;
   activity.setContentView(main);
 }
 /** A genuine progress indicator; the percentage measures pipeline stages, never probability. */
 private static final class ArcMeter extends View {
   private final Paint track=new Paint(3),fill=new Paint(3),line=new Paint(3),number=new Paint(3);
   private int progress=0;private int shown=0;
   private final Handler handler=new Handler(Looper.getMainLooper());
   ArcMeter(Context c){
     super(c);
     track.setColor(0xff20364c);track.setStyle(Paint.Style.STROKE);track.setStrokeWidth(23);track.setStrokeCap(Paint.Cap.ROUND);
     fill.setColor(0xff43cbe7);fill.setStyle(Paint.Style.STROKE);fill.setStrokeWidth(23);fill.setStrokeCap(Paint.Cap.ROUND);
     line.setColor(0xff5e829f);line.setStrokeWidth(2);
     number.setTextAlign(Paint.Align.CENTER);number.setColor(Color.WHITE);number.setTypeface(Typeface.create("sans-serif-light",Typeface.BOLD));
   }
   void setProgress(int n){
     progress=Math.max(0,Math.min(100,n));
     if(shown>progress)shown=progress;
     animateNext();
   }
   private void animateNext(){
     if(shown>=progress)return;
     handler.postDelayed(()->{
       if(!isAttachedToWindow())return;
       shown=Math.min(progress,shown+2);
       invalidate();
       animateNext();
     },20);
   }
   @Override protected void onDraw(Canvas c){
     super.onDraw(c);
     float cx=getWidth()/2f,cy=getHeight()*.65f;
     float r=Math.min(getWidth()*.38f,getHeight()*.46f);
     RectF oval=new RectF(cx-r,cy-r,cx+r,cy+r);
     c.drawArc(oval,160,220,false,track);
     c.drawArc(oval,160,220*shown/100f,false,fill);
     number.setTextSize(Math.max(35,r*.4f));
     c.drawText(shown+"%",cx,cy+8,number);
     number.setTextSize(Math.max(12,r*.12f));
     c.drawText("ANALYSIS PROGRESS",cx,cy+42,number);
   }
 }
}
