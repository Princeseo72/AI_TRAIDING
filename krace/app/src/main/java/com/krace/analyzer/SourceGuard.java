package com.krace.analyzer;
import java.util.*;
import java.util.regex.*;
import org.jsoup.nodes.*;
final class SourceGuard {
 private SourceGuard(){}
 /** Official KRA race listing: columns: no/date/race/grade/distance/entries/actual starters. */
 static Integer officialStarters(Document d,String date,int raceNo){
   for(Element tr:d.select("tr")){
     org.jsoup.select.Elements td=tr.select("td");
     if(td.size()<7)continue;
     String dateText=td.get(1).text();
     Matcher dm=Pattern.compile("(20\\d{2})[/.-](\\d{1,2})[/.-](\\d{1,2})").matcher(dateText);
     if(!dm.find())continue;
     String found=String.format(java.util.Locale.US,"%s-%02d-%02d",dm.group(1),Integer.parseInt(dm.group(2)),Integer.parseInt(dm.group(3)));
     if(!date.equals(found))continue;
     Matcher rn=Pattern.compile("\\d+").matcher(td.get(2).text());
     if(!rn.find()||Integer.parseInt(rn.group())!=raceNo)continue;
     Matcher m=Pattern.compile("(\\d+)\\s*두").matcher(td.get(6).text());
     if(m.find())return Integer.parseInt(m.group(1));
   }
   return null;
 }
 static final class Record{
   int no,recentPlace=-1;String name="";
   double recent=Double.NaN,recentLate=Double.NaN,best=Double.NaN,avg=Double.NaN,early=Double.NaN,late=Double.NaN;
 }
 static HashMap<Integer,Record> gumvitRecords(Document d,String raceDate){
   HashMap<Integer,Record> out=new HashMap<>();
   Pattern row=Pattern.compile("^\\s*(\\d{1,2})\\s+(\\S+)\\s+");
   Pattern clock=Pattern.compile("(?<!\\d)([01]:\\d\\d\\.\\d)(?!\\d)");
   Pattern plain=Pattern.compile("(?<![\\d:])(1[2-6]\\.\\d)(?!\\d)");
   int phase=0;
   for(Element tr:d.select("tr")){
     String text=tr.text().replace('\u00a0',' ').replaceAll("\\s+"," ").trim();
     if(text.contains("마번")&&text.contains("해당거리")&&text.contains("최근")){phase=1;continue;}
     if(text.contains("마번")&&text.contains("S-1F")&&text.contains("최고기록")){phase=2;continue;}
     if(text.contains("마번")&&text.contains("1000")&&text.contains("기록")){phase=3;continue;}
     if(phase!=1&&phase!=2)continue;
     Matcher rowMatch=row.matcher(text);if(!rowMatch.find())continue;
     int no=Integer.parseInt(rowMatch.group(1));
     if(no<1||no>20)continue;
     String name=rowMatch.group(2);
     Record r=out.computeIfAbsent(no,k->new Record());
     if(!r.name.isEmpty()&&!r.name.equals(name))throw new IllegalArgumentException("마번/마명 불일치 "+no);
     r.no=no;r.name=name;
     ArrayList<Double> times=new ArrayList<>();
     Matcher m=clock.matcher(text);
     while(m.find()){
       String[] t=m.group(1).split(":");
       times.add(Integer.parseInt(t[0])*60.+Double.parseDouble(t[1]));
     }
     if(phase==1){
       Matcher old=Pattern.compile("(\\d{2})/(\\d{2})/(\\d{2})").matcher(text);
       if(!old.find())continue;
       String prior=String.format(java.util.Locale.US,"20%s-%s-%s",old.group(1),old.group(2),old.group(3));
       if(prior.compareTo(raceDate)>=0)throw new IllegalArgumentException("동일·미래경주 실적 유입 차단");
       if(times.size()>=3){
         r.recent=times.get(times.size()-1);
         r.recentLate=times.get(times.size()-2);
       }
       Matcher place=Pattern.compile("1:\\d\\d\\.\\d([①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳])").matcher(text);
       if(place.find())r.recentPlace=place.group(1).charAt(0)-'①'+1;
     }else{
       if(times.size()>=2){
         r.best=times.get(times.size()-2);
         r.avg=times.get(times.size()-1);
       }
       Matcher s=plain.matcher(text);ArrayList<Double> a=new ArrayList<>();
       while(s.find())a.add(Double.parseDouble(s.group(1)));
       if(a.size()>=2){r.early=a.get(0);r.late=a.get(1);}
     }
   }
   return out;
 }
}