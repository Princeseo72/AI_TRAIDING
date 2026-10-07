package com.kplay.horseracing.gumvit;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.json.JSONArray;
import org.junit.Test;
import static org.junit.Assert.*;

public class DualSourceLiveSmokeTest {
    private int meet(String region){return "제주".equals(region)?2:("서울".equals(region)?1:3);}
    private String loc(String region){return "제주".equals(region)?"J":("서울".equals(region)?"S":"B");}

    private int gumvitCount(String date,String region,int raceNo){
        for(String type:GumvitPageParser.typeCandidates(date)){
            String url="https://www.gumvit.com/statv40/chulma_detail.html?loc="+loc(region)+"&m_date="+date+"&race_no="+raceNo+"&type="+type;
            try{
                Document d=Jsoup.connect(url).userAgent("Mozilla/5.0").timeout(7000).get();
                if(GumvitPageParser.identityMatches(d,date,region,raceNo)){
                    org.jsoup.nodes.Element t=GumvitPageParser.findEntryTable(d);
                    if(t!=null)return t.select("tr").size()-1;
                }
            }catch(Exception ignored){}
        }
        return 0;
    }

    private int kraCount(String date,String region,int raceNo)throws Exception{
        Document w=Jsoup.connect("https://race.kra.co.kr/thisweekrace/ThisWeekWeight.do")
                .userAgent("Mozilla/5.0").timeout(10000).get();
        assertTrue("KRA race identity missing "+date+" "+region+" "+raceNo,
                KraRaceParser.raceExists(w,date,region,raceNo));
        Document r=Jsoup.connect("https://race.kra.co.kr/chulmainfo/Riding.do?Act=02&Sub=3&meet="+meet(region))
                .userAgent("Mozilla/5.0").timeout(10000).get();
        JSONArray entries=KraRaceParser.parseRiding(r,date,region,raceNo);
        return entries.length();
    }

    private void assertDual(String date,String region,int raceNo)throws Exception{
        int g=gumvitCount(date,region,raceNo);
        if(g>0)return;
        int k=kraCount(date,region,raceNo);
        assertTrue("Both Gumvit and KRA failed "+date+" "+region+" "+raceNo+"R",k>0);
    }

    @Test public void jejuOct2Race1LoadsFromEitherSource()throws Exception{assertDual("2026-10-02","제주",1);}
    @Test public void busanOct2Race1LoadsFromEitherSource()throws Exception{assertDual("2026-10-02","부산경남",1);}
    @Test public void jejuOct3Race2LoadsFromEitherSource()throws Exception{assertDual("2026-10-03","제주",2);}
}
