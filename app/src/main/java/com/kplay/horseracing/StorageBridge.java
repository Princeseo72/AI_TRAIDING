package com.kplay.horseracing.gumvit;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.webkit.JavascriptInterface;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Iterator;

public class StorageBridge {
    private final RaceDbHelper helper;
    public StorageBridge(Context context) { helper = new RaceDbHelper(context); }

    private String error(String message) {
        try { return new JSONObject().put("ok", false).put("error", message).toString(); }
        catch (Exception ignored) { return "{\"ok\":false}"; }
    }

    @JavascriptInterface public String saveAnalysis(String json) {
        SQLiteDatabase db = null;
        try {
            JSONObject p = new JSONObject(json);
            if (!"approved".equals(p.optString("status"))) return error("승인된 분석만 저장할 수 있습니다.");
            String analysisVersion = p.optString("analysisVersion", "");
            if (analysisVersion.trim().isEmpty()) return error("analysisVersion이 없습니다.");
            JSONObject result = p.optJSONObject("result");
            if (result == null || !result.optBoolean("ok", false)) return error("유효한 분석 결과가 없습니다.");
            db = helper.getWritableDatabase(); db.beginTransaction();
            long raceId = upsertRace(db, p);
            insertHorses(db, raceId, p.optJSONArray("horses"));
            insertPoolSnapshots(db, raceId, p.optJSONObject("pools"));
            insertSelectionMetrics(db, raceId, result.optJSONObject("poolResults"));
            insertHorseMetrics(db, raceId, result.optJSONObject("horseMetrics"));
            ContentValues rv = new ContentValues(); rv.put("race_id", raceId); rv.put("analysis_version", analysisVersion);
            rv.put("config_version", result.optString("configVersion", "")); rv.put("payload_json", json); rv.put("approved", 1); rv.put("approved_at", String.valueOf(System.currentTimeMillis()));
            long resultId = db.insertOrThrow("analysis_results", null, rv);
            ContentValues hv = new ContentValues(); hv.put("result_id", resultId); hv.put("action", "approve"); hv.put("snapshot_data", json); db.insertOrThrow("analysis_history", null, hv);
            ContentValues legacy = new ContentValues(); legacy.put("race_date", p.optString("raceDate")); legacy.put("region", p.optString("region")); legacy.put("race_number", p.optInt("raceNumber")); legacy.put("status", "approved"); legacy.put("market_center", p.optInt("marketCenter", 0)); legacy.put("late_money", p.optInt("lateMoney", 0)); legacy.put("payload_json", json);
            long legacyId = db.insertOrThrow("analysis_records", null, legacy);
            db.setTransactionSuccessful();
            return new JSONObject().put("ok", true).put("id", legacyId).put("resultId", resultId).toString();
        } catch (Exception e) { return error(e.getMessage() == null ? e.toString() : e.getMessage()); }
        finally { if (db != null && db.inTransaction()) db.endTransaction(); }
    }

    private long upsertRace(SQLiteDatabase db, JSONObject p) throws Exception {
        ContentValues v = new ContentValues(); v.put("race_date", p.optString("raceDate")); v.put("region", p.optString("region")); v.put("race_number", p.optInt("raceNumber"));
        db.insertWithOnConflict("races", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        try (Cursor c = db.rawQuery("SELECT id FROM races WHERE race_date=? AND region=? AND race_number=?", new String[]{p.optString("raceDate"), p.optString("region"), String.valueOf(p.optInt("raceNumber"))})) { if (!c.moveToFirst()) throw new Exception("경주 레코드 생성 실패"); return c.getLong(0); }
    }

    private void insertHorses(SQLiteDatabase db, long raceId, JSONArray horses) throws Exception {
        if (horses == null) return;
        for (int i=0;i<horses.length();i++) { JSONObject h=horses.optJSONObject(i); if(h==null) continue; ContentValues v=new ContentValues(); v.put("race_id",raceId); v.put("horse_number",h.optInt("number")); v.put("horse_name",h.optString("name")); v.put("record_text",h.optString("record")); v.put("trainer",h.optString("trainer")); v.put("jockey",h.optString("jockey")); v.put("expert",h.optString("expert")); v.put("popularity",h.optString("popularity")); db.insertWithOnConflict("horses",null,v,SQLiteDatabase.CONFLICT_REPLACE); }
    }

    private void insertPoolSnapshots(SQLiteDatabase db, long raceId, JSONObject pools) throws Exception {
        if (pools == null) return; Iterator<String> keys=pools.keys();
        while(keys.hasNext()) { String poolType=keys.next(); JSONObject pool=pools.optJSONObject(poolType); if(pool==null) continue; for(String snap:new String[]{"T20","T5"}) { JSONArray rows=pool.optJSONArray(snap); if(rows==null) continue; for(int i=0;i<rows.length();i++) { JSONObject r=rows.optJSONObject(i); if(r==null) continue; double odds=r.optDouble("odds",Double.NaN); if(!Double.isFinite(odds)||odds<=0) continue; ContentValues v=new ContentValues(); v.put("race_id",raceId); v.put("pool_type",poolType); v.put("snapshot_type",snap); v.put("selection_key",r.optString("key")); v.put("odds",odds); db.insertOrThrow("pool_snapshots",null,v); } } }
    }

    private void insertSelectionMetrics(SQLiteDatabase db, long raceId, JSONObject poolResults) throws Exception {
        if(poolResults==null)return; Iterator<String> keys=poolResults.keys(); while(keys.hasNext()) { String poolType=keys.next(); JSONObject pr=poolResults.optJSONObject(poolType); if(pr==null||!"OK".equals(pr.optString("status")))continue; JSONArray c=pr.optJSONArray("candidates"); if(c==null)continue; for(int i=0;i<c.length();i++) { JSONObject x=c.optJSONObject(i);if(x==null)continue;JSONObject e=x.optJSONObject("evidence");ContentValues v=new ContentValues();v.put("race_id",raceId);v.put("pool_type",poolType);v.put("selection_key",x.optString("key"));v.put("lmi",e==null?null:e.optDouble("lmi"));v.put("score",x.optDouble("score"));v.put("evidence_json",e==null?"{}":e.toString());db.insertOrThrow("selection_metrics",null,v); } }
    }

    private void insertHorseMetrics(SQLiteDatabase db, long raceId, JSONObject horseMetrics) throws Exception {
        if(horseMetrics==null)return; Iterator<String> keys=horseMetrics.keys(); while(keys.hasNext()) { String k=keys.next();JSONObject m=horseMetrics.optJSONObject(k);if(m==null)continue;ContentValues v=new ContentValues();v.put("race_id",raceId);v.put("horse_number",Integer.parseInt(k));v.put("divergence_score",m.optDouble("divergence",0));v.put("role_json",m.toString());db.insertOrThrow("horse_metrics",null,v); }
    }

    @JavascriptInterface public String listAnalyses() {
        JSONArray arr = new JSONArray(); long start=System.nanoTime(); SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT id,race_date,region,race_number,status,market_center,late_money,created_at FROM analysis_records ORDER BY id DESC LIMIT 200", null)) {
            while (c.moveToNext()) { JSONObject o=new JSONObject(); o.put("id",c.getLong(0));o.put("raceDate",c.getString(1));o.put("region",c.getString(2));o.put("raceNumber",c.getInt(3));o.put("status",c.getString(4));o.put("marketCenter",c.getInt(5));o.put("lateMoney",c.getInt(6));o.put("createdAt",c.getString(7));arr.put(o); }
        } catch (Exception ignored) {}
        return arr.toString();
    }

    @JavascriptInterface public String getAnalysis(long id) {
        long start=System.nanoTime(); SQLiteDatabase db=helper.getReadableDatabase();
        try (Cursor c=db.rawQuery("SELECT payload_json FROM analysis_records WHERE id=? LIMIT 1",new String[]{String.valueOf(id)})) {
            if(!c.moveToFirst()) return error("저장 분석을 찾지 못했습니다.");
            return new JSONObject().put("ok",true).put("loadMs",(System.nanoTime()-start)/1_000_000.0).put("payload",new JSONObject(c.getString(0))).toString();
        } catch(Exception e){return error(e.getMessage());}
    }

    @JavascriptInterface public String healthCheck() {
        long start=System.nanoTime(); SQLiteDatabase db=helper.getReadableDatabase();
        try {
            int records=0; try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM analysis_records",null)){if(c.moveToFirst())records=c.getInt(0);}
            long pageCount=0,pageSize=0; try(Cursor c=db.rawQuery("PRAGMA page_count",null)){if(c.moveToFirst())pageCount=c.getLong(0);} try(Cursor c=db.rawQuery("PRAGMA page_size",null)){if(c.moveToFirst())pageSize=c.getLong(0);}
            db.rawQuery("PRAGMA quick_check",null).close();
            return new JSONObject().put("ok",true).put("records",records).put("dbBytes",pageCount*pageSize).put("durationMs",(System.nanoTime()-start)/1_000_000.0).toString();
        } catch(Exception e){return error(e.getMessage());}
    }

    @JavascriptInterface public String cleanupData() {
        long start=System.nanoTime(); SQLiteDatabase db=helper.getWritableDatabase();
        try {
            db.execSQL("DELETE FROM selection_metrics WHERE race_id NOT IN (SELECT id FROM races)");
            db.execSQL("DELETE FROM horse_metrics WHERE race_id NOT IN (SELECT id FROM races)");
            db.execSQL("DELETE FROM pool_snapshots WHERE race_id NOT IN (SELECT id FROM races)");
            db.execSQL("DELETE FROM horses WHERE race_id NOT IN (SELECT id FROM races)");
            db.execSQL("DELETE FROM analysis_history WHERE result_id NOT IN (SELECT id FROM analysis_results)");
            db.execSQL("PRAGMA optimize");
            db.execSQL("VACUUM");
            return new JSONObject().put("ok",true).put("durationMs",(System.nanoTime()-start)/1_000_000.0).toString();
        } catch(Exception e){return error(e.getMessage());}
    }

    @JavascriptInterface public String deleteAnalysis(long id) { int n=helper.getWritableDatabase().delete("analysis_records","id=?",new String[]{String.valueOf(id)});return "{\"ok\":"+(n>0)+"}"; }
}
