package com.krace.analyzer;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import android.text.InputType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.util.*;
import java.util.regex.*;
import java.util.concurrent.*;
import java.text.SimpleDateFormat;

public class MainActivity extends Activity {
    private final ExecutorService pool=Executors.newSingleThreadExecutor();
    private static final int PICK_RACE_CSV=1901, PICK_HISTORY_CSV=1902;
    private static final int MAX_RACE_FILE_BYTES=512*1024;
    private static final int MAX_HISTORY_FILE_BYTES=5*1024*1024;
    private TextView fileInfo;
    private ScrollView screenScroll;
    private Spinner track,raceSpinner;
    private TextView status,output;
    private EditText csv, historyCsv;
    private RankEngine.Fitted fitted=null;
    private TextView modelInfo;
    private Button load,analyze;
    private final ArrayList<Race> races=new ArrayList<>();
    private final String[] codes={"B","S","J"};
    private int seq=0;
    static class Race {
        String date,grade,region,startTime="",venue="",source="검빛";int no,distance,count,published;
        Race(String d,int n,String g,int m,int c,String l){date=d;no=n;grade=g;distance=m;count=c;published=c;region=l;}
        public String toString(){return date+" "+(venue.isEmpty()?"":venue+" ")+no+"경주  "+distance+"m  "+count+"두  "+grade+" ["+source+"]";}
    }
    static class Horse {
        int no,rating,starts=-1,wins=-1;
        String name,jockey;double weight,best=Double.NaN,avg=Double.NaN,early=Double.NaN,late=Double.NaN,score;
        double recent=Double.NaN,recentLate=Double.NaN,training14=Double.NaN,vetRisk=Double.NaN;
        int recentPlace=-1,recentField=-1,layoffWeeks=-1,jockeyRides=-1,jockeyWins=-1;
        Horse(int n,String s,int r,double w,String j){no=n;name=s;rating=r;weight=w;jockey=j;}
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        ScrollView scroll=new ScrollView(this);screenScroll=scroll;scroll.setFillViewport(true);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(24,22,24,22);box.setBackgroundColor(Color.rgb(12,20,33));scroll.addView(box);
        TextView header=label("KRace | 출전마 착순 분석",23,0xfff3f7ff);box.addView(header);
        box.addView(label("검빛 1차 · 실패 시 KRA 확인 · 기록 부족 시 계산 차단",12,0xffadbed4));
        track=new Spinner(this);track.setBackgroundColor(0xffcbd8f2);
        ArrayAdapter<String> places=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
            new String[]{"영남(부경)","서울","제주"});track.setAdapter(places);box.addView(track);
        load=button("① 출전표 불러오기");box.addView(load);
        raceSpinner=new Spinner(this);raceSpinner.setBackgroundColor(0xffcbd8f2);box.addView(raceSpinner);
        analyze=button("② 선택 경주 분석");analyze.setEnabled(false);box.addView(analyze);
        status=label("경마장을 선택하고 출전표를 불러오세요.",13,0xffb5cee9);box.addView(status);
        output=label("",15,0xfff5f6ff);box.addView(output);
        box.addView(label("경주 CSV: 파일 선택 또는 붙여넣기",15,0xffd9e6ff));
        csv=new EditText(this);csv.setMinLines(3);csv.setMaxLines(8);csv.setTextColor(Color.WHITE);
        csv.setHintTextColor(0xff8b9ab1);csv.setHint("num,name,rating,weight,bestSec,avgSec,earlySec,lateSec,starts,wins");
        csv.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        box.addView(csv);
        Button picker=button("📂 경주 CSV 파일 선택·즉시 착순 계산");box.addView(picker);
        fileInfo=label("선택된 CSV 파일 없음",12,0xffb5cee9);box.addView(fileInfo);
        picker.setOnClickListener(v->openCsvPicker(PICK_RACE_CSV));
        Button manual=button("붙여넣은 CSV 검증·계산");box.addView(manual);
        box.addView(label("과거경주 CSV: 파일 선택 또는 붙여넣기 (최소 40경주)",16,0xffd9e6ff));
        historyCsv=new EditText(this);historyCsv.setTextColor(Color.WHITE);historyCsv.setHintTextColor(0xff8b9ab1);
        historyCsv.setMinLines(3);historyCsv.setMaxLines(7);
        historyCsv.setHint("date,track,race,num,name,finish,rating,weight,bestSec,avgSec,recentSec,earlySec,lateSec,recentLateSec,starts,wins,layoffWeeks,recentPlace,recentField");
        historyCsv.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);box.addView(historyCsv);
        Button historyPicker=button("📂 과거 경주 CSV 파일 선택·학습·백테스트");box.addView(historyPicker);
        historyPicker.setOnClickListener(v->openCsvPicker(PICK_HISTORY_CSV));
        Button train=button("③ 붙여넣은 과거경주 학습·백테스트");box.addView(train);
        modelInfo=label("현재 모델: 미학습 임시계수",12,0xffe2c18a);box.addView(modelInfo);
        train.setOnClickListener(v->trainModel());
        restoreModel();
        box.addView(label("※ 과거 경주를 학습하지 않은 결과는 신뢰성 없는 시험용 순위입니다. 실전 금전 베팅 근거로 사용하지 마세요.",11,0xffd3b47e));
        setContentView(scroll);
        load.setOnClickListener(v->loadRaces());
        analyze.setOnClickListener(v->analyzeRace());
        manual.setOnClickListener(v->manual());
        track.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                races.clear();raceSpinner.setAdapter(null);analyze.setEnabled(false);output.setText("");
            }
            public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
    }
    private TextView label(String s,int size,int col){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(col);
        t.setPadding(4,12,4,12);t.setTextIsSelectable(true);return t;
    }
    private Button button(String s){Button b=new Button(this);b.setText(s);return b;}
    private void message(String s){runOnUiThread(()->status.setText(s));}
    private Document doc(String url)throws Exception{
        if(!(url.startsWith("https://www.gumvit.com/")||url.startsWith("https://race.kra.co.kr/")||url.startsWith("https://m.kra.co.kr/")))
            throw new Exception("미허용 사이트");
        return Jsoup.connect(url).timeout(18000).maxBodySize(1500000)
           .userAgent("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
           .get();
    }
    private String base(String loc){return "https://www.gumvit.com/statv40/chulma.html?loc="+loc+"&type="+(loc.equals("S")?"6":"5");}
    private String kra(String loc){String m=loc.equals("B")?"3":loc.equals("S")?"1":"2";
        return "https://race.kra.co.kr/chulmainfo/ChulmaDetailInfoList.do?Act=02&Sub=1&meet="+m;}
    private String detail(String loc,Race r,String name){
        return "https://www.gumvit.com/statv40/"+name+"?loc="+loc+"&m_date="+r.date+"&race_no="+r.no+"&type="+(loc.equals("S")?"6":"5");
    }
    private String kstDate(){SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US);f.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));return f.format(new Date());}
    private ArrayList<Race> list(Document d,String loc){
        ArrayList<Race> a=new ArrayList<>();
        Pattern p=Pattern.compile("(\\d{4}-\\d{2}-\\d{2})\\s+(?:서울|부경|제주)\\s+(\\d{1,2})\\s*R\\s+(\\S+)\\s+(\\d{3,4})\\s*M\\s+(\\d+)\\s*두",Pattern.CASE_INSENSITIVE);
        for(Element row:d.select("tr")){
            Matcher m=p.matcher(row.text());
            if(m.find()){
                Race r=new Race(m.group(1),Integer.parseInt(m.group(2)),m.group(3),Integer.parseInt(m.group(4)),Integer.parseInt(m.group(5)),loc);
                Matcher departure=Pattern.compile("([01][0-9]|2[0-3]):[0-5][0-9]").matcher(row.text());
                if(departure.find())r.startTime=departure.group();
                boolean duplicate=false;for(Race old:a)if(old.no==r.no&&old.date.equals(r.date))duplicate=true;
                if(!duplicate)a.add(r);
            }
        }
        return a;
    }
    private ArrayList<Horse> entries(Document d){
        ArrayList<Horse> a=new ArrayList<>();
        Pattern p=Pattern.compile("^\\s*(\\d{1,2})\\s+(\\S+)\\s+(?:한|미|일|뉴|호|영|아|프|독|캐)\\s+(?:거|암|수)\\s+\\d+\\s*세\\s+(\\d+)\\s*점\\s+(\\d{1,2}(?:\\.\\d+)?)\\s*\\([^)]*\\)\\s+(\\S+)\\s+(\\S+)\\(\\d+\\)");
        for(Element row:d.select("tr")){
            Matcher m=p.matcher(row.text());
            if(m.find()){
                int n=Integer.parseInt(m.group(1));
                if(n<1||n>20)continue;
                boolean dup=false;for(Horse h:a)if(h.no==n)dup=true;
                if(!dup){
                  Horse h=new Horse(n,m.group(2),Integer.parseInt(m.group(3)),Double.parseDouble(m.group(4)),m.group(5));
                  Matcher l=Pattern.compile("(\\d{1,3})\\s*주").matcher(row.text());
                  if(l.find())h.layoffWeeks=Integer.parseInt(l.group(1));
                  a.add(h);
                }
            }
        }
        return a;
    }
    private double toSeconds(String s){
        Matcher m=Pattern.compile("^([01]):(\\d\\d\\.\\d)$").matcher(s);
        return m.find()?60*Integer.parseInt(m.group(1))+Double.parseDouble(m.group(2)):Double.NaN;
    }
    private void records(Document d,ArrayList<Horse> hs,int distance,String raceDate){
        HashMap<Integer,SourceGuard.Record> data=SourceGuard.gumvitRecords(d,raceDate);
        for(Horse h:hs){
            SourceGuard.Record r=data.get(h.no);
            if(r==null||!h.name.equals(r.name))continue;
            h.best=r.best;h.avg=r.avg;h.early=r.early;h.late=r.late;
            h.recent=r.recent;h.recentLate=r.recentLate;h.recentPlace=r.recentPlace;
        }
    }
    private void popularity(Document d,ArrayList<Horse> hs){
        Pattern p=Pattern.compile("^(\\d{1,2})\\s+(\\S+)\\s+(\\d+)\\s*전\\s*\\((\\d+)\\s*/\\s*(\\d+)\\)");
        for(Element row:d.select("tr")){
            Matcher m=p.matcher(row.text());if(!m.find())continue;
            int n=Integer.parseInt(m.group(1));
            for(Horse h:hs)if(h.no==n&&h.name.equals(m.group(2))){
                h.starts=Integer.parseInt(m.group(3));h.wins=Integer.parseInt(m.group(4));
            }
        }
    }
    private double z(Horse h,String key,List<Horse> hs){
        ArrayList<Double> vals=new ArrayList<>();
        for(Horse x:hs){double v=value(x,key);if(!Double.isNaN(v))vals.add(v);}
        double current=value(h,key);
        if(Double.isNaN(current)||vals.size()<2)return 0;
        double mean=0;for(double v:vals)mean+=v;mean/=vals.size();
        double var=0;for(double v:vals)var+=(v-mean)*(v-mean);var/=vals.size();
        return Math.max(-2.5,Math.min(2.5,(current-mean)/Math.max(0.8,Math.sqrt(var))));
    }
    private double value(Horse h,String key){
        switch(key){
            case "best":return h.best;case "avg":return h.avg;
            case "early":return h.early;case "late":return h.late;
            case "rating":return h.rating;case "weight":return h.weight;
            case "win":return h.starts>0?(double)h.wins/h.starts:Double.NaN;
            default:return Double.NaN;
        }
    }
    static class Ticket{int a,b,c;double score;Ticket(int x,int y,int z,double s){a=x;b=y;c=z;score=s;}}
    private RankEngine.Race convert(ArrayList<Horse> horses,String id){
        ArrayList<RankEngine.Runner> rs=new ArrayList<>();
        for(Horse h:horses){
            RankEngine.Runner r=new RankEngine.Runner(h.no,h.name);r.rating=h.rating;r.weight=h.weight;
            r.best=h.best;r.avg=h.avg;r.early=h.early;r.late=h.late;
            r.recent=h.recent;r.recentLate=h.recentLate;r.starts=h.starts;r.wins=h.wins;
            r.recentPlace=h.recentPlace;r.recentField=h.recentField;r.layoffWeeks=h.layoffWeeks;
            r.training14=h.training14;r.vetRisk=h.vetRisk;r.jockeyWins=h.jockeyWins;r.jockeyRides=h.jockeyRides;
            rs.add(r);
        }
        return new RankEngine.Race(id,rs);
    }
    private String calculate(ArrayList<Horse> hs,int expected)throws Exception{
        if(expected>0&&hs.size()!=expected)throw new Exception("출전마 누락 "+hs.size()+"/"+expected+"두");
        RankEngine.Race data=convert(hs,"live");
        boolean partial=false;
        for(RankEngine.Runner h:data.runners)
            if(Double.isNaN(h.avg)&&Double.isNaN(h.best)&&Double.isNaN(h.recent))partial=true;
        RankEngine.Prediction p=partial?RankEngine.predictPartial(data,fitted==null?RankEngine.PRIOR:fitted.weights):
            RankEngine.predict(data,fitted==null?RankEngine.PRIOR:fitted.weights);
        return RankEngine.summary(p,fitted!=null,fitted==null?null:fitted.eval);
    }
    private void restoreModel(){
        String saved=getPreferences(MODE_PRIVATE).getString("learnedWeights","");
        if(saved.isEmpty())return;
        try{
            String[] a=saved.split(",");
            if(a.length!=RankEngine.K)return;
            double[] w=new double[a.length];
            for(int i=0;i<a.length;i++)w[i]=Double.parseDouble(a[i]);
            RankEngine.Evaluation e=new RankEngine.Evaluation();
            e.trainRaces=getPreferences(MODE_PRIVATE).getInt("train",0);
            e.testRaces=getPreferences(MODE_PRIVATE).getInt("test",0);
            e.exactHits=getPreferences(MODE_PRIVATE).getInt("exact",0);
            e.tripleHits=getPreferences(MODE_PRIVATE).getInt("triple",0);
            if(e.trainRaces<25||e.testRaces<10)return;
            fitted=new RankEngine.Fitted(w,e);
            modelInfo.setText("학습모델 활성: 훈련 "+e.trainRaces+" · 시간순 검증 "+e.testRaces+
                "경주 · 쌍승 "+e.exactHits+" · 삼쌍승 "+e.tripleHits+" 적중");
        }catch(Exception ignored){fitted=null;}
    }
    private void trainModel(){
        final String source=historyCsv.getText().toString();
        modelInfo.setText("과거경주 자료 검증 및 학습 중...");
        pool.execute(()->{
            try{
                RankEngine.Fitted next=RankEngine.fitHistorical(source);
                saveFitted(next);
                runOnUiThread(()->modelInfo.setText("완료: 훈련 "+next.eval.trainRaces+
                   "/검증 "+next.eval.testRaces+"경주. 검증 쌍승 "+next.eval.exactHits+
                   " · 삼쌍승 "+next.eval.tripleHits+" 적중 (과거 데이터에만 해당)"));
            }catch(Exception ex){runOnUiThread(()->modelInfo.setText("학습 거부: "+ex.getMessage()));}
        });
    }

    private void openCsvPicker(int requestCode){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*"); // CSV files served as text/csv, application/octet-stream, Excel CSV, etc.
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(intent,requestCode); }
        catch(Exception e){fileInfo.setText("파일 선택기 실행 불가: "+e.getMessage());}
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=PICK_RACE_CSV && requestCode!=PICK_HISTORY_CSV)return;
        if(resultCode!=RESULT_OK || data==null || data.getData()==null){
            fileInfo.setText("CSV 파일 선택 취소 — 기존 결과는 변경하지 않음");
            return;
        }
        Uri uri=data.getData();
        final int kind=requestCode;
        final String displayName=csvDisplayName(uri);
        // Prevent stale suggestions and previous calculations being confused with this import.
        if(kind==PICK_RACE_CSV){output.setText("");csv.setText("");fileInfo.setText("CSV 검사 중: "+displayName);}
        else modelInfo.setText("과거 CSV 자료 검사 중: "+displayName);
        pool.execute(()->{
            try{
                String text=CsvFileCodec.readCsv(getContentResolver().openInputStream(uri),
                    kind==PICK_RACE_CSV?MAX_RACE_FILE_BYTES:MAX_HISTORY_FILE_BYTES,displayName);
                if(kind==PICK_RACE_CSV){
                    RankEngine.Race race=CsvInput.parseManual(text);
                    RankEngine.Prediction predicted=RankEngine.predict(race,fitted==null?RankEngine.PRIOR:fitted.weights);
                    final String rank=RankEngine.summary(predicted,fitted!=null,fitted==null?null:fitted.eval);
                    // Update the editable area only on success; keep imported content inspectable.
                    runOnUiThread(()->{
                        csv.setText(text);
                        fileInfo.setText("불러오기 성공: "+displayName+" | 출전마 "+race.runners.size()+"두 | 누락 신호 "+predicted.missingCells+"개");
                        output.setText("CSV 파일: "+displayName+" (출처 자체 검증 불가)\n\n"+rank);
                        screenScroll.post(()->screenScroll.smoothScrollTo(0,0));
                    });
                }else{
                    RankEngine.Fitted trained=RankEngine.fitHistorical(text);
                    saveFitted(trained);
                    runOnUiThread(()->{
                        historyCsv.setText(text.length()<150000?text:"");
                        modelInfo.setText("학습 완료: "+displayName+" | 훈련 "+trained.eval.trainRaces+
                            "경주 / 시간순 검증 "+trained.eval.testRaces+
                            "경주 | 쌍승 "+trained.eval.exactHits+
                            " / 삼쌍승 "+trained.eval.tripleHits+" 정확 적중");
                    });
                }
            }catch(Exception e){
                final String err=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
                runOnUiThread(()->{
                    if(kind==PICK_RACE_CSV){
                        fileInfo.setText("CSV 불러오기 실패: "+displayName+"\n사유: "+err+"\n이전 결과 무효화");
                        output.setText("파일 착순 계산 중단: "+err);
                        screenScroll.post(()->screenScroll.smoothScrollTo(0,0));
                    }else{
                        modelInfo.setText("학습 중단: "+err+" (기존 학습모델 유지)");
                    }
                });
            }
        });
    }
    private String csvDisplayName(Uri uri){
        String name=null;
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null && c.moveToFirst()){int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(i>=0)name=c.getString(i);}
        }catch(Exception ignored){}
        if(name==null||name.trim().isEmpty())name="selected.csv";
        return name;
    }
    private void saveFitted(RankEngine.Fitted next){
        StringBuilder s=new StringBuilder();
        for(int i=0;i<next.weights.length;i++){if(i>0)s.append(",");s.append(next.weights[i]);}
        getPreferences(MODE_PRIVATE).edit().putString("learnedWeights",s.toString())
            .putInt("train",next.eval.trainRaces).putInt("test",next.eval.testRaces)
            .putInt("exact",next.eval.exactHits).putInt("triple",next.eval.tripleHits).apply();
        fitted=next;
    }


    private void loadRaces(){
        final int ticket=++seq; final String loc=codes[track.getSelectedItemPosition()];
        load.setEnabled(false);analyze.setEnabled(false);output.setText("");message("KRA 공식 출전표 조회 중...");
        pool.execute(()->{
            ArrayList<Race> found=new ArrayList<>();String note="";
            try{
                Document official=doc(kra(loc));
                for(KraBoard.RaceLine kr:KraBoard.parse(official,kstDate())){
                    Race x=new Race(kr.date,kr.race,kr.grade,kr.distance,kr.starters,loc);
                    x.published=kr.published;x.startTime=kr.time;x.venue=kr.venue;x.source="KRA";
                    found.add(x);
                }
                note="KRA 출전확정 "+found.size()+"경주";
            }catch(Exception ex){note="KRA 목록 조회 실패: "+ex.getMessage();}
            if(found.isEmpty()){
                try{
                    for(Race x:list(doc(base(loc)),loc))if(x.date.equals(kstDate()))found.add(x);
                    note+=" / 검빛 경주목록 "+found.size()+"경주";
                }catch(Exception ex){note+=" / 검빛 실패: "+ex.getMessage();}
            }
            found.sort((a,b)->{int c=a.date.compareTo(b.date);return c!=0?c:a.no-b.no;});
            final ArrayList<Race> all=found;final String statusMessage=note;
            runOnUiThread(()->{
                if(seq!=ticket)return;
                races.clear();races.addAll(all);
                raceSpinner.setAdapter(new ArrayAdapter<Race>(this,android.R.layout.simple_spinner_dropdown_item,races));
                load.setEnabled(true);analyze.setEnabled(!races.isEmpty());
                status.setText(races.isEmpty()?"당일 출전표 없음/조회 실패: "+statusMessage:statusMessage+
                    "\n경주별 실제 출전두수를 사용하며, 분석 중 추가정보를 가져옵니다.");
            });
        });
    }
    private ArrayList<Horse> officialMobileRunners(Race r)throws Exception{
        Document page=doc(KraMobile.url(r.region,r.date,r.no));
        ArrayList<Horse> result=new ArrayList<>();
        for(KraMobile.Horse h:KraMobile.parse(page,r.date,r.no)){
            Horse x=new Horse(h.no,h.name,h.rating,h.weight,h.jockey);result.add(x);
        }
        return result;
    }
    private void analyzeRace(){
        if(races.isEmpty())return;
        final Race r=(Race)raceSpinner.getSelectedItem();
        final String loc=codes[track.getSelectedItemPosition()];
        final int ticket=++seq;
        if(!SourceGuard.beforeStart(r.date,r.startTime,System.currentTimeMillis())){
            output.setText("계산 중단: 경주 출발시간 경과 또는 출발시각 확인 불가. 사후 착순 유입을 방지합니다.");
            return;
        }
        // meet=3 KRA may include Yeongcheon races; never combine them with Busan (loc=B) Gumvit.
        if(loc.equals("B") && !r.venue.isEmpty() && !r.venue.equals("부경") && !r.venue.equals("부산") && !r.venue.equals("부산경남")){
            output.setText("자료원 혼합 차단: KRA 개최지는 "+r.venue+
                "입니다. 검빛 부경(loc=B) 자료를 다른 개최지 경주에 잘못 적용하지 않습니다.");
            return;
        }
        analyze.setEnabled(false);load.setEnabled(false);output.setText("");message(r+" 데이터 수집 중...");
        pool.execute(()->{
            String result;
            StringBuilder sources=new StringBuilder("출처: ");
            try{
                // For KRA race schedules, use live official actual count, not the published size.
                int officialCount=r.count;
                if(r.source.equals("KRA")){
                    KraBoard.RaceLine confirmed=KraBoard.find(doc(kra(loc)),r.date,r.no);
                    if(confirmed==null)throw new IllegalStateException("KRA 출전표에서 해당 경주 재확인 실패");
                    officialCount=confirmed.starters;
                    if(!confirmed.time.equals(r.startTime))throw new IllegalStateException("KRA 출발시각 변경. 출전표 다시 조회 필요");
                }else{
                    try {
                        KraBoard.RaceLine confirmed=KraBoard.find(doc(kra(loc)),r.date,r.no);
                        if(confirmed!=null)officialCount=confirmed.starters;
                    }catch(Exception ignored){}
                }
                // Official KRA detailed runner card first; Gumvit supplements pre-race performance.
                ArrayList<Horse> runners=new ArrayList<>();
                try{
                    runners=officialMobileRunners(r);
                    if(!runners.isEmpty())sources.append("KRA 모바일 출전마(").append(runners.size()).append("두), ");
                }catch(Exception ignored){}
                ArrayList<Horse> gumvitRunners=new ArrayList<>();
                Document gumvitDetail=null;
                try{
                    gumvitDetail=doc(detail(loc,r,"chulma_detail.html"));
                    gumvitRunners=entries(gumvitDetail);
                    if(!gumvitRunners.isEmpty())sources.append("검빛 출전마(").append(gumvitRunners.size()).append("두), ");
                }catch(Exception ignored){}
                if(runners.isEmpty()){
                    if(gumvitRunners.size()!=officialCount)
                        throw new IllegalStateException("KRA 실제 출전 "+officialCount+"두 / 검빛 "+gumvitRunners.size()+
                        "두. 제외마 식별 실패 — 임의로 제외하지 않고 계산 중단");
                    runners=gumvitRunners;
                }else if(runners.size()!=officialCount){
                    // Some KRA mobile pages can show stale cards; never calculate with wrong runner count.
                    if(gumvitRunners.size()==officialCount)runners=gumvitRunners;
                    else throw new IllegalStateException("KRA 출전확정 "+officialCount+"두, 출전마 명단 불일치 (공식 모바일 "+
                        runners.size()+"두, 검빛 "+gumvitRunners.size()+"두)");
                }
                if(runners.size()!=officialCount || runners.size()<3)
                    throw new IllegalStateException("확정 출전두수 불일치");
                // Cross-check names and numbers to prevent wrongly joining histories.
                for(Horse official:runners)for(Horse g:gumvitRunners){
                    if(official.no!=g.no)continue;
                    if(!official.name.equals(g.name))
                        throw new IllegalStateException("마번 "+official.no+" KRA/검빛 마명 불일치");
                    if(official.rating==0&&g.rating>0)official.rating=g.rating;
                    if(Double.isNaN(official.weight))official.weight=g.weight;
                }
                if(gumvitDetail!=null){popularity(gumvitDetail,runners);}
                try{
                    records(doc(detail(loc,r,"chulma_record.html")),runners,r.distance,r.date);
                    sources.append("검빛 역대/최근기록, ");
                }catch(Exception ex){sources.append("검빛 주파기록 불가(").append(ex.getClass().getSimpleName()).append("), ");}
                try{
                    Map<Integer,Double> workload=ConditionData.training(doc(detail(loc,r,"train_view.html")),r.date);
                    for(Horse h:runners)if(workload.containsKey(h.no))h.training14=workload.get(h.no);
                    if(!workload.isEmpty())sources.append("조교기록, ");
                }catch(Exception ignored){}
                try{
                    Map<Integer,Double> vet=ConditionData.veterinary(doc(detail(loc,r,"medicalAndEquipment.html")),r.date);
                    for(Horse h:runners)if(vet.containsKey(h.no))h.vetRisk=vet.get(h.no);
                    if(!vet.isEmpty())sources.append("진료이력, ");
                }catch(Exception ignored){}
                // Any partial result is clearly labeled; absence of all distinct features blocks ordering.
                result=sources.toString()+"\n"+r+"\n공식 출전확정 "+officialCount+"두 / 편성 "+
                    r.published+"두\n\n"+calculate(runners,officialCount);
            }catch(Exception ex){
                result="자동 수집 실패/분석 중단\n"+ex.getMessage()+
                   "\n현재 확보하지 않은 마필 기록을 임의 생성하지 않습니다. CSV 파일 직접 선택도 가능합니다.";
            }
            final String text=result;
            runOnUiThread(()->{
                if(seq!=ticket)return;
                output.setText(text);
                analyze.setEnabled(true);load.setEnabled(true);
                status.setText("정보수집 종료 — 결과의 자료 충족도와 경고를 확인하세요.");
            });
        });
    }

    private void manual(){
        // No stale output is retained if validation fails. All manual CSV parsing is pure Java and tested.
        output.setText("");
        try{
            RankEngine.Race race=CsvInput.parseManual(csv.getText().toString());
            RankEngine.Prediction p=RankEngine.predict(race,fitted==null?RankEngine.PRIOR:fitted.weights);
            output.setText("CSV 직접입력 (원본 미확인)\n\n"+
                RankEngine.summary(p,fitted!=null,fitted==null?null:fitted.eval));
            screenScroll.post(()->screenScroll.smoothScrollTo(0,0));
        }catch(Exception ex){
            output.setText("CSV 계산 불가: "+ex.getMessage());
            screenScroll.post(()->screenScroll.smoothScrollTo(0,0));
        }
    }
    @Override protected void onDestroy(){seq++;pool.shutdownNow();super.onDestroy();}
}