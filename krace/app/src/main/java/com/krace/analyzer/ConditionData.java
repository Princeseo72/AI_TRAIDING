package com.krace.analyzer;
import java.util.*;
import java.util.regex.*;
import java.time.*;
import java.time.format.*;
import org.jsoup.nodes.*;
/** Public Gumvit pre-race training and veterinary indicators, without result leakage. */
final class ConditionData{
 private ConditionData(){}
 private static LocalDate prior(String raceDate,String mmdd){
   LocalDate race=LocalDate.parse(raceDate);
   String[] parts=mmdd.split("/");
   int mon=Integer.parseInt(parts[0]),day=Integer.parseInt(parts[1]);
   LocalDate d=LocalDate.of(race.getYear(),mon,day);
   if(d.isAfter(race))d=d.minusYears(1);
   return d;
 }
 static Map<Integer,Double> training(Document doc,String date){
   String flat=doc.body().text().replace('\u00a0',' ').replaceAll("\\s+"," ");
   // Headers contain number+name+jockey/trainer separated by slash.
   Pattern hp=Pattern.compile("(?:^|\\s)(\\d{1,2})\\s+([^\\s/]+)\\s+[^/]{1,22}\\s*/\\s*[^/]{1,22}\\s*/");
   Matcher heads=hp.matcher(flat);
   ArrayList<int[]> positions=new ArrayList<>();
   while(heads.find()){
     int n=Integer.parseInt(heads.group(1));
     if(n>=1&&n<=20)positions.add(new int[]{n,heads.start(),heads.end()});
   }
   LinkedHashMap<Integer,Double> out=new LinkedHashMap<>();
   LocalDate race=LocalDate.parse(date);
   Pattern wp=Pattern.compile("(\\d{2}/\\d{2})\\s+[월화수목금토일]\\s+[^▶]{0,36}?(\\d{1,2})분");
   for(int i=0;i<positions.size();i++){
     int[] h=positions.get(i);int end=i+1<positions.size()?positions.get(i+1)[1]:flat.length();
     String fragment=flat.substring(h[2],end);
     // Results after today's race are NOT part of workout input.
     int arrow=fragment.indexOf('▶');if(arrow>=0)fragment=fragment.substring(0,arrow);
     Matcher m=wp.matcher(fragment);double total=0;int seen=0;
     while(m.find()){
       LocalDate d;try{d=prior(date,m.group(1));}catch(Exception e){continue;}
       long days=java.time.temporal.ChronoUnit.DAYS.between(d,race);
       if(days>=0&&days<=14){total+=Integer.parseInt(m.group(2))*Math.exp(-days/14.);seen++;}
     }
     if(seen>0)out.put(h[0],total);
   }
   return out;
 }
 static Map<Integer,Double> veterinary(Document doc,String raceDate){
   LocalDate race=LocalDate.parse(raceDate);
   HashMap<Integer,Double> out=new HashMap<>();
   Pattern header=Pattern.compile("^\\s*(\\d{1,2})\\s+([^\\s]+)\\s+");
   Pattern dates=Pattern.compile("(20\\d\\d)[.](\\d\\d)[.](\\d\\d)");
   for(Element tr:doc.select("tr")){
     String text=tr.text().replace('\u00a0',' ').replaceAll("\\s+"," ");
     Matcher h=header.matcher(text);if(!h.find())continue;
     int n=Integer.parseInt(h.group(1));if(n<1||n>20)continue;
     Matcher d=dates.matcher(text);ArrayList<Integer> starts=new ArrayList<>(),ends=new ArrayList<>();
     ArrayList<LocalDate> ds=new ArrayList<>();
     while(d.find()){
       try{
        LocalDate v=LocalDate.of(Integer.parseInt(d.group(1)),Integer.parseInt(d.group(2)),Integer.parseInt(d.group(3)));
        starts.add(d.start());ends.add(d.end());ds.add(v);
       }catch(Exception ignored){}
     }
     if(ds.isEmpty())continue;
     double total=0;
     for(int i=0;i<ds.size();i++){
       long days=java.time.temporal.ChronoUnit.DAYS.between(ds.get(i),race);
       if(days<0||days>90)continue;
       int next=i+1<ds.size()?starts.get(i+1):text.length();
       String term=text.substring(ends.get(i),next);
       double severity=0;
       if(Pattern.compile("골절|탈구|파행|인대|구절염|호흡기|폐출혈|마비|축농증|요배통").matcher(term).find())severity=1.;
       else if(Pattern.compile("근육통|염증|각막|외이염|감기").matcher(term).find())severity=.6;
       else if(Pattern.compile("물리치료|피로회복|수액처치").matcher(term).find())severity=.2;
       total+=severity*Math.exp(-days/30.);
     }
     out.put(n,total);
   }
   return out;
 }
}