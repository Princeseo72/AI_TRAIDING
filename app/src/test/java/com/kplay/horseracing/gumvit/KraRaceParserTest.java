package com.kplay.horseracing.gumvit;

import org.jsoup.Jsoup;
import org.json.JSONArray;
import org.junit.Test;
import static org.junit.Assert.*;

public class KraRaceParserTest {
    @Test public void parsesTargetRaceFromJockeyRidingTable() throws Exception {
        String html="<html><body><h3>2026/10/03 (토)</h3><table>"
          +"<tr><th>기수명</th><th>출전</th><th>1</th><th>2</th><th>3</th></tr>"
          +"<tr><td>김한남</td><td>2</td><td>①첫말<br>(1)김길홍</td><td>②태흥산성<br>(19)윤호상</td><td></td></tr>"
          +"<tr><td>문현진</td><td>1</td><td></td><td>④천하제왕<br>(13)고영덕</td><td></td></tr>"
          +"</table></body></html>";
        JSONArray a=KraRaceParser.parseRiding(Jsoup.parse(html),"2026-10-03","제주",2);
        assertEquals(2,a.length());
        assertEquals(2,a.getJSONObject(0).getInt("number"));
        assertEquals("태흥산성",a.getJSONObject(0).getString("name"));
        assertEquals("김한남",a.getJSONObject(0).getString("jockey"));
        assertEquals("윤호상",a.getJSONObject(0).getString("trainer"));
        assertEquals(4,a.getJSONObject(1).getInt("number"));
    }

    @Test public void rejectsWrongDateRidingPage() throws Exception {
        String html="<html><body><h3>2026/10/02 (금)</h3><table><tr><th>기수명</th><th>출전</th><th>1</th></tr>"
          +"<tr><td>기수</td><td>1</td><td>①말<br>(1)조교</td></tr></table></body></html>";
        assertEquals(0,KraRaceParser.parseRiding(Jsoup.parse(html),"2026-10-03","제주",1).length());
    }

    @Test public void parsesCircledHorseNumbers() {
        assertEquals(1,KraRaceParser.circledNumber("①"));
        assertEquals(10,KraRaceParser.circledNumber("⑩"));
        assertEquals(11,KraRaceParser.circledNumber("⑪"));
        assertEquals(-1,KraRaceParser.circledNumber("말"));
    }

    @Test public void recognizesRaceExistenceInKraWeightTable() {
        String html="<table><tr><th>지역</th><th>경주일자</th><th>경주번호</th></tr>"
          +"<tr><td>제주</td><td>2026/10/03(토)</td><td>2</td></tr>"
          +"<tr><td>부경</td><td>2026/10/02(금)</td><td>1</td></tr></table>";
        assertTrue(KraRaceParser.raceExists(Jsoup.parse(html),"2026-10-03","제주",2));
        assertTrue(KraRaceParser.raceExists(Jsoup.parse(html),"2026-10-02","부산경남",1));
        assertFalse(KraRaceParser.raceExists(Jsoup.parse(html),"2026-10-03","제주",3));
    }
}
