package com.kplay.pegasus.histcollector;
import org.jsoup.*;import org.jsoup.nodes.*;import org.jsoup.select.*;import org.json.*;import java.time.*;import java.util.*;import java.util.regex.*;
public final class KraHistorySource{
 static final Map<String,String> MEET=new LinkedHashMap<>();static{MEET.put("SEOUL","1");MEET.put("BUSAN_GYEONGNAM","3");MEET.put("JEJU","2");MEET.put("YEONGCHEON","3");}
 static final Map<String,String> POOL=new LinkedHashMap<>();static{POOL.put("단승식","WIN");POOL.put("연승식","PLACE");POOL.put("복승식","QUINELLA");POOL.put("쌍승식","EXACTA");POOL.put("복연승식","QUINELLA_PLACE");POOL.put("삼복승식","TRIO");POOL.put("삼쌍승식","TRIFECTA");}
 static Document get(String u)throws Exception{return Jsoup.connect(u).userAgent("Mozilla/5.0 PEGASUS-HIST/1.0").timeout(15000).get();}
 static boolean validTrackDate(String track,LocalDate d){return !"YEONGCHEON".equals(track)||!d.isBefore(LocalDate.of(2026,9,13));}
 static String group(String t){return t.equals("SEOUL")?"CAPITAL":t.equals("JEJU")?"JEJU":"YEONGNAM";}
 static String normalizeSelection(String pool,String raw){List<String> n=new ArrayList<>();Matcher m=Pattern.compile("\\d{1,2}").matcher(raw);while(m.find())n.add(String.valueOf(Integer.parseInt(m.group())));if(n.isEmpty())return"";if(pool.equals("EXACTA")||pool.equals("TRIFECTA"))return String.join(">",n);Collections.sort(n,Comparator.comparingInt(Integer::parseInt));return String.join("-",n);}
 static JSONArray parseRunners(Document d)throws Exception{JSONArray a=new JSONArray();for(Element tr:d.select("tr")){Elements td=tr.select("td");if(td.size()<3)continue;String first=td.get(0).text().trim();if(!first.matches("\\d{1,2}"))continue;JSONObject x=new JSONObject();x.put("horse_no",Integer.parseInt(first));x.put("horse_name",td.get(1).text().trim());for(Element e:td){String s=e.text().trim();if(s.matches("\\d{1,2}위"))x.put("finish_rank",Integer.parseInt(s.replace("위","")));}a.put(x);}return dedupRunner(a);}
 static JSONArray dedupRunner(JSONArray in)throws Exception{JSONArray out=new JSONArray();Set<Integer>s=new HashSet<>();for(int i=0;i<in.length();i++){JSONObject x=in.getJSONObject(i);int n=x.getInt("horse_no");if(s.add(n))out.put(x);}return out;}
 static JSONArray parseOdds(Document d)throws Exception{JSONArray a=new JSONArray();String current=null;for(Element tr:d.select("tr")){String txt=tr.text().replace("\u00a0"," ").trim();for(String k:POOL.keySet())if(txt.contains(k)){current=POOL.get(k);break;}if(current==null)continue;Matcher om=Pattern.compile("([0-9]+(?:\\.[0-9]+))").matcher(txt);List<Double> vals=new ArrayList<>();while(om.find())vals.add(Double.parseDouble(om.group(1)));Matcher sm=Pattern.compile("(\\d{1,2}(?:\\s*[-→>]\\s*\\d{1,2}){0,2})").matcher(txt);if(sm.find()&&!vals.isEmpty()){String sel=normalizeSelection(current,sm.group(1));if(!sel.isEmpty())a.put(new JSONObject().put("pool_code",current).put("selection_key",sel).put("odds_decimal",vals.get(vals.size()-1)));}}return dedupOdds(a);}
 static JSONArray dedupOdds(JSONArray in)throws Exception{JSONArray o=new JSONArray();Set<String>s=new HashSet<>();for(int i=0;i<in.length();i++){JSONObject x=in.getJSONObject(i);String k=x.getString("pool_code")+"|"+x.getString("selection_key");if(s.add(k))o.put(x);}return o;}
 public static Race fetch(String track,LocalDate date,int raceNo)throws Exception{
  if(!validTrackDate(track,date))return null;String meet=MEET.get(track);String ds=date.toString().replace("-","");
  String url="https://race.kra.co.kr/raceScore/ScoretableDetailList.do?meet="+meet+"&realRcDate="+ds+"&realRcNo="+raceNo;
  Document d=get(url);String body=d.text();if(track.equals("YEONGCHEON")&&!body.contains("영천"))return null;if(track.equals("BUSAN_GYEONGNAM")&&body.contains("영천")&&!body.contains("부경"))return null;
  JSONArray runners=parseRunners(d),odds=parseOdds(d);if(runners.length()<2)return null;return new Race(track,group(track),date.toString(),raceNo,url,runners,odds);
 }
 public static final class Race{public final String track,group,date,url;public final int no;public final JSONArray runners,odds;Race(String t,String g,String d,int n,String u,JSONArray r,JSONArray o){track=t;group=g;date=d;no=n;url=u;runners=r;odds=o;}}
}
