const fs=require('fs');
function assert(c,m){if(!c)throw new Error(m)}
const gumvit=fs.readFileSync('./app/src/main/java/com/kplay/horseracing/GumvitBridge.java','utf8');
const app=fs.readFileSync('./app/src/main/assets/app.js','utf8');
assert(gumvit.includes('resultScratchNumbers'),'must cross-check result page scratch status');
assert(gumvit.includes('exactScratchStatus'),'must use exact scratch status');
assert(gumvit.includes('if(exactScratchStatus(rank))out.add'),'result exclusion must depend on exact rank/status cell');
assert(!gumvit.includes('rankText.startsWith("취") || tr.text().contains'),'broad result-row exclusion logic must be removed');
assert(gumvit.includes('boolean entryScratch=entryExcluded(tr),resultScratch=resultScratches.contains(no),active=!entryScratch&&!resultScratch'),'active state must combine entry/result scratch flags');
assert(app.includes("filter(h=>h.active!==false&&!h.excluded)"),'UI must accept active horses only');
assert(app.includes("S.pools={WIN:{T20:S.horses.map"),'WIN rows must be built from active horses only');
console.log('STRICT EXCLUDED HORSE INPUT GUARD PASSED');
