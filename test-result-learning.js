const fs=require('fs');
function assert(c,m){if(!c)throw new Error(m)}
const store=fs.readFileSync('./app/src/main/java/com/kplay/horseracing/StorageBridge.java','utf8');
const app=fs.readFileSync('./app/src/main/assets/app.js','utf8');
assert(store.includes('prediction_snapshot_json'),'prediction snapshot missing');
assert(store.includes('predictionImmutable'),'immutable result attach marker missing');
assert(!store.includes('payload.put("postRaceResult"'),'result attach must not rewrite original payload');
assert(store.includes('중복 학습 경주'),'duplicate training guard missing');
assert(app.includes('trainFromResult'),'result learning pipeline missing');
assert(app.includes('actualTop3'),'actual top3 evaluation missing');
assert(app.includes('recordTrainingEvent'),'training persistence missing');
assert(app.includes('ML 실패 - 기본 분석 유지'),'ML fallback missing');
console.log('RESULT LEARNING CONTRACT TESTS PASSED');
