const fs=require('fs');
function assert(c,m){if(!c)throw new Error(m)}
const app=fs.readFileSync('./app/src/main/assets/app.js','utf8');
const gum=fs.readFileSync('./app/src/main/java/com/kplay/horseracing/GumvitBridge.java','utf8');
const store=fs.readFileSync('./app/src/main/java/com/kplay/horseracing/StorageBridge.java','utf8');
const main=fs.readFileSync('./app/src/main/java/com/kplay/horseracing/MainActivity.java','utf8');
const checks=[
 ['경주 선택',"steps=['경주 선택'"],
 ['20분 입력','20분 전 배당'],['5분 입력','5분 전 배당'],['재분석','분석 재실행'],
 ['역할형 예측','역할형 예상착순'],['배당형 예측','배당형 예상착순'],['8조합','최종 승식 8조합'],
 ['검빛 대조','검빛 경주 대조검증'],['경주결과 대조','경주결과 대조'],['ML 학습','ML 학습 상태'],
 ['빠른 불러오기','빠르게 불러오기'],['성능 재검증','성능 재검증'],['데이터 정리','데이터 정리'],['종료','종료']
];
for(const [n,t] of checks)assert(app.includes(t),`${n} menu missing`);
for(const fn of ['fetchRace','verifyRace','fetchRaceResult'])assert(gum.includes(fn),`Gumvit ${fn} missing`);
assert(gum.includes('exactScratchStatus'),'strict scratch status missing');
assert(!gum.includes('rankText.startsWith("취") || tr.text().contains'),'legacy broad result scratch logic remains');
for(const fn of ['getAnalysis','healthCheck','cleanupData','attachRaceResult','getActiveModel','getMlStatus','recordTrainingEvent'])assert(store.includes(fn),`Storage ${fn} missing`);
assert(main.includes('finishAndRemoveTask')||main.includes('finishAffinity'),'real exit missing');
assert(app.includes('analysisEpoch'),'stale reanalysis guard missing');
assert(app.includes('refreshField('),'race refresh guard missing');
assert(app.includes('trainFromResult'),'result-to-learning pipeline missing');
assert(app.includes('finalCombinations'),'eight-combination UI missing');
console.log('MENU/BRIDGE CONTRACT TESTS PASSED');
