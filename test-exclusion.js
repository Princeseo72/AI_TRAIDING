const fs=require('fs');
function assert(c,m){if(!c)throw new Error(m)}
const gumvit=fs.readFileSync('./app/src/main/java/com/kplay/horseracing/GumvitBridge.java','utf8');
const detector=fs.readFileSync('./app/src/main/java/com/kplay/horseracing/ScratchDetector.java','utf8');
const parser=fs.readFileSync('./app/src/main/java/com/kplay/horseracing/GumvitPageParser.java','utf8');
const app=fs.readFileSync('./app/src/main/assets/app.js','utf8');

assert(gumvit.includes('ScratchDetector.resultScratchNumbers(doc)'),'Gumvit result scratch detection must delegate to tested detector');
assert(parser.includes('ScratchDetector.isEntryExcluded(tr)'),'Gumvit entry scratch detection must delegate to tested detector');
assert(gumvit.includes('ScratchDetector.isScratchStatus(s)'),'result status must use shared detector');
assert(detector.includes('출전취소')&&detector.includes('출전제외'),'explicit scratch statuses missing');
assert(detector.includes('alt')&&detector.includes('title')&&detector.includes('data-status'),'nested status attributes missing');
assert(!detector.includes('style.contains("line-through")'),'generic strike-through must not exclude without status evidence');
assert(gumvit.includes('gumvitChangeScratches(date,region,raceNo)'),'public Gumvit change bulletin must be consulted');
assert(gumvit.includes('resultScratches.addAll(kraScratches)'),'Gumvit and KRA exclusion evidence must be unioned');
assert(gumvit.includes('boolean resultScratch=resultScratches.contains(no),active=!e.entryExcluded&&!resultScratch'),'active state must combine shared entry/change scratch flags');
assert(app.includes('activeHorses(')&&app.includes('requireRunnerGuard'),'UI must derive runners through required RunnerGuard');
assert(app.includes('RunnerGuard.validateNoExcludedLeak'),'UI must reject excluded-runner pool leakage');
assert(app.includes('RunnerGuard.isKeyAllowed'),'manual combo entry must reject excluded runners');
assert(app.includes('excludedHorses:S.excludedHorses'),'saved snapshots must preserve excluded-runner evidence');
assert(app.includes("S.pools={WIN:{T20:S.horses.map"),'WIN rows must be built from guarded active horses only');
assert(!app.includes('manualExclude'),'정상 출전마 행에 수동 제외 버튼을 노출하면 안 됨');
assert(!app.includes('manuallyExcludeHorse'),'수동 제외 상태가 자동 검증 로직을 덮어쓰면 안 됨');
assert(!app.includes('manualExcludedNumbers'),'수동 제외 상태는 자동 출전마 검증과 분리해야 함');
assert(!detector.includes('RESULT_ROW_SCRATCH'),'page-global result regex must not drive exclusion');
assert(!detector.includes('DECISION_NUMBER'),'page-global decision regex must not drive exclusion');
assert(!detector.includes('REPORT_CIRCLED'),'circled-number page-global inference must not drive exclusion');
console.log('STRICT EXCLUDED HORSE CONTRACT TESTS PASSED');

assert(!app.includes('출전취소/제외마 —'),'excluded runner list must stay hidden from race UI');
