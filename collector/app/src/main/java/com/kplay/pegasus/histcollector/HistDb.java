package com.kplay.pegasus.histcollector;
import android.content.*;import android.database.sqlite.*;import org.json.*;import java.security.*;import java.nio.charset.StandardCharsets;
public final class HistDb extends SQLiteOpenHelper{
 public static final String SCHEMA="PEGASUS_HIST_V1"; public HistDb(Context c){super(c,"pegasus_hist_v1.sqlite",null,1);}
 public void onCreate(SQLiteDatabase d){
  d.execSQL("CREATE TABLE meta(k TEXT PRIMARY KEY,v TEXT NOT NULL)");
  d.execSQL("INSERT INTO meta VALUES('schema_version','"+SCHEMA+"')");
  d.execSQL("CREATE TABLE race(race_uid TEXT PRIMARY KEY,track_code TEXT NOT NULL,region_group TEXT NOT NULL,race_date TEXT NOT NULL,race_no INTEGER NOT NULL,source_url TEXT,source_hash TEXT NOT NULL UNIQUE,collected_at INTEGER NOT NULL)");
  d.execSQL("CREATE TABLE runner(runner_uid TEXT PRIMARY KEY,race_uid TEXT NOT NULL,horse_no INTEGER NOT NULL,horse_name TEXT,finish_rank INTEGER,source_hash TEXT NOT NULL UNIQUE)");
  d.execSQL("CREATE TABLE odds(odds_uid TEXT PRIMARY KEY,race_uid TEXT NOT NULL,pool_code TEXT NOT NULL,selection_key TEXT NOT NULL,horse_no_1 INTEGER,horse_no_2 INTEGER,horse_no_3 INTEGER,selection_ordered INTEGER NOT NULL,odds_decimal REAL NOT NULL,odds_stage TEXT NOT NULL DEFAULT 'FINAL',source_hash TEXT NOT NULL UNIQUE)");
  d.execSQL("CREATE TABLE checkpoint(track_code TEXT PRIMARY KEY,next_date TEXT NOT NULL,last_race_no INTEGER NOT NULL DEFAULT 0,updated_at INTEGER NOT NULL)");
  d.execSQL("CREATE INDEX ix_race_track_date ON race(track_code,race_date,race_no)");
  d.execSQL("CREATE INDEX ix_runner_race ON runner(race_uid,horse_no)");
  d.execSQL("CREATE INDEX ix_odds_race_pool ON odds(race_uid,pool_code,selection_key)");
 }
 public void onUpgrade(SQLiteDatabase d,int a,int b){throw new IllegalStateException("immutable schema; migrate to new DB");}
 static String sha(String s){try{byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));StringBuilder x=new StringBuilder();for(byte v:b)x.append(String.format("%02x",v));return x.toString();}catch(Exception e){throw new RuntimeException(e);}}
 public synchronized void saveRace(String track,String group,String date,int no,String url,JSONArray runners,JSONArray odds){
  SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{
   String rid="KRA:"+track+":"+date.replace("-","")+":R"+no;String rh=sha(rid+"|"+url+"|"+runners+"|"+odds);
   d.execSQL("INSERT OR IGNORE INTO race VALUES(?,?,?,?,?,?,?,?)",new Object[]{rid,track,group,date,no,url,rh,System.currentTimeMillis()});
   for(int i=0;i<runners.length();i++){JSONObject r=runners.getJSONObject(i);String uid=rid+":H"+r.getInt("horse_no"),h=sha(uid+"|"+r.toString());d.execSQL("INSERT OR IGNORE INTO runner VALUES(?,?,?,?,?,?)",new Object[]{uid,rid,r.getInt("horse_no"),r.optString("horse_name",""),r.has("finish_rank")?r.optInt("finish_rank"):null,h});}
   for(int i=0;i<odds.length();i++){JSONObject o=odds.getJSONObject(i);String pool=o.getString("pool_code"),sel=o.getString("selection_key"),uid=rid+":"+pool+":"+sel+":FINAL",h=sha(uid+"|"+o.getDouble("odds_decimal"));String[] ns=sel.split("[-\\u003e]");Integer n1=ns.length>0?Integer.valueOf(ns[0]):null,n2=ns.length>1?Integer.valueOf(ns[1]):null,n3=ns.length>2?Integer.valueOf(ns[2]):null;int ordered=(pool.equals("EXACTA")||pool.equals("TRIFECTA"))?1:0;d.execSQL("INSERT OR IGNORE INTO odds VALUES(?,?,?,?,?,?,?,?,?,?,?)",new Object[]{uid,rid,pool,sel,n1,n2,n3,ordered,o.getDouble("odds_decimal"),"FINAL",h});}
   d.setTransactionSuccessful();
  }catch(Exception e){throw new RuntimeException(e);}finally{d.endTransaction();}
 }
 public java.time.LocalDate resumeDate(String track,java.time.LocalDate fallback){android.database.Cursor c=getReadableDatabase().rawQuery("SELECT next_date FROM checkpoint WHERE track_code=?",new String[]{track});try{return c.moveToFirst()?java.time.LocalDate.parse(c.getString(0)):fallback;}catch(Exception e){return fallback;}finally{c.close();}}
 public synchronized void saveCheckpoint(String track,java.time.LocalDate next){getWritableDatabase().execSQL("INSERT OR REPLACE INTO checkpoint(track_code,next_date,last_race_no,updated_at) VALUES(?,?,0,?)",new Object[]{track,next.toString(),System.currentTimeMillis()});}
 public synchronized void checkpointWal(){SQLiteDatabase d=getWritableDatabase();try{d.rawQuery("PRAGMA wal_checkpoint(FULL)",null).close();}catch(Exception ignored){}}
 public long count(String t){return android.database.DatabaseUtils.queryNumEntries(getReadableDatabase(),t);}
}
