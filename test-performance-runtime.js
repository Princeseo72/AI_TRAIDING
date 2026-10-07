const A=require('./app/src/main/assets/analysis.js');
const ML=require('./app/src/main/assets/ml.js');
function assert(c,m){if(!c)throw new Error(m)}
const input={horseNumbers:[1,3,4,5,6,7,8,9,10],popularity:[1,4,2,2,1,2,3,5,6],pools:{WIN:{
 T20:[1,3,4,5,6,7,8,9,10].map((n,i)=>({key:String(n),odds:[2.8,8.1,4.4,5.7,3.5,6.8,10.2,15.3,18.4][i]})),
 T5:[1,3,4,5,6,7,8,9,10].map((n,i)=>({key:String(n),odds:[3.0,7.2,4.1,5.5,3.7,5.6,9.8,13.1,17.5][i]}))
}}};
let analysisMax=0;
for(let i=0;i<100;i++){const st=performance.now();const r=A.analyzeRace(input);analysisMax=Math.max(analysisMax,performance.now()-st);assert(r.ok,'analysis benchmark failed')}
assert(analysisMax<200,`analysis exceeded 200ms: ${analysisMax.toFixed(2)}ms`);
let model=ML.createBaselineModel('GLOBAL'),learningMax=0;
for(let i=0;i<30;i++){
 const r=A.analyzeRace(input),ex={raceKey:'bench-'+i,region:'GLOBAL',features:r.featureVectors,prediction:{rolePrediction:r.rolePrediction,oddsPrediction:r.oddsPrediction,finalCombinations:r.finalCombinations},actual:{top3:r.rolePrediction.map(x=>x.horseNumber),payouts:{}}};
 const st=performance.now();const t=ML.trainCandidate({model,example:ex});learningMax=Math.max(learningMax,performance.now()-st);assert(t.ok,'learning benchmark failed');model=t.candidate;
}
assert(learningMax<250,`learning exceeded 250ms: ${learningMax.toFixed(2)}ms`);
console.log(`RUNTIME PERFORMANCE PASS analysisMax=${analysisMax.toFixed(2)}ms learningMax=${learningMax.toFixed(2)}ms`);
