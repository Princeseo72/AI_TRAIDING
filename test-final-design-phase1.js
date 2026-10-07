const fs=require('fs');
function assert(c,m){if(!c)throw new Error(m)}
const db=fs.readFileSync('./app/src/main/java/com/kplay/horseracing/RaceDbHelper.java','utf8');
const store=fs.readFileSync('./app/src/main/java/com/kplay/horseracing/StorageBridge.java','utf8');
const app=fs.readFileSync('./app/src/main/assets/app.js','utf8');

for(const t of [
 'hist_manifest','regional_profiles','regional_profile_events','rating_state','rating_events',
 'track_bias_state','blend_models','calibration_models','champion_registry','drift_events',
 'prediction_engine_outputs','feature_snapshots','feature_materialization_log',
 'performance_metrics','data_quality_events'
]) assert(db.includes(t), 'DB v5 table missing: '+t);

for(const fn of ['getPreRaceContext','getRegionalProfile','getVersionContract'])
  assert(store.includes(fn), 'Storage bridge missing: '+fn);

for(const key of ['preRaceContext','regionalProfile','contextVersion','dataFreshness'])
  assert(app.includes(key), 'App state/context missing: '+key);

for(const text of ['PEGASUS 사전 브리핑','Hist 상태','지역 프로파일','Champion','데이터 최신'])
  assert(app.includes(text), 'Pre-race briefing UI missing: '+text);

assert(app.includes("HIST_PENDING") || store.includes("HIST_PENDING"), 'Hist missing state must be explicit');
console.log('FINAL DESIGN PHASE 1-4 CONTRACT TESTS PASSED');