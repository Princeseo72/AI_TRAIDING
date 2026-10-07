const ML=require('./app/src/main/assets/ml.js');
function assert(c,m){if(!c)throw new Error(m)}
const SCORES=['DarkHorseScore','FavoriteScore','AbilityScore','WinScore','Place2Score','Place3Score'];
const baseline=ML.createBaselineModel('GLOBAL');
assert(baseline.status==='ACTIVE','baseline active');
for(const score of SCORES)assert(baseline.weights[score]&&typeof baseline.weights[score].lmi==='number',score+' weight family missing');
let no=ML.trainCandidate({model:baseline,example:null});assert(!no.ok,'missing result accepted');

function example(key,region='제주'){
  const prediction={
    rolePrediction:[{horseNumber:5},{horseNumber:2},{horseNumber:7}],
    oddsPrediction:[{horseNumber:5},{horseNumber:2},{horseNumber:7}],
    finalCombinations:{
      QUINELLA:[{key:'2-5'},{key:'5-7'}],
      EXACTA:[{key:'5>2'},{key:'2>5'}],
      TRIO:[{key:'2-5-7'},{key:'2-5-9'}],
      TRIFECTA:[{key:'5>2>7'},{key:'5>7>2'}]
    }
  };
  return {raceKey:key,region,features:{
    2:{winShareT5:.20,winLmi:.05,crossPoolDivergence:.02,popularityPrior:.5,stability:.9,structureEvidence:.5},
    5:{winShareT5:.35,winLmi:.25,crossPoolDivergence:.15,popularityPrior:.25,stability:.8,structureEvidence:.6},
    7:{winShareT5:.18,winLmi:.10,crossPoolDivergence:.04,popularityPrior:.33,stability:.85,structureEvidence:.7}
  },prediction,actual:{top3:[5,2,7],payouts:{
    QUINELLA:{key:'2-5'},EXACTA:{key:'5>2'},TRIO:{key:'2-5-7'},TRIFECTA:{key:'5>2>7'}
  }}};
}
const ev=ML.evaluate(example('eval').prediction,example('eval').actual);
for(const k of ['first','second','third','roleExact123','oddsExact123','roleTop3','oddsTop3','quinella','exacta','trio','trifecta'])assert(k in ev,'metric missing '+k);
assert(ev.quinella===1&&ev.exacta===1&&ev.trio===1&&ev.trifecta===1,'pool hit evaluation wrong');

let t=ML.trainCandidate({model:baseline,example:example('r1')});assert(t.ok,'training failed');
for(const score of SCORES)for(const [k,v] of Object.entries(t.weightDelta[score]))assert(Math.abs(v)<=ML.CONFIG.maxDeltaPerRace+1e-12,`delta overflow ${score}.${k}`);
assert(t.candidate.sampleCount===1,'sample count wrong');
assert(t.promoted===false,'must not activate before minimum samples');

let model=t.candidate;
for(let i=2;i<=ML.CONFIG.minSamples;i++){
  const step=ML.trainCandidate({model,example:example('r'+i)});
  assert(step.ok,'global accumulation stopped at '+i);
  model=step.candidate;
  if(i<ML.CONFIG.minSamples)assert(step.promoted===false,'global promoted too early');
  if(i===ML.CONFIG.minSamples)assert(step.promoted===true,'global did not promote at minimum samples');
}
let regional=ML.createBaselineModel('제주');
for(let i=1;i<=ML.CONFIG.regionalMinSamples;i++){
  const step=ML.trainCandidate({model:regional,example:example('j'+i,'제주')});
  assert(step.ok,'regional accumulation stopped');
  regional=step.candidate;
  if(i===ML.CONFIG.regionalMinSamples)assert(step.promoted===true,'regional did not promote at regional threshold');
}
const comp=ML.composeModel(model,regional);
assert(comp.regionAdjustmentActive===true,'qualified regional adjustment not applied');
let dup=ML.trainCandidate({model,example:example('r20'),seenRaceKeys:new Set(['r20'])});assert(!dup.ok&&dup.error.includes('중복'),'duplicate training accepted');
console.log('ML ENGINE TESTS PASSED');
