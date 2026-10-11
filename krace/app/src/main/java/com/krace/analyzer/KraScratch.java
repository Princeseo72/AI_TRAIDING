package com.krace.analyzer;
import java.util.*;
import java.util.regex.*;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;
/** Reads ONLY the KRA official withdrawn-horse table; never guesses scratched numbers. */
public final class KraScratch {
 public static final String URL="https://race.kra.co.kr/thisweekrace/ThisWeekChulmapyoChange.do";
 public static final class Entry{
  public final String date,region,name;
  public final int raceNo,horseNo;
  Entry(String d,String r,int n,int h,String s){date=d;region=r;raceNo=n;horseNo=h;name=s;}
 }
 private KraScratch(){}
 private static String normalizeDate(String date){
  Matcher m=Pattern.compile("(20\\d{2})[/.-](\\d{1,2})[/.-](\\d{1,2})").matcher(date);
  return m.find()?String.format(Locale.US,"%s-%02d-%02d",m.group(1),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3))):"";
 }
 private static String clean(String s){return s==null?"":s.replaceAll("\\s+","").trim();}
 /** Full list from change table only; jockey changes must never cause horse removals. */
 public static List<Entry> parse(Document doc){
  ArrayList<Entry> result=new ArrayList<>();
  if(doc==null)return result;
  for(Element table:doc.select("table")){
   boolean canceledHeader=false;
   for(Element tr:table.select("tr")){
    String h=clean(tr.text());
    if(h.contains("지역")&&h.contains("경주일자")&&h.contains("출전번호")&&h.contains("마명")&&
        h.contains("사유")&&!h.contains("변경전")&&!h.contains("변경후")){
      canceledHeader=true;break;
    }
   }
   if(!canceledHeader)continue;
   for(Element tr:table.select("tr")){
    Elements cells=tr.select("> td");
    if(cells.size()!=8)continue;
    String d=normalizeDate(cells.get(1).text());
    if(d.isEmpty())continue;
    String reg=clean(cells.get(0).text()),name=clean(cells.get(4).text());
    try{
      int race=Integer.parseInt(clean(cells.get(2).text()));
      int no=Integer.parseInt(clean(cells.get(3).text()));
      if(race<1||race>20||no<1||no>30||name.isEmpty())continue;
      result.add(new Entry(d,reg,race,no,name));
    }catch(NumberFormatException ignored){}
   }
  }
  return result;
 }
 public static List<Entry> forRace(Document doc,String date,String region,int raceNo){
  String target=region.equals("S")?"서울":region.equals("J")?"제주":"부경";
  ArrayList<Entry> found=new ArrayList<>();
  for(Entry e:parse(doc)){
   if(e.date.equals(date)&&e.region.equals(target)&&e.raceNo==raceNo)found.add(e);
  }
  return found;
 }
 /** Trim an official mobile roster only with matching official number and exact name. */
 public static ArrayList<KraMobile.Horse> reconcile(List<KraMobile.Horse> card,List<Entry> scratch,int expected){
  ArrayList<KraMobile.Horse> out=new ArrayList<>(card);
  if(out.size()==expected)return out;
  if(out.size()<expected || scratch==null||scratch.isEmpty())return new ArrayList<>();
  HashSet<Integer> removed=new HashSet<>();
  for(Entry e:scratch){
   for(int i=0;i<out.size();i++){
    KraMobile.Horse h=out.get(i);
    if(h.no==e.horseNo&&clean(h.name).equals(clean(e.name))){
      if(!removed.add(h.no))throw new IllegalArgumentException("중복 출전취소 공지");
      out.remove(i);break;
    }
   }
  }
  return out.size()==expected?out:new ArrayList<>();
 }
}
