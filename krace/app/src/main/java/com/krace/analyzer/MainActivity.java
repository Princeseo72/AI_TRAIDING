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
    private EditText csv;
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
        box.addView(label("※ 출력은 검증되지 않은 탐색적 수식의 상대순위입니다. 실제 적중확률이나 수익을 보장하지 않습니다.",11,0xffd3b47e));
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
                if(!dup)a.add(new Horse(n,m.group(2),Integer.parseInt(m.group(3)),Double.parseDouble(m.group(4)),m.group(5)));
            }
        }
        return a;
    }
    private double toSeconds(String s){
        Matcher m=Pattern.compile("^([01]):(\\d\\d\\.\\d)$").matcher(s);
        return m.find()?60*Integer.parseInt(m.group(1))+Double.parseDouble(m.group(2)):Double.NaN;
    }
    private void records(Document d,ArrayList<Horse> hs,int distance){
        Pattern times=Pattern.compile("(?<!\\d)([01]:\\d\\d\\.\\d)(?!\\d)");
        Pattern speeds=Pattern.compile("0:(1[2-6]\\.\\d)");
        boolean primary=false;
        for(Element row:d.select("tr")){
            String text=row.text().replace('\u00a0',' ').replaceAll("\\s+"," ").trim();
            if(text.contains("해당거리 최고기록")||text.contains("5착내 평균기록"))primary=true;
            if(text.contains("기록 비교 최고기록")&&text.contains("S-1F"))primary=false;
            for(Horse h:hs){
                if(!Pattern.compile("^"+h.no+"\\s+"+Pattern.quote(h.name)+"(?:\\s|$)").matcher(text).find())continue;
                ArrayList<Double> raceTimes=new ArrayList<>();
                Matcher tm=times.matcher(text);
                while(tm.find()){
                    double x=toSeconds(tm.group(1));
                    if(x>distance*.052&&x<distance*.12)raceTimes.add(x);
                }
                if(!primary){
                    if(raceTimes.size()==1&&Double.isNaN(h.best))h.score=0; // latest-race row, not scoring evidence
                    continue;
                }
                if(raceTimes.size()>=2){
                    h.best=raceTimes.get(raceTimes.size()-2);
                    h.avg=raceTimes.get(raceTimes.size()-1);
                    ArrayList<Double> seg=new ArrayList<>();
                    Matcher sm=speeds.matcher(text);while(sm.find())seg.add(Double.parseDouble(sm.group(1)));
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
    private String calculate(ArrayList<Horse> hs,int expected)throws Exception{
        if(hs.size()<3)throw new Exception("최소 출전 3두 미만");
        if(expected>0&&hs.size()!=expected)throw new Exception("출전마 누락 "+hs.size()+"/"+expected+"두");
        int valid=0;for(Horse h:hs)if(!Double.isNaN(h.best)&&!Double.isNaN(h.avg))valid++;
        if(valid<Math.ceil(hs.size()*.6))throw new Exception("거리 최고/평균기록 부족 "+valid+"/"+hs.size()+"두. 추정 계산 차단.");
        for(Horse h:hs){
            h.score=-.27*z(h,"best",hs)-.18*z(h,"avg",hs)-.18*z(h,"late",hs)-.07*z(h,"early",hs)
                  +.16*z(h,"rating",hs)-.06*z(h,"weight",hs)+.08*z(h,"win",hs);
        }
        double sum=0;HashMap<Integer,Double> strength=new HashMap<>();
        for(Horse h:hs){double s=Math.exp(Math.max(-3,Math.min(3,h.score))*1.5);strength.put(h.no,s);sum+=s;}
        ArrayList<Ticket> pairs=new ArrayList<>(),triples=new ArrayList<>();
        for(Horse a:hs)for(Horse b:hs)if(a.no!=b.no){
            double pa=strength.get(a.no)/sum*strength.get(b.no)/(sum-strength.get(a.no));
            pairs.add(new Ticket(a.no,b.no,0,pa));
            for(Horse c:hs)if(c.no!=a.no&&c.no!=b.no){
                double pc=pa*strength.get(c.no)/(sum-strength.get(a.no)-strength.get(b.no));
                triples.add(new Ticket(a.no,b.no,c.no,pc));
            }
        }
        pairs.sort((x,y)->Double.compare(y.score,x.score));
        triples.sort((x,y)->Double.compare(y.score,x.score));
        hs.sort((a,b)->Double.compare(b.score,a.score));
        StringBuilder s=new StringBuilder();
        s.append("쌍승: ").append(pairs.get(0).a).append(" → ").append(pairs.get(0).b).append("\n");
        s.append("삼쌍승: ").append(triples.get(0).a).append(" → ").append(triples.get(0).b).append(" → ").append(triples.get(0).c).append("\n");
        s.append("보완: ").append(triples.get(1).a).append(" → ").append(triples.get(1).b).append(" → ").append(triples.get(1).c);
        s.append("\n\n출전마 ").append(hs.size()).append("두 · 거리기록 ").append(valid).append("두 확보\n");
        s.append("---- 전체 상대점수 ----\n");
        for(Horse h:hs){
            s.append(h.no).append(" ").append(h.name).append(" / 레이팅 ").append(h.rating)
            .append(" / ").append(h.weight).append("kg / ")
            .append(String.format(Locale.US,"%.3f",h.score)).append(" / 최고 ")
            .append(Double.isNaN(h.best)?"미확인":String.format(Locale.US,"%.1f",h.best))
            .append("\n");
        }
        s.append("\n중요: 위 수치는 검증되지 않은 임의 가중치의 상대점수이며 적중확률이 아닙니다.");
        return s.toString();
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
                final ArrayList<Race> display=today.isEmpty()?a:today;
                runOnUiThread(()->{
                    if(seq!=ticket)return;
                    races.clear();races.addAll(display);
                    raceSpinner.setAdapter(new ArrayAdapter<Race>(this,android.R.layout.simple_spinner_dropdown_item,races));
                    analyze.setEnabled(true);load.setEnabled(true);
                    status.setText("검빛 출전목록 "+races.size()+"경주 확인"+(today.isEmpty()?" (당일 목록 아님)":""));
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