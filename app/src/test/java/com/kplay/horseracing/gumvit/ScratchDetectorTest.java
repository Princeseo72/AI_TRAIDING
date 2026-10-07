package com.kplay.horseracing.gumvit;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.*;

public class ScratchDetectorTest {
    @Test
    public void entryRowWithExplicitScratchTextIsExcluded() {
        Element row = Jsoup.parse("<table><tr><td>3</td><td>그래티튜드</td><td><span>출전취소</span></td></tr></table>").selectFirst("tr");
        assertTrue(ScratchDetector.isEntryExcluded(row));
    }

    @Test
    public void nestedScratchAttributeIsExcluded() {
        Element row = Jsoup.parse("<table><tr><td>9</td><td>넘버원삭스</td><td><img alt='출전제외'></td></tr></table>").selectFirst("tr");
        assertTrue(ScratchDetector.isEntryExcluded(row));
    }

    @Test
    public void genericStrikeThroughWithoutScratchStatusDoesNotExclude() {
        Element row = Jsoup.parse("<table><tr style='text-decoration:line-through'><td>4</td><td>정상마</td><td>정상 출전</td></tr></table>").selectFirst("tr");
        assertFalse(ScratchDetector.isEntryExcluded(row));
    }

    @Test
    public void resultTableExplicitChwiRowsAreCollected() {
        String html = "<html><body><table>"
                + "<tr><th>순위</th><th>관리</th><th>마번</th><th>마명</th></tr>"
                + "<tr><td>1</td><td></td><td>5</td><td>라온블레이드</td></tr>"
                + "<tr><td>취</td><td></td><td>3</td><td>그래티튜드</td></tr>"
                + "<tr><td>취</td><td></td><td>9</td><td>넘버원삭스</td></tr>"
                + "</table></body></html>";
        Document doc = Jsoup.parse(html);
        Set<Integer> numbers = ScratchDetector.resultScratchNumbers(doc);
        assertEquals(2, numbers.size());
        assertTrue(numbers.contains(3));
        assertTrue(numbers.contains(9));
        assertFalse(numbers.contains(5));
    }

    @Test
    public void pageGlobalReportTextAloneDoesNotExcludeWithoutResultRowStatus() {
        String html = "<html><body>"
                + "<div>과거/설명 문구: ③ 그래티튜드 출전취소 사례</div>"
                + "<div>과거/설명 문구: ⑨ 넘버원삭스 출전제외 사례</div>"
                + "</body></html>";
        Set<Integer> numbers = ScratchDetector.resultScratchNumbers(Jsoup.parse(html));
        assertTrue("페이지 전체 문구만으로 현재 제외마를 만들면 안 됨", numbers.isEmpty());
    }
    @Test
    public void jejuOct3Race2OnlyCurrentExplicitScratchIsExcluded() {
        String html = "<html><body>"
                + "<table>"
                + "<tr><th>순위</th><th>마번</th><th>마명</th></tr>"
                + "<tr><td>취</td><td>2</td><td>태흥산성</td></tr>"
                + "<tr><td>5</td><td>9</td><td>천년여왕</td></tr>"
                + "<tr><td>7</td><td>10</td><td>레드크라운</td></tr>"
                + "</table>"
                + "<div>과거 기록: ⑨천년여왕 관련 과거 경주에서 다른 말이 출전제외 처리됨.</div>"
                + "<div>과거 기록: ⑩레드크라운 관련 과거 기록 2번 말 출전제외 사례.</div>"
                + "</body></html>";
        Set<Integer> numbers = ScratchDetector.resultScratchNumbers(Jsoup.parse(html));
        assertEquals("현재 결과표의 명시적 취소마만 제외해야 함", 1, numbers.size());
        assertTrue(numbers.contains(2));
        assertFalse(numbers.contains(9));
        assertFalse(numbers.contains(10));
    }

    @Test
    public void entryHistoricalNoteContainingScratchWordDoesNotExcludeCurrentRunner() {
        Element row = Jsoup.parse("<table><tr><td>9</td><td>천년여왕</td><td>과거 출전취소 이후 정상 출전</td></tr></table>").selectFirst("tr");
        assertFalse("현재 상태 셀이 아닌 과거 설명문으로 제외하면 안 됨", ScratchDetector.isEntryExcluded(row));
    }

}
