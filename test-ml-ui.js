const fs=require('fs');
function assert(c,m){if(!c)throw new Error(m)}
const app=fs.readFileSync('./app/src/main/assets/app.js','utf8');
const css=fs.readFileSync('./app/src/main/assets/style.css','utf8');
for(const t of ['PEGASUS 최종 예상','펀더멘털 예상','시장·배당 예상','최종 승식 8조합','엔진 비교','TOP5 확률','ML 학습 상태','예측 vs 실제','학습 대기 / 기본모델 사용','최근20','누적','학습시간'])assert(app.includes(t),`${t} missing`);
assert(app.includes('finalEight'),'final eight UI missing');
assert(app.includes('weightDelta'),'weight delta visual missing');
for(const c of ['predictionGrid','final8','deltaRow','metricStrip','mlMetrics','metricBlock'])assert(css.includes(`.${c}`),`${c} style missing`);
console.log('ML UI CONTRACT TESTS PASSED');

assert(/function historyView\(\)[\s\S]*mlPanel\(\)/.test(app),'history screen ML panel missing');
