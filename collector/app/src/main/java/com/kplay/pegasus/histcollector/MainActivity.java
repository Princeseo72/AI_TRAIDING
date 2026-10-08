package com.kplay.pegasus.histcollector;
import android.app.*;import android.os.*;import android.content.*;import android.net.*;import android.widget.*;import java.io.*;import java.time.*;import java.util.concurrent.atomic.AtomicBoolean;
public class MainActivity extends Activity{
 HistDb db; TextView status,counts; ProgressBar progress; AtomicBoolean stop=new AtomicBoolean(false),running=new AtomicBoolean(false);
 static final String[] TRACKS={"SEOUL","BUSAN_GYEONGNAM","JEJU","YEONGCHEON"}; static final int CREATE_DB=7001;
 public void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_main);db=new HistDb(this);status=findViewById(R.id.status);counts=findViewById(R.id.counts);progress=findViewById(R.id.progress);findViewById(R.id.start).setOnClickListener(v->start());findViewById(R.id.stop).setOnClickListener(v->stop.set(true));findViewById(R.id.export).setOnClickListener(v->chooseExport());refresh();}
 void start(){if(!running.compareAndSet(false,true)){status.setText("이미 수집 중입니다.");return;}stop.set(false);new Thread(()->{try{
  LocalDate end=LocalDate.now(),fallback=end.minusYears(5); int total=(int)(end.toEpochDay()-fallback.toEpochDay()+1)*TRACKS.length,done=0;
  for(String t:TRACKS){LocalDate begin=db.resumeDate(t,fallback);for(LocalDate d=begin;!d.isAfter(end)&&!stop.get();d=d.plusDays(1)){done++;if(!KraHistorySource.validTrackDate(t,d)){db.saveCheckpoint(t,d.plusDays(1));continue;}boolean networkFailed=false;
   for(int r=1;r<=16&&!stop.get();r++){try{KraHistorySource.Race x=KraHistorySource.fetch(t,d,r);if(x==null)break;db.saveRace(x.track,x.group,x.date,x.no,x.url,x.runners,x.odds);final int pct=Math.min(99,done*100/Math.max(1,total));runOnUiThread(()->{progress.setProgress(pct);refresh();});Thread.sleep(250);}catch(Exception e){networkFailed=true;final String m=t+" "+d+" 네트워크/파싱 오류 · 저장분 유지 · 다시 시작 가능: "+e.getClass().getSimpleName();runOnUiThread(()->status.setText(m));break;}}
   if(networkFailed||stop.get())break;db.saveCheckpoint(t,d.plusDays(1));
  }}
 }finally{running.set(false);runOnUiThread(()->{status.setText(stop.get()?"안전 중지 · 저장분 유지 · 다시 시작하면 체크포인트부터 이어받기":"수집 작업 종료 · 현재 DB는 언제든 내보낼 수 있습니다.");refresh();});}}).start();}
 void refresh(){counts.setText("race "+db.count("race")+" · runner "+db.count("runner")+" · odds "+db.count("odds"));}
 void chooseExport(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/octet-stream");i.putExtra(Intent.EXTRA_TITLE,"PEGASUS_HIST_V1.sqlite");startActivityForResult(i,CREATE_DB);}
 protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(req==CREATE_DB&&res==RESULT_OK&&data!=null&&data.getData()!=null)exportTo(data.getData());}
 void exportTo(Uri uri){new Thread(()->{try{db.checkpointWal();File src=getDatabasePath("pegasus_hist_v1.sqlite");try(InputStream in=new FileInputStream(src);OutputStream out=getContentResolver().openOutputStream(uri,"w")){if(out==null)throw new IOException("output unavailable");byte[]b=new byte[65536];for(int n;(n=in.read(b))>0;)out.write(b,0,n);out.flush();}runOnUiThread(()->status.setText("내보내기 완료 · PEGASUS에서 이 파일을 선택하세요."));}catch(Exception e){runOnUiThread(()->status.setText("내보내기 실패 · DB는 보존됨: "+e.getMessage()));}}).start();}
}