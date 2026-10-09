package com.krace.analyzer;

import java.util.*;
/** Strict manual CSV parser. Only pre-race features are accepted as prediction inputs. */
public final class CsvInput {
    private CsvInput(){}

    public static RankEngine.Race parseManual(String csv){
        List<List<String>> rows=parseRows(csv);
        if(rows.size()<4)throw new IllegalArgumentException("CSV: 헤더 및 최소 3두 필요");
        if(rows.size()>21)throw new IllegalArgumentException("CSV: 출전마 최대 20두");
        List<String> header=rows.get(0);
        Map<String,Integer> columns=new HashMap<>();
        for(int i=0;i<header.size();i++){
            String key=header.get(i).trim();
            if(i==0&&key.startsWith("\uFEFF"))key=key.substring(1);
            if("number".equalsIgnoreCase(key))key="num"; // input method can expand "num"
            if(!columns.containsKey(key))columns.put(key,i);
            else throw new IllegalArgumentException("CSV 중복 열: "+key);
        }
        String[] required={"num","name","rating","weight","bestSec","avgSec","earlySec","lateSec","starts","wins"};
        for(String s:required)if(!columns.containsKey(s))throw new IllegalArgumentException("CSV 누락 열: "+s);
        ArrayList<RankEngine.Runner> runners=new ArrayList<>();
        for(int i=1;i<rows.size();i++){
            List<String> cells=rows.get(i);
            if(cells.size()!=header.size())throw new IllegalArgumentException("CSV "+(i+1)+"행 열 개수 오류: "+cells.size()+"/"+header.size());
            int number=requiredInt(cells,columns,"num",i+1,1,30);
            String name=cells.get(columns.get("name")).trim();
            if(name.length()==0||name.length()>45)throw new IllegalArgumentException("CSV "+(i+1)+"행 마명 오류");
            RankEngine.Runner h=new RankEngine.Runner(number,name);
            h.rating=requiredInt(cells,columns,"rating",i+1,0,150);
            h.weight=requiredNumber(cells,columns,"weight",i+1,45,65);
            h.best=time(cells,columns,"bestSec",i+1,35,220,false);
            h.avg=time(cells,columns,"avgSec",i+1,35,220,false);
            h.recent=time(cells,columns,"recentSec",i+1,35,220,false);
            h.early=time(cells,columns,"earlySec",i+1,10,30,false);
            h.late=time(cells,columns,"lateSec",i+1,10,30,false);
            h.recentLate=time(cells,columns,"recentLateSec",i+1,10,30,false);
            h.starts=optionalInt(cells,columns,"starts",i+1,0,1000,-1);
            h.wins=optionalInt(cells,columns,"wins",i+1,0,1000,-1);
            h.layoffWeeks=optionalInt(cells,columns,"layoffWeeks",i+1,0,500,-1);
            h.recentPlace=optionalInt(cells,columns,"recentPlace",i+1,1,30,-1);
            h.recentField=optionalInt(cells,columns,"recentField",i+1,1,30,-1);
            h.training14=optionalNumber(cells,columns,"training14",i+1,0,1000);
            h.vetRisk=optionalNumber(cells,columns,"vetRisk",i+1,0,100);
            h.jockeyRides=optionalInt(cells,columns,"jockeyRides",i+1,0,5000,-1);
            h.jockeyWins=optionalInt(cells,columns,"jockeyWins",i+1,0,5000,-1);
            if(h.starts<0 != (h.wins<0))throw new IllegalArgumentException("CSV "+(i+1)+"행 출전수/승수는 함께 입력");
            if(h.jockeyRides<0 != (h.jockeyWins<0))throw new IllegalArgumentException("CSV "+(i+1)+"행 기수기승/승수는 함께 입력");
            if(h.recentPlace>0&&h.recentField<0)throw new IllegalArgumentException("CSV "+(i+1)+"행 최근 출전두수 필요");
            if(h.recentField>0&&h.recentPlace>h.recentField)throw new IllegalArgumentException("CSV "+(i+1)+"행 최근 착순>출전두수");
            runners.add(h);
        }
        RankEngine.Race race=new RankEngine.Race("CSV:manual-unverified",runners);
        RankEngine.validate(race,false);
        return race;
    }
    private static String cell(List<String> row,Map<String,Integer> col,String name){
        Integer p=col.get(name);return p==null?"":row.get(p).trim();
    }
    private static double numeric(String value,String key,int line,double low,double high,boolean required){
        if(value.trim().isEmpty()){
            if(required)throw new IllegalArgumentException("CSV "+line+"행 "+key+" 값 누락");
            return Double.NaN;
        }
        double d;
        try{d=Double.parseDouble(value.trim());}
        catch(NumberFormatException ex){throw new IllegalArgumentException("CSV "+line+"행 "+key+" 숫자 아님: "+value);}
        if(!Double.isFinite(d)||d<low||d>high)throw new IllegalArgumentException("CSV "+line+"행 "+key+" 범위 오류: "+value);
        return d;
    }
    private static int requiredInt(List<String> r,Map<String,Integer> col,String key,int line,int low,int high){
        double x=numeric(cell(r,col,key),key,line,low,high,true);
        if(Math.rint(x)!=x)throw new IllegalArgumentException("CSV "+line+"행 "+key+" 정수 필요");
        return (int)x;
    }
    private static int optionalInt(List<String> r,Map<String,Integer> col,String key,int line,int low,int high,int missing){
        double x=numeric(cell(r,col,key),key,line,low,high,false);
        if(Double.isNaN(x))return missing;
        if(Math.rint(x)!=x)throw new IllegalArgumentException("CSV "+line+"행 "+key+" 정수 필요");
        return (int)x;
    }
    private static double requiredNumber(List<String> r,Map<String,Integer> col,String key,int line,double low,double high){
        return numeric(cell(r,col,key),key,line,low,high,true);
    }
    private static double optionalNumber(List<String> r,Map<String,Integer> col,String key,int line,double low,double high){
        return numeric(cell(r,col,key),key,line,low,high,false);
    }
    private static double time(List<String> r,Map<String,Integer> col,String key,int line,double low,double high,boolean required){
        String value=cell(r,col,key);
        if(value.indexOf(':')>0){
            String[] segments=value.split(":",-1);
            if(segments.length!=2)throw new IllegalArgumentException("CSV "+line+"행 "+key+" 시간 형식 오류");
            try{
                int minutes=Integer.parseInt(segments[0]);
                double seconds=Double.parseDouble(segments[1]);
                if(minutes<0||seconds<0||seconds>=60)throw new NumberFormatException();
                return numeric(Double.toString(minutes*60+seconds),key,line,low,high,required);
            }catch(NumberFormatException e){throw new IllegalArgumentException("CSV "+line+"행 "+key+" 시간 형식 오류");}
        }
        return numeric(value,key,line,low,high,required);
    }
    /** Proper RFC4180 quotes, escaped quotes, CRLF, and embedded comma/newline. */
    static List<List<String>> parseRows(String csv){
        if(csv==null||csv.trim().isEmpty())throw new IllegalArgumentException("CSV 내용 없음");
        if(csv.length()>200000)throw new IllegalArgumentException("CSV 크기 초과");
        ArrayList<List<String>> rows=new ArrayList<>();
        ArrayList<String> cells=new ArrayList<>();
        StringBuilder field=new StringBuilder();
        boolean quoted=false,closed=false,start=true;
        for(int i=0;i<csv.length();i++){
            char c=csv.charAt(i);
            if(quoted){
                if(c=='"'){
                    if(i+1<csv.length()&&csv.charAt(i+1)=='"'){field.append('"');i++;}
                    else{quoted=false;closed=true;}
                }else field.append(c);
                continue;
            }
            if(c=='"' && start){quoted=true;start=false;continue;}
            if(c==','||c=='\r'||c=='\n'){
                cells.add(field.toString().trim());field.setLength(0);start=true;closed=false;
                if(c!='.'){
                    if(c=='\r'&&i+1<csv.length()&&csv.charAt(i+1)=='\n')i++;
                    if(c=='\r'||c=='\n'){
                        if(!blank(cells))rows.add(new ArrayList<>(cells));
                        cells.clear();
                    }
                }
            }else{
                if(closed&&!Character.isWhitespace(c))throw new IllegalArgumentException("CSV 인용부호 이후 문자 오류");
                if(c=='"')throw new IllegalArgumentException("CSV 잘못된 인용부호");
                field.append(c);start=false;
            }
        }
        if(quoted)throw new IllegalArgumentException("CSV 닫히지 않은 인용부호");
        cells.add(field.toString().trim());
        if(!blank(cells))rows.add(cells);
        return rows;
    }
    private static boolean blank(List<String> cells){
        for(String s:cells)if(!s.isEmpty())return false;
        return true;
    }
}
