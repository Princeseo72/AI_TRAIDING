package com.kplay.horseracing;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class RaceDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "horse_racing_analysis.db";
    private static final int DB_VERSION = 1;
    public RaceDbHelper(Context context) { super(context, DB_NAME, null, DB_VERSION); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE analysis_records (id INTEGER PRIMARY KEY AUTOINCREMENT,race_date TEXT NOT NULL,region TEXT NOT NULL,race_number INTEGER NOT NULL,status TEXT NOT NULL,market_center INTEGER,late_money INTEGER,payload_json TEXT NOT NULL,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE INDEX idx_analysis_race ON analysis_records(race_date, region, race_number)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { db.execSQL("DROP TABLE IF EXISTS analysis_records"); onCreate(db); }
}
