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

    public StorageBridge(Context context) {
        helper = new RaceDbHelper(context);
    }

    private String error(String message) {
        try {
            return new JSONObject().put("ok", false).put("error", message == null ? "오류" : message).toString();
        } catch (Exception ignored) {
            return "{\"ok\":false}";
        }
    }

    @JavascriptInterface
    public String saveAnalysis(String json) {
        SQLiteDatabase db = null;
        try {
            JSONObject p = new JSONObject(json);
            if (!"approved".equals(p.optString("status"))) return error("승인된 분석만 저장할 수 있습니다.");
            JSONObject result = p.optJSONObject("result");
            if (result == null || !result.optBoolean("ok", false)) return error("유효한 분석 결과가 없습니다.");
            String analysisVersion = p.optString("analysisVersion");
            if (analysisVersion.isEmpty()) return error("analysisVersion이 없습니다.");

            db = helper.getWritableDatabase();
            db.beginTransaction();
            long raceId = upsertRace(db, p);
            insertHorses(db, raceId, p.optJSONArray("horses"));
            insertPoolSnapshots(db, raceId, p.optJSONObject("pools"));
            insertSelectionMetrics(db, raceId, result.optJSONObject("poolResults"));
            insertHorseMetrics(db, raceId, result.optJSONObject("horseMetrics"));

            ContentValues rv = new ContentValues();
            rv.put("race_id", raceId);
            rv.put("analysis_version", analysisVersion);
            rv.put("config_version", result.optString("configVersion"));
            rv.put("payload_json", json);
            rv.put("approved", 1);
            rv.put("approved_at", String.valueOf(System.currentTimeMillis()));
            long resultId = db.insertOrThrow("analysis_results", null, rv);

            ContentValues hv = new ContentValues();
            hv.put("result_id", resultId);
            hv.put("action", "approve");
            hv.put("snapshot_data", json);
            db.insertOrThrow("analysis_history", null, hv);

            JSONObject snapshot = buildPredictionSnapshot(p, result);
            ContentValues legacy = new ContentValues();
            legacy.put("race_date", p.optString("raceDate"));
            legacy.put("region", p.optString("region"));
            legacy.put("race_number", p.optInt("raceNumber"));
            legacy.put("status", "approved");
            legacy.put("market_center", p.optInt("marketCenter"));
            legacy.put("late_money", p.optInt("lateMoney"));
            legacy.put("payload_json", json);
            legacy.put("prediction_snapshot_json", snapshot.toString());
            long id = db.insertOrThrow("analysis_records", null, legacy);

            saveTrainingShell(db, raceId, id, result);
            db.setTransactionSuccessful();
            return new JSONObject().put("ok", true).put("id", id).put("resultId", resultId).toString();
        } catch (Exception e) {
            return error(e.getMessage());
        } finally {
            if (db != null && db.inTransaction()) db.endTransaction();
        }
    }

    private JSONObject buildPredictionSnapshot(JSONObject p, JSONObject result) throws Exception {
        return new JSONObject()
                .put("raceDate", p.optString("raceDate"))
                .put("region", p.optString("region"))
                .put("raceNumber", p.optInt("raceNumber"))
                .put("analysisVersion", result.optString("analysisVersion"))
                .put("featureSchemaVersion", result.optString("featureSchemaVersion"))
                .put("contextVersion", p.optString("contextVersion"))
                .put("preRaceContext", p.optJSONObject("preRaceContext"))
                .put("featureVectors", result.optJSONObject("featureVectors"))
                .put("legacyPrediction", new JSONObject()
                        .put("rolePrediction", result.optJSONArray("rolePrediction"))
                        .put("oddsPrediction", result.optJSONArray("oddsPrediction"))
                        .put("finalCombinations", result.optJSONObject("finalCombinations")))
                .put("pegasusResult", p.optJSONObject("pegasusResult"));
    }

    private void saveTrainingShell(SQLiteDatabase db, long raceId, long id, JSONObject result) throws Exception {
        ContentValues v = new ContentValues();
        v.put("race_id", raceId);
        v.put("analysis_record_id", id);
        v.put("model_version", result.optString("modelVersion", "ML-v1-GLOBAL"));
        JSONObject features = result.optJSONObject("featureVectors");
        v.put("feature_snapshot_json", features == null ? "{}" : features.toString());
        JSONObject pred = new JSONObject()
                .put("rolePrediction", result.optJSONArray("rolePrediction"))
                .put("oddsPrediction", result.optJSONArray("oddsPrediction"))
                .put("finalCombinations", result.optJSONObject("finalCombinations"));
        v.put("prediction_json", pred.toString());
        db.insertWithOnConflict("ml_training_examples", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    @JavascriptInterface
    public String savePredictionSnapshot(long id, String snapshotJson) {
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            ContentValues v = new ContentValues();
            v.put("prediction_snapshot_json", new JSONObject(snapshotJson).toString());
            int n = db.update("analysis_records", v, "id=? AND prediction_snapshot_json IS NULL", new String[]{String.valueOf(id)});
            return new JSONObject().put("ok", n == 1).put("immutable", true).toString();
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    private JSONObject versionContract(SQLiteDatabase db) throws Exception {
        JSONObject out = new JSONObject()
                .put("histContractVersion", "HIST-CONTRACT-v2")
                .put("histSchemaVersion", "HIST-SCHEMA-v2")
                .put("featureSchemaVersion", "FEATURE-v2")
                .put("regionalProfileVersion", "REGIONAL-v1")
                .put("ratingVersion", "RATING-v1")
                .put("trackBiasVersion", "TRACK-v1")
                .put("marketVersion", "MARKET-v2")
                .put("blendVersion", "BLEND-v1")
                .put("calibrationVersion", "CAL-v1")
                .put("orderedFinishVersion", "ORDERED-v1");
        try (Cursor c = db.rawQuery("SELECT hist_contract_version,hist_schema_version,feature_schema_version,last_hist_date,status,updated_at FROM hist_manifest WHERE id=1", null)) {
            if (c.moveToFirst()) {
                out.put("histContractVersion", c.getString(0))
                        .put("histSchemaVersion", c.getString(1))
                        .put("featureSchemaVersion", c.getString(2))
                        .put("lastHistDate", c.isNull(3) ? JSONObject.NULL : c.getString(3))
                        .put("histStatus", c.getString(4))
                        .put("histUpdatedAt", c.getString(5));
            } else {
                out.put("histStatus", "HIST_PENDING");
            }
        }
        return out;
    }

    @JavascriptInterface
    public String getVersionContract() {
        try {
            SQLiteDatabase db = helper.getReadableDatabase();
            return new JSONObject().put("ok", true).put("versions", versionContract(db)).toString();
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    private JSONObject regionalProfile(SQLiteDatabase db, String targetDate, String region) throws Exception {
        JSONArray outcomes = new JSONArray();
        String latest = null;
        try (Cursor c = db.rawQuery(
                "SELECT race_date,result_json FROM race_outcomes WHERE region=? AND race_date<? ORDER BY race_date DESC,race_number DESC LIMIT 2000",
                new String[]{region, targetDate})) {
            while (c.moveToNext()) {
                if (latest == null) latest = c.getString(0);
                outcomes.put(new JSONObject(c.getString(1)));
            }
        }
        int total = outcomes.length();
        JSONObject all = profileWindow(outcomes, total);
        JSONObject recent100 = profileWindow(outcomes, Math.min(100, total));
        JSONObject recent20 = profileWindow(outcomes, Math.min(20, total));
        return new JSONObject()
                .put("region", region)
                .put("sampleCount", total)
                .put("all", all)
                .put("recent100", recent100)
                .put("recent20", recent20)
                .put("asOfDate", latest == null ? JSONObject.NULL : latest)
                .put("status", total == 0 ? "NO_LOCAL_OUTCOME_DATA" : (total < 20 ? "LOW_SAMPLE" : "READY"))
                .put("profileVersion", "REGIONAL-v1");
    }

    private JSONObject profileWindow(JSONArray outcomes, int limit) throws Exception {
        int count = Math.min(limit, outcomes.length());
        int favoriteWins = 0, exactaCount = 0, trifectaCount = 0;
        double exactaSum = 0, trifectaSum = 0;
        for (int i = 0; i < count; i++) {
            JSONObject r = outcomes.optJSONObject(i);
            if (r == null) continue;
            JSONArray f = r.optJSONArray("finishers");
            if (f != null && f.length() > 0) {
                JSONObject first = f.optJSONObject(0);
                if (first != null) {
                    int pop = first.optInt("popularity", first.optInt("popularityRank", 0));
                    if (pop == 1) favoriteWins++;
                }
            }
            JSONObject p = r.optJSONObject("payouts");
            if (p != null) {
                JSONObject e = p.optJSONObject("EXACTA");
                if (e != null && Double.isFinite(e.optDouble("odds", Double.NaN))) {
                    exactaSum += e.optDouble("odds"); exactaCount++;
                }
                JSONObject t = p.optJSONObject("TRIFECTA");
                if (t != null && Double.isFinite(t.optDouble("odds", Double.NaN))) {
                    trifectaSum += t.optDouble("odds"); trifectaCount++;
                }
            }
        }
        return new JSONObject()
                .put("count", count)
                .put("favoriteWinRate", count == 0 ? JSONObject.NULL : (double) favoriteWins / count)
                .put("meanExactaOdds", exactaCount == 0 ? JSONObject.NULL : exactaSum / exactaCount)
                .put("meanTrifectaOdds", trifectaCount == 0 ? JSONObject.NULL : trifectaSum / trifectaCount);
    }

    @JavascriptInterface
    public String getRegionalProfile(String targetDate, String region) {
        long start = System.nanoTime();
        try {
            SQLiteDatabase db = helper.getReadableDatabase();
            return new JSONObject().put("ok", true)
                    .put("profile", regionalProfile(db, targetDate, region))
                    .put("durationMs", (System.nanoTime() - start) / 1_000_000.0).toString();
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    private JSONObject activeChampion(SQLiteDatabase db, String region) throws Exception {
        try (Cursor c = db.rawQuery(
                "SELECT model_version,metrics_json,feature_schema_version,created_at FROM champion_registry WHERE region IN (?, 'GLOBAL') AND alias='CHAMPION' ORDER BY CASE WHEN region=? THEN 0 ELSE 1 END,id DESC LIMIT 1",
                new String[]{region, region})) {
            if (!c.moveToFirst()) return new JSONObject().put("modelVersion", "BASELINE").put("status", "FALLBACK");
            return new JSONObject()
                    .put("modelVersion", c.getString(0))
                    .put("metrics", new JSONObject(c.getString(1)))
                    .put("featureSchemaVersion", c.getString(2))
                    .put("createdAt", c.getString(3))
                    .put("status", "ACTIVE");
        }
    }

    private JSONObject currentTrackBias(SQLiteDatabase db, String date, String region) throws Exception {
        try (Cursor c = db.rawQuery(
                "SELECT sample_count,bias_json,bias_version,updated_at FROM track_bias_state WHERE race_date=? AND region=? LIMIT 1",
                new String[]{date, region})) {
            if (!c.moveToFirst()) return new JSONObject()
                    .put("sampleCount", 0).put("bias", new JSONObject())
                    .put("biasVersion", "TRACK-v1").put("status", "NO_SAME_DAY_SAMPLE");
            return new JSONObject()
                    .put("sampleCount", c.getInt(0))
                    .put("bias", new JSONObject(c.getString(1)))
                    .put("biasVersion", c.getString(2))
                    .put("updatedAt", c.getString(3))
                    .put("status", c.getInt(0) < 3 ? "LOW_SAMPLE" : "READY");
        }
    }

    @JavascriptInterface
    public String getPreRaceContext(String date, String region, int raceNo, String horsesJson) {
        long start = System.nanoTime();
        try {
            SQLiteDatabase db = helper.getReadableDatabase();
            JSONObject versions = versionContract(db);
            JSONObject regional = regionalProfile(db, date, region);
            JSONObject champion = activeChampion(db, region);
            JSONObject track = currentTrackBias(db, date, region);
            JSONArray horses;
            try { horses = new JSONArray(horsesJson == null ? "[]" : horsesJson); }
            catch (Exception ignored) { horses = new JSONArray(); }

            int ratingRows = count(db, "SELECT COUNT(*) FROM rating_state WHERE region IN (?, 'GLOBAL')", new String[]{region});
            JSONObject freshness = new JSONObject()
                    .put("hist", versions.opt("lastHistDate"))
                    .put("regional", regional.opt("asOfDate"))
                    .put("ratingRows", ratingRows)
                    .put("trackSamples", track.optInt("sampleCount", 0))
                    .put("histStatus", versions.optString("histStatus", "HIST_PENDING"));

            String contextVersion = "CTX-" + date.replace("-", "") + "-" +
                    region.replace("부산경남", "BUSAN").replace("서울", "SEOUL").replace("제주", "JEJU") +
                    "-R" + raceNo + "-v1";

            JSONObject out = new JSONObject()
                    .put("ok", true)
                    .put("contextVersion", contextVersion)
                    .put("raceDate", date).put("region", region).put("raceNo", raceNo)
                    .put("runnerCount", horses.length())
                    .put("versions", versions)
                    .put("regionalProfile", regional)
                    .put("historicalPrior", new JSONObject()
                            .put("status", versions.optString("histStatus", "HIST_PENDING"))
                            .put("similarRaceCount", 0)
                            .put("note", "5년 Hist 인덱스 연결 전에는 로컬 검증결과만 사용"))
                    .put("ratingState", new JSONObject()
                            .put("status", ratingRows == 0 ? "RATING_PENDING" : "AVAILABLE")
                            .put("rows", ratingRows))
                    .put("trackBias", track)
                    .put("champion", champion)
                    .put("dataFreshness", freshness)
                    .put("uncertainty", new JSONObject()
                            .put("level", regional.optInt("sampleCount", 0) < 20 ? "HIGH" : "MID")
                            .put("reason", regional.optInt("sampleCount", 0) < 20 ? "지역 실전 표본 부족" : "지역 표본 존재"))
                    .put("durationMs", (System.nanoTime() - start) / 1_000_000.0);
            return out.toString();
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @JavascriptInterface
    public String getActiveModel(String region) {
        try {
            SQLiteDatabase db = helper.getReadableDatabase();
            JSONObject global = queryModel(db, "GLOBAL");
            JSONObject regional = "GLOBAL".equals(region) ? null : queryModel(db, region);
            return new JSONObject().put("ok", true).put("global", global == null ? JSONObject.NULL : global)
                    .put("regional", regional == null ? JSONObject.NULL : regional).toString();
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    private JSONObject queryModel(SQLiteDatabase db, String region) throws Exception {
        String sql = "SELECT model_version,region,sample_count,weights_json,metrics_json,status,created_at FROM ml_models WHERE region=? AND status='ACTIVE' ORDER BY id DESC LIMIT 1";
        try (Cursor c = db.rawQuery(sql, new String[]{region})) {
            if (!c.moveToFirst()) return null;
            return new JSONObject()
                    .put("modelVersion", c.getString(0)).put("region", c.getString(1))
                    .put("sampleCount", c.getInt(2)).put("weights", new JSONObject(c.getString(3)))
                    .put("metrics", new JSONObject(c.getString(4))).put("status", c.getString(5))
                    .put("createdAt", c.getString(6));
        }
    }

    private JSONObject queryLearningModel(SQLiteDatabase db, String region) throws Exception {
        String sql = "SELECT model_version,region,sample_count,weights_json,metrics_json,status,created_at FROM ml_models WHERE region=? AND status IN ('ACTIVE','TRAINING','CANDIDATE') ORDER BY id DESC LIMIT 1";
        try (Cursor c = db.rawQuery(sql, new String[]{region})) {
            if (!c.moveToFirst()) return null;
            return new JSONObject()
                    .put("modelVersion", c.getString(0)).put("region", c.getString(1))
                    .put("sampleCount", c.getInt(2)).put("weights", new JSONObject(c.getString(3)))
                    .put("metrics", new JSONObject(c.getString(4))).put("status", c.getString(5))
                    .put("createdAt", c.getString(6));
        }
    }

    @JavascriptInterface
    public String getLearningState(String region) {
        try {
            SQLiteDatabase db = helper.getReadableDatabase();
            JSONObject globalTraining = queryLearningModel(db, "GLOBAL");
            JSONObject regionalTraining = "GLOBAL".equals(region) ? null : queryLearningModel(db, region);
            return new JSONObject().put("ok", true)
                    .put("globalTraining", globalTraining == null ? JSONObject.NULL : globalTraining)
                    .put("regionalTraining", regionalTraining == null ? JSONObject.NULL : regionalTraining)
                    .toString();
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    private JSONObject rollingMetrics(SQLiteDatabase db, int limit) throws Exception {
        String sql = "SELECT e.target_json FROM ml_training_examples e WHERE e.trained_at IS NOT NULL ORDER BY e.id DESC LIMIT ?";
        JSONObject sums = new JSONObject();
        int count = 0;
        try (Cursor c = db.rawQuery(sql, new String[]{String.valueOf(limit)})) {
            while (c.moveToNext()) {
                JSONObject j = new JSONObject(c.isNull(0) ? "{}" : c.getString(0));
                Iterator<String> keys = j.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    if (!j.has(k)) continue;
                    double v = j.optDouble(k, Double.NaN);
                    if (Double.isFinite(v)) sums.put(k, sums.optDouble(k, 0) + v);
                }
                count++;
            }
        }
        JSONObject out = new JSONObject().put("count", count);
        Iterator<String> keys = sums.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            out.put(k, count == 0 ? 0 : sums.optDouble(k, 0) / count);
        }
        return out;
    }

    @JavascriptInterface
    public String getMlStatus(String region) {
        long start = System.nanoTime();
        try {
            SQLiteDatabase db = helper.getReadableDatabase();
            JSONObject global = queryModel(db, "GLOBAL");
            JSONObject regional = queryModel(db, region);
            JSONObject globalTraining = queryLearningModel(db, "GLOBAL");
            JSONObject regionalTraining = queryLearningModel(db, region);
            int examples = count(db, "SELECT COUNT(*) FROM ml_training_examples WHERE trained_at IS NOT NULL", null);
            int regionalModels = count(db, "SELECT COUNT(*) FROM ml_models WHERE region=?", new String[]{region});
            JSONObject last = null;
            try (Cursor c = db.rawQuery("SELECT model_from,model_to,weight_delta_json,before_metrics_json,after_metrics_json,promoted,reason,created_at FROM ml_training_events ORDER BY id DESC LIMIT 1", null)) {
                if (c.moveToFirst()) {
                    last = new JSONObject().put("modelFrom", c.getString(0)).put("modelTo", c.getString(1))
                            .put("weightDelta", new JSONObject(c.isNull(2) ? "{}" : c.getString(2)))
                            .put("beforeMetrics", new JSONObject(c.isNull(3) ? "{}" : c.getString(3)))
                            .put("afterMetrics", new JSONObject(c.isNull(4) ? "{}" : c.getString(4)))
                            .put("promoted", c.getInt(5) == 1).put("reason", c.getString(6)).put("createdAt", c.getString(7));
                }
            }
            JSONObject cumulative = globalTraining == null ? new JSONObject().put("verified", examples) : globalTraining.optJSONObject("metrics");
            return new JSONObject().put("ok", true)
                    .put("global", global == null ? JSONObject.NULL : global)
                    .put("regional", regional == null ? JSONObject.NULL : regional)
                    .put("globalTraining", globalTraining == null ? JSONObject.NULL : globalTraining)
                    .put("regionalTraining", regionalTraining == null ? JSONObject.NULL : regionalTraining)
                    .put("trainedExamples", examples).put("regionalModels", regionalModels)
                    .put("rolling20", rollingMetrics(db, 20))
                    .put("cumulative", cumulative == null ? new JSONObject() : cumulative)
                    .put("lastEvent", last == null ? JSONObject.NULL : last)
                    .put("durationMs", (System.nanoTime() - start) / 1_000_000.0).toString();
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    private int count(SQLiteDatabase db, String sql, String[] args) {
        try (Cursor c = db.rawQuery(sql, args)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    private void persistTrainingCandidate(SQLiteDatabase db, long raceId, long analysisId, JSONObject training) throws Exception {
        if (training == null) return;
        JSONObject candidate = training.optJSONObject("candidate");
        if (candidate == null) return;
        String from = training.optString("modelFrom", candidate.optString("modelVersion"));
        String to = candidate.optString("modelVersion", from);
        boolean promoted = training.optBoolean("promoted", false);
        String status = candidate.optString("status", promoted ? "ACTIVE" : "TRAINING");

        ContentValues ev = new ContentValues();
        ev.put("model_from", from); ev.put("model_to", to); ev.put("race_id", raceId); ev.put("analysis_record_id", analysisId);
        JSONObject delta = training.optJSONObject("weightDelta");
        ev.put("weight_delta_json", delta == null ? "{}" : delta.toString());
        JSONObject before = training.optJSONObject("beforeMetrics");
        ev.put("before_metrics_json", before == null ? "{}" : before.toString());
        JSONObject after = candidate.optJSONObject("metrics");
        ev.put("after_metrics_json", after == null ? "{}" : after.toString());
        ev.put("promoted", promoted ? 1 : 0); ev.put("reason", training.optString("promotionReason"));
        db.insertOrThrow("ml_training_events", null, ev);

        ContentValues mv = new ContentValues();
        mv.put("model_version", to); mv.put("region", candidate.optString("region", "GLOBAL"));
        mv.put("sample_count", candidate.optInt("sampleCount"));
        mv.put("weights_json", candidate.optJSONObject("weights") == null ? "{}" : candidate.optJSONObject("weights").toString());
        mv.put("metrics_json", after == null ? "{}" : after.toString());
        mv.put("status", "REJECTED".equals(status) ? "ROLLED_BACK" : status);
        db.insertWithOnConflict("ml_models", null, mv, SQLiteDatabase.CONFLICT_IGNORE);
        if (promoted) {
            db.execSQL("UPDATE ml_models SET status='ROLLED_BACK' WHERE region=? AND status='ACTIVE' AND model_version<>?",
                    new Object[]{candidate.optString("region", "GLOBAL"), to});
            db.execSQL("UPDATE ml_models SET status='ACTIVE' WHERE model_version=?", new Object[]{to});
        }
    }

    @JavascriptInterface
    public String recordTrainingBundle(long analysisId, String trainingJson) {
        SQLiteDatabase db = null;
        long start = System.nanoTime();
        try {
            JSONObject bundle = new JSONObject(trainingJson);
            db = helper.getWritableDatabase();
            db.beginTransaction();
            long raceId;
            try (Cursor c = db.rawQuery("SELECT r.id FROM analysis_records a LEFT JOIN races r ON r.race_date=a.race_date AND r.region=a.region AND r.race_number=a.race_number WHERE a.id=?",
                    new String[]{String.valueOf(analysisId)})) {
                if (!c.moveToFirst()) return error("분석 기록 없음");
                raceId = c.isNull(0) ? 0 : c.getLong(0);
            }
            try (Cursor c = db.rawQuery("SELECT actual_result_json FROM ml_training_examples WHERE analysis_record_id=?",
                    new String[]{String.valueOf(analysisId)})) {
                if (c.moveToFirst() && !c.isNull(0)) return error("중복 학습 경주");
            }

            JSONObject actual = bundle.optJSONObject("actual");
            JSONObject evaluation = bundle.optJSONObject("evaluation");
            ContentValues ex = new ContentValues();
            ex.put("actual_result_json", actual == null ? "{}" : actual.toString());
            ex.put("target_json", evaluation == null ? "{}" : evaluation.toString());
            ex.put("error_json", "{}");
            ex.put("trained_at", String.valueOf(System.currentTimeMillis()));
            db.update("ml_training_examples", ex, "analysis_record_id=?", new String[]{String.valueOf(analysisId)});

            persistTrainingCandidate(db, raceId, analysisId, bundle.optJSONObject("globalTraining"));
            persistTrainingCandidate(db, raceId, analysisId, bundle.optJSONObject("regionalTraining"));
            db.setTransactionSuccessful();
            return new JSONObject().put("ok", true)
                    .put("durationMs", (System.nanoTime() - start) / 1_000_000.0)
                    .put("globalPromoted", bundle.optJSONObject("globalTraining") != null && bundle.optJSONObject("globalTraining").optBoolean("promoted"))
                    .put("regionalPromoted", bundle.optJSONObject("regionalTraining") != null && bundle.optJSONObject("regionalTraining").optBoolean("promoted"))
                    .toString();
        } catch (Exception e) {
            return error(e.getMessage());
        } finally {
            if (db != null && db.inTransaction()) db.endTransaction();
        }
    }

    @JavascriptInterface
    public String recordTrainingEvent(long analysisId, String trainingJson) {
        SQLiteDatabase db = null;
        try {
            JSONObject t = new JSONObject(trainingJson);
            db = helper.getWritableDatabase();
            db.beginTransaction();

            long raceId;
            String region;
            try (Cursor c = db.rawQuery("SELECT r.id,a.region FROM analysis_records a LEFT JOIN races r ON r.race_date=a.race_date AND r.region=a.region AND r.race_number=a.race_number WHERE a.id=?", new String[]{String.valueOf(analysisId)})) {
                if (!c.moveToFirst()) return error("분석 기록 없음");
                raceId = c.isNull(0) ? 0 : c.getLong(0);
                region = c.getString(1);
            }
            try (Cursor c = db.rawQuery("SELECT actual_result_json FROM ml_training_examples WHERE analysis_record_id=?", new String[]{String.valueOf(analysisId)})) {
                if (c.moveToFirst() && !c.isNull(0)) return error("중복 학습 경주");
            }

            JSONObject candidate = t.optJSONObject("candidate");
            JSONObject delta = t.optJSONObject("weightDelta");
            JSONObject evaluation = t.optJSONObject("evaluation");
            JSONObject actual = t.optJSONObject("actual");

            ContentValues ex = new ContentValues();
            ex.put("actual_result_json", actual == null ? "{}" : actual.toString());
            ex.put("target_json", evaluation == null ? "{}" : evaluation.toString());
            ex.put("error_json", t.optString("errorJson", "{}"));
            ex.put("trained_at", String.valueOf(System.currentTimeMillis()));
            db.update("ml_training_examples", ex, "analysis_record_id=?", new String[]{String.valueOf(analysisId)});

            String from = t.optString("modelFrom", "ML-v1-GLOBAL");
            String to = candidate == null ? t.optString("modelTo", from) : candidate.optString("modelVersion", from);
            boolean promoted = t.optBoolean("promoted", false);

            ContentValues ev = new ContentValues();
            ev.put("model_from", from); ev.put("model_to", to); ev.put("race_id", raceId); ev.put("analysis_record_id", analysisId);
            ev.put("weight_delta_json", delta == null ? "{}" : delta.toString());
            ev.put("before_metrics_json", t.optJSONObject("beforeMetrics") == null ? "{}" : t.optJSONObject("beforeMetrics").toString());
            ev.put("after_metrics_json", candidate == null || candidate.optJSONObject("metrics") == null ? "{}" : candidate.optJSONObject("metrics").toString());
            ev.put("promoted", promoted ? 1 : 0); ev.put("reason", t.optString("promotionReason"));
            db.insertOrThrow("ml_training_events", null, ev);

            if (candidate != null) {
                ContentValues mv = new ContentValues();
                mv.put("model_version", to); mv.put("region", candidate.optString("region", "GLOBAL"));
                mv.put("sample_count", candidate.optInt("sampleCount"));
                mv.put("weights_json", candidate.optJSONObject("weights") == null ? "{}" : candidate.optJSONObject("weights").toString());
                mv.put("metrics_json", candidate.optJSONObject("metrics") == null ? "{}" : candidate.optJSONObject("metrics").toString());
                mv.put("status", promoted ? "ACTIVE" : "CANDIDATE");
                db.insertWithOnConflict("ml_models", null, mv, SQLiteDatabase.CONFLICT_IGNORE);
                if (promoted) {
                    db.execSQL("UPDATE ml_models SET status='ROLLED_BACK' WHERE region=? AND status='ACTIVE' AND model_version<>?", new Object[]{candidate.optString("region", "GLOBAL"), to});
                    db.execSQL("UPDATE ml_models SET status='ACTIVE' WHERE model_version=?", new Object[]{to});
                }
            }
            db.setTransactionSuccessful();
            return new JSONObject().put("ok", true).put("modelVersion", to).put("promoted", promoted).put("region", region).toString();
        } catch (Exception e) {
            return error(e.getMessage());
        } finally {
            if (db != null && db.inTransaction()) db.endTransaction();
        }
    }

    private long upsertRace(SQLiteDatabase db, JSONObject p) throws Exception {
        ContentValues v = new ContentValues();
        v.put("race_date", p.optString("raceDate")); v.put("region", p.optString("region")); v.put("race_number", p.optInt("raceNumber"));
        db.insertWithOnConflict("races", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        try (Cursor c = db.rawQuery("SELECT id FROM races WHERE race_date=? AND region=? AND race_number=?", new String[]{p.optString("raceDate"), p.optString("region"), String.valueOf(p.optInt("raceNumber"))})) {
            if (!c.moveToFirst()) throw new Exception("경주 레코드 생성 실패");
            return c.getLong(0);
        }
    }

    private void insertHorses(SQLiteDatabase db, long raceId, JSONArray horses) throws Exception {
        if (horses == null) return;
        for (int i = 0; i < horses.length(); i++) {
            JSONObject h = horses.optJSONObject(i); if (h == null) continue;
            ContentValues v = new ContentValues();
            v.put("race_id", raceId); v.put("horse_number", h.optInt("number")); v.put("horse_name", h.optString("name"));
            v.put("record_text", h.optString("record")); v.put("trainer", h.optString("trainer")); v.put("jockey", h.optString("jockey"));
            v.put("expert", h.optString("expert")); v.put("popularity", h.optString("popularity"));
            db.insertWithOnConflict("horses", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    private void insertPoolSnapshots(SQLiteDatabase db, long raceId, JSONObject pools) throws Exception {
        if (pools == null) return;
        Iterator<String> keys = pools.keys();
        while (keys.hasNext()) {
            String poolType = keys.next(); JSONObject pool = pools.optJSONObject(poolType); if (pool == null) continue;
            for (String snap : new String[]{"T20", "T5"}) {
                JSONArray rows = pool.optJSONArray(snap); if (rows == null) continue;
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject r = rows.optJSONObject(i); if (r == null) continue;
                    double odds = r.optDouble("odds", Double.NaN); if (!Double.isFinite(odds) || odds <= 0) continue;
                    ContentValues v = new ContentValues();
                    v.put("race_id", raceId); v.put("pool_type", poolType); v.put("snapshot_type", snap); v.put("selection_key", r.optString("key")); v.put("odds", odds);
                    db.insertOrThrow("pool_snapshots", null, v);
                }
            }
        }
    }

    private void insertSelectionMetrics(SQLiteDatabase db, long raceId, JSONObject poolResults) throws Exception {
        if (poolResults == null) return;
        Iterator<String> keys = poolResults.keys();
        while (keys.hasNext()) {
            String poolType = keys.next(); JSONObject pr = poolResults.optJSONObject(poolType);
            if (pr == null || !"OK".equals(pr.optString("status"))) continue;
            JSONArray c = pr.optJSONArray("candidates"); if (c == null) continue;
            for (int i = 0; i < c.length(); i++) {
                JSONObject x = c.optJSONObject(i); if (x == null) continue; JSONObject e = x.optJSONObject("evidence");
                ContentValues v = new ContentValues(); v.put("race_id", raceId); v.put("pool_type", poolType); v.put("selection_key", x.optString("key"));
                if (e != null && e.has("lmi")) v.put("lmi", e.optDouble("lmi")); v.put("score", x.optDouble("score")); v.put("evidence_json", e == null ? "{}" : e.toString());
                db.insertOrThrow("selection_metrics", null, v);
            }
        }
    }

    private void insertHorseMetrics(SQLiteDatabase db, long raceId, JSONObject horseMetrics) throws Exception {
        if (horseMetrics == null) return;
        Iterator<String> keys = horseMetrics.keys();
        while (keys.hasNext()) {
            String k = keys.next(); JSONObject m = horseMetrics.optJSONObject(k); if (m == null) continue;
            ContentValues v = new ContentValues(); v.put("race_id", raceId); v.put("horse_number", Integer.parseInt(k));
            v.put("divergence_score", m.optDouble("divergence", 0)); v.put("role_json", m.toString());
            db.insertOrThrow("horse_metrics", null, v);
        }
    }

    @JavascriptInterface
    public String listAnalyses() {
        JSONArray arr = new JSONArray(); SQLiteDatabase db = helper.getReadableDatabase();
        String sql = "SELECT a.id,a.race_date,a.region,a.race_number,a.status,a.market_center,a.late_money,a.created_at,CASE WHEN o.id IS NULL THEN 0 ELSE 1 END FROM analysis_records a LEFT JOIN race_outcomes o ON o.analysis_record_id=a.id ORDER BY a.id DESC LIMIT 200";
        try (Cursor c = db.rawQuery(sql, null)) {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject().put("id", c.getLong(0)).put("raceDate", c.getString(1)).put("region", c.getString(2))
                        .put("raceNumber", c.getInt(3)).put("status", c.getString(4)).put("marketCenter", c.getInt(5))
                        .put("lateMoney", c.getInt(6)).put("createdAt", c.getString(7)).put("hasOutcome", c.getInt(8) == 1);
                arr.put(o);
            }
        } catch (Exception ignored) {}
        return arr.toString();
    }

    @JavascriptInterface
    public String getAnalysis(long id) {
        long start = System.nanoTime(); SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT a.payload_json,a.prediction_snapshot_json,o.result_json FROM analysis_records a LEFT JOIN race_outcomes o ON o.analysis_record_id=a.id WHERE a.id=? LIMIT 1", new String[]{String.valueOf(id)})) {
            if (!c.moveToFirst()) return error("저장 분석을 찾지 못했습니다.");
            JSONObject p = new JSONObject(c.getString(0));
            if (!c.isNull(1)) p.put("predictionSnapshot", new JSONObject(c.getString(1)));
            if (!c.isNull(2)) p.put("postRaceResult", new JSONObject(c.getString(2)));
            return new JSONObject().put("ok", true).put("loadMs", (System.nanoTime() - start) / 1_000_000.0).put("id", id).put("payload", p).toString();
        } catch (Exception e) { return error(e.getMessage()); }
    }

    @JavascriptInterface
    public String attachRaceResult(long id, String resultJson) {
        SQLiteDatabase db = null;
        try {
            JSONObject result = new JSONObject(resultJson);
            if (!result.optBoolean("ok", false) || !result.optBoolean("verified", false)) return error("검증된 경주결과가 아닙니다.");
            JSONArray finishers = result.optJSONArray("finishers");
            if (finishers == null || finishers.length() < 3) return error("실제 1·2·3착이 필요합니다.");
            db = helper.getWritableDatabase(); db.beginTransaction();
            String raceDate, region; int raceNumber;
            try (Cursor c = db.rawQuery("SELECT race_date,region,race_number FROM analysis_records WHERE id=?", new String[]{String.valueOf(id)})) {
                if (!c.moveToFirst()) return error("저장 분석을 찾지 못했습니다.");
                raceDate = c.getString(0); region = c.getString(1); raceNumber = c.getInt(2);
            }
            if (!raceDate.equals(result.optString("date")) || raceNumber != result.optInt("raceNo") || !region.equals(result.optString("region"))) return error("저장 분석과 경주결과가 일치하지 않습니다.");
            ContentValues v = new ContentValues(); v.put("analysis_record_id", id); v.put("race_date", raceDate); v.put("region", region); v.put("race_number", raceNumber); v.put("result_json", result.toString());
            db.insertWithOnConflict("race_outcomes", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
            return new JSONObject().put("ok", true).put("id", id).put("predictionImmutable", true).toString();
        } catch (Exception e) { return error(e.getMessage()); }
        finally { if (db != null && db.inTransaction()) db.endTransaction(); }
    }

    private JSONObject regionalProfileThrough(SQLiteDatabase db, String date, String region) throws Exception {
        JSONArray outcomes = new JSONArray();
        String latest = null;
        try (Cursor c = db.rawQuery(
                "SELECT race_date,result_json FROM race_outcomes WHERE region=? AND race_date<=? ORDER BY race_date DESC,race_number DESC LIMIT 2000",
                new String[]{region, date})) {
            while (c.moveToNext()) {
                if (latest == null) latest = c.getString(0);
                outcomes.put(new JSONObject(c.getString(1)));
            }
        }
        int total = outcomes.length();
        return new JSONObject()
                .put("region", region).put("sampleCount", total)
                .put("all", profileWindow(outcomes, total))
                .put("recent100", profileWindow(outcomes, Math.min(100, total)))
                .put("recent20", profileWindow(outcomes, Math.min(20, total)))
                .put("asOfDate", latest == null ? JSONObject.NULL : latest)
                .put("status", total < 20 ? "LOW_SAMPLE" : "READY")
                .put("profileVersion", "REGIONAL-v1");
    }

    private void updateRegionalProfileState(SQLiteDatabase db, String date, String region, int raceNo, JSONObject result) throws Exception {
        ContentValues ev = new ContentValues();
        ev.put("region", region); ev.put("race_date", date); ev.put("race_number", raceNo);
        ev.put("event_json", result.toString());
        db.insert("regional_profile_events", null, ev);

        JSONObject profile = regionalProfileThrough(db, date, region);
        for (String window : new String[]{"ALL","RECENT100","RECENT20"}) {
            JSONObject body = "ALL".equals(window) ? profile.optJSONObject("all") :
                    ("RECENT100".equals(window) ? profile.optJSONObject("recent100") : profile.optJSONObject("recent20"));
            ContentValues v = new ContentValues();
            v.put("region", region); v.put("window_key", window);
            v.put("sample_count", body == null ? 0 : body.optInt("count"));
            v.put("profile_json", body == null ? "{}" : body.toString());
            v.put("profile_version", "REGIONAL-v1"); v.put("as_of_date", date);
            db.insertWithOnConflict("regional_profiles", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    private JSONObject currentRating(SQLiteDatabase db, String type, String entity, String region) throws Exception {
        try (Cursor c = db.rawQuery("SELECT mu,sigma FROM rating_state WHERE entity_type=? AND entity_id=? AND region=? LIMIT 1",
                new String[]{type, entity, region})) {
            if (c.moveToFirst()) return new JSONObject().put("mu", c.getDouble(0)).put("sigma", c.getDouble(1));
        }
        return new JSONObject().put("mu", 0.0).put("sigma", 1.0);
    }

    private void updateOneRating(SQLiteDatabase db, String date, String region, int raceNo, String type, String entity, double target) throws Exception {
        if (entity == null || entity.trim().isEmpty()) return;
        JSONObject before = currentRating(db, type, entity, region);
        double oldMu = before.optDouble("mu", 0.0), oldSigma = before.optDouble("sigma", 1.0);
        double delta = 0.08 * (target - 0.5);
        double newMu = oldMu + delta;
        double newSigma = Math.max(0.20, oldSigma * 0.995);
        ContentValues v = new ContentValues();
        v.put("entity_type", type); v.put("entity_id", entity); v.put("region", region);
        v.put("mu", newMu); v.put("sigma", newSigma); v.put("rating_version", "RATING-INCREMENTAL-v1"); v.put("as_of_date", date);
        db.insertWithOnConflict("rating_state", null, v, SQLiteDatabase.CONFLICT_REPLACE);

        ContentValues ev = new ContentValues();
        ev.put("race_date", date); ev.put("region", region); ev.put("race_number", raceNo);
        ev.put("entity_type", type); ev.put("entity_id", entity);
        ev.put("before_json", before.toString());
        ev.put("after_json", new JSONObject().put("mu", newMu).put("sigma", newSigma).put("delta", delta).toString());
        db.insert("rating_events", null, ev);
    }

    private void updateRatingState(SQLiteDatabase db, String date, String region, int raceNo, JSONObject payload, JSONObject result) throws Exception {
        JSONArray horses = payload.optJSONArray("horses"), finishers = result.optJSONArray("finishers");
        if (horses == null || finishers == null || finishers.length() < 1) return;
        JSONObject rankByNumber = new JSONObject();
        for (int i = 0; i < finishers.length(); i++) {
            JSONObject x = finishers.optJSONObject(i); if (x == null) continue;
            rankByNumber.put(String.valueOf(x.optInt("number")), x.optInt("rank", i + 1));
        }
        int field = Math.max(2, horses.length());
        for (int i = 0; i < horses.length(); i++) {
            JSONObject h = horses.optJSONObject(i); if (h == null) continue;
            int rank = rankByNumber.optInt(String.valueOf(h.optInt("number")), field);
            double target = 1.0 - ((double)Math.max(0, rank - 1) / Math.max(1, field - 1));
            updateOneRating(db, date, region, raceNo, "HORSE", h.optString("name"), target);
            updateOneRating(db, date, region, raceNo, "JOCKEY", h.optString("jockey"), target);
            updateOneRating(db, date, region, raceNo, "TRAINER", h.optString("trainer"), target);
        }
    }

    private JSONObject recordDriftState(SQLiteDatabase db, String date, String region) throws Exception {
        int samples = count(db, "SELECT COUNT(*) FROM race_outcomes WHERE region=? AND race_date<=?", new String[]{region, date});
        String state = samples < 20 ? "INSUFFICIENT_SAMPLE" : "MONITORING_NO_VALIDATED_BASELINE";
        JSONObject metrics = new JSONObject().put("verifiedOutcomes", samples).put("note",
                samples < 20 ? "20경주 미만: 드리프트 판정 금지" : "검증된 시장대비 LogLoss 기준선 연결 전");
        ContentValues v = new ContentValues();
        v.put("region", region); v.put("race_date", date); v.put("detector", "ROLLING_GUARD-v1");
        v.put("state", state); v.put("metrics_json", metrics.toString());
        db.insert("drift_events", null, v);
        return new JSONObject().put("state", state).put("metrics", metrics);
    }

    private JSONObject materializeNextRaceContext(SQLiteDatabase db, String date, String region, int raceNo) throws Exception {
        long started = System.nanoTime();
        JSONObject versions = versionContract(db);
        JSONObject regional = regionalProfileThrough(db, date, region);
        JSONObject champion = activeChampion(db, region);
        JSONObject track = currentTrackBias(db, date, region);
        String compactRegion = region.replace("부산경남", "BUSAN").replace("서울", "SEOUL").replace("제주", "JEJU");
        String contextVersion = "CTX-" + date.replace("-", "") + "-" + compactRegion + "-R" + (raceNo + 1) + "-v1";
        boolean histReady = !"HIST_PENDING".equals(versions.optString("histStatus", "HIST_PENDING"));
        String status = histReady ? "READY" : "READY_PARTIAL";
        JSONObject detail = new JSONObject()
                .put("regionalProfile", regional).put("champion", champion).put("trackBias", track)
                .put("histStatus", versions.optString("histStatus", "HIST_PENDING"));
        ContentValues v = new ContentValues();
        v.put("context_version", contextVersion); v.put("race_date", date); v.put("region", region); v.put("race_number", raceNo + 1);
        v.put("status", status); v.put("duration_ms", (System.nanoTime() - started) / 1_000_000.0); v.put("detail_json", detail.toString());
        db.insertWithOnConflict("feature_materialization_log", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        return new JSONObject().put("contextVersion", contextVersion).put("status", status).put("detail", detail);
    }

    @JavascriptInterface
    public String applyClosedLoopUpdate(long analysisId) {
        SQLiteDatabase db = null; long started = System.nanoTime();
        try {
            db = helper.getWritableDatabase(); db.beginTransaction();
            String date, region; int raceNo; JSONObject payload, result;
            try (Cursor c = db.rawQuery(
                    "SELECT a.race_date,a.region,a.race_number,a.payload_json,o.result_json FROM analysis_records a JOIN race_outcomes o ON o.analysis_record_id=a.id WHERE a.id=? LIMIT 1",
                    new String[]{String.valueOf(analysisId)})) {
                if (!c.moveToFirst()) return error("검증된 실제결과가 없습니다.");
                date = c.getString(0); region = c.getString(1); raceNo = c.getInt(2);
                payload = new JSONObject(c.getString(3)); result = new JSONObject(c.getString(4));
            }
            updateRegionalProfileState(db, date, region, raceNo, result);
            updateRatingState(db, date, region, raceNo, payload, result);

            JSONObject trackState = new JSONObject().put("status", "INSUFFICIENT_FIELDS")
                    .put("note", "gate/pace/section feature materialization 전에는 Track Bias 수치 생성 금지");
            ContentValues tv = new ContentValues();
            tv.put("race_date", date); tv.put("region", region);
            tv.put("sample_count", count(db, "SELECT COUNT(*) FROM race_outcomes WHERE region=? AND race_date=?", new String[]{region, date}));
            tv.put("bias_json", trackState.toString()); tv.put("bias_version", "TRACK-v1");
            db.insertWithOnConflict("track_bias_state", null, tv, SQLiteDatabase.CONFLICT_REPLACE);

            JSONObject drift = recordDriftState(db, date, region);
            JSONObject materialized = materializeNextRaceContext(db, date, region, raceNo);

            ContentValues pm = new ContentValues();
            pm.put("metric_scope", "CLOSED_LOOP"); pm.put("region", region); pm.put("metric_name", "update_ms");
            pm.put("metric_value", (System.nanoTime() - started) / 1_000_000.0); pm.put("window_key", "LAST");
            db.insert("performance_metrics", null, pm);

            db.setTransactionSuccessful();
            JSONArray steps = new JSONArray()
                    .put("결과 검증").put("지역 프로파일 갱신").put("Rating 갱신")
                    .put("Track Bias 상태 갱신").put("Drift 검사").put("Next-Race Feature Materialization");
            return new JSONObject().put("ok", true).put("steps", steps)
                    .put("drift", drift).put("materialization", materialized)
                    .put("durationMs", (System.nanoTime() - started) / 1_000_000.0).toString();
        } catch (Exception e) {
            return error(e.getMessage());
        } finally {
            if (db != null && db.inTransaction()) db.endTransaction();
        }
    }

    @JavascriptInterface
    public String healthCheck() {
        long start = System.nanoTime();
        try {
            SQLiteDatabase db = helper.getReadableDatabase();
            int records = count(db, "SELECT COUNT(*) FROM analysis_records", null);
            int outcomes = count(db, "SELECT COUNT(*) FROM race_outcomes", null);
            int models = count(db, "SELECT COUNT(*) FROM ml_models", null);
            int trained = count(db, "SELECT COUNT(*) FROM ml_training_examples WHERE trained_at IS NOT NULL", null);
            long pageCount = 0, pageSize = 0;
            try (Cursor c = db.rawQuery("PRAGMA page_count", null)) { if (c.moveToFirst()) pageCount = c.getLong(0); }
            try (Cursor c = db.rawQuery("PRAGMA page_size", null)) { if (c.moveToFirst()) pageSize = c.getLong(0); }
            String quick = ""; try (Cursor c = db.rawQuery("PRAGMA quick_check", null)) { if (c.moveToFirst()) quick = c.getString(0); }
            return new JSONObject().put("ok", "ok".equalsIgnoreCase(quick)).put("records", records).put("outcomes", outcomes)
                    .put("models", models).put("trainedExamples", trained).put("dbBytes", pageCount * pageSize)
                    .put("durationMs", (System.nanoTime() - start) / 1_000_000.0).toString();
        } catch (Exception e) { return error(e.getMessage()); }
    }

    @JavascriptInterface
    public String cleanupData() {
        long start = System.nanoTime(); SQLiteDatabase db = helper.getWritableDatabase();
        try {
            db.beginTransaction();
            db.execSQL("DELETE FROM race_outcomes WHERE analysis_record_id NOT IN (SELECT id FROM analysis_records)");
            db.execSQL("DELETE FROM ml_training_examples WHERE analysis_record_id NOT IN (SELECT id FROM analysis_records)");
            db.execSQL("DELETE FROM ml_training_events WHERE analysis_record_id IS NOT NULL AND analysis_record_id NOT IN (SELECT id FROM analysis_records)");
            db.execSQL("DELETE FROM ml_models WHERE status<>'ACTIVE' AND id NOT IN (SELECT MAX(id) FROM ml_models GROUP BY region,status)");
            db.execSQL("DELETE FROM selection_metrics WHERE race_id NOT IN (SELECT id FROM races)");
            db.execSQL("DELETE FROM horse_metrics WHERE race_id NOT IN (SELECT id FROM races)");
            db.execSQL("DELETE FROM pool_snapshots WHERE race_id NOT IN (SELECT id FROM races)");
            db.execSQL("DELETE FROM horses WHERE race_id NOT IN (SELECT id FROM races)");
            db.execSQL("DELETE FROM analysis_history WHERE result_id NOT IN (SELECT id FROM analysis_results)");
            db.setTransactionSuccessful();
        } catch (Exception e) {
            return error(e.getMessage());
        } finally {
            if (db.inTransaction()) db.endTransaction();
        }
        try { db.execSQL("PRAGMA optimize"); } catch (Exception ignored) {}
        try { return new JSONObject().put("ok", true).put("durationMs", (System.nanoTime() - start) / 1_000_000.0).toString(); }
        catch (Exception e) { return error(e.getMessage()); }
    }

    @JavascriptInterface
    public String deleteAnalysis(long id) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.delete("race_outcomes", "analysis_record_id=?", new String[]{String.valueOf(id)});
        db.delete("ml_training_examples", "analysis_record_id=?", new String[]{String.valueOf(id)});
        db.delete("ml_training_events", "analysis_record_id=?", new String[]{String.valueOf(id)});
        int n = db.delete("analysis_records", "id=?", new String[]{String.valueOf(id)});
        return "{\"ok\":" + (n > 0) + "}";
    }
}
