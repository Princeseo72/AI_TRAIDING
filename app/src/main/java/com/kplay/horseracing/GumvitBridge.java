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
        if (t.contains("출전취소") || t.contains("출전제외") || t.contains("취소") || t.contains("제외")) return true;
        String cls = tr.className().toLowerCase();
        String style = tr.attr("style").toLowerCase();
        return cls.contains("cancel") || cls.contains("scratch") || style.contains("line-through");
    }

    private JSONObject fetch(String date, String region, int raceNo) throws Exception {
        String loc = code(region);
        String url = "https://www.gumvit.com/statv40/chulma_detail.html?loc=" + loc
                + "&m_date=" + date + "&race_no=" + raceNo + "&type=" + typeCode(date, region);
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36")
                .referrer("https://www.gumvit.com/statv40/")
                .timeout(15000)
                .get();

        String requestedDate = date;
        int requestedRaceNo = raceNo;
        String parsedDate = actualDate(doc);
        int parsedRace = actualRaceNo(doc);
        if (parsedDate.isEmpty() || parsedRace < 1) throw new Exception("검빛 페이지에서 실제 날짜/경주번호를 확인할 수 없습니다.");
        if (!requestedDate.equals(parsedDate) || requestedRaceNo != parsedRace) {
            throw new Exception("경주 정보 불일치: 요청 " + requestedDate + " " + requestedRaceNo + "R / 검빛 " + parsedDate + " " + parsedRace + "R");
        }

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

            boolean active = !isExcluded(tr);
            JSONObject h = new JSONObject();
            h.put("number", number);
            h.put("name", name);
            h.put("record", td.size() > 2 ? td.get(2).text().trim() : "");
            h.put("trainer", td.size() > 3 ? td.get(3).text().trim().replaceAll("\\(\\d+\\)$", "") : "");
            h.put("jockey", td.size() > 4 ? td.get(4).text().trim() : "");
            h.put("active", active);
            h.put("excluded", !active);
            StringBuilder tail = new StringBuilder();
            for (int i = 5; i < td.size(); i++) {
                String t = td.get(i).text().trim();
                if (!t.isEmpty()) { if (tail.length() > 0) tail.append(" "); tail.append(t); }
            }
            h.put("expert", tail.toString());
            String popularity = "";
            for (int i = td.size() - 1; i >= 5; i--) {
                String t = td.get(i).text().trim();
                if (t.matches("\\d{1,4}")) { popularity = t; break; }
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

    @JavascriptInterface
    public String fetchRace(String date, String region, int raceNo) {
        try { return fetch(date, region, raceNo).toString(); }
        catch (Exception e) {
            try { return new JSONObject().put("ok", false).put("verified", false).put("error", e.getMessage() == null ? e.toString() : e.getMessage()).toString(); }
            catch (Exception ignored) { return "{\"ok\":false,\"verified\":false,\"error\":\"검빛 데이터 수집 실패\"}"; }
        }
    }

    @JavascriptInterface
    public String verifyRace(String date, String region, int raceNo, String expectedHorseNumbersJson) {
        try {
            JSONObject fresh = fetch(date, region, raceNo);
            JSONArray expected = new JSONArray(expectedHorseNumbersJson);
            JSONArray actual = fresh.getJSONArray("horses");
            Set<Integer> e = new HashSet<>(), a = new HashSet<>();
            for (int i=0;i<expected.length();i++) e.add(expected.getInt(i));
            for (int i=0;i<actual.length();i++) a.add(actual.getJSONObject(i).getInt("number"));
            JSONArray missing = new JSONArray(), added = new JSONArray();
            for (Integer n : e) if (!a.contains(n)) missing.put(n);
            for (Integer n : a) if (!e.contains(n)) added.put(n);
            boolean same = missing.length()==0 && added.length()==0;
            return new JSONObject()
                    .put("ok", same).put("verified", same)
                    .put("requestedDate", date).put("actualDate", fresh.getString("actualDate"))
                    .put("requestedRaceNo", raceNo).put("actualRaceNo", fresh.getInt("actualRaceNo"))
                    .put("activeCount", fresh.getInt("activeCount")).put("excludedCount", fresh.getInt("excludedCount"))
                    .put("missing", missing).put("added", added).put("source", fresh.getString("source"))
                    .put("error", same ? "" : "출전마 구성이 최초 수집 시점과 달라졌습니다.")
                    .toString();
        } catch (Exception e) {
            try { return new JSONObject().put("ok", false).put("verified", false).put("error", e.getMessage()).toString(); }
            catch (Exception ignored) { return "{\"ok\":false,\"verified\":false}"; }
        }
    }
}
