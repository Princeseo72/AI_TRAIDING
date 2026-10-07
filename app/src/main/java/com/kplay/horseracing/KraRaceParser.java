package com.kplay.horseracing.gumvit;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class KraRaceParser {
    static final class Entry {
        final int number; final String name; final String jockey; final String trainer;
        Entry(int number,String name,String jockey,String trainer){
            this.number=number;this.name=name;this.jockey=jockey;this.trainer=trainer;
        }
    }

    private static final Pattern CIRCLED = Pattern.compile("([①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳])");
    private static final Pattern TRAINER = Pattern.compile("\\((?:\\d{1,2})\\)\\s*([^\\s]+)");
    private KraRaceParser(){}

    static int circledNumber(String value){
        if(value==null||value.isEmpty())return -1;
        char c=value.charAt(0);
        if(c>='①'&&c<='⑳')return c-'①'+1;
        return -1;
    }

    private static String compact(String s){return s==null?"":s.replace("\u00A0","").replaceAll("\\s+","");}
    private static String normalizedDate(String s){
        if(s==null)return "";
        Matcher m=Pattern.compile("(20\\d{2})[./-](\\d{1,2})[./-](\\d{1,2})").matcher(s);
        if(!m.find())return "";
        return String.format("%s-%02d-%02d",m.group(1),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)));
    }
    private static boolean regionMatches(String text,String region){
        String x=compact(text);
        if("서울".equals(region))return x.contains("서울");
        if("제주".equals(region))return x.contains("제주");
        return x.contains("부경")||x.contains("부산")||x.contains("영남");
    }
    private static int headerIndex(Elements cells,String... labels){
        for(int i=0;i<cells.size();i++){
            String x=compact(cells.get(i).text());
            for(String label:labels)if(x.contains(label))return i;
        }
        return -1;
    }

    static boolean raceExists(Document doc,String date,String region,int raceNo){
        if(doc==null)return false;
        for(Element table:doc.select("table")){
            Element header=null;
            for(Element tr:table.select("tr")){
                String x=compact(tr.text());
                if(x.contains("지역")&&x.contains("경주일자")&&x.contains("경주번호")){header=tr;break;}
            }
            if(header==null)continue;
            Elements hs=header.select("th,td");
            int ir=headerIndex(hs,"지역"), id=headerIndex(hs,"경주일자"), ino=headerIndex(hs,"경주번호");
            if(ir<0||id<0||ino<0)continue;
            for(Element tr:table.select("tr")){
                if(tr==header)continue;
                Elements td=tr.select("td");
                int need=Math.max(ir,Math.max(id,ino)); if(td.size()<=need)continue;
                if(!regionMatches(td.get(ir).text(),region))continue;
                if(!date.equals(normalizedDate(td.get(id).text())))continue;
                String n=td.get(ino).text().replaceAll("[^0-9]","");
                if(String.valueOf(raceNo).equals(n))return true;
            }
        }
        return false;
    }

    static List<Entry> parseRiding(Document doc,String date,String region,int raceNo){
        List<Entry> out=new ArrayList<>();
        if(doc==null)return out;
        String targetSlash=date.replace('-','/');
        if(!doc.text().contains(targetSlash))return out;

        for(Element table:doc.select("table")){
            Element titleHeader=null, raceHeader=null;
            for(Element tr:table.select("tr")){
                String x=compact(tr.text());
                if(x.contains("기수명")&&x.contains("출전")&&x.contains("경주"))titleHeader=tr;
                Elements cells=tr.select("th,td");
                int numeric=0;
                for(Element c:cells)if(compact(c.text()).matches("\\d{1,2}"))numeric++;
                if(numeric>=2)raceHeader=tr;
            }
            if(titleHeader==null)continue;

            List<Integer> raceNumbers=new ArrayList<>();
            if(raceHeader!=null){
                for(Element c:raceHeader.select("th,td")){
                    String x=compact(c.text());
                    if(x.matches("\\d{1,2}"))raceNumbers.add(Integer.parseInt(x));
                }
            }
            int racePos=raceNumbers.indexOf(raceNo);
            if(racePos<0){
                // one-row synthetic/simple table fallback
                Elements hs=titleHeader.select("th,td");
                int idx=-1;
                for(int i=0;i<hs.size();i++)if(compact(hs.get(i).text()).equals(String.valueOf(raceNo))){idx=i;break;}
                if(idx>=0)racePos=Math.max(0,idx-2);
            }
            if(racePos<0)continue;

            for(Element tr:table.select("tr")){
                if(tr==titleHeader||tr==raceHeader)continue;
                Elements td=tr.select("td");
                if(td.size()<3)continue;
                String jockey=td.get(0).text().trim();
                if(jockey.isEmpty()||jockey.matches("\\d+"))continue;
                int cellIndex=2+racePos;
                if(td.size()<=cellIndex)continue;
                String cellText=td.get(cellIndex).text().trim();
                if(cellText.isEmpty())continue;
                Matcher cm=CIRCLED.matcher(cellText);
                if(!cm.find())continue;
                int no=circledNumber(cm.group(1)); if(no<1)continue;
                String after=cellText.substring(cm.end()).trim();
                String name=after.replaceFirst("\\s*\\(\\d{1,2}\\).*","").trim();
                if(name.isEmpty())continue;
                Matcher tm=TRAINER.matcher(cellText);
                String trainer=tm.find()?tm.group(1).trim():"";
                out.add(new Entry(no,name,jockey,trainer));
            }
            if(!out.isEmpty())break;
        }
        return out;
    }
}
