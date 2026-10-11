package com.krace.analyzer;
import org.junit.Test;
import org.jsoup.Jsoup;
import static org.junit.Assert.*;
import java.util.*;
public class KraBoardTest {
 private String row(String day,int race,int pub,int actual,String time){
  return "<tr><td>1</td><td>"+day+" (일)</td><td>"+race+"</td><td>국5등급</td><td>1400M</td>"+
     "<td>"+pub+"두</td><td>"+actual+"두</td><td>일반</td><td>"+time+"</td><td>서울</td><td></td></tr>";
 }
 @Test public void readsActualNotPublished(){
  String html="<table><tr><th>순</th><th>경주일자</th><th>경주</th><th>등급</th><th>거리</th><th>편성</th><th>출전</th><th>경주명</th><th>출발시각</th><th>개최지</th></tr>"+
  row("2026/10/11",7,11,10,"14:50")+row("2026/10/10",8,12,12,"16:05")+"</table>";
  List<KraBoard.RaceLine> found=KraBoard.parse(Jsoup.parse(html),"2026-10-11");
  assertEquals(1,found.size());assertEquals(10,found.get(0).starters);assertEquals(11,found.get(0).published);
  assertEquals(7,found.get(0).race);assertEquals("14:50",found.get(0).time);
 }
 @Test public void ignoresMalformedCountsAndMissingClock(){
  String html="<table>"+row("2026/10/11",4,8,11,"13:30")+row("2026/10/11",5,8,8,"시간미정")+"</table>";
  assertTrue(KraBoard.parse(Jsoup.parse(html),"2026-10-11").isEmpty());
 }
}