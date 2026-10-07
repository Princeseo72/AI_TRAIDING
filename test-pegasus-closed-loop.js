const fs=require('fs');function assert(c,m){if(!c)throw new Error(m)}
const store=fs.readFileSync('./app/src/main/java/com/kplay/horseracing/StorageBridge.java','utf8');
const db=fs.readFileSync('./app/src/main/java/com/kplay/horseracing/RaceDbHelper.java','utf8');
const app=fs.readFileSync('./app/src/main/assets/app.js','utf8');
for(const fn of ['applyClosedLoopUpdate','materializeNextRaceContext','updateRegionalProfileState','updateRatingState','recordDriftState'])assert(store.includes(fn),'closed-loop bridge missing '+fn);
for(const t of ['regional_profile_events','rating_events','feature_materialization_log','drift_events','performance_metrics'])assert(db.includes(t),'closed-loop table missing '+t);
for(const x of ['결과 검증','지역 프로파일 갱신','Rating 갱신','Drift 검사','Next-Race Feature Materialization','다음 경주 준비'])assert(app.includes(x),'closed-loop UI missing '+x);
assert(app.includes('applyClosedLoopUpdate'),'app closed-loop call missing');
console.log('PEGASUS CLOSED LOOP CONTRACT TESTS PASSED');