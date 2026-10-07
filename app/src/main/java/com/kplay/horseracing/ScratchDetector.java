package com.kplay.horseracing.gumvit;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ScratchDetector {
    private static final String[] SCRATCH_WORDS = {
            "출전취소", "출전제외", "경주제외", "취소", "제외"
    };
    private ScratchDetector() {}

    static boolean isScratchStatus(String value) {
        String x = value == null ? "" : value.replace(" ", "").trim();
        return "취".equals(x)
                || "취소".equals(x)
                || "제외".equals(x)
                || "출전취소".equals(x)
                || "출전제외".equals(x)
                || "경주제외".equals(x);
    }

    static boolean isEntryExcluded(Element row) {
        if (row == null) return false;

        for (Element cell : row.select("td,th")) {
            if (isScratchStatus(cell.text())) return true;
        }

        for (Element node : row.getAllElements()) {
            if (node == row) continue;
            for (String attrName : new String[]{"alt", "title", "aria-label", "data-status", "data-state", "class"}) {
                String value = compact(node.attr(attrName));
                if (containsExplicitScratchWord(value)
                        || value.toLowerCase().contains("scratch")
                        || value.toLowerCase().contains("withdraw")
                        || value.toLowerCase().contains("cancelled")
                        || value.toLowerCase().contains("canceled")) {
                    return true;
                }
            }
        }
        return false;
    }

    static Set<Integer> resultScratchNumbers(Document doc) {
        Set<Integer> out = new HashSet<>();
        if (doc == null) return out;

        Element table = findResultTable(doc);
        if (table != null) {
            Element header = null;
            for (Element tr : table.select("tr")) {
                String text = compact(tr.text());
                if (text.contains("순위") && text.contains("마번") && text.contains("마명")) {
                    header = tr;
                    break;
                }
            }
            if (header != null) {
                Elements hs = header.select("th,td");
                int rankIndex = headerIndex(hs, "순위");
                int numberIndex = headerIndex(hs, "마번");
                if (rankIndex >= 0 && numberIndex >= 0) {
                    for (Element tr : table.select("tr")) {
                        if (tr == header) continue;
                        Elements td = tr.select("td");
                        if (td.size() <= Math.max(rankIndex, numberIndex)) continue;
                        String rank = td.get(rankIndex).text().trim();
                        String no = td.get(numberIndex).text().trim();
                        if (isScratchStatus(rank) && no.matches("\\d{1,2}")) {
                            out.add(Integer.parseInt(no));
                        }
                    }
                }
            }
        }

        return out;
    }

    static Set<Integer> changeScratchNumbers(Document doc, int raceNo) {
        return changeScratchNumbers(doc, null, null, raceNo);
    }

    static Set<Integer> changeScratchNumbers(Document doc, String date, String region, int raceNo) {
        Set<Integer> out = new HashSet<>();
        if (doc == null) return out;
        for (Element table : doc.select("table")) {
            Element header = null;
            for (Element tr : table.select("tr")) {
                String text = compact(tr.text());
                if (text.contains("마명") && text.contains("경주") &&
                        (text.contains("출전번호") || text.contains("번호")) &&
                        (text.contains("사유") || text.contains("조교사") || text.contains("기수"))) {
                    header = tr;
                    break;
                }
            }
            if (header == null) continue;
            Elements hs = header.select("th,td");
            int raceIndex = headerIndex(hs, "경주번호");
            if (raceIndex < 0) raceIndex = headerIndex(hs, "경주");
            int noIndex = headerIndex(hs, "출전번호");
            if (noIndex < 0) noIndex = headerIndex(hs, "번호");
            int dateIndex = headerIndex(hs, "경주일자");
            int regionIndex = headerIndex(hs, "지역");
            if (regionIndex < 0) regionIndex = headerIndex(hs, "개최지");
            if (raceIndex < 0 || noIndex < 0) continue;

            for (Element tr : table.select("tr")) {
                if (tr == header) continue;
                Elements td = tr.select("td");
                if (td.size() <= Math.max(raceIndex, noIndex)) continue;
                String raceText = td.get(raceIndex).text().replaceAll("[^0-9]", "");
                String noText = td.get(noIndex).text().replaceAll("[^0-9]", "");
                if (raceText.isEmpty() || noText.isEmpty()) continue;
                if (Integer.parseInt(raceText) != raceNo) continue;

                if (date != null && dateIndex >= 0 && td.size() > dateIndex) {
                    String rowDate = td.get(dateIndex).text().replaceAll("\\([^)]*\\)", "").trim().replace('/', '-').replace('.', '-');
                    rowDate = rowDate.replaceAll("\\s+", "");
                    if (!date.equals(rowDate)) continue;
                }
                if (region != null && regionIndex >= 0 && td.size() > regionIndex) {
                    String rr = compact(td.get(regionIndex).text());
                    boolean regionMatch =
                            ("서울".equals(region) && rr.contains("서울")) ||
                            ("제주".equals(region) && rr.contains("제주")) ||
                            (("부산경남".equals(region) || "부산".equals(region)) && (rr.contains("부경") || rr.contains("부산") || rr.contains("영남")));
                    if (!regionMatch) continue;
                }
                String rowText = compact(tr.text());
                if (rowText.contains("자료가없습니다")) continue;
                out.add(Integer.parseInt(noText));
            }
        }
        return out;
    }

    private static Element findResultTable(Document doc) {
        for (Element table : doc.select("table")) {
            for (Element tr : table.select("tr")) {
                String text = compact(tr.text());
                if (text.contains("순위") && text.contains("마번") && text.contains("마명")) {
                    return table;
                }
            }
        }
        return null;
    }

    private static int headerIndex(Elements cells, String label) {
        for (int i = 0; i < cells.size(); i++) {
            if (compact(cells.get(i).text()).contains(label)) return i;
        }
        return -1;
    }

    private static boolean containsExplicitScratchWord(String value) {
        if (value == null || value.isEmpty()) return false;
        for (String word : SCRATCH_WORDS) {
            if (value.contains(word)) return true;
        }
        return false;
    }

    private static String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }

}
