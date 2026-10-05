package com.kplay.horseracing.gumvit;

import android.webkit.JavascriptInterface;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.json.JSONArray;
import org.json.JSONObject;

public class GumvitBridge {
    private static String code(String region) {
        if ("부산경남".equals(region) || "부산".equals(region)) return "B";
        if ("제주".equals(region)) return "J";
        return "S";
    }

    @JavascriptInterface
    public String fetchRace(String date, String region, int raceNo) {
        JSONObject out = new JSONObject();
        try {
            String loc = code(region);
            String url = "https://www.gumvit.com/statv40/chulma_detail.html?loc=" + loc
                    + "&m_date=" + date + "&race_no=" + raceNo + "&type=6";
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36")
                    .referrer("https://www.gumvit.com/statv40/")
                    .timeout(15000)
                    .get();

            JSONArray horses = new JSONArray();
            Element target = null;
            for (Element table : doc.select("table")) {
                String text = table.text();
                if (text.contains("마번") && text.contains("마명") && text.contains("전적")
                        && text.contains("조교사") && text.contains("기수")) target = table;
            }
            if (target == null) throw new Exception("검빛 출전마 정보 표를 찾지 못했습니다.");

            for (Element tr : target.select("tr")) {
                Elements td = tr.select("td");
                if (td.size() < 5) continue;
                String n = td.get(0).text().trim();
                if (!n.matches("\\d{1,2}")) continue;
                int number = Integer.parseInt(n);
                if (number < 1 || number > 30) continue;
                String name = td.get(1).text().trim();
                if (name.isEmpty() || "마명".equals(name)) continue;

                JSONObject h = new JSONObject();
                h.put("number", number);
                h.put("name", name);
                h.put("record", td.get(2).text().trim());
                h.put("trainer", td.get(3).text().trim().replaceAll("\\(\\d+\\)$", ""));
                h.put("jockey", td.get(4).text().trim());
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
                horses.put(h);
            }

            if (horses.length() == 0) throw new Exception("선택 경주의 출전마 데이터를 찾지 못했습니다. 날짜/지역/경주번호를 확인하세요.");
            out.put("ok", true);
            out.put("source", url);
            out.put("count", horses.length());
            out.put("horses", horses);
        } catch (Exception e) {
            try { out.put("ok", false); out.put("error", e.getMessage() == null ? e.toString() : e.getMessage()); }
            catch (Exception ignored) { return "{\"ok\":false,\"error\":\"검빛 데이터 수집 실패\"}"; }
        }
        return out.toString();
    }
}
