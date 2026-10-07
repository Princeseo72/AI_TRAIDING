const A=require('./app/src/main/assets/analysis.js');
const P=require('./app/src/main/assets/pegasus-engine.js');
const M=require('./app/src/main/assets/ml.js');
function assert(c,m){if(!c)throw new Error(m)}
function finiteDeep(x,path='root'){if(typeof x==='number')assert(Number.isFinite(x),'non-finite '+path);else if(Array.isArray(x))x.forEach((v,i)=>finiteDeep(v,path+'['+i+']'));else if(x&&typeof x==='object')Object.entries(x).forEach(([k,v])=>finiteDeep(v,path+'.'+k))}
const horses=[
 {number:1,name:'A',record:'12전 (3/2)',popularity:'1',active:true},
 {number:2,name:'B',record:'10전 (1/3)',popularity:'3',active:true},
 {number:3,name:'C',record:'8전 (2/1)',popularity:'2',active:true},
 {number:4,name:'D',record:'15전 (1/2)',popularity:'5',active:true},
 {number:5,name:'E',record:'7전 (1/1)',popularity:'4',active:true}
];
const pools={WIN:{
 T20:[{key:'1',odds:2.4},{key:'2',odds:7.8},{key:'3',odds:4.1},{key:'4',odds:12},{key:'5',odds:9.5}],
 T5:[{key:'1',odds:2.7},{key:'2',odds:5.1},{key:'3',odds:4.5},{key:'4',odds:14},{key:'5',odds:8.2}]
}};
const legacy=A.analyzeRace({horseNumbers:horses.map(h=>h.number),popularity:horses.map(h=>+h.popularity),pools});
assert(legacy.ok,'legacy analysis failed');
const peg=P.analyze({horses,pools,preRaceContext:{historicalPrior:{status:'HIST_PENDING'},regionalProfile:{sampleCount:0}}});
assert(peg.ok,'PEGASUS failed');
assert(peg.final123.length===3,'final123 count');
assert(new Set(peg.final123.map(x=>x.horseNumber)).size===3,'final123 duplicate');
const f=peg.finalEight;for(const k of ['QUINELLA','EXACTA','TRIO','TRIFECTA'])assert(f[k].length===2,k+' not 2');finiteDeep(peg);
const actualTop3=[2,1,3];
const actual={top3:actualTop3,payouts:{}};
const finishPrediction=peg.final123.map(x=>({horseNumber:x.horseNumber,position:x.position,probability:x.probability}));
const example={raceKey:'E2E|SEOUL|1',region:'서울',features:legacy.featureVectors,prediction:{rolePrediction:legacy.rolePrediction,oddsPrediction:legacy.oddsPrediction,finishPrediction,finalCombinations:legacy.finalCombinations},actual};
const base=M.createBaselineModel('GLOBAL');
const t=M.trainCandidate({model:base,example});
assert(t.ok,'training failed');
assert(t.candidate.sampleCount===1,'sample count');
assert(t.candidate.status==='TRAINING','premature promotion');
finiteDeep(t.candidate.weights);
const dup=M.trainCandidate({model:{...t.candidate,seenRaceKeys:[example.raceKey]},example});
assert(!dup.ok&&/중복/.test(dup.error),'duplicate training guard failed');
// Critical regression: evaluation must follow finishPrediction, not semantic role order.
const forced={...example,prediction:{...example.prediction,finishPrediction:actualTop3.map((n,i)=>({horseNumber:n,position:i+1})),rolePrediction:[{horseNumber:5},{horseNumber:4},{horseNumber:1}]}};
const ev=M.evaluate(forced.prediction,actual);
assert(ev.first===1&&ev.second===1&&ev.third===1&&ev.oddsExact123===1,'finish prediction evaluation wiring failed');
assert(ev.roleTop3<1,'role tags incorrectly treated as finish truth');
console.log('LEARNING + PEGASUS RUNTIME TESTS PASSED');

const ctxFeatures={1:{winShareT5:.1,winLmi:0,crossPoolDivergence:0,popularityPrior:.2,stability:.5,structureEvidence:.5,horseRatingPrior:.9,jockeyRatingPrior:.8,trainerRatingPrior:.7,regionalPrior:.6,historicalPrior:.5},2:{winShareT5:.2,winLmi:0,crossPoolDivergence:0,popularityPrior:.3,stability:.5,structureEvidence:.5},3:{winShareT5:.3,winLmi:0,crossPoolDivergence:0,popularityPrior:.4,stability:.5,structureEvidence:.5}};
const ctxModel=RacingML.createBaselineModel('GLOBAL');
const ctxEx={raceKey:'CTX-CHANGE',region:'GLOBAL',features:ctxFeatures,prediction:{finishPrediction:[{horseNumber:2},{horseNumber:3},{horseNumber:1}],rolePrediction:[{horseNumber:2},{horseNumber:3},{horseNumber:1}],finalCombinations:{}},actual:{top3:[1,2,3],payouts:{}}};
const ctxTrain=RacingML.trainCandidate({model:ctxModel,example:ctxEx});
assert(ctxTrain.ok,'context learning must train');
assert(Math.abs(ctxTrain.weightDelta.WinScore.horseRating)>0,'horse rating must alter learned weights');
assert(Math.abs(ctxTrain.weightDelta.WinScore.jockeyRating)>0,'jockey rating must alter learned weights');
assert(Math.abs(ctxTrain.weightDelta.WinScore.trainerRating)>0,'trainer rating must alter learned weights');
assert(Math.abs(ctxTrain.weightDelta.WinScore.regionalPrior)>0,'regional prior must alter learned weights');
assert(Math.abs(ctxTrain.weightDelta.WinScore.historicalPrior)>0,'historical prior must alter learned weights');
console.log('context-learning-v3 PASS');
