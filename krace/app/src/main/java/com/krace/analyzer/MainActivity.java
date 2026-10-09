package com.krace.analyzer;

import android.app.Activity;
import android.os.Bundle;
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
        String date,grade,region;int no,distance,count;
        Race(String d,int n,String g,int m,int c,String l){date=d;no=n;grade=g;distance=m;count=c;region=l;}
        public String toString(){return date+"  "+no+"경주  "+distance+"m  "+count+"두  "+grade;}
    }
    static class Horse {
        int no,rating,starts=-1,wins=-1;
        String name,jockey;double weight,best=Double.NaN,avg=Double.NaN,early=Double.NaN,late=Double.NaN,score;
        double recent=Double.NaN,recentLate=Double.NaN;int recentPlace=-1,recentField=-1,layoffWeeks=-1;
        Horse(int n,String s,int r,double w,String j){no=n;name=s;rating=r;weight=w;jockey=j;}
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
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
        box.addView(label("CSV 수동 기록 입력 (선택)",15,0xffd9e6ff));
        csv=new EditText(this);csv.setMinLines(3);csv.setMaxLines(8);csv.setTextColor(Color.WHITE);
        csv.setHintTextColor(0xff8b9ab1);csv.setHint("num,name,rating,weight,bestSec,avgSec,earlySec,lateSec,starts,wins");
        csv.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        box.addView(csv);
        Button manual=button("CSV 검증 후 계산");box.addView(manual);
        box.addView(label("과거 확정 경주 학습 · 실제 결과 기반 (최소 40경주)",16,0xffd9e6ff));
        historyCsv=new EditText(this);historyCsv.setTextColor(Color.WHITE);historyCsv.setHintTextColor(0xff8b9ab1);
        historyCsv.setMinLines(3);historyCsv.setMaxLines(7);
        historyCsv.setHint("date,track,race,num,name,finish,rating,weight,bestSec,avgSec,recentSec,earlySec,lateSec,recentLateSec,starts,wins,layoffWeeks,recentPlace,recentField");
        historyCsv.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);box.addView(historyCsv);
        Button train=button("③ 과거경주 시간순 학습·백테스트");box.addView(train);
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
        if(!(url.startsWith("https://www.gumvit.com/")||url.startsWith("https://race.kra.co.kr/")))
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
    private void records(Document d,ArrayList<Horse> hs,int distance){
        Pattern clock=Pattern.compile("(?<!\\d)([01]:\\d\\d\\.\\d)(?!\\d)");
        Pattern plain=Pattern.compile("(?<![\\d:])(1[2-6]\\.\\d)(?!\\d)");
        int phase=0;
        for(Element row:d.select("tr")){
            String t=row.text().replace('\u00a0',' ').replaceAll("\\s+"," ").trim();
            if(t.contains("마번")&&t.contains("해당거리")&&t.contains("최근")){phase=1;continue;}
            if(t.contains("마번")&&t.contains("S-1F")&&t.contains("최고기록")){phase=2;continue;}
            if(t.contains("마번")&&t.contains("1000")&&t.contains("기록")){phase=3;continue;}
            if(phase!=1&&phase!=2)continue;
            for(Horse h:hs){
                if(!Pattern.compile("^"+h.no+"\\s+"+Pattern.quote(h.name)+"(?:\\s|$)").matcher(t).find())continue;
                ArrayList<Double> clocks=new ArrayList<>();
                Matcher m=clock.matcher(t);
                while(m.find())clocks.add(toSeconds(m.group(1)));
                if(phase==1){
                    if(clocks.size()>=3){h.recent=clocks.get(clocks.size()-1);h.recentLate=clocks.get(clocks.size()-2);}
                    Matcher fm=Pattern.compile("1:\\d\\d\\.\\d([①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳])").matcher(t);
                    if(fm.find())h.recentPlace=fm.group(1).charAt(0)-'①'+1;
                }else if(phase==2){
                    if(clocks.size()>=2){h.best=clocks.get(clocks.size()-2);h.avg=clocks.get(clocks.size()-1);}
                    Matcher sm=plain.matcher(t);ArrayList<Double> seg=new ArrayList<>();
                    while(sm.find())seg.add(Double.parseDouble(sm.group(1)));
                    if(seg.size()>=2){h.early=seg.get(0);h.late=seg.get(1);}
                }
            }
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
            rs.add(r);
        }
        return new RankEngine.Race(id,rs);
    }
    private String calculate(ArrayList<Horse> hs,int expected)throws Exception{
        if(expected>0&&hs.size()!=expected)throw new Exception("출전마 누락 "+hs.size()+"/"+expected+"두");
        RankEngine.Prediction p=RankEngine.predict(convert(hs,"live"),fitted==null?RankEngine.PRIOR:fitted.weights);
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
                StringBuilder s=new StringBuilder();
                for(int i=0;i<next.weights.length;i++){if(i>0)s.append(",");s.append(next.weights[i]);}
                getPreferences(MODE_PRIVATE).edit().putString("learnedWeights",s.toString())
                    .putInt("train",next.eval.trainRaces).putInt("test",next.eval.testRaces)
                    .putInt("exact",next.eval.exactHits).putInt("triple",next.eval.tripleHits).apply();
                fitted=next;
                runOnUiThread(()->modelInfo.setText("완료: 훈련 "+next.eval.trainRaces+
                   "/검증 "+next.eval.testRaces+"경주. 검증 쌍승 "+next.eval.exactHits+
                   " · 삼쌍승 "+next.eval.tripleHits+" 적중 (과거 데이터에만 해당)"));
            }catch(Exception ex){runOnUiThread(()->modelInfo.setText("학습 거부: "+ex.getMessage()));}
        });
    }
    private void loadRaces(){
        final int ticket=++seq; final String loc=codes[track.getSelectedItemPosition()];
        load.setEnabled(false);analyze.setEnabled(false);output.setText("");message("검빛 출전목록 조회 중...");
        pool.execute(()->{
            try{
                ArrayList<Race> a=list(doc(base(loc)),loc);
                if(a.isEmpty())throw new Exception("검빛 출전목록 파싱 실패");
                Collections.sort(a,(x,y)->{int c=y.date.compareTo(x.date);return c!=0?c:x.no-y.no;});
                ArrayList<Race> today=new ArrayList<>();
                for(Race r:a)if(r.date.equals(kstDate()))today.add(r);
                final ArrayList<Race> display=today;
                runOnUiThread(()->{
                    if(seq!=ticket)return;
                    races.clear();races.addAll(display);
                    raceSpinner.setAdapter(new ArrayAdapter<Race>(this,android.R.layout.simple_spinner_dropdown_item,races));
                    analyze.setEnabled(!races.isEmpty());load.setEnabled(true);
                    status.setText(races.isEmpty()?"검빛 접속 성공, 당일 출전 목록 없음":"검빛 당일 출전목록 "+races.size()+"경주 확인");
                });
            }catch(Exception ex){
                String fallback="";
                try{
                    Document official=doc(kra(loc));
                    fallback=official.text().contains("출전")?"KRA 공식 페이지 접속 확인":"KRA 응답 수신";
                }catch(Exception e){fallback="KRA 조회도 실패: "+e.getMessage();}
                final String f=fallback,err=ex.getMessage();
                runOnUiThread(()->{if(seq!=ticket)return;load.setEnabled(true);
                    status.setText("검빛 조회 실패: "+err+"\n"+f+"\nKRA 상세 자동 해석은 아직 지원하지 않습니다. 검증된 CSV 입력을 사용하세요.");});
            }
        });
    }
    private void analyzeRace(){
        if(races.isEmpty())return;
        final Race r=(Race)raceSpinner.getSelectedItem();
        final String loc=codes[track.getSelectedItemPosition()];
        final int ticket=++seq;
        analyze.setEnabled(false);load.setEnabled(false);output.setText("");message(r+" 분석 중...");
        pool.execute(()->{
            String result;
            try{
                Document info=doc(detail(loc,r,"chulma_detail.html"));
                ArrayList<Horse> hs=entries(info);
                if(hs.size()!=r.count)throw new Exception("검빛 출전마 파싱 누락 "+hs.size()+"/"+r.count);
                popularity(info,hs);
                records(doc(detail(loc,r,"chulma_record.html")),hs,r.distance);
                result="원본: 검빛 / "+r+"\n\n"+calculate(hs,r.count);
            }catch(Exception ex){
                String k="";try{k=doc(kra(loc)).text().contains("출전")?"KRA 접속 확인":"KRA 응답 수신";}catch(Exception e){k="KRA 조회 실패";}
                result="분석 중단 (데이터 추정·날조 금지)\n"+ex.getMessage()+"\n"+k+"\n필요시 아래 CSV로 원본 기록을 입력하세요.";
            }
            final String s=result;runOnUiThread(()->{if(seq==ticket){output.setText(s);analyze.setEnabled(true);load.setEnabled(true);status.setText("분석 처리 종료");}});
        });
    }
    private void manual(){
        try{
            String[] lines=csv.getText().toString().trim().split("\\r?\\n");
            if(lines.length<4)throw new Exception("헤더와 최소 3두를 입력하세요.");
            String[] keys=lines[0].replace(" ","").split(",");
            String[] fields={"num","name","rating","weight","bestSec","avgSec","earlySec","lateSec","starts","wins"};
            HashMap<String,Integer> column=new HashMap<>();
            for(int i=0;i<keys.length;i++)column.put(keys[i],i);
            for(String f:fields)if(!column.containsKey(f))throw new Exception("CSV 누락 열: "+f);
            ArrayList<Horse> hs=new ArrayList<>();
            for(int i=1;i<lines.length;i++){
                String[] v=lines[i].split(",",-1);
                if(v.length<keys.length)throw new Exception("CSV "+(i+1)+"행 열 부족");
                Horse h=new Horse(Integer.parseInt(v[column.get("num")].trim()),v[column.get("name")].trim(),
                    Integer.parseInt(v[column.get("rating")].trim()),Double.parseDouble(v[column.get("weight")].trim()),"CSV");
                if(h.no<1||h.no>20||h.name.isEmpty())throw new Exception("CSV 마번 오류");
                for(Horse old:hs)if(old.no==h.no)throw new Exception("CSV 마번 중복");
                h.best=number(v[column.get("bestSec")]);h.avg=number(v[column.get("avgSec")]);
                h.early=number(v[column.get("earlySec")]);h.late=number(v[column.get("lateSec")]);
                h.starts=(int)number(v[column.get("starts")]);h.wins=(int)number(v[column.get("wins")]);
                hs.add(h);
            }
            output.setText("CSV 직접입력 (원본 미확인)\n\n"+calculate(hs,0));
        }catch(Exception e){output.setText("CSV 계산 불가: "+e.getMessage());}
    }
    private double number(String s){try{return Double.parseDouble(s.trim());}catch(Exception e){return Double.NaN;}}
    @Override protected void onDestroy(){seq++;pool.shutdownNow();super.onDestroy();}
}