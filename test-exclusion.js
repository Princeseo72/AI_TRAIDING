const fs=require('fs');
function assert(c,m){if(!c)throw new Error(m)}
const gumvit=fs.readFileSync('./app/src/main/java/com/kplay/horseracing/GumvitBridge.java','utf8');
const app=fs.readFileSync('./app/src/main/assets/app.js','utf8');
assert(gumvit.includes('excludedNumbersFromResult'),'must cross-check result page exclusions');
assert(gumvit.includes('rankText.startsWith("취")'),'must detect result rank 취');
assert(gumvit.includes('resultExcluded.contains(number)'),'result-page excluded horse must be inactive');
assert(gumvit.includes('boolean active = !isExcluded(tr) && !resultPageExcluded'),'excluded horse must not enter active list');
assert(app.includes("filter(h=>h.active!==false&&!h.excluded)"),'UI must accept active horses only');
assert(app.includes("S.pools={WIN:{T20:S.horses.map"),'WIN T20/T5 rows must be built from active horses only');
console.log('EXCLUDED HORSE INPUT GUARD PASSED');
