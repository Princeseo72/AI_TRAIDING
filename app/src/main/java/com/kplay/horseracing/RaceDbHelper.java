package com.kplay.horseracing.gumvit;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class RaceDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "horse_racing_analysis.db";
    private static final int DB_VERSION = 3;
    public RaceDbHelper(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE analysis_records (id INTEGER PRIMARY KEY AUTOINCREMENT,race_date TEXT NOT NULL,region TEXT NOT NULL,race_number INTEGER NOT NULL,status TEXT NOT NULL,market_center INTEGER,late_money INTEGER,payload_json TEXT NOT NULL,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE INDEX idx_analysis_race ON analysis_records(race_date, region, race_number)");
        createV2Tables(db);
        createV3Tables(db);
    }

    private void createV2Tables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE races (id INTEGER PRIMARY KEY AUTOINCREMENT,race_date TEXT NOT NULL,region TEXT NOT NULL,race_number INTEGER NOT NULL,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,UNIQUE(race_date,region,race_number))");
        db.execSQL("CREATE TABLE horses (id INTEGER PRIMARY KEY AUTOINCREMENT,race_id INTEGER NOT NULL,horse_number INTEGER NOT NULL,horse_name TEXT,record_text TEXT,trainer TEXT,jockey TEXT,expert TEXT,popularity TEXT,UNIQUE(race_id,horse_number))");
        db.execSQL("CREATE TABLE pool_snapshots (id INTEGER PRIMARY KEY AUTOINCREMENT,race_id INTEGER NOT NULL,pool_type TEXT NOT NULL,snapshot_type TEXT NOT NULL,selection_key TEXT NOT NULL,odds REAL NOT NULL,implied_share REAL,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE TABLE selection_metrics (id INTEGER PRIMARY KEY AUTOINCREMENT,race_id INTEGER NOT NULL,pool_type TEXT NOT NULL,selection_key TEXT NOT NULL,lmi REAL,score REAL,evidence_json TEXT)");
        db.execSQL("CREATE TABLE horse_metrics (id INTEGER PRIMARY KEY AUTOINCREMENT,race_id INTEGER NOT NULL,horse_number INTEGER NOT NULL,win_share_t20 REAL,win_share_t5 REAL,win_lmi REAL,divergence_score REAL,role_json TEXT)");
        db.execSQL("CREATE TABLE analysis_results (id INTEGER PRIMARY KEY AUTOINCREMENT,race_id INTEGER NOT NULL,analysis_version TEXT NOT NULL,config_version TEXT,payload_json TEXT NOT NULL,approved INTEGER NOT NULL DEFAULT 0,approved_at TEXT,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE TABLE analysis_history (id INTEGER PRIMARY KEY AUTOINCREMENT,result_id INTEGER NOT NULL,action TEXT NOT NULL,snapshot_data TEXT,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE INDEX idx_pool_snapshots_race ON pool_snapshots(race_id,pool_type,snapshot_type)");
        db.execSQL("CREATE INDEX idx_selection_metrics_race ON selection_metrics(race_id,pool_type)");
        db.execSQL("CREATE INDEX idx_horse_metrics_race ON horse_metrics(race_id,horse_number)");
    }

    private void createV3Tables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE race_outcomes (id INTEGER PRIMARY KEY AUTOINCREMENT,analysis_record_id INTEGER NOT NULL UNIQUE,race_date TEXT NOT NULL,region TEXT NOT NULL,race_number INTEGER NOT NULL,result_json TEXT NOT NULL,fetched_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE INDEX idx_race_outcomes_race ON race_outcomes(race_date,region,race_number)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) createV2Tables(db);
        if (oldVersion < 3) createV3Tables(db);
    }
}
