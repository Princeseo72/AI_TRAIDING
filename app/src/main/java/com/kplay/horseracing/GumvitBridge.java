package com.kplay.horseracing.gumvit;

import android.webkit.JavascriptInterface;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.Map;

public class GumvitBridge {
    private static final Pattern DATE_PATTERN=Pattern.compile("(20\\d{2})[-./년\\s]+(\\d{1,2})[-./월\\s]+(\\d{1,2})");
    private static final Pattern RACE_PATTERN=Pattern.compile("(?:^|\\s)(\\d{1,2})경주(?:\\s|$)");
    private static final long RACE_CACHE_TTL_MS = 30_000L;
    private static final long KRA_CACHE_TTL_MS = 60_000L;
    private static final Map<String, CacheEntry> RACE_CACHE = new HashMap<>();
    private static final Map<String, KraScratchCache> KRA_SCRATCH_CACHE = new HashMap<>();

    private static final class CacheEntry {
        final long at; final JSONObject value;
        CacheEntry(long at, JSONObject value){this.at=at;this.value=value;}
    }
    private static final class KraScratchCache {
        final long at; final Set<Integer> numbers;
        KraScratchCache(long at, Set<Integer> numbers){this.at=at;this.numbers=numbers;}
    }

    private static String code(String region){if("부산경남".equals(region)||"부산".equals(region))return "B";if("제주".equals(region))return "J";return "S";}
    private static int kraMeet(String region){if("제주".equals(region))return 2;if("부산경남".equals(region)||"부산".equals(region))return 3;return 1;}
    private static String typeCode(String date,String region){if(!"서울".equals(region))return "5";try{return LocalDate.parse(date).getDayOfWeek()==DayOfWeek.SUNDAY?"7":"6";}catch(Exception e){return "6";}}
    private static String normalizeDate(String y,String m,String d){return String.format("%s-%02d-%02d",y,Integer.parseInt(m),Integer.parseInt(d));}
    private static String actualDate(Document doc){Matcher m=DATE_PATTERN.matcher(doc.body()==null?doc.text():doc.body().text());return m.find()?normalizeDate(m.group(1),m.group(2),m.group(3)):"";}
    private static int actualRaceNo(Document doc){Matcher m=RACE_PATTERN.matcher(doc.body()==null?doc.text():doc.body().text());return m.find()?Integer.parseInt(m.group(1)):-1;}
    private static Document getOnce(String url, int timeoutMs)throws Exception{
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36")
                .referrer(url.contains("race.kra.co.kr")?"https://race.kra.co.kr/":"https://www.gumvit.com/statv40/")
                .timeout(timeoutMs).get();
    }
    private static Document getPrimary(String url)throws Exception{
        Exception last=null;
        for(int i=0;i<2;i++){
            try{return getOnce(url, 5000);}catch(Exception e){last=e;}
        }
        throw last==null?new Exception("네트워크 조회 실패"):last;
    }
    private static Document getSupplement(String url)throws Exception{
        return getOnce(url, 4500);
    }
    private static int indexOfHeader(Elements cells,String label){for(int i=0;i<cells.size();i++)if(cells.get(i).text().replace(" ","").contains(label))return i;return -1;}

    private static boolean exactScratchStatus(String s){
        return ScratchDetector.isScratchStatus(s);
    }

    private static boolean entryExcluded(Element tr){
        return ScratchDetector.isEntryExcluded(tr);
    }

    private static Element findResultTable(Document doc){
        for(Element t:doc.select("table"))for(Element tr:t.select("tr")){
            String x=tr.text().replace(" ","");
            if(x.contains("순위")&&x.contains("마번")&&x.contains("마명"))return t;
        }
        return null;
    }


    private static String cacheKey(String date,String region,int raceNo){
        return date+"|"+region+"|"+raceNo;
    }

    private Set<Integer> kraChangeScratches(String date,String region,int raceNo){
        String key=cacheKey(date,region,raceNo);
        long now=System.currentTimeMillis();
        synchronized(KRA_SCRATCH_CACHE){
            KraScratchCache c=KRA_SCRATCH_CACHE.get(key);
            if(c!=null && now-c.at<=KRA_CACHE_TTL_MS)return new HashSet<>(c.numbers);
        }
        Set<Integer> out=new HashSet<>();
        try{
            String url="https://race.kra.co.kr/thisweekrace/ThisWeekChulmapyoChange.do";
            Document doc=getSupplement(url);
            out.addAll(ScratchDetector.changeScratchNumbers(doc,date,region,raceNo));
            synchronized(KRA_SCRATCH_CACHE){
                KRA_SCRATCH_CACHE.put(key,new KraScratchCache(now,new HashSet<>(out)));
            }
        }catch(Exception ignored){
            synchronized(KRA_SCRATCH_CACHE){
                KraScratchCache c=KRA_SCRATCH_CACHE.get(key);
                if(c!=null)out.addAll(c.numbers);
            }
        }
        return out;
    }

    private JSONObject fetchKraRace(String date,String region,int raceNo)throws Exception{
        String weightUrl="https://race.kra.co.kr/thisweekrace/ThisWeekWeight.do";
        Document weight=getSupplement(weightUrl);
        if(!KraRaceParser.raceExists(weight,date,region,raceNo))
            throw new Exception("KRA 경주 식별 실패: "+date+" "+region+" "+raceNo+"R");

        int meet=kraMeet(region);
        String ridingUrl="https://race.kra.co.kr/chulmainfo/Riding.do?Act=02&Sub=3&meet="+meet;
        Document riding=getSupplement(ridingUrl);
        java.util.List<KraRaceParser.Entry> raw=KraRaceParser.parseRiding(riding,date,region,raceNo);
        if(raw.isEmpty())throw new Exception("KRA 출전마 표 미검출: "+date+" "+region+" "+raceNo+"R");

        Set<Integer> scratches=kraChangeScratches(date,region,raceNo);
        JSONArray horses=new JSONArray(),excluded=new JSONArray();
        Set<Integer> seen=new HashSet<>();
        for(KraRaceParser.Entry e:raw){
            int no=e.number;
            if(no<1||seen.contains(no))continue;seen.add(no);
            boolean active=!scratches.contains(no);
            JSONObject h=new JSONObject().put("number",no).put("name",e.name).put("record","")
                    .put("trainer",e.trainer).put("jockey",e.jockey).put("popularity","").put("expert","")
                    .put("active",active).put("excluded",!active)
                    .put("excludeSource",active?"":"KRA_CHANGE")
                    .put("source","KRA_RIDING");
            if(active)horses.put(h);else excluded.put(h);
        }
        if(horses.length()==0)throw new Exception("KRA 유효 출전마 없음");
        return new JSONObject().put("ok",true).put("verified",true)
                .put("source",ridingUrl).put("sourceProvider","KRA")
                .put("requestedDate",date).put("actualDate",date)
                .put("requestedRaceNo",raceNo).put("actualRaceNo",raceNo)
                .put("region",region).put("activeCount",horses.length())
                .put("excludedCount",excluded.length()).put("supplementalSource","KRA_THIS_WEEK_CHANGE")
                .put("horses",horses).put("excludedHorses",excluded);
    }

    private JSONObject fetchResilient(String date,String region,int raceNo)throws Exception{
        Exception gumvitError=null;
        try{
            JSONObject g=fetch(date,region,raceNo);
            g.put("sourceProvider","GUMVIT");
            return g;
        }catch(Exception e){gumvitError=e;}
        try{
            JSONObject k=fetchKraRace(date,region,raceNo);
            k.put("fallbackFrom","GUMVIT");
            if(gumvitError!=null)k.put("primaryError",gumvitError.getMessage());
            return k;
        }catch(Exception kraError){
            throw new Exception("경주정보 이중조회 실패 / 검빛: "+(gumvitError==null?"미상":gumvitError.getMessage())+" / KRA: "+kraError.getMessage());
        }
    }

    private Set<Integer> resultScratchNumbers(String date,String region,int raceNo)throws Exception{
        String url="https://www.gumvit.com/statv40/result_detail.html?loc="+code(region)+"&race="+raceNo+"&racedate="+date;
        Document doc=getPrimary(url);
        if(!GumvitPageParser.identityMatches(doc,date,region,raceNo))return new HashSet<>();
        return ScratchDetector.resultScratchNumbers(doc);
    }

    private JSONObject fetch(String date,String region,int raceNo)throws Exception{
        String ckey=cacheKey(date,region,raceNo); long now=System.currentTimeMillis();
        synchronized(RACE_CACHE){
            CacheEntry c=RACE_CACHE.get(ckey);
            if(c!=null && now-c.at<=RACE_CACHE_TTL_MS)return new JSONObject(c.value.toString());
        }
        String url=null; Document doc=null; String parsedDate=""; int parsedRace=-1; StringBuilder attempts=new StringBuilder();
        for(String type:GumvitPageParser.typeCandidates(date)){
            String candidate="https://www.gumvit.com/statv40/chulma_detail.html?loc="+code(region)+"&m_date="+date+"&race_no="+raceNo+"&type="+type;
            try{
                Document d=getPrimary(candidate);
                String pd=GumvitPageParser.actualDate(d); int pr=GumvitPageParser.actualRaceNo(d);
                if(GumvitPageParser.identityMatches(d,date,region,raceNo)){
                    url=candidate; doc=d; parsedDate=GumvitPageParser.resolvedDate(d,date); parsedRace=GumvitPageParser.resolvedRaceNo(d,raceNo); break;
                }
                if(attempts.length()>0)attempts.append(" | ");
                attempts.append("type=").append(type).append(":").append(pd).append("/").append(pr);
            }catch(Exception e){
                if(attempts.length()>0)attempts.append(" | ");
                attempts.append("type=").append(type).append(":NET");
            }
        }
        if(doc==null)throw new Exception("검빛 경주 식별 실패: "+date+" "+region+" "+raceNo+"R ["+attempts+"]");

        // Pre-race field loading must never query result_detail: it causes duplicate Gumvit
        // requests and can leak post-race information during historical Replay.
        Set<Integer> kraScratches=kraChangeScratches(date,region,raceNo);
        Set<Integer> resultScratches=new HashSet<>(kraScratches);
        java.util.List<GumvitPageParser.Entry> entries=GumvitPageParser.parseEntries(doc);
        JSONArray horses=new JSONArray(),excluded=new JSONArray();
        for(GumvitPageParser.Entry e:entries){
            int no=e.number; boolean resultScratch=resultScratches.contains(no),active=!e.entryExcluded&&!resultScratch;
            JSONObject h=new JSONObject().put("number",no).put("name",e.name).put("record",e.record)
                    .put("trainer",e.trainer).put("jockey",e.jockey).put("active",active).put("excluded",!active)
                    .put("excludeSource",resultScratch?(kraScratches.contains(no)?"KRA_CHANGE_OR_RESULT":"RESULT_STATUS"):(e.entryExcluded?"ENTRY_STATUS":""))
                    .put("expert",e.expert).put("popularity",e.popularity);
            if(active)horses.put(h);else excluded.put(h);
        }
        if(horses.length()==0){
            String title=doc.title()==null?"":doc.title();
            String body=doc.body()==null?doc.text():doc.body().text();
            String state=GumvitPageParser.findEntryTable(doc)==null?"ENTRY_TABLE_MISSING":"ENTRY_ROWS_EMPTY";
            throw new Exception("검빛 출전마 파싱 실패 ["+state+"] title="+title+" tables="+doc.select("table").size()+" url="+doc.location());
        }
        JSONObject out=new JSONObject().put("ok",true).put("verified",true).put("source",url).put("requestedDate",date).put("actualDate",parsedDate)
                .put("requestedRaceNo",raceNo).put("actualRaceNo",parsedRace).put("region",region).put("activeCount",horses.length()).put("excludedCount",excluded.length())
                .put("supplementalSource","KRA_THIS_WEEK_CHANGE").put("horses",horses).put("excludedHorses",excluded);
        synchronized(RACE_CACHE){RACE_CACHE.put(ckey,new CacheEntry(now,new JSONObject(out.toString())));}
        return out;
    }

    private static Double payoutOdd(String text,String label){Matcher m=Pattern.compile("배당률.*?"+Pattern.quote(label)+"\\s*:?\\s*[^0-9]*([0-9]+(?:\\.[0-9]+)?)").matcher(text);return m.find()?Double.parseDouble(m.group(1)):null;}
    private static String sortedKey(int...n){java.util.Arrays.sort(n);StringBuilder b=new StringBuilder();for(int i=0;i<n.length;i++){if(i>0)b.append('-');b.append(n[i]);}return b.toString();}

    private JSONObject fetchResult(String date,String region,int raceNo)throws Exception{
        String url="https://www.gumvit.com/statv40/result_detail.html?loc="+code(region)+"&race="+raceNo+"&racedate="+date;Document doc=getPrimary(url);
        String pd=GumvitPageParser.actualDate(doc);int pr=GumvitPageParser.actualRaceNo(doc);if(!GumvitPageParser.identityMatches(doc,date,region,raceNo))throw new Exception("경주결과 정보 불일치");
        Element table=findResultTable(doc);if(table==null)throw new Exception("경주결과 착순표 미확정");Element header=null;
        for(Element tr:table.select("tr")){String x=tr.text().replace(" ","");if(x.contains("순위")&&x.contains("마번")&&x.contains("마명")){header=tr;break;}}
        Elements hs=header.select("th,td");int ir=indexOfHeader(hs,"순위"),in=indexOfHeader(hs,"마번"),im=indexOfHeader(hs,"마명"),iw=indexOfHeader(hs,"단승식"),ip=indexOfHeader(hs,"연승식");
        if(ir<0||in<0||im<0)throw new Exception("경주결과 열 구조 오류");
        JSONArray finishers=new JSONArray(),excluded=new JSONArray();
        for(Element tr:table.select("tr")){
            if(tr==header)continue;Elements td=tr.select("td");if(td.size()<=Math.max(ir,Math.max(in,im)))continue;
            String rank=td.get(ir).text().trim(),ns=td.get(in).text().trim(),name=td.get(im).text().trim();if(!ns.matches("\\d{1,2}")||name.isEmpty())continue;int no=Integer.parseInt(ns);
            if(rank.matches("\\d+")){JSONObject f=new JSONObject().put("rank",Integer.parseInt(rank)).put("number",no).put("name",name);try{if(iw>=0&&td.size()>iw)f.put("winOdds",Double.parseDouble(td.get(iw).text().trim()));}catch(Exception ignored){}try{if(ip>=0&&td.size()>ip)f.put("placeOdds",Double.parseDouble(td.get(ip).text().trim()));}catch(Exception ignored){}finishers.put(f);}
            else if(exactScratchStatus(rank))excluded.put(new JSONObject().put("number",no).put("name",name).put("status",rank));
        }
        if(finishers.length()<1)throw new Exception("경주결과 미확정");
        java.util.List<JSONObject> list=new java.util.ArrayList<>();for(int i=0;i<finishers.length();i++)list.add(finishers.getJSONObject(i));java.util.Collections.sort(list,(a,b)->Integer.compare(a.optInt("rank",999),b.optInt("rank",999)));finishers=new JSONArray();for(JSONObject f:list)finishers.put(f);
        int first=finishers.getJSONObject(0).getInt("number"),second=finishers.length()>1?finishers.getJSONObject(1).getInt("number"):0,third=finishers.length()>2?finishers.getJSONObject(2).getInt("number"):0;
        String body=doc.body()==null?doc.text():doc.body().text();JSONObject payouts=new JSONObject();Double win=payoutOdd(body,"단승식"),quin=payoutOdd(body,"복승식"),exact=payoutOdd(body,"쌍승식"),trio=payoutOdd(body,"삼복승식"),tri=payoutOdd(body,"삼쌍승식");
        if(win!=null)payouts.put("WIN",new JSONObject().put("key",String.valueOf(first)).put("odds",win));if(second>0&&quin!=null)payouts.put("QUINELLA",new JSONObject().put("key",sortedKey(first,second)).put("odds",quin));if(second>0&&exact!=null)payouts.put("EXACTA",new JSONObject().put("key",first+">"+second).put("odds",exact));if(third>0&&trio!=null)payouts.put("TRIO",new JSONObject().put("key",sortedKey(first,second,third)).put("odds",trio));if(third>0&&tri!=null)payouts.put("TRIFECTA",new JSONObject().put("key",first+">"+second+">"+third).put("odds",tri));
        return new JSONObject().put("ok",true).put("verified",true).put("date",pd).put("raceNo",pr).put("region",region).put("source",url).put("finishers",finishers).put("excludedHorses",excluded).put("payouts",payouts);
    }

    @JavascriptInterface public String selfDiagnose(){
        long started=System.currentTimeMillis(); JSONObject out=new JSONObject();
        try{
            String date="2026-10-03",region="제주";int raceNo=1;
            String url="https://www.gumvit.com/statv40/chulma_detail.html?loc=J&m_date=2026-10-03&race_no=1&type=6";
            Document d=getOnce(url,7000);
            boolean identity=GumvitPageParser.identityMatches(d,date,region,raceNo);
            Element table=GumvitPageParser.findEntryTable(d);
            java.util.List<GumvitPageParser.Entry> entries=GumvitPageParser.parseEntries(d);
            boolean parser=identity&&table!=null&&entries.size()>=3;
            String body=d.body()==null?d.text():d.body().text();
            out.put("gumvitHttp",true).put("gumvitParser",parser).put("gumvitRunnerRows",entries.size())
               .put("premiumRestrictionPresent",body.contains("이용권한이 없습니다"))
               .put("entryTablePresent",table!=null).put("tableCount",d.select("table").size())
               .put("pageTitle",d.title()).put("finalUrl",d.location())
               .put("canary","2026-10-03|제주|1R").put("duplicateResultLookup",false)
               .put("preRaceSourcePolicy","VISIBLE_ENTRY_TABLE + KRA_CHANGE; PREMIUM_RESTRICTION_IGNORED; RESULT_DETAIL_POST_RACE_ONLY");
            boolean modules=true;try{Class.forName("com.kplay.horseracing.gumvit.GumvitPageParser");Class.forName("com.kplay.horseracing.gumvit.KraRaceParser");Class.forName("com.kplay.horseracing.gumvit.ScratchDetector");}catch(Throwable x){modules=false;}
            out.put("modules",modules).put("ok",parser&&modules);
            if(!parser)out.put("error","검빛 HTTP 응답 내 visible 출전마 table parser 실패");
        }catch(Exception e){try{out.put("ok",false).put("gumvitHttp",false).put("gumvitParser",false).put("error",e.getMessage());}catch(Exception ignored){}}
        try{out.put("durationMs",System.currentTimeMillis()-started);}catch(Exception ignored){}
        return out.toString();
    }

    @JavascriptInterface public String fetchRace(String date,String region,int raceNo){try{return fetchResilient(date,region,raceNo).toString();}catch(Exception e){try{return new JSONObject().put("ok",false).put("verified",false).put("error",e.getMessage()).toString();}catch(Exception x){return "{\"ok\":false}";}}}
    @JavascriptInterface public String verifyRace(String date,String region,int raceNo,String expectedHorseNumbersJson){try{JSONObject fresh=fetchResilient(date,region,raceNo);JSONArray expected=new JSONArray(expectedHorseNumbersJson),actual=fresh.getJSONArray("horses");Set<Integer> e=new HashSet<>(),a=new HashSet<>();for(int i=0;i<expected.length();i++)e.add(expected.getInt(i));for(int i=0;i<actual.length();i++)a.add(actual.getJSONObject(i).getInt("number"));JSONArray missing=new JSONArray(),added=new JSONArray();for(Integer n:e)if(!a.contains(n))missing.put(n);for(Integer n:a)if(!e.contains(n))added.put(n);boolean same=missing.length()==0&&added.length()==0;return new JSONObject().put("ok",same).put("verified",same).put("activeCount",fresh.getInt("activeCount")).put("excludedCount",fresh.getInt("excludedCount")).put("missing",missing).put("added",added).put("source",fresh.getString("source")).put("error",same?"":"출전마 구성이 달라졌습니다.").toString();}catch(Exception e){try{return new JSONObject().put("ok",false).put("verified",false).put("error",e.getMessage()).toString();}catch(Exception x){return "{\"ok\":false}";}}}
    @JavascriptInterface public String fetchRaceResult(String date,String region,int raceNo){try{return fetchResult(date,region,raceNo).toString();}catch(Exception e){try{return new JSONObject().put("ok",false).put("verified",false).put("error",e.getMessage()).toString();}catch(Exception x){return "{\"ok\":false}";}}}
}
