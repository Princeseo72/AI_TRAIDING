const A=require('./app/src/main/assets/analysis.js');
function assert(c,m){if(!c)throw new Error(m)}
function close(a,b,eps=1e-9){if(Math.abs(a-b)>eps)throw new Error(`${a} != ${b}`)}

// PRD formulas
let q=A.normalizeSnapshot([{key:'1',odds:2},{key:'2',odds:4},{key:'3',odds:4}]);
close(q.reduce((s,x)=>s+x.share,0),1);close(q[0].share,0.5);close(q[1].share,0.25);
let l=A.computeLmi([{key:'1',share:.08},{key:'2',share:.24}],[{key:'1',share:.17},{key:'2',share:.18}]);
close(l['1'],1.125);close(l['2'],-.25);

// canonical keys
assert(A.canonicalKey('QUINELLA','7-3')==='3-7','quinella key not canonicalized');
assert(A.canonicalKey('TRIO','11-3-7')==='3-7-11','trio key not canonicalized');
assert(A.canonicalKey('EXACTA','7>3')==='7>3','exacta direction lost');
assert(A.canonicalKey('TRIFECTA','7>3>11')==='7>3>11','trifecta direction lost');

const input={
  horseNumbers:[1,3,7,11],
  popularity:[4,1,5,8],
  pools:{
    WIN:{
      T20:[{key:'1',odds:8},{key:'3',odds:3.2},{key:'7',odds:12},{key:'11',odds:18}],
      T5:[{key:'1',odds:8.5},{key:'3',odds:3.4},{key:'7',odds:7},{key:'11',odds:16}]
    },
    QUINELLA:{
      T20:[{key:'3-7',odds:11},{key:'3-11',odds:18},{key:'1-3',odds:14}],
      T5:[{key:'3-7',odds:6.2},{key:'3-11',odds:17},{key:'1-3',odds:15}]
    },
    EXACTA:{
      T20:[{key:'7>3',odds:24},{key:'3>7',odds:15},{key:'3>11',odds:31}],
      T5:[{key:'7>3',odds:12},{key:'3>7',odds:13},{key:'3>11',odds:29}]
    }
  }
};

let r=A.analyzeRace(input);
assert(r.ok,'multi-pool analysis failed');
assert(r.roles.marketCenter.horseNumber===3,'market center should be horse 3');
assert(r.roles.lateMoney.horseNumber===7,'late money should be horse 7');
assert(r.roles.sleeper.horseNumber===7,'cross-pool sleeper should be horse 7');
assert(r.poolResults.QUINELLA.candidates[0].key==='3-7','quinella candidate must come from actual selection');
assert(r.poolResults.EXACTA.candidates[0].key==='7>3','exacta candidate must preserve direction');
assert(r.poolResults.TRIO.status==='INSUFFICIENT_DATA','missing trio must not fabricate candidates');
assert(r.poolResults.TRIFECTA.status==='INSUFFICIENT_DATA','missing trifecta must not fabricate candidates');
assert(r.poolResults.QUINELLA.candidates[0].evidence.q20>=0,'candidate q20 missing');
assert(r.poolResults.QUINELLA.candidates[0].evidence.q5>=0,'candidate q5 missing');
assert(Number.isFinite(r.poolResults.QUINELLA.candidates[0].evidence.lmi),'candidate lmi missing');
assert(Number.isFinite(r.poolResults.QUINELLA.candidates[0].score),'candidate score missing');
assert(r.analysisVersion,'analysis version missing');

// no synthetic LMI for snapshot mismatch
let mismatch=A.analyzeRace({horseNumbers:[1,2],pools:{WIN:{T20:[{key:'1',odds:2},{key:'2',odds:3}],T5:[{key:'1',odds:2.5}]}}});
assert(!mismatch.ok,'mismatched mandatory WIN selections should fail');

// invalid odds
let bad=A.analyzeRace({horseNumbers:[1,2],pools:{WIN:{T20:[{key:'1',odds:2},{key:'2',odds:0}],T5:[{key:'1',odds:2},{key:'2',odds:3}]}}});
assert(!bad.ok,'invalid odds accepted');

console.log('ALL MULTI-POOL ANALYSIS TESTS PASSED');
