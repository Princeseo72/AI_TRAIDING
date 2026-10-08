package com.kplay.horseracing.gumvit;
import android.content.*;import android.database.Cursor;import android.database.sqlite.SQLiteDatabase;import android.net.Uri;import org.json.*;import java.io.*;import java.security.*;import java.util.*;
public final class HistRepository{
 public static final String SCHEMA="PEGASUS_HIST_V1";private final Context ctx;private final File dir,file;
 private static final Set<String> TRACKS=new HashSet<>(Arrays.asList("SEOUL","BUSAN_GYEONGNAM","JEJU","YEONGCHEON"));
 private static final Set<String> POOLS=new HashSet<>(Arrays.asList("WIN","PLACE","QUINELLA","EXACTA","QUINELLA_PLACE","TRIO","TRIFECTA"));
 HistRepository(Context c){ctx=c.getApplicationContext();dir=new File(ctx.getFilesDir(),"pegasus_hist");file=new File(dir,"PEGASUS_HIST_V1.sqlite");}
 JSONObject importFromCollector()throws Exception{
  Uri uri=Uri.parse("content://com.kplay.pegasus.histcollector.hist/current");
  JSONObject r=importFrom(uri);return r.put("transferSource","HIST_COLLECTOR_PROVIDER").put("sourceUri",uri.toString());
 }
 JSONObject importFrom(Uri uri)throws Exception{if(!dir.exists()&&!dir.mkdirs())throw new IOException("HIST 폴더 생성 실패");File tmp=new File(dir,"PEGASUS_HIST_V1.importing");
  try(InputStream in=ctx.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(tmp)){if(in==null)throw new IOException("선택 파일 열기 실패");byte[]b=new byte[65536];for(int n;(n=in.read(b))>0;)out.write(b,0,n);}
  JSONObject v=validate(tmp);if(!v.optBoolean("ok")){tmp.delete();return v;}if(file.exists()&&!file.delete())throw new IOException("기존 HIST 교체 실패");if(!tmp.renameTo(file))throw new IOException("HIST 설치영역 이동 실패");return validate(file).put("installedPath",file.getAbsolutePath()).put("immutable",true);
 }
 JSONObject status(){try{return file.exists()?validate(file):new JSONObject().put("ok",true).put("status","HIST_NOT_IMPORTED").put("installed",false);}catch(Exception e){return err(e);}}
 JSONObject validate(File f)throws Exception{SQLiteDatabase d=null;try{d=SQLiteDatabase.openDatabase(f.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);
  String schema=scalar(d,"SELECT v FROM meta WHERE k='schema_version'");if(!SCHEMA.equals(schema))return new JSONObject().put("ok",false).put("status","HIST_INVALID").put("error","schema_version 불일치: "+schema);
  long races=count(d,"race"),runners=count(d,"runner"),odds=count(d,"odds");if(races<1||runners<3||odds<1)return new JSONObject().put("ok",false).put("status","HIST_INVALID").put("error","HIST 데이터가 비어 있습니다.");
  JSONArray badTracks=queryDistinct(d,"SELECT DISTINCT track_code FROM race");for(int i=0;i<badTracks.length();i++)if(!TRACKS.contains(badTracks.getString(i)))return new JSONObject().put("ok",false).put("status","HIST_INVALID").put("error","알 수 없는 track_code");
  JSONArray pools=queryDistinct(d,"SELECT DISTINCT pool_code FROM odds");for(int i=0;i<pools.length();i++)if(!POOLS.contains(pools.getString(i)))return new JSONObject().put("ok",false).put("status","HIST_INVALID").put("error","알 수 없는 pool_code");
  String min=scalar(d,"SELECT MIN(race_date) FROM race"),max=scalar(d,"SELECT MAX(race_date) FROM race");
  return new JSONObject().put("ok",true).put("status","HIST_READY").put("installed",true).put("schemaVersion",schema).put("sha256",sha(f)).put("raceCount",races).put("runnerCount",runners).put("oddsCount",odds).put("minDate",min).put("maxDate",max).put("poolCodes",pools);
 }finally{if(d!=null)d.close();}}
 JSONObject trainSummary()throws Exception{if(!file.exists())return new JSONObject().put("ok",false).put("error","HIST 파일 미입력");SQLiteDatabase d=SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);try{
  JSONObject global=aggregate(d,null),regions=new JSONObject();for(String t:TRACKS)regions.put(t,aggregate(d,t));
  return new JSONObject().put("ok",true).put("schemaVersion",SCHEMA).put("method","WALK_FORWARD_AGGREGATE_V1").put("global",global).put("regions",regions).put("sourceSha256",sha(file));
 }finally{d.close();}}
 private JSONObject aggregate(SQLiteDatabase d,String track)throws Exception{String w=track==null?"":" WHERE r.track_code=?";String[]a=track==null?null:new String[]{track};
  JSONObject pools=new JSONObject();for(String p:POOLS){String sql="SELECT COUNT(*),AVG(o.odds_decimal),SUM(CASE WHEN o.odds_decimal>0 THEN 1.0/o.odds_decimal ELSE 0 END) FROM odds o JOIN race r ON r.race_uid=o.race_uid"+(track==null?" WHERE ":" WHERE r.track_code=? AND ")+"o.pool_code=?";String[]q=track==null?new String[]{p}:new String[]{track,p};try(Cursor c=d.rawQuery(sql,q)){if(c.moveToFirst())pools.put(p,new JSONObject().put("samples",c.getLong(0)).put("meanOdds",c.isNull(1)?JSONObject.NULL:c.getDouble(1)).put("impliedMass",c.getDouble(2)));}}
  long races;try(Cursor c=d.rawQuery("SELECT COUNT(*) FROM race r"+w,a)){c.moveToFirst();races=c.getLong(0);}
  long outcomes;try(Cursor c=d.rawQuery("SELECT COUNT(*) FROM runner u JOIN race r ON r.race_uid=u.race_uid"+(track==null?" WHERE ":" WHERE r.track_code=? AND ")+"u.finish_rank IS NOT NULL",a)){c.moveToFirst();outcomes=c.getLong(0);}
  return new JSONObject().put("raceSamples",races).put("outcomeRows",outcomes).put("poolStats",pools).put("status",races>=20&&outcomes>=60?"CANDIDATE_READY":"LOW_SAMPLE");
 }
 JSONObject replayTrain()throws Exception{if(!file.exists())return new JSONObject().put("ok",false).put("error","HIST 파일 미입력");SQLiteDatabase d=SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);try{
  JSONObject regions=new JSONObject();JSONObject global=replayScope(d,null);for(String t:TRACKS)regions.put(t,replayScope(d,t));
  boolean ready=global.optInt("evaluatedRaces",0)>=100&&global.optDouble("brierModel",9)<global.optDouble("brierMarket",9);
  return new JSONObject().put("ok",true).put("method","WALK_FORWARD_V1").put("global",global).put("regions",regions)
    .put("candidateStatus",ready?"CHAMPION_ELIGIBLE":"CHALLENGER_ONLY").put("promoted",false)
    .put("promotionRule","evaluatedRaces>=100 && brierModel<brierMarket; promotion persisted only by PEGASUS model registry")
    .put("sourceSha256",sha(file));
 }finally{d.close();}}
 private JSONObject replayScope(SQLiteDatabase d,String track)throws Exception{
  String sql="SELECT r.race_date,r.race_uid FROM race r"+(track==null?"":" WHERE r.track_code=?")+" ORDER BY r.race_date,r.race_no";String[] args=track==null?null:new String[]{track};
  int evaluated=0,top1=0;double bModel=0,bMarket=0;try(Cursor rc=d.rawQuery(sql,args)){while(rc.moveToNext()){
   String date=rc.getString(0),rid=rc.getString(1);java.util.Map<Integer,Integer> finish=new java.util.HashMap<>();java.util.Map<Integer,String> horseNames=new java.util.HashMap<>();java.util.Map<Integer,Double> winOdds=new java.util.HashMap<>();
   try(Cursor x=d.rawQuery("SELECT horse_no,horse_name,finish_rank FROM runner WHERE race_uid=? AND finish_rank IS NOT NULL",new String[]{rid})){while(x.moveToNext()){finish.put(x.getInt(0),x.getInt(2));horseNames.put(x.getInt(0),x.getString(1));}}
   try(Cursor x=d.rawQuery("SELECT horse_no_1,odds_decimal FROM odds WHERE race_uid=? AND pool_code='WIN' AND horse_no_1 IS NOT NULL AND odds_decimal>0",new String[]{rid})){while(x.moveToNext())winOdds.put(x.getInt(0),x.getDouble(1));}
   if(finish.size()<3||winOdds.size()<3)continue;double z=0;for(double o:winOdds.values())z+=1.0/o;if(z<=0)continue;
   int actual=-1;for(java.util.Map.Entry<Integer,Integer> e:finish.entrySet())if(e.getValue()==1){actual=e.getKey();break;}if(actual<0)continue;
   double best=-1;int predicted=-1;for(java.util.Map.Entry<Integer,Double> e:winOdds.entrySet()){double market=(1.0/e.getValue())/z;double prior=historicalHorsePrior(d,horseNames.get(e.getKey()),track,date);double model=.55*prior+.45*market;if(model>best){best=model;predicted=e.getKey();}double y=e.getKey()==actual?1:0;bModel+=(model-y)*(model-y);bMarket+=(market-y)*(market-y);}
   evaluated++;if(predicted==actual)top1++;
  }}
  return new JSONObject().put("evaluatedRaces",evaluated).put("top1HitRate",evaluated==0?JSONObject.NULL:(double)top1/evaluated).put("brierModel",evaluated==0?JSONObject.NULL:bModel/evaluated).put("brierMarket",evaluated==0?JSONObject.NULL:bMarket/evaluated).put("status",evaluated>=100?"VALIDATED":"LOW_SAMPLE");
 }
 private double historicalHorsePrior(SQLiteDatabase d,String horseName,String track,String before)throws Exception{
  if(horseName==null||horseName.trim().isEmpty())return .20;
  String sql="SELECT COUNT(*),SUM(CASE WHEN u.finish_rank=1 THEN 1 ELSE 0 END),SUM(CASE WHEN u.finish_rank BETWEEN 1 AND 3 THEN 1 ELSE 0 END) FROM runner u JOIN race r ON r.race_uid=u.race_uid WHERE u.horse_name=? AND r.race_date<?"+(track==null?"":" AND r.track_code=?");
  String[] a=track==null?new String[]{horseName,before}:new String[]{horseName,before,track};try(Cursor c=d.rawQuery(sql,a)){c.moveToFirst();double n=c.getDouble(0),w=c.getDouble(1),t=c.getDouble(2);return .55*((w+1)/(n+8))+.45*((t+3)/(n+10));}
 }
 JSONObject oddsCoverage()throws Exception{if(!file.exists())return new JSONObject().put("complete",false);SQLiteDatabase d=SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);try{
  JSONObject pools=new JSONObject();boolean complete=true;for(String pool:POOLS){long actual=0,expected=0;try(Cursor r=d.rawQuery("SELECT r.race_uid,COUNT(u.runner_uid) FROM race r JOIN runner u ON u.race_uid=r.race_uid GROUP BY r.race_uid",null)){while(r.moveToNext()){int n=r.getInt(1);expected+=expectedSelections(pool,n);try(Cursor o=d.rawQuery("SELECT COUNT(*) FROM odds WHERE race_uid=? AND pool_code=?",new String[]{r.getString(0),pool})){o.moveToFirst();actual+=o.getLong(0);}}}double ratio=expected>0?(double)actual/expected:0;boolean ok=ratio>=.90;pools.put(pool,new JSONObject().put("actualRows",actual).put("expectedRows",expected).put("coverageRatio",ratio).put("complete",ok));if(!ok)complete=false;}return new JSONObject().put("complete",complete).put("minimumCoverage",.90).put("pools",pools);
 }finally{d.close();}}
 private static long expectedSelections(String p,int n){if(n<1)return 0;if(p.equals("WIN")||p.equals("PLACE"))return n;if(p.equals("QUINELLA")||p.equals("QUINELLA_PLACE"))return (long)n*(n-1)/2;if(p.equals("EXACTA"))return (long)n*(n-1);if(p.equals("TRIO"))return n<3?0:(long)n*(n-1)*(n-2)/6;if(p.equals("TRIFECTA"))return n<3?0:(long)n*(n-1)*(n-2);return 0;}
 JSONObject poolOutcomeModels()throws Exception{if(!file.exists())return new JSONObject();SQLiteDatabase d=SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);try{
  JSONObject out=new JSONObject();for(String pool:POOLS){JSONObject bins=new JSONObject();for(String bin:new String[]{"LE3","LE10","LE30","GT30"})bins.put(bin,new JSONObject().put("samples",0).put("hits",0));
   try(Cursor x=d.rawQuery("SELECT o.race_uid,o.selection_key,o.odds_decimal FROM odds o WHERE o.pool_code=? AND o.odds_decimal>0",new String[]{pool})){while(x.moveToNext()){String rid=x.getString(0),sel=x.getString(1);double odds=x.getDouble(2);int[] top=top3(d,rid);if(top[0]<=0)continue;String bin=odds<=3?"LE3":odds<=10?"LE10":odds<=30?"LE30":"GT30";JSONObject b=bins.getJSONObject(bin);b.put("samples",b.getInt("samples")+1);if(poolHit(pool,sel,top))b.put("hits",b.getInt("hits")+1);}}
   for(String bin:new String[]{"LE3","LE10","LE30","GT30"}){JSONObject b=bins.getJSONObject(bin);int n=b.getInt("samples"),h=b.getInt("hits");b.put("posteriorHitRate",(h+1.0)/(n+4.0));}
   out.put(pool,new JSONObject().put("bins",bins).put("target",poolTarget(pool)));
  }return out;
 }finally{d.close();}}
 private static int[] top3(SQLiteDatabase d,String rid){int[] a={-1,-1,-1};try(Cursor c=d.rawQuery("SELECT horse_no,finish_rank FROM runner WHERE race_uid=? AND finish_rank BETWEEN 1 AND 3",new String[]{rid})){while(c.moveToNext()){int r=c.getInt(1);if(r>=1&&r<=3)a[r-1]=c.getInt(0);}}return a;}
 private static boolean poolHit(String pool,String sel,int[] t){int[] n=parseSelection(sel);if(pool.equals("WIN"))return n.length>=1&&n[0]==t[0];if(pool.equals("PLACE"))return n.length>=1&&(n[0]==t[0]||n[0]==t[1]||n[0]==t[2]);if(pool.equals("EXACTA"))return n.length>=2&&n[0]==t[0]&&n[1]==t[1];if(pool.equals("TRIFECTA"))return n.length>=3&&n[0]==t[0]&&n[1]==t[1]&&n[2]==t[2];if(pool.equals("QUINELLA"))return n.length>=2&&sameSet(n,new int[]{t[0],t[1]});if(pool.equals("TRIO"))return n.length>=3&&sameSet(n,t);if(pool.equals("QUINELLA_PLACE"))return n.length>=2&&contains(t,n[0])&&contains(t,n[1]);return false;}
 private static int[] parseSelection(String s){java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\d+").matcher(String.valueOf(s));java.util.ArrayList<Integer>a=new java.util.ArrayList<>();while(m.find())a.add(Integer.parseInt(m.group()));int[]x=new int[a.size()];for(int i=0;i<x.length;i++)x[i]=a.get(i);return x;}
 private static boolean sameSet(int[]a,int[]b){if(a.length<b.length)return false;int[]x=java.util.Arrays.copyOf(a,b.length),y=java.util.Arrays.copyOf(b,b.length);java.util.Arrays.sort(x);java.util.Arrays.sort(y);return java.util.Arrays.equals(x,y);}
 private static boolean contains(int[]a,int v){for(int x:a)if(x==v)return true;return false;}
 private static String poolTarget(String p){if(p.equals("WIN"))return"FIRST";if(p.equals("PLACE"))return"TOP3";if(p.equals("QUINELLA"))return"FIRST2_UNORDERED";if(p.equals("EXACTA"))return"FIRST2_ORDERED";if(p.equals("QUINELLA_PLACE"))return"PAIR_IN_TOP3";if(p.equals("TRIO"))return"TOP3_UNORDERED";return"TOP3_ORDERED";}
 JSONObject priors(String track,String beforeDate,JSONArray horses)throws Exception{JSONObject out=new JSONObject();if(!file.exists())return out;SQLiteDatabase d=SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);try{
  for(int i=0;i<horses.length();i++){JSONObject h=horses.optJSONObject(i);if(h==null)continue;String name=h.optString("name","").trim();if(name.isEmpty())continue;
   try(Cursor c=d.rawQuery("SELECT COUNT(*),SUM(CASE WHEN u.finish_rank=1 THEN 1 ELSE 0 END),SUM(CASE WHEN u.finish_rank BETWEEN 1 AND 3 THEN 1 ELSE 0 END) FROM runner u JOIN race r ON r.race_uid=u.race_uid WHERE u.horse_name=? AND r.track_code=? AND r.race_date<?",new String[]{name,track,beforeDate})){if(c.moveToFirst()){double n=c.getDouble(0),win=c.getDouble(1),top3=c.getDouble(2);if(n>0){double wr=(win+1.0)/(n+8.0),tr=(top3+3.0)/(n+10.0);out.put(String.valueOf(h.optInt("number")),new JSONObject().put("samples",(int)n).put("winRateBayes",wr).put("top3RateBayes",tr).put("prior",Math.min(1,.55*wr+.45*tr)));}}}
  }return out;
 }finally{d.close();}}
 private static long count(SQLiteDatabase d,String t){try(Cursor c=d.rawQuery("SELECT COUNT(*) FROM "+t,null)){c.moveToFirst();return c.getLong(0);}}
 private static String scalar(SQLiteDatabase d,String q){try(Cursor c=d.rawQuery(q,null)){return c.moveToFirst()&&!c.isNull(0)?c.getString(0):"";}}
 private static JSONArray queryDistinct(SQLiteDatabase d,String q)throws Exception{JSONArray a=new JSONArray();try(Cursor c=d.rawQuery(q,null)){while(c.moveToNext())a.put(c.getString(0));}return a;}
 private static String sha(File f)throws Exception{MessageDigest m=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[]b=new byte[65536];for(int n;(n=in.read(b))>0;)m.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte x:m.digest())s.append(String.format("%02x",x));return s.toString();}
 private static JSONObject err(Exception e){try{return new JSONObject().put("ok",false).put("status","HIST_INVALID").put("error",String.valueOf(e.getMessage()));}catch(Exception x){return new JSONObject();}}
}