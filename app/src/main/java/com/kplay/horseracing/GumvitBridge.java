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
                    url=candidate; doc=d; parsedDate=pd; parsedRace=pr; break;
                }
                if(attempts.length()>0)attempts.append(" | ");
                attempts.append("type=").append(type).append(":").append(pd).append("/").append(pr);
            }catch(Exception e){
                if(attempts.length()>0)attempts.append(" | ");
                attempts.append("type=").append(type).append(":NET");
            }
        }
        if(doc==null)throw new Exception("검빛 경주 식별 실패: "+date+" "+region+" "+raceNo+"R ["+attempts+"]");

        Set<Integer> resultScratches=new HashSet<>();
        try{resultScratches.addAll(resultScratchNumbers(date,region,raceNo));}catch(Exception ignored){}
        Set<Integer> kraScratches=kraChangeScratches(date,region,raceNo);
        resultScratches.addAll(kraScratches);
        Element target=GumvitPageParser.findEntryTable(doc);
        if(target==null)throw new Exception("검빛 출전마 인기도 표 미검출");
        Element header=null;for(Element tr:target.select("tr")){String x=tr.text().replace(" ","").replace("\u00A0","");if(x.contains("마번")&&x.contains("마명")&&x.contains("전적")&&x.contains("조교사")&&x.contains("기수")){header=tr;break;}}
        if(header==null)throw new Exception("검빛 출전마 표 헤더 미검출");
        Elements hh=header.select("th,td");int ino=indexOfHeader(hh,"마번"),iname=indexOfHeader(hh,"마명"),irec=indexOfHeader(hh,"전적"),itr=indexOfHeader(hh,"조교사"),ij=indexOfHeader(hh,"기수");
        if(ino<0||iname<0||itr<0||ij<0)throw new Exception("검빛 출전마 열 구조 오류");
        JSONArray horses=new JSONArray(),excluded=new JSONArray();Set<Integer> seen=new HashSet<>();
        for(Element tr:target.select("tr")){
            if(tr==header)continue;Elements td=tr.select("td");int need=Math.max(Math.max(ino,iname),Math.max(itr,ij));if(td.size()<=need)continue;String ns=td.get(ino).text().trim();if(!ns.matches("\\d{1,2}"))continue;
            int no=Integer.parseInt(ns);if(no<1||no>30||seen.contains(no))continue;String name=td.get(iname).text().trim();if(name.isEmpty()||"마명".equals(name))continue;seen.add(no);
            boolean entryScratch=entryExcluded(tr),resultScratch=resultScratches.contains(no),active=!entryScratch&&!resultScratch;
            JSONObject h=new JSONObject().put("number",no).put("name",name).put("record",irec>=0&&td.size()>irec?td.get(irec).text().trim():"")
                    .put("trainer",td.size()>itr?td.get(itr).text().trim().replaceAll("\\(\\d+\\)$",""):"")
                    .put("jockey",td.size()>ij?td.get(ij).text().trim():"").put("active",active).put("excluded",!active)
                    .put("excludeSource",resultScratch?(kraScratches.contains(no)?"KRA_CHANGE_OR_RESULT":"RESULT_STATUS"):(entryScratch?"ENTRY_STATUS":""));
            StringBuilder tail=new StringBuilder();for(int i=5;i<td.size();i++){String x=td.get(i).text().trim();if(!x.isEmpty()){if(tail.length()>0)tail.append(' ');tail.append(x);}}h.put("expert",tail.toString());
            String pop="";for(int i=td.size()-1;i>=5;i--){String x=td.get(i).text().trim();if(x.matches("\\d{1,4}")){pop=x;break;}}h.put("popularity",pop);
            if(active)horses.put(h);else excluded.put(h);
        }
        if(horses.length()==0)throw new Exception("유효 출전마 없음");
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

    @JavascriptInterface public String fetchRace(String date,String region,int raceNo){try{return fetch(date,region,raceNo).toString();}catch(Exception e){try{return new JSONObject().put("ok",false).put("verified",false).put("error",e.getMessage()).toString();}catch(Exception x){return "{\"ok\":false}";}}}
    @JavascriptInterface public String verifyRace(String date,String region,int raceNo,String expectedHorseNumbersJson){try{JSONObject fresh=fetch(date,region,raceNo);JSONArray expected=new JSONArray(expectedHorseNumbersJson),actual=fresh.getJSONArray("horses");Set<Integer> e=new HashSet<>(),a=new HashSet<>();for(int i=0;i<expected.length();i++)e.add(expected.getInt(i));for(int i=0;i<actual.length();i++)a.add(actual.getJSONObject(i).getInt("number"));JSONArray missing=new JSONArray(),added=new JSONArray();for(Integer n:e)if(!a.contains(n))missing.put(n);for(Integer n:a)if(!e.contains(n))added.put(n);boolean same=missing.length()==0&&added.length()==0;return new JSONObject().put("ok",same).put("verified",same).put("activeCount",fresh.getInt("activeCount")).put("excludedCount",fresh.getInt("excludedCount")).put("missing",missing).put("added",added).put("source",fresh.getString("source")).put("error",same?"":"출전마 구성이 달라졌습니다.").toString();}catch(Exception e){try{return new JSONObject().put("ok",false).put("verified",false).put("error",e.getMessage()).toString();}catch(Exception x){return "{\"ok\":false}";}}}
    @JavascriptInterface public String fetchRaceResult(String date,String region,int raceNo){try{return fetchResult(date,region,raceNo).toString();}catch(Exception e){try{return new JSONObject().put("ok",false).put("verified",false).put("error",e.getMessage()).toString();}catch(Exception x){return "{\"ok\":false}";}}}
}
