package com.krace.analyzer;
import org.junit.Test;
import org.jsoup.Jsoup;
import static org.junit.Assert.*;
import java.util.*;
public class SourceGuardTest {
 @Test public void officialCountDetectsWithdrawal(){
  String html="<table><tr><th>순</th><th>일자</th><th>경주</th><th>등급</th><th>거리</th><th>편성</th><th>출전</th></tr>"+
  "<tr><td>9</td><td>2026/10/09 (금)</td><td>9</td><td>1등급</td><td>1200M</td><td>9두</td><td>8두</td></tr></table>";
  assertEquals(Integer.valueOf(8),SourceGuard.officialStarters(Jsoup.parse(html),"2026-10-09",9));
  assertNull(SourceGuard.officialStarters(Jsoup.parse(html),"2026-10-09",8));
 }
 @Test public void parsesActualRecordSectionAndRecentWithNoResultLeak(){
  String html="<table><tr><th>마번 마명 해당거리 최근 경주기록 출전 횟수</th></tr>"+
   "<tr><td>3 강서자이언트 26/03/08-6R 건 5% 서승운 55.0K 0:13.8⑥ 0:24.8⑤ 0:23.5④ 0:37.5 0:13.4③ 1:13.8③ 9회</td></tr>"+
   "<tr><th>마번 마명 S-1F(초반) G-1F(후반) 해당거리 최고기록 5착내 평균기록</th></tr>"+
   "<tr><td>3 강서자이언트 13.9 0:13.5⑥ 13.0① 0:12.0① 24/08/03-6R 건조 2% 먼로 53.0K 1:10.4① 1:12.8②</td></tr></table>";
  SourceGuard.Record r=SourceGuard.gumvitRecords(Jsoup.parse(html),"2026-10-09").get(3);
  assertNotNull(r);assertEquals(70.4,r.best,.001);assertEquals(72.8,r.avg,.001);
  assertEquals(13.9,r.early,.001);assertEquals(13.0,r.late,.001);
  assertEquals(73.8,r.recent,.001);assertEquals(13.4,r.recentLate,.001);assertEquals(3,r.recentPlace);
 }
 @Test public void blockFutureRecord(){
  String html="<table><tr><th>마번 마명 해당거리 최근 경주기록 출전</th></tr>"+
  "<tr><td>1 말 26/10/09-1R 건 2% 홍 55K 0:13.5 0:39.0 1:15.0</td></tr></table>";
  try{SourceGuard.gumvitRecords(Jsoup.parse(html),"2026-10-09");fail();}catch(IllegalArgumentException ex){assertTrue(ex.getMessage().contains("차단"));}
 }
 @Test public void blocksRaceAfterStart(){
  java.util.Calendar c=java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Seoul"));
  c.clear();c.set(2026,9,9,17,56,0);
  assertFalse(SourceGuard.beforeStart("2026-10-09","17:55",c.getTimeInMillis()));
  c.set(2026,9,9,17,54,0);
  assertTrue(SourceGuard.beforeStart("2026-10-09","17:55",c.getTimeInMillis()));
  assertFalse(SourceGuard.beforeStart("2026-10-09","",c.getTimeInMillis()));
 }

 @Test public void twoDigitCircledFinishesAreCorrect(){
   assertEquals(10,SourceGuard.circledRank("⑩"));
   assertEquals(12,SourceGuard.circledRank("⑫"));
   assertEquals(1,SourceGuard.circledRank("①"));
 }
}