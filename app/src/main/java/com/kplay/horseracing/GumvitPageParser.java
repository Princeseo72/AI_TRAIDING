package com.kplay.horseracing.gumvit;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GumvitPageParser {
    private static final Pattern DATE_PATTERN=Pattern.compile("(20\\d{2})[-./년\\s\\u00A0]+(\\d{1,2})[-./월\\s\\u00A0]+(\\d{1,2})");
    private static final Pattern RACE_PATTERN=Pattern.compile("(?<!\\d)(\\d{1,2})\\s*경주");
    private GumvitPageParser(){}

    static String actualDate(Document doc){
        String text=doc==null?"":doc.text();
        Matcher m=DATE_PATTERN.matcher(text);
        if(!m.find())return "";
        return String.format("%s-%02d-%02d",m.group(1),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)));
    }

    static int actualRaceNo(Document doc){
        String text=doc==null?"":doc.text().replace('\u00A0',' ');
        Matcher m=RACE_PATTERN.matcher(text);
        return m.find()?Integer.parseInt(m.group(1)):-1;
    }

    static boolean regionMatches(Document doc,String region){
        String x=doc==null?"":doc.text().replace(" ","").replace("\u00A0","");
        if("서울".equals(region))return x.contains("서울경마장")||x.contains("서울경마");
        if("제주".equals(region))return x.contains("제주경마장")||x.contains("제주경마");
        if("부산경남".equals(region)||"부산".equals(region))
            return x.contains("부경경마장")||x.contains("부산경마장")||x.contains("부경경마")||x.contains("부산경마");
        return false;
    }

    private static boolean requestedDateMatchesLocation(Document doc,String date){
        if(doc==null||date==null||date.isEmpty())return false;
        String loc=doc.location()==null?"":doc.location();
        return loc.contains("m_date="+date)||loc.contains("racedate="+date);
    }

    private static boolean requestedRaceMatchesLocation(Document doc,int raceNo){
        if(doc==null||raceNo<1)return false;
        String loc=doc.location()==null?"":doc.location();
        return loc.contains("race_no="+raceNo)||loc.contains("race="+raceNo);
    }

    static String resolvedDate(Document doc,String requestedDate){
        String parsed=actualDate(doc);
        return parsed.isEmpty()&&requestedDateMatchesLocation(doc,requestedDate)?requestedDate:parsed;
    }

    static int resolvedRaceNo(Document doc,int requestedRaceNo){
        int parsed=actualRaceNo(doc);
        return parsed<0&&requestedRaceMatchesLocation(doc,requestedRaceNo)?requestedRaceNo:parsed;
    }

    static boolean identityMatches(Document doc,String date,String region,int raceNo){
        if(doc==null||!regionMatches(doc,region))return false;
        String parsedDate=actualDate(doc);
        int parsedRace=actualRaceNo(doc);
        if(!parsedDate.isEmpty()&&!date.equals(parsedDate))return false;
        if(parsedRace>=0&&raceNo!=parsedRace)return false;
        boolean missingDate=parsedDate.isEmpty(), missingRace=parsedRace<0;
        boolean dateOk=date.equals(parsedDate)||(missingDate&&requestedDateMatchesLocation(doc,date));
        boolean raceOk=raceNo==parsedRace||(missingRace&&requestedRaceMatchesLocation(doc,raceNo));
        // Visible identity is sufficient. URL recovery is deliberately stricter and
        // requires a real entry table so an error/login page can never be accepted.
        if((missingDate||missingRace)&&findEntryTable(doc)==null)return false;
        return dateOk&&raceOk;
    }

    static Element findEntryTable(Document doc){
        if(doc==null)return null;
        for(Element table:doc.select("table")){
            for(Element tr:table.select("tr")){
                String x=tr.text().replace(" ","").replace("\u00A0","");
                if(x.contains("마번")&&x.contains("마명")&&x.contains("전적")&&x.contains("조교사")&&x.contains("기수")){
                    return table;
                }
            }
        }
        return null;
    }

    static final class Entry {
        final int number; final String name,record,trainer,jockey,popularity,expert; final boolean entryExcluded;
        Entry(int number,String name,String record,String trainer,String jockey,String popularity,String expert,boolean entryExcluded){
            this.number=number;this.name=name;this.record=record;this.trainer=trainer;this.jockey=jockey;this.popularity=popularity;this.expert=expert;this.entryExcluded=entryExcluded;
        }
    }

    private static int headerIndex(org.jsoup.select.Elements cells,String label){
        for(int i=0;i<cells.size();i++)if(cells.get(i).text().replace(" ","").contains(label))return i;return -1;
    }

    static List<Entry> parseEntries(Document doc){
        List<Entry> out=new ArrayList<>(); Element target=findEntryTable(doc); if(target==null)return out;
        Element header=null;
        for(Element tr:target.select("tr")){
            String x=tr.text().replace(" ","").replace("\u00A0","");
            if(x.contains("마번")&&x.contains("마명")&&x.contains("전적")&&x.contains("조교사")&&x.contains("기수")){header=tr;break;}
        }
        if(header==null)return out;
        org.jsoup.select.Elements hh=header.select("th,td");
        int ino=headerIndex(hh,"마번"),iname=headerIndex(hh,"마명"),irec=headerIndex(hh,"전적"),itr=headerIndex(hh,"조교사"),ij=headerIndex(hh,"기수");
        if(ino<0||iname<0||itr<0||ij<0)return out;
        Set<Integer> seen=new LinkedHashSet<>();
        for(Element tr:target.select("tr")){
            if(tr==header)continue; org.jsoup.select.Elements td=tr.select("td");
            int need=Math.max(Math.max(ino,iname),Math.max(itr,ij)); if(td.size()<=need)continue;
            String ns=td.get(ino).text().trim(); if(!ns.matches("\\d{1,2}"))continue;
            int no=Integer.parseInt(ns); if(no<1||no>30||seen.contains(no))continue;
            String name=td.get(iname).text().trim(); if(name.isEmpty()||"마명".equals(name))continue; seen.add(no);
            StringBuilder tail=new StringBuilder(); for(int i=Math.max(5,ij+1);i<td.size();i++){String x=td.get(i).text().trim();if(!x.isEmpty()){if(tail.length()>0)tail.append(' ');tail.append(x);}}
            String pop=""; for(int i=td.size()-1;i>=Math.max(5,ij+1);i--){String x=td.get(i).text().trim();if(x.matches("\\d{1,4}")){pop=x;break;}}
            out.add(new Entry(no,name,irec>=0&&td.size()>irec?td.get(irec).text().trim():"",td.get(itr).text().trim().replaceAll("\\(\\d+\\)$",""),td.get(ij).text().trim(),pop,tail.toString(),ScratchDetector.isEntryExcluded(tr)));
        }
        return out;
    }

    static List<String> typeCandidates(String date){
        Set<String> out=new LinkedHashSet<>();
        try{
            DayOfWeek d=LocalDate.parse(date).getDayOfWeek();
            if(d==DayOfWeek.FRIDAY)out.add("5");
            else if(d==DayOfWeek.SATURDAY)out.add("6");
            else if(d==DayOfWeek.SUNDAY)out.add("7");
        }catch(Exception ignored){}
        out.add("5");out.add("6");out.add("7");out.add("1");
        return new ArrayList<>(out);
    }
}
