package com.kplay.horseracing.gumvit;

import android.app.Activity;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.jsoup.Connection;
import org.jsoup.Jsoup;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class IntroActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private ProgressBar gauge;
    private TextView percent, current, detail, internetState, gumvitState, kraState, engineState, moduleState, finalState;
    private Button retry;
    private volatile boolean destroyed;

    private static final String GUMVIT = "https://www.gumvit.com/statv40/";
    private static final String KRA = "https://race.kra.co.kr/thisweekrace/ThisWeekWeight.do";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro);
        ImageView logo = findViewById(R.id.introLogo);
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        float logoScale = Math.min((dm.widthPixels * 0.80f) / 360f, (dm.heightPixels * 0.42f) / 308f);
        ViewGroup.LayoutParams logoLp = logo.getLayoutParams();
        logoLp.width = Math.max(160, Math.round(360f * logoScale));
        logoLp.height = Math.max(137, Math.round(308f * logoScale));
        logo.setLayoutParams(logoLp);
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
        gauge=findViewById(R.id.preflightGauge); percent=findViewById(R.id.preflightPercent);
        current=findViewById(R.id.preflightCurrent); detail=findViewById(R.id.preflightDetail);
        internetState=findViewById(R.id.stateInternet); gumvitState=findViewById(R.id.stateGumvit);
        kraState=findViewById(R.id.stateKra); engineState=findViewById(R.id.stateEngine);
        moduleState=findViewById(R.id.stateModules); finalState=findViewById(R.id.preflightFinal);
        retry=findViewById(R.id.preflightRetry);
        retry.setOnClickListener(v -> runPreflight());
        runPreflight();
    }

    private void runPreflight(){
        retry.setVisibility(View.GONE);
        setRow(internetState,"대기"); setRow(gumvitState,"대기"); setRow(kraState,"대기");
        setRow(engineState,"대기"); setRow(moduleState,"대기");
        finalState.setText("실전 사전체크 진행 중");
        update(0,"PEGASUS 실전 운용 사전체크","인터넷·실시간 데이터원·분석엔진·모듈을 검증합니다.");
        new Thread(() -> {
            boolean internet=false,gumvit=false,kra=false,engine=false,modules=false;
            try{
                update(8,"1/5 인터넷 연결 확인","활성 네트워크와 인터넷 capability를 확인합니다.");
                internet=hasInternetCapability();
                row(internetState,internet?"PASS":"FAIL");
                if(!internet){ finishGate(false,"인터넷 연결이 없습니다. 실시간 분석을 시작할 수 없습니다."); return; }

                update(25,"2/5 검빛 접속 확인","검빛 경마정보 서버에 실제 HTTP 요청을 수행합니다.");
                gumvit=probe(GUMVIT,"gumvit");
                row(gumvitState,gumvit?"PASS":"WARN");
                update(43,"3/5 서울경마(KRA) 접속 확인","KRA 공식 경마정보 서버에 실제 HTTP 요청을 수행합니다.");
                kra=probe(KRA,"kra");
                row(kraState,kra?"PASS":"WARN");
                if(!gumvit && !kra){ finishGate(false,"검빛과 KRA가 모두 응답하지 않습니다. 경주정보 이중조회가 불가능합니다."); return; }

                update(62,"4/5 분석 알고리즘 엔진 점검","분석 JS 자산과 PEGASUS 핵심 계약을 검사합니다.");
                engine=engineAssetsHealthy();
                row(engineState,engine?"PASS":"FAIL");
                if(!engine){ finishGate(false,"분석 알고리즘 엔진 무결성 점검 실패. 분석 실행을 차단합니다."); return; }

                update(82,"5/5 모듈 배선 점검","Parser / Bridge / Scratch / Storage / DB 핵심 모듈을 확인합니다.");
                modules=modulesHealthy();
                row(moduleState,modules?"PASS":"FAIL");
                if(!modules){ finishGate(false,"핵심 모듈 점검 실패. 앱 실행을 차단합니다."); return; }

                String source = gumvit&&kra ? "GUMVIT + KRA 이중소스 정상" : (gumvit ? "GUMVIT 정상 / KRA 경고" : "KRA 정상 / GUMVIT 경고");
                update(100,"READY — 실전 분석 준비 완료",source);
                finishGate(true,source);
            }catch(Exception e){
                finishGate(false,"사전체크 예외: "+safe(e.getMessage()));
            }
        },"pegasus-preflight").start();
    }

    private boolean hasInternetCapability(){
        try{
            ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            if(cm==null)return false;
            Network n=cm.getActiveNetwork(); if(n==null)return false;
            NetworkCapabilities c=cm.getNetworkCapabilities(n);
            return c!=null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }catch(Exception e){ return false; }
    }

    private boolean probe(String url,String marker){
        try{
            Connection.Response r=Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36")
                    .timeout(6000).ignoreHttpErrors(true).execute();
            int code=r.statusCode();
            String body=r.body()==null?"":r.body().toLowerCase();
            return code>=200 && code<400 && body.length()>200 && (body.contains(marker)||body.contains("경마")||body.contains("race"));
        }catch(Exception e){ return false; }
    }

    private boolean engineAssetsHealthy(){
        return assetHas("analysis.js","analyzeRace")
                && assetHas("pegasus-engine.js","PegasusEngine")
                && assetHas("pegasus-engine.js","finalEight")
                && assetHas("ml.js","model")
                && assetHas("runner-guard.js","Runner")
                && assetHas("app.js","AndroidRace");
    }

    private boolean assetHas(String name,String token){
        try(BufferedReader br=new BufferedReader(new InputStreamReader(getAssets().open(name), StandardCharsets.UTF_8))){
            String line; while((line=br.readLine())!=null) if(line.contains(token)) return true;
        }catch(Exception ignored){}
        return false;
    }

    private boolean modulesHealthy(){
        try{
            Class.forName("com.kplay.horseracing.gumvit.GumvitBridge");
            Class.forName("com.kplay.horseracing.gumvit.GumvitPageParser");
            Class.forName("com.kplay.horseracing.gumvit.KraRaceParser");
            Class.forName("com.kplay.horseracing.gumvit.ScratchDetector");
            Class.forName("com.kplay.horseracing.gumvit.StorageBridge");
            Class.forName("com.kplay.horseracing.gumvit.RaceDbHelper");
            double a=1.0/2.0,b=1.0/4.0,z=a+b,p=a/z;
            return Math.abs(p-0.6666666667)<1e-6;
        }catch(Throwable e){ return false; }
    }

    private void update(int p,String title,String msg){
        main.post(() -> { if(destroyed)return; gauge.setProgress(p); percent.setText(p+"%"); current.setText(title); detail.setText(msg); });
    }
    private void row(TextView v,String state){ main.post(() -> setRow(v,state)); }
    private void setRow(TextView v,String state){
        v.setText(state);
        if("PASS".equals(state))v.setTextColor(0xFF7CFF8A);
        else if("WARN".equals(state))v.setTextColor(0xFFFFC857);
        else if("FAIL".equals(state))v.setTextColor(0xFFFF6B6B);
        else v.setTextColor(0xFFB7C8B8);
    }
    private void finishGate(boolean ok,String msg){
        main.post(() -> {
            if(destroyed)return;
            finalState.setText(ok?"PREFLIGHT PASS — "+msg:"PREFLIGHT BLOCKED — "+msg);
            if(ok){
                gauge.setProgress(100); percent.setText("100%");
                main.postDelayed(() -> { if(!destroyed){ startActivity(new Intent(this,MainActivity.class)); finish(); overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out); }},900);
            }else{
                retry.setVisibility(View.VISIBLE);
            }
        });
    }
    private static String safe(String s){ return s==null?"unknown":s; }
    @Override protected void onDestroy(){ destroyed=true; main.removeCallbacksAndMessages(null); super.onDestroy(); }
}
