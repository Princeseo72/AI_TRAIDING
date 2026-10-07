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
    public void horseSpecificReportStatusIsCollectedAsFallback() {
        String html = "<html><body>"
                + "<div>● ③“그래티튜드”는 고체온증으로 「출전취소」 조치.</div>"
                + "<div>● ⑨“넘버원삭스”는 마체이상으로 「출전제외」 조치.</div>"
                + "</body></html>";
        Set<Integer> numbers = ScratchDetector.resultScratchNumbers(Jsoup.parse(html));
        assertTrue(numbers.contains(3));
        assertTrue(numbers.contains(9));
    }
}
