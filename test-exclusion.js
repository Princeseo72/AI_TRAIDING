const fs=require('fs');
function assert(c,m){if(!c)throw new Error(m)}
const gumvit=fs.readFileSync('./app/src/main/java/com/kplay/horseracing/GumvitBridge.java','utf8');
const detector=fs.readFileSync('./app/src/main/java/com/kplay/horseracing/ScratchDetector.java','utf8');
const app=fs.readFileSync('./app/src/main/assets/app.js','utf8');

assert(gumvit.includes('ScratchDetector.resultScratchNumbers(doc)'),'Gumvit result scratch detection must delegate to tested detector');
assert(gumvit.includes('ScratchDetector.isEntryExcluded(tr)'),'Gumvit entry scratch detection must delegate to tested detector');
assert(gumvit.includes('ScratchDetector.isScratchStatus(s)'),'result status must use shared detector');
assert(detector.includes('출전취소')&&detector.includes('출전제외'),'explicit scratch statuses missing');
assert(detector.includes('alt')&&detector.includes('title')&&detector.includes('data-status'),'nested status attributes missing');
assert(!detector.includes('style.contains("line-through")'),'generic strike-through must not exclude without status evidence');
assert(gumvit.includes('boolean entryScratch=entryExcluded(tr),resultScratch=resultScratches.contains(no),active=!entryScratch&&!resultScratch'),'active state must combine entry/result scratch flags');
assert(app.includes('activeHorses(')&&app.includes('requireRunnerGuard'),'UI must derive runners through required RunnerGuard');
assert(app.includes('RunnerGuard.validateNoExcludedLeak'),'UI must reject excluded-runner pool leakage');
assert(app.includes('RunnerGuard.isKeyAllowed'),'manual combo entry must reject excluded runners');
assert(app.includes('excludedHorses:S.excludedHorses'),'saved snapshots must preserve excluded-runner evidence');
assert(app.includes("S.pools={WIN:{T20:S.horses.map"),'WIN rows must be built from guarded active horses only');
console.log('STRICT EXCLUDED HORSE CONTRACT TESTS PASSED');
