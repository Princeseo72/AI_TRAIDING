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
}
