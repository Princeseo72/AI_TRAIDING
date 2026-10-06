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

public class GumvitBridge {
    private static final Pattern DATE_PATTERN = Pattern.compile("(20\\d{2})[-./년\\s]+(\\d{1,2})[-./월\\s]+(\\d{1,2})");
    private static final Pattern RACE_PATTERN = Pattern.compile("(?:^|\\s)(\\d{1,2})경주(?:\\s|$)");

    private static String code(String region) {
        if ("부산경남".equals(region) || "부산".equals(region)) return "B";
        if ("제주".equals(region)) return "J";
        return "S";
    }

    private static String typeCode(String date, String region) {
        if (!"서울".equals(region)) return "5";
        try {
            DayOfWeek d = LocalDate.parse(date).getDayOfWeek();
            return d == DayOfWeek.SUNDAY ? "7" : "6";
        } catch (Exception ignored) { return "6"; }
    }

    private static String normalizeDate(String y, String m, String d) {
        return String.format("%s-%02d-%02d", y, Integer.parseInt(m), Integer.parseInt(d));
    }

    private static String actualDate(Document doc) {
        String text = doc.body() == null ? doc.text() : doc.body().text();
        Matcher m = DATE_PATTERN.matcher(text);
        return m.find() ? normalizeDate(m.group(1), m.group(2), m.group(3)) : "";
    }

    private static int actualRaceNo(Document doc) {
        String text = doc.body() == null ? doc.text() : doc.body().text();
        Matcher m = RACE_PATTERN.matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private static boolean isExcluded(Element tr) {
        String t = tr.text().replace(" ", "");
        if (t.contains("출전취소") || t.contains("출전제외")) return true;
        for (Element td : tr.select("td")) {
            String x = td.text().trim();
            if ("취".equals(x) || "취소".equals(x) || "제외".equals(x) || "출전취소".equals(x) || "출전제외".equals(x)) return true;
        }
        if (!tr.select("s,strike,.cancel,.scratch,[style*=line-through]").isEmpty()) return true;
        String cls = tr.className().toLowerCase();
        String style = tr.attr("style").toLowerCase();
        String html = tr.html().toLowerCase();
        return cls.contains("cancel") || cls.contains("scratch") || style.contains("line-through") || html.contains("line-through");
    }

    private static Document get(String url) throws Exception {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36")
                .referrer("https://www.gumvit.com/statv40/")
                .timeout(15000)
                .get();
    }

    private static int indexOfHeader(Elements cells, String label) {
        for (int i=0;i<cells.size();i++) if (cells.get(i).text().replace(" ", "").contains(label)) return i;
        return -1;
    }

    private Set<Integer> excludedNumbersFromResult(String date, String region, int raceNo) throws Exception {
        String url = "https://www.gumvit.com/statv40/result_detail.html?loc=" + code(region) + "&race=" + raceNo + "&racedate=" + date;
        Document doc = get(url);
        String parsedDate = actualDate(doc);
        int parsedRace = actualRaceNo(doc);
        if (!date.equals(parsedDate) || raceNo != parsedRace) return new HashSet<>();

        Set<Integer> excluded = new HashSet<>();
        for (Element table : doc.select("table")) {
            Element headerRow = null;
            for (Element tr : table.select("tr")) {
                String x = tr.text().replace(" ", "");
                if (x.contains("순위") && x.contains("마번") && x.contains("마명")) { headerRow = tr; break; }
            }
            if (headerRow == null) continue;
            Elements headers = headerRow.select("th,td");
            int iRank = indexOfHeader(headers, "순위"), iNo = indexOfHeader(headers, "마번");
            if (iRank < 0 || iNo < 0) continue;
            for (Element tr : table.select("tr")) {
                if (tr == headerRow) continue;
                Elements td = tr.select("td");
                if (td.size() <= Math.max(iRank, iNo)) continue;
                String rankText = td.get(iRank).text().trim();
                String noText = td.get(iNo).text().trim();
                if (!noText.matches("\\d{1,2}")) continue;
                if (rankText.startsWith("취") || tr.text().contains("출전제외") || tr.text().contains("출전취소") || isExcluded(tr)) {
                    excluded.add(Integer.parseInt(noText));
                }
            }
        }
        return excluded;
    }

    private JSONObject fetch(String date, String region, int raceNo) throws Exception {
        String loc = code(region);
        String url = "https://www.gumvit.com/statv40/chulma_detail.html?loc=" + loc
                + "&m_date=" + date + "&race_no=" + raceNo + "&type=" + typeCode(date, region);
        Document doc = get(url);

        String requestedDate = date;
        int requestedRaceNo = raceNo;
        String parsedDate = actualDate(doc);
        int parsedRace = actualRaceNo(doc);
        if (parsedDate.isEmpty() || parsedRace < 1) throw new Exception("검빛 페이지에서 실제 날짜/경주번호를 확인할 수 없습니다.");
        if (!requestedDate.equals(parsedDate) || requestedRaceNo != parsedRace) {
            throw new Exception("경주 정보 불일치: 요청 " + requestedDate + " " + requestedRaceNo + "R / 검빛 " + parsedDate + " " + parsedRace + "R");
        }

        Set<Integer> resultExcluded = new HashSet<>();
        try { resultExcluded.addAll(excludedNumbersFromResult(date, region, raceNo)); }
        catch (Exception ignored) { /* 경주 전에는 결과 페이지가 없으므로 출전표 기준으로 계속 진행 */ }

        JSONArray horses = new JSONArray();
        JSONArray excluded = new JSONArray();
        Element target = null;
        for (Element table : doc.select("table")) {
            String text = table.text();
            if (text.contains("마번") && text.contains("마명") && text.contains("조교사") && text.contains("기수")) target = table;
        }
        if (target == null) throw new Exception("검빛 출전마 정보 표를 찾지 못했습니다.");

        Set<Integer> seen = new HashSet<>();
        for (Element tr : target.select("tr")) {
            Elements td = tr.select("td");
            if (td.size() < 5) continue;
            String n = td.get(0).text().trim();
            if (!n.matches("\\d{1,2}")) continue;
            int number = Integer.parseInt(n);
            if (number < 1 || number > 30 || seen.contains(number)) continue;
            String name = td.get(1).text().trim();
            if (name.isEmpty() || "마명".equals(name)) continue;
            seen.add(number);

            boolean resultPageExcluded = resultExcluded.contains(number);
            boolean active = !isExcluded(tr) && !resultPageExcluded;
            JSONObject h = new JSONObject();
            h.put("number", number);
            h.put("name", name);
            h.put("record", td.size() > 2 ? td.get(2).text().trim() : "");
            h.put("trainer", td.size() > 3 ? td.get(3).text().trim().replaceAll("\\(\\d+\\)$", "") : "");
            h.put("jockey", td.size() > 4 ? td.get(4).text().trim() : "");
            h.put("active", active);
            h.put("excluded", !active);
            h.put("excludeSource", resultPageExcluded ? "RESULT" : (!active ? "ENTRY" : ""));
            StringBuilder tail = new StringBuilder();
            for (int i = 5; i < td.size(); i++) {
                String x = td.get(i).text().trim();
                if (!x.isEmpty()) { if (tail.length() > 0) tail.append(" "); tail.append(x); }
            }
            h.put("expert", tail.toString());
            String popularity = "";
            for (int i = td.size() - 1; i >= 5; i--) {
                String x = td.get(i).text().trim();
                if (x.matches("\\d{1,4}")) { popularity = x; break; }
            }
            h.put("popularity", popularity);
            if (active) horses.put(h); else excluded.put(h);
        }
        if (horses.length() == 0) throw new Exception("유효한 출전마가 없습니다. 출전취소/경주정보를 확인하세요.");

        JSONObject out = new JSONObject();
        out.put("ok", true);
        out.put("verified", true);
        out.put("source", url);
        out.put("requestedDate", requestedDate);
        out.put("actualDate", parsedDate);
        out.put("requestedRaceNo", requestedRaceNo);
        out.put("actualRaceNo", parsedRace);
        out.put("region", region);
        out.put("activeCount", horses.length());
        out.put("excludedCount", excluded.length());
        out.put("horses", horses);
        out.put("excludedHorses", excluded);
        return out;
    }

    private static Double payoutOdd(String text, String label) {
        Matcher m = Pattern.compile("배당률.*?" + Pattern.quote(label) + "\\s*:\\s*[^0-9]*([0-9]+(?:\\.[0-9]+)?)").matcher(text);
        return m.find() ? Double.parseDouble(m.group(1)) : null;
    }

    private static String sortedKey(int... nums) {
        java.util.Arrays.sort(nums);
        StringBuilder b = new StringBuilder();
        for (int i=0;i<nums.length;i++) { if (i>0) b.append('-'); b.append(nums[i]); }
        return b.toString();
    }

    private JSONObject fetchResult(String date, String region, int raceNo) throws Exception {
        String url = "https://www.gumvit.com/statv40/result_detail.html?loc=" + code(region) + "&race=" + raceNo + "&racedate=" + date;
        Document doc = get(url);
        String parsedDate = actualDate(doc);
        int parsedRace = actualRaceNo(doc);
        if (!date.equals(parsedDate) || raceNo != parsedRace) throw new Exception("경주결과 정보 불일치");

        Element table = null, headerRow = null;
        for (Element t : doc.select("table")) {
            for (Element tr : t.select("tr")) {
                String x = tr.text().replace(" ", "");
                if (x.contains("순위") && x.contains("마번") && x.contains("마명") && x.contains("단승식")) { table=t; headerRow=tr; break; }
            }
            if (table != null) break;
        }
        if (table == null || headerRow == null) throw new Exception("검빛 경주결과 착순표가 아직 없습니다.");
        Elements headers = headerRow.select("th,td");
        int iRank=indexOfHeader(headers,"순위"), iNo=indexOfHeader(headers,"마번"), iName=indexOfHeader(headers,"마명"), iWin=indexOfHeader(headers,"단승식"), iPlace=indexOfHeader(headers,"연승식");
        if (iRank<0 || iNo<0 || iName<0) throw new Exception("경주결과 열 구조를 확인할 수 없습니다.");

        JSONArray finishers=new JSONArray(), excluded=new JSONArray();
        for (Element tr : table.select("tr")) {
            if (tr == headerRow) continue;
            Elements td=tr.select("td");
            int max=Math.max(iRank,Math.max(iNo,iName));
            if (td.size()<=max) continue;
            String rankText=td.get(iRank).text().trim(), noText=td.get(iNo).text().trim(), name=td.get(iName).text().trim();
            if (!noText.matches("\\d{1,2}") || name.isEmpty()) continue;
            int no=Integer.parseInt(noText);
            if (rankText.matches("\\d+")) {
                JSONObject f=new JSONObject().put("rank",Integer.parseInt(rankText)).put("number",no).put("name",name);
                if (iWin>=0 && td.size()>iWin) { try { f.put("winOdds",Double.parseDouble(td.get(iWin).text().trim())); } catch(Exception ignored){} }
                if (iPlace>=0 && td.size()>iPlace) { try { f.put("placeOdds",Double.parseDouble(td.get(iPlace).text().trim())); } catch(Exception ignored){} }
                finishers.put(f);
            } else if (rankText.startsWith("취") || isExcluded(tr)) excluded.put(new JSONObject().put("number",no).put("name",name).put("status",rankText));
        }
        if (finishers.length()<1) throw new Exception("경주결과가 아직 확정되지 않았습니다.");
        java.util.List<JSONObject> list=new java.util.ArrayList<>();
        for(int i=0;i<finishers.length();i++) list.add(finishers.getJSONObject(i));
        java.util.Collections.sort(list,(a,b)->Integer.compare(a.optInt("rank",999),b.optInt("rank",999)));
        JSONArray sorted=new JSONArray(); for(JSONObject f:list) sorted.put(f); finishers=sorted;

        int first=finishers.getJSONObject(0).getInt("number");
        int second=finishers.length()>1?finishers.getJSONObject(1).getInt("number"):0;
        int third=finishers.length()>2?finishers.getJSONObject(2).getInt("number"):0;
        String body=doc.body()==null?doc.text():doc.body().text();
        JSONObject payouts=new JSONObject();
        Double win=payoutOdd(body,"단승식"), quin=payoutOdd(body,"복승식"), exact=payoutOdd(body,"쌍승식"), trio=payoutOdd(body,"삼복승"), trifecta=payoutOdd(body,"삼쌍승");
        if(win!=null)payouts.put("WIN",new JSONObject().put("key",String.valueOf(first)).put("odds",win));
        if(second>0&&quin!=null)payouts.put("QUINELLA",new JSONObject().put("key",sortedKey(first,second)).put("odds",quin));
        if(second>0&&exact!=null)payouts.put("EXACTA",new JSONObject().put("key",first+">"+second).put("odds",exact));
        if(third>0&&trio!=null)payouts.put("TRIO",new JSONObject().put("key",sortedKey(first,second,third)).put("odds",trio));
        if(third>0&&trifecta!=null)payouts.put("TRIFECTA",new JSONObject().put("key",first+">"+second+">"+third).put("odds",trifecta));
        if (!payouts.has("WIN")) throw new Exception("경주 확정배당이 아직 게시되지 않았습니다.");

        return new JSONObject().put("ok",true).put("verified",true).put("date",parsedDate).put("raceNo",parsedRace)
                .put("region",region).put("source",url).put("finishers",finishers).put("excludedHorses",excluded).put("payouts",payouts);
    }

    @JavascriptInterface public String fetchRace(String date, String region, int raceNo) {
        try { return fetch(date, region, raceNo).toString(); }
        catch (Exception e) {
            try { return new JSONObject().put("ok", false).put("verified", false).put("error", e.getMessage() == null ? e.toString() : e.getMessage()).toString(); }
            catch (Exception ignored) { return "{\"ok\":false,\"verified\":false,\"error\":\"검빛 데이터 수집 실패\"}"; }
        }
    }

    @JavascriptInterface public String verifyRace(String date, String region, int raceNo, String expectedHorseNumbersJson) {
        try {
            JSONObject fresh = fetch(date, region, raceNo);
            JSONArray expected = new JSONArray(expectedHorseNumbersJson), actual = fresh.getJSONArray("horses");
            Set<Integer> e = new HashSet<>(), a = new HashSet<>();
            for (int i=0;i<expected.length();i++) e.add(expected.getInt(i));
            for (int i=0;i<actual.length();i++) a.add(actual.getJSONObject(i).getInt("number"));
            JSONArray missing = new JSONArray(), added = new JSONArray();
            for (Integer n : e) if (!a.contains(n)) missing.put(n);
            for (Integer n : a) if (!e.contains(n)) added.put(n);
            boolean same = missing.length()==0 && added.length()==0;
            return new JSONObject().put("ok", same).put("verified", same)
                    .put("requestedDate", date).put("actualDate", fresh.getString("actualDate"))
                    .put("requestedRaceNo", raceNo).put("actualRaceNo", fresh.getInt("actualRaceNo"))
                    .put("activeCount", fresh.getInt("activeCount")).put("excludedCount", fresh.getInt("excludedCount"))
                    .put("missing", missing).put("added", added).put("source", fresh.getString("source"))
                    .put("error", same ? "" : "출전마 구성이 최초 수집 시점과 달라졌습니다.").toString();
        } catch (Exception e) {
            try { return new JSONObject().put("ok", false).put("verified", false).put("error", e.getMessage()).toString(); }
            catch (Exception ignored) { return "{\"ok\":false,\"verified\":false}"; }
        }
    }

    @JavascriptInterface public String fetchRaceResult(String date, String region, int raceNo) {
        try { return fetchResult(date,region,raceNo).toString(); }
        catch(Exception e) {
            try { return new JSONObject().put("ok",false).put("verified",false).put("error",e.getMessage()==null?e.toString():e.getMessage()).toString(); }
            catch(Exception ignored){ return "{\"ok\":false,\"verified\":false}"; }
        }
    }
}
