package com.krace.analyzer;
import java.util.*;
/** KRA meet=3 groups Busan/Gyeongnam and Yeongcheon; Gumvit loc=B is not a venue discriminator. */
public final class YeongnamPolicy {
 private YeongnamPolicy(){}
 public static boolean accepts(String loc,String venue){
  if(venue==null||venue.trim().isEmpty())return true;
  String v=venue.trim().replaceAll("\\s+","");
  if("B".equals(loc))return v.equals("영천")||v.equals("부경")||v.equals("부산")||v.equals("부산경남")||v.equals("영남");
  if("S".equals(loc))return v.equals("서울");
  if("J".equals(loc))return v.equals("제주");
  return false;
 }
 public static int matchedRunners(java.util.List<MainActivity.Horse> official,java.util.List<MainActivity.Horse> supplement){
   int count=0;
   for(MainActivity.Horse o:official){
    for(MainActivity.Horse g:supplement){
      if(o.no==g.no&&o.name.equals(g.name)){count++;break;}
    }
   }
   return count;
 }
}