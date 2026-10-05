package com.kplay.horseracing.gumvit;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.webkit.JavascriptInterface;
import org.json.JSONArray;
import org.json.JSONObject;

public class StorageBridge {
    private final RaceDbHelper helper;
    public StorageBridge(Context context) { helper = new RaceDbHelper(context); }

    @JavascriptInterface public String saveAnalysis(String json) {
        try {
            JSONObject p = new JSONObject(json);
            SQLiteDatabase db = helper.getWritableDatabase();
            ContentValues v = new ContentValues();
            v.put("race_date", p.optString("raceDate")); v.put("region", p.optString("region"));
            v.put("race_number", p.optInt("raceNumber")); v.put("status", p.optString("status", "approved"));
            v.put("market_center", p.optInt("marketCenter", 0)); v.put("late_money", p.optInt("lateMoney", 0));
            v.put("payload_json", json);
            long id = db.insertOrThrow("analysis_records", null, v);
            return new JSONObject().put("ok", true).put("id", id).toString();
        } catch (Exception e) {
            try { return new JSONObject().put("ok", false).put("error", e.getMessage()).toString(); }
            catch (Exception ignored) { return "{\"ok\":false}"; }
        }
    }

    @JavascriptInterface public String listAnalyses() {
        JSONArray arr = new JSONArray(); SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT id,race_date,region,race_number,status,market_center,late_money,created_at,payload_json FROM analysis_records ORDER BY id DESC LIMIT 200", null)) {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("id", c.getLong(0)); o.put("raceDate", c.getString(1)); o.put("region", c.getString(2));
                o.put("raceNumber", c.getInt(3)); o.put("status", c.getString(4)); o.put("marketCenter", c.getInt(5));
                o.put("lateMoney", c.getInt(6)); o.put("createdAt", c.getString(7)); o.put("payload", new JSONObject(c.getString(8))); arr.put(o);
            }
        } catch (Exception ignored) {}
        return arr.toString();
    }

    @JavascriptInterface public String deleteAnalysis(long id) {
        int n = helper.getWritableDatabase().delete("analysis_records", "id=?", new String[]{String.valueOf(id)});
        return "{\"ok\":" + (n > 0) + "}";
    }
}
