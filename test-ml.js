const ML=require('./app/src/main/assets/ml.js');
function assert(c,m){if(!c)throw new Error(m)}
const baseline=ML.createBaselineModel('GLOBAL');
assert(baseline.status==='ACTIVE','baseline active');
let no=ML.trainCandidate({model:baseline,example:null});assert(!no.ok,'missing result accepted');
const ex={raceKey:'2026-10-03|제주|2',features:{2:{winShareT5:.1,winLmi:.2,crossPoolDivergence:.1},5:{winShareT5:.3,winLmi:-.1,crossPoolDivergence:0}},prediction:{rolePrediction:[{horseNumber:2},{horseNumber:5},{horseNumber:7}],oddsPrediction:[{horseNumber:5},{horseNumber:2},{horseNumber:7}]},actual:{top3:[5,2,7]},region:'제주'};
let t=ML.trainCandidate({model:baseline,example:ex});assert(t.ok,'training failed');assert(t.candidate.modelVersion!==baseline.modelVersion,'version not changed');
for(const [k,v] of Object.entries(t.weightDelta))assert(Math.abs(v)<=ML.CONFIG.maxDeltaPerRace+1e-12,`delta overflow ${k}`);
assert(t.candidate.sampleCount===1,'sample count wrong');
assert(t.candidate.status==='CANDIDATE','candidate status wrong');
assert(t.promoted===false,'must not promote before minimum samples');
let dup=ML.trainCandidate({model:t.candidate,example:ex,seenRaceKeys:new Set([ex.raceKey])});assert(!dup.ok&&dup.error.includes('중복'),'duplicate training accepted');
let comp=ML.composeModel(baseline,{...baseline,region:'제주',sampleCount:0});assert(comp.regionAdjustmentActive===false,'regional adjustment active too early');
console.log('ML ENGINE TESTS PASSED');
