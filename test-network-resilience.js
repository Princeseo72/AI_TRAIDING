const fs=require('fs');
function assert(c,m){if(!c)throw new Error(m)}
const gum=fs.readFileSync('./app/src/main/java/com/kplay/horseracing/GumvitBridge.java','utf8');
const app=fs.readFileSync('./app/src/main/assets/app.js','utf8');
assert(gum.includes('ThisWeekChulmapyoChange.do'),'KRA weekly change fallback missing');
assert(gum.includes('RACE_CACHE_TTL_MS = 30_000L'),'race cache TTL missing');
assert(gum.includes('KRA_CACHE_TTL_MS = 60_000L'),'KRA cache TTL missing');
assert(gum.includes('getSupplement(url)'),'supplement request path missing');
assert(gum.includes('timeout(timeoutMs)'),'network timeout missing');
assert(gum.includes('for(int i=0;i<2;i++)'),'primary retry must be bounded to one retry');
assert(gum.includes('catch(Exception ignored)'),'supplement failure must be isolated');
assert(!app.includes('출전취소/제외마 —'),'excluded runner list must stay hidden');
console.log('NETWORK RESILIENCE CONTRACT TESTS PASSED');