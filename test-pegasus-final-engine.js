const E=require('./app/src/main/assets/pegasus-engine.js');
function assert(c,m){if(!c)throw new Error(m)}
const input={
 horses:[
  {number:1,record:'10전 (3/1)',popularity:'1'},
  {number:2,record:'10전 (1/2)',popularity:'2'},
  {number:3,record:'10전 (0/1)',popularity:'4'},
  {number:4,record:'8전 (2/2)',popularity:'3'}
 ],
 pools:{WIN:{
  T20:[{key:'1',odds:2.1},{key:'2',odds:3.8},{key:'3',odds:8.0},{key:'4',odds:5.0}],
  T5:[{key:'1',odds:2.4},{key:'2',odds:3.6},{key:'3',odds:6.2},{key:'4',odds:4.7}]
 }},
 preRaceContext:{
  historicalPrior:{status:'HIST_PENDING',similarRaceCount:0},
  regionalProfile:{status:'LOW_SAMPLE',sampleCount:5,recent100:{favoriteWinRate:.3}},
  ratingState:{status:'RATING_PENDING'},
  trackBias:{status:'NO_SAME_DAY_SAMPLE',sampleCount:0,bias:{}},
  champion:{modelVersion:'BASELINE'}
 }
};
const r=E.analyze(input);
assert(r.ok,'engine failed');
assert(Math.abs(Object.values(r.market.p1).reduce((a,b)=>a+b,0)-1)<1e-9,'market not normalized');
assert(Math.abs(Object.values(r.fundamental.p1).reduce((a,b)=>a+b,0)-1)<1e-9,'fundamental not normalized');
assert(Math.abs(Object.values(r.blend.p1).reduce((a,b)=>a+b,0)-1)<1e-9,'blend not normalized');
assert(r.calibration.status==='CALIBRATION_PENDING','must not fake calibration');
assert(r.ordered.status==='PL_FALLBACK','untrained position correction must be explicit');
assert(r.final123.length===3 && new Set(r.final123.map(x=>x.horseNumber)).size===3,'final 123 invalid');
for(const p of ['QUINELLA','EXACTA','TRIO','TRIFECTA'])assert(r.finalEight[p].length===2,p+' must have 2');
assert(Object.values(r.finalEight).flat().length===8,'must have exactly 8');
assert(r.engineCompare.market.length===3 && r.engineCompare.fundamental.length===3 && r.engineCompare.final.length===3,'engine compare missing');
assert(['LOW','MID','HIGH'].includes(r.uncertainty.level),'uncertainty missing');
assert(r.versions.marketVersion && r.versions.orderedFinishVersion,'versions missing');
assert(r.market.p5 && r.market.movementEvidence,'v4 movement evidence missing');
const flat=E.analyze({...input,pools:{WIN:{T20:input.pools.WIN.T5,T5:input.pools.WIN.T5}}});
for(const k of Object.keys(flat.market.movementEvidence))assert(Math.abs(flat.market.movementEvidence[k].movement)<1e-12,'no movement must produce zero bonus');
const sameLateA=E.analyze({...input,pools:{WIN:{T20:[{key:'1',odds:5},{key:'2',odds:2},{key:'3',odds:8},{key:'4',odds:6}],T5:input.pools.WIN.T5}}});
const sameLateB=E.analyze({...input,pools:{WIN:{T20:[{key:'1',odds:2},{key:'2',odds:6},{key:'3',odds:8},{key:'4',odds:5}],T5:input.pools.WIN.T5}}});
assert(Math.abs(sameLateA.market.p1['1']-sameLateB.market.p1['1'])>1e-4,'same T5 with different T20 must not collapse to same market probability');
assert(E.parseRecord('10전 3/1').status==='READY','record without parentheses must parse');
assert(E.parseRecord('').status==='PENDING','blank record must not masquerade as zero-start history');
const histAbility={...input,horses:input.horses.map((h,i)=>({...h,record:'',ability:{starts:10,wins:i===3?4:0,seconds:i===3?2:0,speed:i===3?92:40,early:50,closing:i===3?90:40,source:'GUMVIT_DAEBAK_PUBLIC'}}))};
const ha=E.analyze(histAbility);
assert(ha.fundamental.evidence['4'].ability.speed>.9,'public historical ability not consumed');
assert(ha.fundamental.p1['4']>ha.fundamental.p1['3'],'historical ability must change fundamental ordering');
const learnedModel={modelVersion:'ML-verified',sampleCount:25,weights:{WinScore:{share:.05,lmi:.05,crossPool:.05,popularity:.05,stability:.05,structure:.05,horseRating:.05,jockeyRating:.05,trainerRating:.05,regionalPrior:.05,historicalPrior:.8}}};
const lr=E.analyze({...input,learningModel:learnedModel,preRaceContext:{...input.preRaceContext,historicalPrior:{status:'PUBLIC_HISTORY_READY',horsePriors:{'1':.05,'2':.05,'3':.9,'4':.05}}}});
assert(lr.learned.status==='ACTIVE_LEARNED_ADJUSTMENT','verified learned model not wired');
assert(Math.abs(lr.learned.p1['3']-lr.fundamental.p1['3'])>1e-5,'learned weights must alter PEGASUS model probability');
console.log('PEGASUS FINAL ENGINE TESTS PASSED');
const histChampion={role:'CHAMPION',modelVersion:'HIST-5Y-v1',poolModels:{
 EXACTA:{bins:{LE3:{posteriorHitRate:.20},LE10:{posteriorHitRate:.12},LE30:{posteriorHitRate:.07},GT30:{posteriorHitRate:.03}}},
 TRIFECTA:{bins:{LE3:{posteriorHitRate:.12},LE10:{posteriorHitRate:.08},LE30:{posteriorHitRate:.04},GT30:{posteriorHitRate:.02}}},
 QUINELLA:{bins:{LE3:{posteriorHitRate:.30},LE10:{posteriorHitRate:.20},LE30:{posteriorHitRate:.10},GT30:{posteriorHitRate:.05}}},
 TRIO:{bins:{LE3:{posteriorHitRate:.25},LE10:{posteriorHitRate:.16},LE30:{posteriorHitRate:.09},GT30:{posteriorHitRate:.04}}}
}};
const histPools={...input.pools,
 EXACTA:{T5:[{key:'1>2',odds:4.0}]},TRIFECTA:{T5:[{key:'1>2>4',odds:9.0}]},
 QUINELLA:{T5:[{key:'1-2',odds:3.5}]},TRIO:{T5:[{key:'1-2-4',odds:7.0}]}
};
const hr=E.analyze({...input,pools:histPools,histChampion});
assert(hr.ordered.status==='HIST_CONDITIONAL_ORDERED','validated HIST champion must reach Ordered Finish');
assert(hr.ordered.scenarios.some(s=>s.histConditionalFactor!==1),'7-pool HIST correction not applied');
