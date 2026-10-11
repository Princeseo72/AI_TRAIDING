package com.krace.analyzer;
import java.util.*;
import java.util.regex.*;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;
/** Strict KRA official calendar card parser. It reads actual starters, not published field size. */
public final class KraBoard {
 public static class RaceLine {
  public String date="",time="",grade="",venue="";
  public int race,distance,published,starters;
  public String id(){return date+"|"+race;}
 }
 private static String date(String t){
   Matcher m=Pattern.compile("(20\\d{2})[/.-](\\d{1,2})[/.-](\\d{1,2})").matcher(t);
   if(!m.find())return "";
   return String.format(Locale.US,"%s-%02d-%02d",m.group(1),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)));
 }
 private static int number(String t){
   Matcher m=Pattern.compile("\\d+").matcher(t);return m.find()?Integer.parseInt(m.group()):0;
 }
 public static List<RaceLine> parse(Document page,String selectedDate){
   LinkedHashMap<String,RaceLine> out=new LinkedHashMap<>();
   for(Element tr:page.select("tr")){
     Elements cells=tr.select("> td");
     if(cells.size()<10)continue;
     String d=date(cells.get(1).text());
     if(d.isEmpty() || (selectedDate!=null&&!selectedDate.equals(d)))continue;
     int no=number(cells.get(2).text()),dist=number(cells.get(4).text());
     int published=number(cells.get(5).text()),starters=number(cells.get(6).text());
     String clock=cells.get(8).text().trim();
     if(no<1||no>20||dist<800||dist>2500||published<3||published>20||starters<3||starters>published||
        !clock.matches("\\d{2}:\\d{2}"))continue;
     RaceLine r=new RaceLine();r.date=d;r.race=no;r.distance=dist;r.grade=cells.get(3).text().trim();
     r.published=published;r.starters=starters;r.time=clock;r.venue=cells.get(9).text().trim();
     out.put(r.id(),r);
   }
   return new ArrayList<>(out.values());
 }
 static RaceLine find(Document page,String date,int no){
    for(RaceLine l:parse(page,date))if(l.race==no)return l;
    return null;
 }
}