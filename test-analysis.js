const A=require('./app/src/main/assets/analysis.js');
function assert(c,m){if(!c)throw new Error(m)}
function close(a,b,e=1e-9){if(Math.abs(a-b)>e)throw new Error(`${a} != ${b}`)}
let q=A.normalizeSnapshot([{key:'1',odds:2},{key:'2',odds:4},{key:'3',odds:4}]);close(q.reduce((s,x)=>s+x.share,0),1);close(q[0].share,.5);
let l=A.computeLmi([{key:'1',share:.08},{key:'2',share:.24}],[{key:'1',share:.17},{key:'2',share:.18}]);close(l['1'],1.125);close(l['2'],-.25);
assert(A.canonicalKey('QUINELLA','7-3')==='3-7','quinella canonical');assert(A.canonicalKey('EXACTA','7>3')==='7>3','exacta direction');
const base={horseNumbers:[1,3,7,11],popularity:[4,1,5,8],pools:{WIN:{T20:[{key:'1',odds:8},{key:'3',odds:3.2},{key:'7',odds:12},{key:'11',odds:18}],T5:[{key:'1',odds:8.5},{key:'3',odds:3.4},{key:'7',odds:7},{key:'11',odds:16}]}}};
let d=A.analyzeRace(base);assert(d.ok,'derived analysis failed');for(const p of ['QUINELLA','EXACTA','TRIO','TRIFECTA']){assert(d.poolResults[p].status==='OK',`${p} not calculated`);assert(d.poolResults[p].mode==='DERIVED_FROM_WIN',`${p} fallback mode missing`);assert(d.poolResults[p].candidates.length>0,`${p} candidates empty`)}
assert(d.poolResults.EXACTA.candidates.every(c=>c.key.includes('>')),'exacta direction missing');assert(d.poolResults.TRIFECTA.candidates.every(c=>c.key.split('>').length===3),'trifecta structure wrong');
assert(d.finalEight&&d.finalEight.length===8,'final output must be exactly 8 combinations');
assert(d.finishStructures&&d.finishStructures.analysis&&d.finishStructures.originalOdds,'two finish structures missing');
assert(new Set(d.finishStructures.analysis).size===3,'analysis finish horses must be distinct');
assert(new Set(d.finishStructures.originalOdds).size===3,'original odds finish horses must be distinct');
for(const group of ['ANALYSIS','ORIGINAL_ODDS']){const rows=d.finalEight.filter(x=>x.group===group);assert(rows.length===4,`${group} must have 4 bet types`);for(const p of ['QUINELLA','EXACTA','TRIO','TRIFECTA'])assert(rows.some(x=>x.pool===p),`${group} ${p} missing`)}
const full=JSON.parse(JSON.stringify(base));full.pools.QUINELLA={T20:[{key:'3-7',odds:11},{key:'3-11',odds:18},{key:'1-3',odds:14}],T5:[{key:'3-7',odds:6.2},{key:'3-11',odds:17},{key:'1-3',odds:15}]};full.pools.EXACTA={T20:[{key:'7>3',odds:24},{key:'3>7',odds:15},{key:'3>11',odds:31}],T5:[{key:'7>3',odds:12},{key:'3>7',odds:13},{key:'3>11',odds:29}]};
let r=A.analyzeRace(full);assert(r.ok,'full analysis failed');assert(r.poolResults.QUINELLA.mode==='ACTUAL_POOL','actual quinella not used');assert(r.poolResults.EXACTA.mode==='ACTUAL_POOL','actual exacta not used');assert(r.poolResults.QUINELLA.candidates[0].key==='3-7','actual quinella rank wrong');assert(r.poolResults.EXACTA.candidates[0].key==='7>3','actual exacta rank wrong');
let bad=A.analyzeRace({horseNumbers:[1,2],pools:{WIN:{T20:[{key:'1',odds:2},{key:'2',odds:3}],T5:[{key:'1',odds:2.5}]}}});assert(!bad.ok,'mismatched WIN accepted');
console.log('ANALYSIS ENGINE TESTS PASSED');
