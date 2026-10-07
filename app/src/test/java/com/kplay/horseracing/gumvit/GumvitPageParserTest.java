package com.kplay.horseracing.gumvit;

import org.jsoup.Jsoup;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class GumvitPageParserTest {
    @Test
    public void parsesRaceNumberAcrossNbspAndSpacing() {
        String html="<html><body><div>\u00A02경주\u00A02026-10-03 (토) 제주 경마장 제78일 경주 출발 12:05</div></body></html>";
        assertEquals(2, GumvitPageParser.actualRaceNo(Jsoup.parse(html)));
    }

    @Test
    public void parsesDateFromHeaderWithKoreanDay() {
        String html="<html><body><div>2경주 2026-10-03(토) 제주경마장</div></body></html>";
        assertEquals("2026-10-03", GumvitPageParser.actualDate(Jsoup.parse(html)));
    }

    @Test
    public void typeCandidatesFollowRaceDayAndIncludeFallbacks() {
        List<String> fri=GumvitPageParser.typeCandidates("2026-10-02");
        List<String> sat=GumvitPageParser.typeCandidates("2026-10-03");
        List<String> sun=GumvitPageParser.typeCandidates("2026-10-04");
        assertEquals("5",fri.get(0));
        assertEquals("6",sat.get(0));
        assertEquals("7",sun.get(0));
        assertTrue(sat.contains("5"));
        assertTrue(sat.contains("7"));
    }

    @Test
    public void validatesRequestedIdentityAndRegion() {
        String html="<html><body><div>2경주 2026-10-03 (토) 제주 경마장</div></body></html>";
        assertTrue(GumvitPageParser.identityMatches(Jsoup.parse(html),"2026-10-03","제주",2));
        assertFalse(GumvitPageParser.identityMatches(Jsoup.parse(html),"2026-10-03","부산경남",2));
        assertFalse(GumvitPageParser.identityMatches(Jsoup.parse(html),"2026-10-03","제주",3));
    }
    @Test
    public void selectsPopularityEntryTableInsteadOfBasicProfileTable() {
        String html="<html><body>"
                +"<table><tr><th>마번</th><th>관리</th><th>마명</th><th>산지</th><th>기수명</th><th>조교사(조)</th></tr>"
                +"<tr><td>1</td><td></td><td>기본표말</td><td>한</td><td>기수A</td><td>조교A</td></tr></table>"
                +"<table><tr><th>마번</th><th>마명</th><th>전적</th><th>조교사</th><th>기수</th><th>검빛전문위원</th></tr>"
                +"<tr><td>1</td><td>정상마</td><td>10전 (2/1)</td><td>조교B</td><td>기수B</td><td></td></tr></table>"
                +"</body></html>";
        assertNotNull(GumvitPageParser.findEntryTable(Jsoup.parse(html)));
        assertTrue(GumvitPageParser.findEntryTable(Jsoup.parse(html)).text().contains("정상마"));
    }
}
