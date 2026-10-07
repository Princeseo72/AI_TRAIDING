const fs=require('fs');
function assert(c,m){if(!c)throw new Error(m)}
const app=fs.readFileSync('./app/src/main/assets/app.js','utf8');
const css=fs.readFileSync('./app/src/main/assets/style.css','utf8');
for(const t of ['역할형 예상착순','배당형 예상착순','최종 승식 8조합','ML 학습 상태','예측 vs 실제','학습 대기 / 기본모델 사용','최근20','누적','학습시간'])assert(app.includes(t),`${t} missing`);
assert(app.includes("['복병마 1착','인기마 2착','실력마 3착']"),'role labels missing');
assert(app.includes('finalCombinations'),'final combinations UI missing');
assert(app.includes('weightDelta'),'weight delta visual missing');
for(const c of ['predictionGrid','final8','deltaRow','metricStrip','mlMetrics','metricBlock'])assert(css.includes(`.${c}`),`${c} style missing`);
console.log('ML UI CONTRACT TESTS PASSED');

assert(/function historyView\(\)[\s\S]*mlPanel\(\)/.test(app),'history screen ML panel missing');
