package com.krace.analyzer;
import org.junit.Test;
import static org.junit.Assert.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import java.util.Map;
/** Real public endpoints: diagnostics only, never claim success from skipped/failed results. */
public class LiveWebSmokeTest {
 private Document fetch(String u)throws Exception{
  return Jsoup.connect(u).userAgent("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
   .timeout(17000).maxBodySize(1500000).get();
 }
 @Test public void gumvitRecordPageParsesKnownNineHorseCard()throws Exception{
  Document d=fetch("https://www.gumvit.com/statv40/chulma_record.html?loc=B&m_date=2026-10-09&race_no=9&type=5");
  Map<Integer,SourceGuard.Record> m=SourceGuard.gumvitRecords(d,"2026-10-09");
  assertEquals("Gumvit race 9 must include nine card entries",9,m.size());
  assertNotNull(m.get(3));
  assertEquals("강서자이언트",m.get(3).name);
  assertEquals(70.4,m.get(3).best,.01);
  assertEquals(72.8,m.get(3).avg,.01);
  assertEquals(13.9,m.get(3).early,.01);
 }
 @Test public void kraChangesLiveStarterCountFromCardNineToEight()throws Exception{
  Document d=fetch("https://race.kra.co.kr/chulmainfo/ChulmaDetailInfoList.do?Act=02&Sub=1&meet=3");
  Integer n=SourceGuard.officialStarters(d,"2026-10-09",9);
  assertEquals("KRA official starter count",Integer.valueOf(8),n);
 }
}