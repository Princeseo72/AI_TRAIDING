const fs=require('fs');
function assert(c,m){if(!c)throw new Error(m)}
const db=fs.readFileSync('./app/src/main/java/com/kplay/horseracing/RaceDbHelper.java','utf8');
const store=fs.readFileSync('./app/src/main/java/com/kplay/horseracing/StorageBridge.java','utf8');
for(const t of ['ml_models','ml_training_examples','ml_training_events'])assert(db.includes(t),`${t} missing`);
assert(db.includes('UNIQUE(analysis_record_id)')||db.includes('UNIQUE(race_id,analysis_record_id)'),'training uniqueness missing');
for(const fn of ['getActiveModel','savePredictionSnapshot','getMlStatus','recordTrainingEvent'])assert(store.includes(fn),`${fn} bridge missing`);
assert(store.includes('prediction_snapshot_json'),'immutable prediction snapshot missing');
assert(store.includes("status='ACTIVE'")||store.includes('status=\"ACTIVE\"'),'ACTIVE model query missing');
assert(store.includes('DELETE FROM ml_training_examples')&&store.includes("status<>'ACTIVE'"),'cleanup must preserve ACTIVE model');
console.log('ML SCHEMA CONTRACT TESTS PASSED');
