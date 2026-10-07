package com.kplay.horseracing.gumvit;
import android.content.*;import android.database.Cursor;import android.database.sqlite.SQLiteDatabase;import android.net.Uri;import org.json.*;import java.io.*;import java.security.*;import java.util.*;
public final class HistRepository{
 public static final String SCHEMA="PEGASUS_HIST_V1";private final Context ctx;private final File dir,file;
 private static final Set<String> TRACKS=new HashSet<>(Arrays.asList("SEOUL","BUSAN_GYEONGNAM","JEJU","YEONGCHEON"));
 private static final Set<String> POOLS=new HashSet<>(Arrays.asList("WIN","PLACE","QUINELLA","EXACTA","QUINELLA_PLACE","TRIO","TRIFECTA"));
 HistRepository(Context c){ctx=c.getApplicationContext();dir=new File(ctx.getFilesDir(),"pegasus_hist");file=new File(dir,"PEGASUS_HIST_V1.sqlite");}
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