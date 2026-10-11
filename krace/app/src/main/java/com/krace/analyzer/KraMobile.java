package com.krace.analyzer;
import java.util.*;
import java.util.regex.*;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;
/** KRA mobile race card, which supplies official runner number, name, weight and rating. */
public final class KraMobile {
 public static final class Horse {
  public int no,rating;public String name,jockey,trainer;
  public double weight;
  public Horse(int n,String s,int r,double kg,String j,String tr){
    no=n;name=s;rating=r;weight=kg;jockey=j;trainer=tr;
  }
 }
 public static String url(String track,String date,int race){
   String region=track.equals("S")?"seoul":track.equals("B")?"bukyeong":"jeju";
   if(!date.matches("\\d{4}-\\d{2}-\\d{2}")||race<1||race>20)throw new IllegalArgumentException("경주 날짜/번호 오류");
   return "https://m.kra.co.kr/race/"+region+"/chulmaDetailInfoChulmapyo.do?rcDate="+date.replace("-","")+"&rcNo="+race;
 }
 static List<Horse> parse(Document d,String date,int race){
   String text=d.text().replace('\u00a0',' ').replaceAll("\\s+"," ");
   // A response with null() or a different race must never be accepted.
   Matcher top=Pattern.compile("(\\d{2})[.]\\s*(\\d{2})[.]\\s*(\\d{2})\\s*\\([^)]*\\)\\s*제\\s*(\\d+)\\s*R").matcher(text);
   if(!top.find() || !(("20"+top.group(1)+"-"+top.group(2)+"-"+top.group(3)).equals(date)) ||
      Integer.parseInt(top.group(4))!=race)return Collections.emptyList();
   LinkedHashMap<Integer,Horse> out=new LinkedHashMap<>();
   for(Element table:d.select("table")){
    String label=table.text().replaceAll("\\s+","");
    if(!(label.contains("마명")&&label.contains("중량")&&label.contains("레이팅")&&label.contains("기수")))continue;
    for(Element row:table.select("tr")){
     Elements cells=row.select("> td");if(cells.size()<9)continue;
     String idx=cells.get(0).text().trim();
     if(!idx.matches("\\d{1,2}"))continue;
     int no=Integer.parseInt(idx);
     if(no<1||no>30)continue;
     String name=cells.get(1).text().trim();
     if(name.isEmpty())continue;
     double weight;
     try {weight=Double.parseDouble(cells.get(5).text().replaceAll("[^0-9.]",""));}
     catch(Exception e){continue;}
     String rr=cells.get(6).text().trim();
     int rating=0;try{rating=Integer.parseInt(rr);}catch(Exception ignored){}
     out.put(no,new Horse(no,name,rating,weight,cells.get(7).text().trim(),cells.get(8).text().trim()));
    }
   }
   return new ArrayList<>(out.values());
 }
}