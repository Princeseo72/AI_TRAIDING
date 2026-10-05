const A=require('./app/src/main/assets/analysis.js');
function close(a,b,eps=1e-9){if(Math.abs(a-b)>eps)throw new Error(`${a} != ${b}`)}
let q=A.impliedShares([2,4,4]);close(q.reduce((a,b)=>a+b,0),1);close(q[0],0.5);close(q[1],0.25);
let l=A.lmi([.08,.24],[.17,.18]);close(l[0],1.125);close(l[1],-.25);
let r=A.analyze({t20Odds:[7.8,13,3.5,6.2,5.1,15,9.5,22],t5Odds:[7.2,5.1,4.1,6.3,4.8,14.2,8,25],popularity:[3,7,1,5,2,8,4,9]});
if(!r.ok)throw new Error('analysis failed');if(!r.marketCenter||!r.lateMoney)throw new Error('missing core outputs');
let bad=A.analyze({t20Odds:[2,0],t5Odds:[2,3]});if(bad.ok)throw new Error('invalid odds accepted');
console.log('ALL ANALYSIS TESTS PASSED');
