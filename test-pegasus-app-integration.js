const fs=require('fs');function assert(c,m){if(!c)throw new Error(m)}
const app=fs.readFileSync('./app/src/main/assets/app.js','utf8');
const idx=fs.readFileSync('./app/src/main/assets/index.html','utf8');
assert(idx.includes('pegasus-engine.js'),'PEGASUS engine script missing');
for(const x of ['PEGASUS 최종 예상','펀더멘털 예상','시장·배당 예상','엔진 비교','TOP5 확률','CALIBRATION_PENDING','PL_FALLBACK'])assert(app.includes(x),'final UI contract missing '+x);
for(const x of ['Hist Context','Regional Profile','Fundamental','Rating','Track Bias','Market T20/T5','Live Odds','Blend','Calibration','Ordered Finish','Final Eight','Model Integrity'])assert(app.includes(x),'process step missing '+x);
assert(!/scheduleAnalysis\(\)/.test(app.replace(/function scheduleAnalysis[\s\S]*?\}/,'')),'T5 edits must not auto-run analysis');
console.log('PEGASUS APP INTEGRATION CONTRACT TESTS PASSED');