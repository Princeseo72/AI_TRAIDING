package com.krace.analyzer;
import org.junit.Test;
import org.jsoup.Jsoup;
import static org.junit.Assert.*;
import java.util.*;
public class KraMobileTest{
 static String html(String head){
  return "<html><body><h2>"+head+"</h2><table>"+
   "<tr><th>번호</th><th>마명</th><th>산지</th><th>성별</th><th>연령</th><th>중량</th><th>레이팅</th><th>기수</th><th>조교사</th></tr>"+
   "<tr><td>1</td><td>용비신성</td><td>한</td><td>수</td><td>2</td><td>55.0</td><td>-</td><td>유승완</td><td>백종수</td></tr>"+
   "<tr><td>2</td><td>호랑이</td><td>한</td><td>암</td><td>3</td><td>53.5</td><td>30</td><td>다실바</td><td>김승태</td></tr></table></body></html>";
 }
 @Test public void officialDateAndRaceMustMatch(){
  List<KraMobile.Horse> good=KraMobile.parse(Jsoup.parse(html("26.10.11(일) 제11R (서울)")),"2026-10-11",11);
  assertEquals(2,good.size());assertEquals(53.5,good.get(1).weight,.001);assertEquals(30,good.get(1).rating);
  assertEquals(0,good.get(0).rating);
  assertTrue(KraMobile.parse(Jsoup.parse(html("26.10.11(일) 제11R")),"2026-10-10",11).isEmpty());
  assertTrue(KraMobile.parse(Jsoup.parse(html("26.10.11(일) 제11R")),"2026-10-11",10).isEmpty());
 }
 @Test public void urlsAreOfficialAndTrackSpecific(){
  assertTrue(KraMobile.url("B","2026-10-09",9).contains("/bukyeong/"));
  assertTrue(KraMobile.url("S","2026-10-11",1).contains("/seoul/"));
  assertTrue(KraMobile.url("J","2026-10-11",1).contains("/jeju/"));
 }
}