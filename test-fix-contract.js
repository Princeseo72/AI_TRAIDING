const fs=require('fs');
function assert(c,m){if(!c)throw new Error(m)}
const fix=fs.readFileSync('./fix.md','utf8');
const ml=fs.readFileSync('./app/src/main/assets/ml.js','utf8');
const app=fs.readFileSync('./app/src/main/assets/app.js','utf8');
for(const x of ['FIX-001','FIX-002','FIX-003','FIX-004','FIX-005','Historical Replay','Self Diagnosis','Release Gate'])assert(fix.includes(x),'fix.md missing '+x);
assert(app.includes('finishPrediction=(S.pegasusResult?.final123||[])'),'app must learn from PEGASUS final123');
assert(ml.includes("pred?.finishPrediction||pred?.oddsPrediction"),'ML evaluation must prefer finishPrediction');
assert(ml.includes("source:'finishPrediction'"),'finish targets must use finishPrediction');
assert(!ml.includes("first:+(role[0]===top[0])"),'role tag must not be scored as finish position');
console.log('FIX CONTRACT TESTS PASSED');

const learnDoc=fs.readFileSync('./docs/학습엔진_변경기술서_v3.md','utf8');
assert(learnDoc.includes('MANUAL_CONFIRMED_OUTCOME'),'manual learning contract missing');
assert(app.includes('forceLearnFromManualOutcome'),'manual learning UI/runtime missing');
assert(app.includes('contextSignalsForAnalysis'),'next-race context serving missing');
for(const k of ['horseRating','jockeyRating','trainerRating','regionalPrior','historicalPrior'])assert(ml.includes(k),'v3 learned feature missing '+k);

const v4=fs.readFileSync('./docs/엔진수정설계도_v4.md','utf8');
const peg=fs.readFileSync('./app/src/main/assets/pegasus-engine.js','utf8');
const storeV4=fs.readFileSync('./app/src/main/java/com/kplay/horseracing/StorageBridge.java','utf8');
for(const x of ['T20=시장 기준선','T5=late state','HIST_READY 금지','Final8 exactly=8'])assert(v4.includes(x),'v4 design lock missing '+x);
for(const x of ['movementEvidence','shareDelta','relativeLmi','rankShift','READINESS_FALLBACK'])assert(peg.includes(x),'v4 market engine missing '+x);
assert(storeV4.includes('recent1y'),'regional 1y materialization missing');
assert(storeV4.includes('UNVERIFIED'),'champion verification state missing');
assert(storeV4.includes('INSUFFICIENT_FIELDS'),'track readiness gate missing');

const histRepo=fs.readFileSync('./app/src/main/java/com/kplay/horseracing/HistRepository.java','utf8');
const histBridge=fs.readFileSync('./app/src/main/java/com/kplay/horseracing/HistBridge.java','utf8');
const mainV6=fs.readFileSync('./app/src/main/java/com/kplay/horseracing/MainActivity.java','utf8');
const buildV6=fs.readFileSync('./app/build.gradle','utf8');
for(const x of ['PEGASUS_HIST_V1','OPEN_READONLY','pegasus_hist','WIN','PLACE','QUINELLA','EXACTA','QUINELLA_PLACE','TRIO','TRIFECTA','race_date<?','winRateBayes','top3RateBayes'])assert(histRepo.includes(x),'v6 HIST contract missing '+x);
assert(histBridge.includes('ACTION_OPEN_DOCUMENT'),'v6 HIST file picker missing');
assert(mainV6.includes('AndroidHist'),'v6 HIST bridge missing');
assert(app.includes('5년 HIST 파일 선택')&&app.includes('5년 HIST 재학습'),'v6 HIST utility UI missing');
assert(app.includes('PEGASUS_HIST_V1')&&app.includes('horsePriors:Object.fromEntries')&&app.includes('AndroidHist.getHorsePriors'),'v6 HIST prior injection missing');
assert(buildV6.includes("versionName '6.0.0'")&&buildV6.includes('crosspool.v600'),'v6 package identity missing');

for(const x of ['replayTrain','WALK_FORWARD_V1','r.race_date<?','brierModel','brierMarket','CHAMPION_ELIGIBLE','CHALLENGER_ONLY'])assert(histRepo.includes(x),'v6 replay gate missing '+x);
assert(histBridge.includes('replayTrain'),'HIST training must execute replay validation');

const histStore=fs.readFileSync('./app/src/main/java/com/kplay/horseracing/HistModelStore.java','utf8');
assert(histRepo.includes('u.horse_name=?')&&!histRepo.includes('historicalHorsePrior(d,e.getKey()'),'Replay must learn horse identity, never recurring race number');
for(const x of ['poolOutcomeModels','posteriorHitRate','FIRST2_ORDERED','TOP3_ORDERED'])assert(histRepo.includes(x),'7-pool outcome model missing '+x);
for(const x of ['HIST_CHAMPION.json','HIST_CHALLENGER.json','writeAtomic'])assert(histStore.includes(x),'HIST model persistence missing '+x);
assert(histBridge.includes('getChampion')&&histBridge.includes('saveCandidate'),'HIST champion serving/promotion missing');
assert(app.includes('histChampion'),'HIST champion not passed to live engine');

for(const x of ['oddsCoverage','expectedSelections','minimumCoverage'])assert(histRepo.includes(x),'7-pool completeness gate missing '+x);
assert(histBridge.includes('coverage.optBoolean("complete")'),'incomplete 7-pool HIST must never promote Champion');

assert(histRepo.includes('importFromCollector')&&histRepo.includes('com.kplay.pegasus.histcollector.hist/current'),'Pegasus must pull existing collector DB directly');
assert(histBridge.includes('importFromCollector'),'collector direct-import bridge missing');
for(const x of ['histCollector','importCollectorAndTrain','AndroidHist.startCollectorImportAsync','AndroidHist.startTrainAsync'])assert(app.includes(x),'non-blocking collector-to-Replay wiring missing '+x);
assert(histBridge.includes('startCollectorImportAsync')&&histBridge.includes('startTrainAsync'),'native async HIST utility bridge missing');
