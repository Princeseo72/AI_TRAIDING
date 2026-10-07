package com.kplay.pegasus.histcollector;
import android.app.*;import android.os.*;import android.content.*;import android.net.*;import android.view.*;import android.widget.*;import java.io.*;import java.time.*;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;
public class MainActivity extends Activity{
 HistDb db;TextView status,counts;ProgressBar progress;AtomicBoolean stop=new AtomicBoolean(false);
 static final String[] TRACKS={"SEOUL","BUSAN_GYEONGNAM","JEJU","YEONGCHEON"};
 public void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_main);db=new HistDb(this);status=findViewById(R.id.status);counts=findViewById(R.id.counts);progress=findViewById(R.id.progress);findViewById(R.id.start).setOnClickListener(v->start());findViewById(R.id.stop).setOnClickListener(v->stop.set(true));findViewById(R.id.export).setOnClickListener(v->exportDb());refresh();}
 void start(){stop.set(false);new Thread(()->{LocalDate end=LocalDate.now(),start=end.minusYears(5);int total=(int)(end.toEpochDay()-start.toEpochDay()+1)*TRACKS.length,done=0;
  for(LocalDate d=start;!d.isAfter(end)&&!stop.get();d=d.plusDays(1))for(String t:TRACKS){done++;if(stop.get())break;if(!KraHistorySource.validTrackDate(t,d))continue;for(int r=1;r<=16&&!stop.get();r++)try{KraHistorySource.Race x=KraHistorySource.fetch(t,d,r);if(x==null){if(r==1)break;else break;}db.saveRace(x.track,x.group,x.date,x.no,x.url,x.runners,x.odds);final int pct=Math.min(100,done*100/Math.max(1,total));runOnUiThread(()->{progress.setProgress(pct);refresh();});Thread.sleep(180);}catch(Exception e){final String m=t+" "+d+" R"+r+" 재시도 대상: "+e.getClass().getSimpleName();runOnUiThread(()->status.setText(m));try{Thread.sleep(1200);}catch(Exception ignored){}break;}}
  runOnUiThread(()->{status.setText(stop.get()?"안전 중지됨 · 다시 시작하면 기존 DB에 중복 없이 이어집니다.":"5년 수집 스캔 완료");refresh();});
 }).start();}
 void refresh(){counts.setText("race "+db.count("race")+" · runner "+db.count("runner")+" · odds "+db.count("odds"));}
 void exportDb(){try{File src=getDatabasePath("pegasus_hist_v1.sqlite"),dst=new File(getExternalFilesDir(null),"PEGASUS_HIST_V1.sqlite");try(InputStream i=new FileInputStream(src);OutputStream o=new FileOutputStream(dst)){byte[]b=new byte[65536];for(int n;(n=i.read(b))>0;)o.write(b,0,n);}status.setText("내보냄: "+dst.getAbsolutePath());}catch(Exception e){status.setText("내보내기 실패: "+e.getMessage());}}
}
