package com.kplay.horseracing.gumvit;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;
import static org.junit.Assert.*;

public class GumvitLiveSmokeTest {
    private Document loadVerified(String date,String region,int raceNo) throws Exception {
        Exception last=null;
        String loc="서울".equals(region)?"S":("제주".equals(region)?"J":"B");
        for(String type:GumvitPageParser.typeCandidates(date)){
            String url="https://www.gumvit.com/statv40/chulma_detail.html?loc="+loc+"&m_date="+date+"&race_no="+raceNo+"&type="+type;
            for(int attempt=0;attempt<2;attempt++){
                try{
                    Document d=Jsoup.connect(url)
                            .userAgent("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36")
                            .referrer("https://www.gumvit.com/statv40/")
                            .timeout(10000).get();
                    if(GumvitPageParser.identityMatches(d,date,region,raceNo)
                            && GumvitPageParser.findEntryTable(d)!=null)return d;
                }catch(Exception e){last=e;}
            }
        }
        throw new AssertionError("LIVE_GUMVIT_FAIL "+date+" "+region+" "+raceNo+"R",last);
    }

    @Test public void jejuOct2Race1LoadsLive() throws Exception {
        Document d=loadVerified("2026-10-02","제주",1);
        assertEquals("2026-10-02",GumvitPageParser.actualDate(d));
        assertEquals(1,GumvitPageParser.actualRaceNo(d));
    }

    @Test public void busanOct2Race1LoadsLive() throws Exception {
        Document d=loadVerified("2026-10-02","부산경남",1);
        assertEquals("2026-10-02",GumvitPageParser.actualDate(d));
        assertEquals(1,GumvitPageParser.actualRaceNo(d));
    }

    @Test public void jejuOct3Race2LoadsLive() throws Exception {
        Document d=loadVerified("2026-10-03","제주",2);
        assertEquals("2026-10-03",GumvitPageParser.actualDate(d));
        assertEquals(2,GumvitPageParser.actualRaceNo(d));
    }
}
