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
console.log('PEGASUS FINAL ENGINE TESTS PASSED');