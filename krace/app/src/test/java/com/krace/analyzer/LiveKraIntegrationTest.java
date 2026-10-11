package com.krace.analyzer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
/** Live diagnostics intentionally separate from reproducible unit tests. */
public class LiveKraIntegrationTest {
 private Document get(String url)throws Exception {
  Document doc=Jsoup.connect(url).timeout(22000)
    .userAgent("Mozilla/5.0 (Android 10; Mobile) AppleWebKit/537.36 Chrome/120.0")
    .get();
  System.out.println("LIVE KRA "+url+" length="+doc.outerHtml().length()+" title="+doc.title());
  return doc;
 }
 @Test public void kraBoardHasOct11SeoulCards()throws Exception {
  Document d=get("https://race.kra.co.kr/chulmainfo/ChulmaDetailInfoList.do?Act=02&Sub=1&meet=1");
  List<KraBoard.RaceLine> rs=KraBoard.parse(d,"2026-10-11");
  System.out.println("KRA 2026-10-11 cards="+rs.size());
  for(KraBoard.RaceLine r:rs)System.out.println("KRA BOARD "+r.date+" "+r.race+" "+r.starters+" "+r.time);
  assertFalse("KRA Oct11 Seoul current official list must be parseable",rs.isEmpty());
 }
 @Test public void kraMobileCardHasOct11RunnerDetails()throws Exception {
  String u=KraMobile.url("S","2026-10-11",1);
  Document d=get(u);
  List<KraMobile.Horse> hs=KraMobile.parse(d,"2026-10-11",1);
  System.out.println("KRA Mobile runners="+hs.size()+" first="+(hs.isEmpty()?d.text().substring(0,Math.min(260,d.text().length())):hs.get(0).name));
  assertTrue("KRA mobile current runner card must be accessible",hs.size()>=3);
 }
}