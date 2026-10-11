package com.krace.analyzer;
import org.junit.Test;
import org.jsoup.Jsoup;
import static org.junit.Assert.*;
import java.util.*;
public class KraScratchTest{
 String html="<table><tr><th>지역</th><th>경주일자</th><th>경주번호</th><th>출전번호</th><th>마명</th><th>조교사</th><th>기수명</th><th>사유</th></tr>"+
  "<tr><td>서울</td><td>2026/10/11</td><td>9</td><td>11</td><td>벌교데스티노</td><td>서홍수</td><td>빅투아르</td><td>왼 뒷다리 질병</td></tr>"+
  "</table><table><tr><th>지역</th><th>경주일자</th><th>경주번호</th><th>출전번호</th><th>마명</th><th>변경전</th><th>변경후</th><th>중량</th><th>사유</th></tr>"+
  "<tr><td>서울</td><td>2026/10/11</td><td>9</td><td>5</td><td>기수변경말</td><td>이동하</td><td>임다빈</td><td>55</td><td>교체</td></tr></table>";
 @Test public void onlyOfficialWithdrawals(){
  List<KraScratch.Entry> x=KraScratch.forRace(Jsoup.parse(html),"2026-10-11","S",9);
  assertEquals(1,x.size());assertEquals(11,x.get(0).horseNo);assertEquals("벌교데스티노",x.get(0).name);
 }
 @Test public void canReconcileElevenToTenWhenWithdrawalMatches(){
  ArrayList<KraMobile.Horse> horses=new ArrayList<>();
  for(int i=1;i<=11;i++)horses.add(new KraMobile.Horse(i,i==11?"벌교데스티노":"예시"+i,60,54,"기수","조교사"));
  assertEquals(10,KraScratch.reconcile(horses,KraScratch.forRace(Jsoup.parse(html),"2026-10-11","S",9),10).size());
  assertEquals(11,horses.size()); // original untouched
 }
 @Test public void cannotGuessWithdrawnHorseWhenNameDisagrees(){
  ArrayList<KraMobile.Horse> hs=new ArrayList<>();
  for(int i=1;i<=11;i++)hs.add(new KraMobile.Horse(i,"말"+i,50,54,"",""));
  assertTrue(KraScratch.reconcile(hs,KraScratch.forRace(Jsoup.parse(html),"2026-10-11","S",9),10).isEmpty());
 }
}