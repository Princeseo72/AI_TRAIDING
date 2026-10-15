const fs=require('fs'),vm=require('vm');function ok(x,m){if(!x)throw Error(m)}
class El{constructor(){this.innerHTML='';this.textContent='';this.value='';this.dataset={};this.classList={add(){},remove(){}};this.style={};}querySelectorAll(){return[]} }
const els=new Map();const document={querySelector(s){if(!els.has(s))els.set(s,new El());return els.get(s)},querySelectorAll(){return[]}};
const sandbox={console,performance,document,setTimeout,clearTimeout,setInterval,clearInterval,window:null,globalThis:null};sandbox.window=sandbox;sandbox.globalThis=sandbox;
vm.createContext(sandbox);
for(const p of ['runner-guard.js','analysis.js','pegasus-engine.js','ml.js'])vm.runInContext(fs.readFileSync('./app/src/main/assets/'+p,'utf8'),sandbox,{filename:p});
sandbox.AndroidStore={
 getPreRaceContext(){return JSON.stringify({ok:true,contextVersion:'E2E',historicalPrior:{status:'HIST_PENDING'},regionalProfile:{status:'LOW_SAMPLE',sampleCount:0},ratingState:{status:'RATING_PENDING'},trackBias:{status:'NO_SAME_DAY_SAMPLE',sampleCount:0},champion:{modelVersion:'BASELINE'}})},
 getActiveModel(){return JSON.stringify({ok:false})},getMlStatus(){return JSON.stringify({ok:true})},listAnalyses(){return '[]'},
 saveAnalysis(p){const x=JSON.parse(p);sandbox.__saved=JSON.parse(JSON.stringify(x));return JSON.stringify({ok:true,id:1})},healthCheck(){return JSON.stringify({ok:true})}
};
sandbox.AndroidRace={fetchRace(){return JSON.stringify({ok:true,source:'FIXTURE',sourceProvider:'GUMVIT',horses:[]})},selfDiagnose(){return JSON.stringify({ok:true,gumvitParser:true,modules:true,duplicateResultLookup:false})}};
sandbox.AndroidApp={};
vm.runInContext(fs.readFileSync('./app/src/main/assets/app.js','utf8'),sandbox,{filename:'app.js'});
const run=x=>vm.runInContext(x,sandbox);
// MENU1 actual state: verified field, active/excluded separation, pool initialization.
run(`S.race={date:'2026-10-08',region:'서울',number:1};S.horses=[1,2,3,4,5].map(n=>({number:n,name:'H'+n,record:'10전 (2/2)',trainer:'T'+n,jockey:'J'+n,popularity:String(n),active:true}));S.excludedHorses=[{number:6,name:'SCRATCH'}];S.gumvit={verified:true,loading:false,source:'FIXTURE',sourceProvider:'GUMVIT',error:''};initPools();loadPreRaceContext()`);
ok(run('can(1)')===true,'MENU1->2 blocked');ok(run('S.horses.some(h=>h.number===6)')===false,'excluded horse in active field');
// MENU2 actual T20 input and validation.
run(`S.pools.WIN.T20.forEach((r,i)=>r.odds=[2.4,4.2,6.1,8.8,12.5][i])`);
ok(run("winValid('T20')")===true,'MENU2 T20 invalid');ok(run('can(2)')===true,'MENU2->3 blocked');
// MENU3 actual T5 and guard.
run(`S.pools.WIN.T5.forEach((r,i)=>r.odds=[2.7,3.8,5.9,9.4,11.8][i])`);
ok(run("winValid('T5')")===true,'MENU3 T5 invalid');ok(run('runnerGuardOk()')===true,'MENU3 excluded leak');
// MENU4 actual analysis functions using app buildInput/buildPegasusInput.
run(`S.result=RacingAnalysis.analyzeRace(buildInput());S.pegasusResult=PegasusEngine.analyze(buildPegasusInput())`);
ok(run('S.result.ok')===true,'MENU4 legacy analysis failed');ok(run('S.pegasusResult.ok')===true,'MENU4 PEGASUS failed');
const p=run('S.pegasusResult');ok(p.final123.length===3&&new Set(p.final123.map(x=>x.horseNumber)).size===3,'MENU4 final123 invalid');ok(p.final123.every(x=>x.horseNumber!==6),'MENU4 excluded in final123');
for(const k of ['QUINELLA','EXACTA','TRIO','TRIFECTA'])ok(p.finalEight[k].length===2,'MENU4 '+k+' !=2');ok(Object.values(p.finalEight).flat().length===8,'MENU4 Final8 !=8');
JSON.stringify(p,(k,v)=>{if(typeof v==='number')ok(Number.isFinite(v),'MENU4 nonfinite '+k);return v});
// MENU5 output contract actual view rendering.
run('S.step=4');const html=run('resultView()');for(const x of ['PEGASUS 최종 예상','최종 승식 8조합','엔진 비교'])ok(html.includes(x),'MENU5 missing '+x);
// MENU6 actual save payload and immutable-copy check.
run(`S.postVerify={verified:true};save()`);ok(sandbox.__saved,'MENU6 saveAnalysis not called');const before=JSON.stringify(sandbox.__saved.pegasusResult);run('S.postRaceResult={top3:[2,1,3]}');ok(JSON.stringify(sandbox.__saved.pegasusResult)===before,'MENU6 prediction snapshot mutated after actual');
ok(sandbox.__saved.excludedHorses.some(h=>h.number===6),'MENU6 excluded audit missing');ok(!sandbox.__saved.pegasusResult.final123.some(x=>x.horseNumber===6),'MENU6 excluded leaked');
console.log('MENU 1->6 DIRECT APP.JS STATE/RUNTIME E2E PASS');