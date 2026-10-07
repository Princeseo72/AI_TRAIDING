package com.kplay.horseracing.gumvit;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class RaceDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "horse_racing_analysis.db";
    private static final int DB_VERSION = 5;
    public RaceDbHelper(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE analysis_records (id INTEGER PRIMARY KEY AUTOINCREMENT,race_date TEXT NOT NULL,region TEXT NOT NULL,race_number INTEGER NOT NULL,status TEXT NOT NULL,market_center INTEGER,late_money INTEGER,payload_json TEXT NOT NULL,prediction_snapshot_json TEXT,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE INDEX idx_analysis_race ON analysis_records(race_date, region, race_number)");
        createV2Tables(db); createV3Tables(db); createV4Tables(db); createV5Tables(db);
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

    private void createV4Tables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE ml_models (id INTEGER PRIMARY KEY AUTOINCREMENT,model_version TEXT NOT NULL UNIQUE,region TEXT NOT NULL,sample_count INTEGER NOT NULL DEFAULT 0,weights_json TEXT NOT NULL,metrics_json TEXT NOT NULL,status TEXT NOT NULL,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE TABLE ml_training_examples (id INTEGER PRIMARY KEY AUTOINCREMENT,race_id INTEGER,analysis_record_id INTEGER NOT NULL UNIQUE,model_version TEXT NOT NULL,feature_snapshot_json TEXT NOT NULL,prediction_json TEXT NOT NULL,actual_result_json TEXT,target_json TEXT,error_json TEXT,trained_at TEXT)");
        db.execSQL("CREATE TABLE ml_training_events (id INTEGER PRIMARY KEY AUTOINCREMENT,model_from TEXT,model_to TEXT,race_id INTEGER,analysis_record_id INTEGER,weight_delta_json TEXT,before_metrics_json TEXT,after_metrics_json TEXT,promoted INTEGER NOT NULL DEFAULT 0,reason TEXT,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE INDEX idx_ml_models_region_status ON ml_models(region,status,created_at)");
        db.execSQL("CREATE INDEX idx_ml_examples_model ON ml_training_examples(model_version,trained_at)");
        db.execSQL("CREATE INDEX idx_ml_events_record ON ml_training_events(analysis_record_id,created_at)");
        db.execSQL("INSERT OR IGNORE INTO ml_models(model_version,region,sample_count,weights_json,metrics_json,status) VALUES('ML-v1-GLOBAL','GLOBAL',0,'{\"share\":0.32,\"lmi\":0.24,\"crossPool\":0.20,\"popularity\":0.10,\"stability\":0.08,\"structure\":0.06}','{\"verified\":0}','ACTIVE')");
    }

    private void createV5Tables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS hist_manifest (id INTEGER PRIMARY KEY CHECK(id=1),hist_contract_version TEXT NOT NULL,hist_schema_version TEXT NOT NULL,feature_schema_version TEXT NOT NULL,last_hist_date TEXT,status TEXT NOT NULL DEFAULT 'HIST_PENDING',updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE TABLE IF NOT EXISTS regional_profiles (id INTEGER PRIMARY KEY AUTOINCREMENT,region TEXT NOT NULL,window_key TEXT NOT NULL,sample_count INTEGER NOT NULL DEFAULT 0,profile_json TEXT NOT NULL,profile_version TEXT NOT NULL,as_of_date TEXT,updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,UNIQUE(region,window_key,profile_version))");
        db.execSQL("CREATE TABLE IF NOT EXISTS regional_profile_events (id INTEGER PRIMARY KEY AUTOINCREMENT,region TEXT NOT NULL,race_date TEXT NOT NULL,race_number INTEGER NOT NULL,event_json TEXT NOT NULL,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE TABLE IF NOT EXISTS rating_state (entity_type TEXT NOT NULL,entity_id TEXT NOT NULL,region TEXT NOT NULL DEFAULT 'GLOBAL',mu REAL NOT NULL DEFAULT 0,sigma REAL NOT NULL DEFAULT 1,rating_version TEXT NOT NULL,as_of_date TEXT,updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(entity_type,entity_id,region))");
        db.execSQL("CREATE TABLE IF NOT EXISTS rating_events (id INTEGER PRIMARY KEY AUTOINCREMENT,race_date TEXT NOT NULL,region TEXT NOT NULL,race_number INTEGER NOT NULL,entity_type TEXT NOT NULL,entity_id TEXT NOT NULL,before_json TEXT,after_json TEXT,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE TABLE IF NOT EXISTS track_bias_state (race_date TEXT NOT NULL,region TEXT NOT NULL,sample_count INTEGER NOT NULL DEFAULT 0,bias_json TEXT NOT NULL,bias_version TEXT NOT NULL,updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(race_date,region))");
        db.execSQL("CREATE TABLE IF NOT EXISTS blend_models (id INTEGER PRIMARY KEY AUTOINCREMENT,region TEXT NOT NULL,blend_version TEXT NOT NULL,coefficients_json TEXT NOT NULL,metrics_json TEXT NOT NULL,status TEXT NOT NULL,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,UNIQUE(region,blend_version))");
        db.execSQL("CREATE TABLE IF NOT EXISTS calibration_models (id INTEGER PRIMARY KEY AUTOINCREMENT,region TEXT NOT NULL,calibration_version TEXT NOT NULL,method TEXT NOT NULL,params_json TEXT NOT NULL,metrics_json TEXT NOT NULL,status TEXT NOT NULL,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,UNIQUE(region,calibration_version))");
        db.execSQL("CREATE TABLE IF NOT EXISTS champion_registry (id INTEGER PRIMARY KEY AUTOINCREMENT,region TEXT NOT NULL,model_version TEXT NOT NULL,alias TEXT NOT NULL,metrics_json TEXT NOT NULL,feature_schema_version TEXT NOT NULL,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,UNIQUE(region,alias))");
        db.execSQL("CREATE TABLE IF NOT EXISTS drift_events (id INTEGER PRIMARY KEY AUTOINCREMENT,region TEXT NOT NULL,race_date TEXT,detector TEXT NOT NULL,state TEXT NOT NULL,metrics_json TEXT NOT NULL,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE TABLE IF NOT EXISTS prediction_engine_outputs (id INTEGER PRIMARY KEY AUTOINCREMENT,analysis_record_id INTEGER NOT NULL,engine_name TEXT NOT NULL,output_json TEXT NOT NULL,engine_version TEXT NOT NULL,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_prediction_engine_outputs_record ON prediction_engine_outputs(analysis_record_id,engine_name)");
        db.execSQL("CREATE TABLE IF NOT EXISTS feature_snapshots (id INTEGER PRIMARY KEY AUTOINCREMENT,race_date TEXT NOT NULL,region TEXT NOT NULL,race_number INTEGER NOT NULL,entity_type TEXT NOT NULL,entity_id TEXT NOT NULL,event_timestamp TEXT,source_timestamp TEXT,availability_timestamp TEXT,feature_schema_version TEXT NOT NULL,feature_json TEXT NOT NULL,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_feature_snapshots_race ON feature_snapshots(race_date,region,race_number,entity_type)");
        db.execSQL("CREATE TABLE IF NOT EXISTS feature_materialization_log (id INTEGER PRIMARY KEY AUTOINCREMENT,context_version TEXT NOT NULL UNIQUE,race_date TEXT NOT NULL,region TEXT NOT NULL,race_number INTEGER NOT NULL,status TEXT NOT NULL,duration_ms REAL,detail_json TEXT,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE TABLE IF NOT EXISTS performance_metrics (id INTEGER PRIMARY KEY AUTOINCREMENT,metric_scope TEXT NOT NULL,region TEXT,metric_name TEXT NOT NULL,metric_value REAL,window_key TEXT,model_version TEXT,recorded_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE TABLE IF NOT EXISTS data_quality_events (id INTEGER PRIMARY KEY AUTOINCREMENT,race_date TEXT,region TEXT,race_number INTEGER,severity TEXT NOT NULL,code TEXT NOT NULL,detail_json TEXT,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("INSERT OR IGNORE INTO hist_manifest(id,hist_contract_version,hist_schema_version,feature_schema_version,status) VALUES(1,'HIST-CONTRACT-v2','HIST-SCHEMA-v2','FEATURE-v2','HIST_PENDING')");
        db.execSQL("INSERT OR IGNORE INTO champion_registry(region,model_version,alias,metrics_json,feature_schema_version) VALUES('GLOBAL','ML-v1-GLOBAL','CHAMPION','{}','FEATURE-v2')");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) createV2Tables(db);
        if (oldVersion < 3) createV3Tables(db);
        if (oldVersion < 4) {
            try { db.execSQL("ALTER TABLE analysis_records ADD COLUMN prediction_snapshot_json TEXT"); } catch (Exception ignored) {}
            createV4Tables(db);
        }
        if (oldVersion < 5) createV5Tables(db);
    }
}
