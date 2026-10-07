package com.kplay.horseracing.gumvit;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class KraRaceParser {
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

    static boolean raceExists(Document doc,String date,String region,int raceNo){
        if(doc==null)return false;
        for(Element tr:doc.select("tr")){
            Elements td=tr.select("td");
            if(td.size()<3)continue;
            String row=tr.text();
            if(!regionMatches(row,region))continue;
            boolean dateOk=false,raceOk=false;
            for(Element cell:td){
                if(date.equals(normalizedDate(cell.text())))dateOk=true;
                String n=cell.text().replaceAll("[^0-9]","");
                if(n.equals(String.valueOf(raceNo)))raceOk=true;
            }
            if(dateOk&&raceOk)return true;
        }
        return false;
    }

    static JSONArray parseRiding(Document doc,String date,String region,int raceNo)throws Exception{
        JSONArray out=new JSONArray();
        if(doc==null)return out;
        String targetSlash=date.replace('-','/');
        if(!doc.text().contains(targetSlash))return out;

        for(Element table:doc.select("table")){
            Element header=null;
            for(Element tr:table.select("tr")){
                String x=compact(tr.text());
                if(x.contains("기수명")&&x.contains("출전")&&x.contains(String.valueOf(raceNo))){header=tr;break;}
            }
            if(header==null)continue;
            Elements hs=header.select("th,td");
            int jockeyIndex=-1,raceIndex=-1;
            for(int i=0;i<hs.size();i++){
                String x=compact(hs.get(i).text());
                if(x.contains("기수명"))jockeyIndex=i;
                if(x.equals(String.valueOf(raceNo)))raceIndex=i;
            }
            if(jockeyIndex<0||raceIndex<0)continue;

            for(Element tr:table.select("tr")){
                if(tr==header)continue;
                Elements td=tr.select("td");
                if(td.size()<=Math.max(jockeyIndex,raceIndex))continue;
                String jockey=td.get(jockeyIndex).text().trim();
                String cellText=td.get(raceIndex).text().trim();
                if(jockey.isEmpty()||cellText.isEmpty())continue;
                Matcher cm=CIRCLED.matcher(cellText);
                if(!cm.find())continue;
                int no=circledNumber(cm.group(1));
                if(no<1)continue;
                String after=cellText.substring(cm.end()).trim();
                String name=after.replaceFirst("\\s*\\(\\d{1,2}\\).*","").trim();
                if(name.isEmpty())continue;
                Matcher tm=TRAINER.matcher(cellText);
                String trainer=tm.find()?tm.group(1).trim():"";
                out.put(new JSONObject()
                        .put("number",no).put("name",name).put("record","")
                        .put("trainer",trainer).put("jockey",jockey)
                        .put("active",true).put("excluded",false)
                        .put("expert","").put("popularity","")
                        .put("source","KRA_RIDING"));
            }
            if(out.length()>0)break;
        }
        return out;
    }
}
