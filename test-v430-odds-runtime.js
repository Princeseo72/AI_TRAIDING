const E=require('./app/src/main/assets/pegasus-engine.js');
function A(c,m){if(!c)throw new Error(m)}
const horses=[1,2,3,4].map((number,i)=>({number,record:'10전 (2/2)',popularity:String(i+1)}));
const win20=[{key:'1',odds:2.4},{key:'2',odds:4.2},{key:'3',odds:7.5},{key:'4',odds:11}];
const win5=[{key:'1',odds:2.7},{key:'2',odds:3.8},{key:'3',odds:6.4},{key:'4',odds:9.5}];
const base={horses,pools:{WIN:{T20:win20,T5:win5}},preRaceContext:{historicalPrior:{status:'HIST_PENDING'},regionalProfile:{sampleCount:0}}};
let r=E.analyze(base);A(r.ok,'normal analysis failed');A(r.final123.length===3&&new Set(r.final123.map(x=>x.horseNumber)).size===3,'Final123 invalid');A(Object.values(r.finalEight).flat().length===8,'Final8 != 8');A(Math.abs(r.ordered.scenarios.reduce((s,x)=>s+x.probability,0)-1)<1e-9,'ordered probability mass != 1');
r=E.analyze({...base,pools:{WIN:{T20:win20,T5:win5.slice(0,3)}}});A(!r.ok,'missing T5 runner accepted');
r=E.analyze({...base,horses:[...horses,{...horses[0]}]});A(!r.ok,'duplicate horse accepted');
r=E.analyze({...base,pools:{WIN:{T20:[{...win20[0],key:'01'},...win20.slice(1)],T5:win5}}});A(!r.ok,'noncanonical horse key accepted');
r=E.analyze({...base,pools:{WIN:{T20:[{...win20[0],odds:Infinity},...win20.slice(1)],T5:win5}}});A(!r.ok,'Infinity odds accepted');
const champ={role:'CHAMPION',poolModels:{EXACTA:{bins:{LE10:{posteriorHitRate:0}}},TRIFECTA:{bins:{LE30:{posteriorHitRate:0}}},QUINELLA:{bins:{LE10:{posteriorHitRate:0}}},TRIO:{bins:{LE30:{posteriorHitRate:0}}}}};
const pools={WIN:{T20:win20,T5:win5},EXACTA:{T20:[{key:'1>2',odds:8}],T5:[{key:'1>2',odds:8}]},TRIFECTA:{T20:[{key:'1>2>3',odds:20}],T5:[{key:'1>2>3',odds:20}]},QUINELLA:{T20:[{key:'1-2',odds:5}],T5:[{key:'1-2',odds:5}]},TRIO:{T20:[{key:'1-2-3',odds:15}],T5:[{key:'1-2-3',odds:15}]}};
r=E.analyze({...base,pools,histChampion:champ});A(r.ok,'HIST zero analysis failed');A(r.ordered.scenarios.some(x=>Math.abs(x.histConditionalFactor-.9)<1e-12),'zero hit rate not applied as 0.90 factor');A(Math.abs(r.ordered.scenarios.reduce((s,x)=>s+x.probability,0)-1)<1e-9,'HIST ordered mass != 1');
console.log('V4.3 ODDS/HIST DIRECT RUNTIME PASS');
