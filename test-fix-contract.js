const fs=require('fs');
function assert(c,m){if(!c)throw new Error(m)}
const fix=fs.readFileSync('./fix.md','utf8');
const ml=fs.readFileSync('./app/src/main/assets/ml.js','utf8');
const app=fs.readFileSync('./app/src/main/assets/app.js','utf8');
for(const x of ['FIX-001','FIX-002','FIX-003','FIX-004','FIX-005','Historical Replay','Self Diagnosis','Release Gate'])assert(fix.includes(x),'fix.md missing '+x);
assert(app.includes('finishPrediction=(S.pegasusResult?.final123||[])'),'app must learn from PEGASUS final123');
assert(ml.includes("pred?.finishPrediction||pred?.oddsPrediction"),'ML evaluation must prefer finishPrediction');
assert(ml.includes("source:'finishPrediction'"),'finish targets must use finishPrediction');
assert(!ml.includes("first:+(role[0]===top[0])"),'role tag must not be scored as finish position');
console.log('FIX CONTRACT TESTS PASSED');
