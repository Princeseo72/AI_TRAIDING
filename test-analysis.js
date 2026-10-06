const fs=require('fs');
const A=require('./app/src/main/assets/analysis.js');
function assert(c,m){if(!c)throw new Error(m)}
function close(a,b,eps=1e-9){if(Math.abs(a-b)>eps)throw new Error(`${a} != ${b}`)}

let q=A.normalizeSnapshot([{key:'1',odds:2},{key:'2',odds:4},{key:'3',odds:4}]);
close(q.reduce((s,x)=>s+x.share,0),1);close(q[0].share,0.5);close(q[1].share,0.25);
let l=A.computeLmi([{key:'1',share:.08},{key:'2',share:.24}],[{key:'1',share:.17},{key:'2',share:.18}]);
close(l['1'],1.125);close(l['2'],-.25);
assert(A.canonicalKey('QUINELLA','7-3')==='3-7','quinella key not canonicalized');
assert(A.canonicalKey('TRIO','11-3-7')==='3-7-11','trio key not canonicalized');
assert(A.canonicalKey('EXACTA','7>3')==='7>3','exacta direction lost');
assert(A.canonicalKey('TRIFECTA','7>3>11')==='7>3>11','trifecta direction lost');

const input={horseNumbers:[1,3,7,11],popularity:[4,1,5,8],pools:{
WIN:{T20:[{key:'1',odds:8},{key:'3',odds:3.2},{key:'7',odds:12},{key:'11',odds:18}],T5:[{key:'1',odds:8.5},{key:'3',odds:3.4},{key:'7',odds:7},{key:'11',odds:16}]},
QUINELLA:{T20:[{key:'3-7',odds:11},{key:'3-11',odds:18},{key:'1-3',odds:14}],T5:[{key:'3-7',odds:6.2},{key:'3-11',odds:17},{key:'1-3',odds:15}]},
EXACTA:{T20:[{key:'7>3',odds:24},{key:'3>7',odds:15},{key:'3>11',odds:31}],T5:[{key:'7>3',odds:12},{key:'3>7',odds:13},{key:'3>11',odds:29}]}
}};
let r=A.analyzeRace(input);
assert(r.ok,'multi-pool analysis failed');
assert(r.roles.marketCenter.horseNumber===3,'market center should be horse 3');
assert(r.roles.lateMoney.horseNumber===7,'late money should be horse 7');
assert(r.roles.sleeper.horseNumber===7,'cross-pool sleeper should be horse 7');
assert(r.poolResults.QUINELLA.candidates[0].key==='3-7','quinella candidate must come from actual selection');
assert(r.poolResults.EXACTA.candidates[0].key==='7>3','exacta candidate must preserve direction');
assert(r.poolResults.TRIO.status==='INSUFFICIENT_DATA','missing trio must not fabricate candidates');
assert(r.poolResults.TRIFECTA.status==='INSUFFICIENT_DATA','missing trifecta must not fabricate candidates');
for(const k of ['q20','q5','lmi'])assert(Number.isFinite(r.poolResults.QUINELLA.candidates[0].evidence[k]),`candidate ${k} missing`);
assert(Number.isFinite(r.poolResults.QUINELLA.candidates[0].score),'candidate score missing');
assert(r.analysisVersion,'analysis version missing');
let mismatch=A.analyzeRace({horseNumbers:[1,2],pools:{WIN:{T20:[{key:'1',odds:2},{key:'2',odds:3}],T5:[{key:'1',odds:2.5}]}}});
assert(!mismatch.ok,'mismatched mandatory WIN selections should fail');
let bad=A.analyzeRace({horseNumbers:[1,2],pools:{WIN:{T20:[{key:'1',odds:2},{key:'2',odds:0}],T5:[{key:'1',odds:2},{key:'2',odds:3}]}}});
assert(!bad.ok,'invalid odds accepted');

const app=fs.readFileSync('./app/src/main/assets/app.js','utf8');
const html=fs.readFileSync('./app/src/main/assets/index.html','utf8');
for(const label of ['QUINELLA','EXACTA','TRIO','TRIFECTA'])assert(app.includes(label),`UI missing ${label} pool`);
assert(app.includes('analyzeRace'),'UI must call multi-pool analyzeRace');
assert(app.includes('INSUFFICIENT_DATA'),'UI must render insufficient pool state');
assert(!app.includes('세로 붙여넣기'),'vertical paste UI must be removed');
assert(!html.includes('paste=function'),'legacy paste override must be removed');
assert(app.includes('검빛 경주 대조검증'),'post-analysis Gumvit verification menu missing');
assert(app.includes('analysisEpoch'),'reanalysis must invalidate stale async result');
assert(app.includes('loadAnalysis'),'history must support fast reload');
assert(app.includes('빠르게 불러오기'),'history quick reload button missing');
assert(app.includes('fetchRaceResult'),'post-race result fetch UI missing');
assert(app.includes('경주결과 대조'),'post-race comparison menu missing');
assert(app.includes('착순'),'post-race finish display missing');
assert(app.includes('확정배당'),'post-race payout display missing');
assert(app.includes('healthCheck'),'maintenance/performance check missing');
assert(app.includes('cleanupData'),'data cleanup missing');
assert(app.includes('closeApp'),'exit control missing');

const gumvit=fs.readFileSync('./app/src/main/java/com/kplay/horseracing/GumvitBridge.java','utf8');
for(const token of ['requestedDate','actualDate','requestedRaceNo','actualRaceNo','excluded','active'])assert(gumvit.includes(token),`Gumvit validation missing ${token}`);
assert(gumvit.includes('경주 정보 불일치'),'Gumvit must reject mismatched race page');
assert(gumvit.includes('select("s,strike')||gumvit.includes("select(\"s,strike"),'scratch detection must inspect descendant strike/s tags');
assert(gumvit.includes('fetchRaceResult'),'GumvitBridge must fetch official race result page');
assert(gumvit.includes('result_detail.html'),'GumvitBridge must use result detail endpoint');
assert(gumvit.includes('payouts'),'result parser must expose payouts');
assert(gumvit.includes('finishers'),'result parser must expose finishers');

const db=fs.readFileSync('./app/src/main/java/com/kplay/horseracing/RaceDbHelper.java','utf8');
const storage=fs.readFileSync('./app/src/main/java/com/kplay/horseracing/StorageBridge.java','utf8');
for(const table of ['races','horses','pool_snapshots','selection_metrics','horse_metrics','analysis_results','analysis_history','race_outcomes'])assert(db.includes(`CREATE TABLE ${table}`),`DB missing ${table}`);
assert(/DB_VERSION\s*=\s*[3-9]/.test(db),'DB schema version must include race outcomes');
assert(storage.includes('"approved".equals'),'saveAnalysis must explicitly require approved status');
assert(storage.includes('analysisVersion'),'saveAnalysis must require analysis version');
for(const method of ['getAnalysis','healthCheck','cleanupData','attachRaceResult'])assert(storage.includes(method),`StorageBridge missing ${method}`);

const main=fs.readFileSync('./app/src/main/java/com/kplay/horseracing/MainActivity.java','utf8');
assert(main.includes('AndroidApp'),'MainActivity must expose app control bridge');
assert(main.includes('finishAndRemoveTask')||main.includes('finishAffinity'),'app control must support real exit');

console.log('ALL MULTI-POOL ANALYSIS TESTS PASSED');
