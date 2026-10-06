const fs=require('fs');
function assert(c,m){if(!c)throw new Error(m)}
const db=fs.readFileSync('./app/src/main/java/com/kplay/horseracing/RaceDbHelper.java','utf8');
const store=fs.readFileSync('./app/src/main/java/com/kplay/horseracing/StorageBridge.java','utf8');
const app=fs.readFileSync('./app/src/main/assets/app.js','utf8');
assert(store.includes('ORDER BY a.id DESC LIMIT 200'),'history list not bounded');
assert(store.includes('prediction_snapshot_json'),'prediction snapshot storage missing');
assert(db.includes('idx_ml_models_region_status')&&db.includes('idx_ml_examples_model'),'ML indexes missing');
assert(store.includes("status<>'ACTIVE'"),'cleanup ACTIVE preservation missing');
assert(store.includes('trainedExamples')&&store.includes('durationMs'),'health ML/duration metrics missing');
assert(app.includes('lastAnalysisMs')&&app.includes('lastLoadMs'),'UI performance durations missing');
console.log('PERFORMANCE CONTRACT TESTS PASSED');
