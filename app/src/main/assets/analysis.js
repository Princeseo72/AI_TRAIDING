(function(global){
  const clamp=(x,a,b)=>Math.max(a,Math.min(b,x));
  function validateOdds(odds){
    if(!Array.isArray(odds)||!odds.length) return {ok:false,errors:['배당 데이터가 없습니다.']};
    const errors=[];
    odds.forEach((v,i)=>{const n=Number(v);if(!Number.isFinite(n)||n<=0) errors.push(`${i+1}번 배당이 0 이하이거나 숫자가 아닙니다.`);if(n>9999) errors.push(`${i+1}번 배당이 비정상적으로 큽니다.`);});
    return {ok:errors.length===0,errors};
  }
  function impliedShares(odds){const inv=odds.map(v=>1/Number(v));const sum=inv.reduce((a,b)=>a+b,0);return inv.map(v=>v/sum);}
  function lmi(earlyShare,lateShare){return earlyShare.map((e,i)=>e>0?(lateShare[i]-e)/e:0);}
  function rank(values,desc=true){return values.map((v,i)=>({i,v})).sort((a,b)=>desc?b.v-a.v:a.v-b.v).map(x=>x.i);}
  function analyze(input){
    const e=validateOdds(input.t20Odds),l=validateOdds(input.t5Odds);
    if(!e.ok||!l.ok) return {ok:false,errors:[...e.errors,...l.errors]};
    if(input.t20Odds.length!==input.t5Odds.length) return {ok:false,errors:['20분 전과 5분 전 출전마 수가 다릅니다.']};
    const n=input.t20Odds.length,q20=impliedShares(input.t20Odds),q5=impliedShares(input.t5Odds),m=lmi(q20,q5);
    const popularity=input.popularity||Array(n).fill(null),cross=input.crossPoolScores||Array(n).fill(0);
    const nums=Array.isArray(input.horseNumbers)&&input.horseNumbers.length===n?input.horseNumbers.map(Number):Array.from({length:n},(_,i)=>i+1);
    const maxL=Math.max(...m.map(x=>Math.abs(x)),0.0001),maxQ=Math.max(...q5,0.0001);
    const score=q5.map((q,i)=>{const pop=Number(popularity[i]);const popScore=Number.isFinite(pop)&&pop>0?1/pop:0;return .50*(q/maxQ)+.30*clamp(m[i]/maxL,-1,1)+.15*clamp(Number(cross[i])||0,-1,1)+.05*popScore;});
    const marketCenter=rank(q5)[0],lateMoney=rank(m)[0],overall=rank(score),sleeper=overall.find(i=>i!==marketCenter&&i!==lateMoney)??overall[0],top=overall.slice(0,Math.min(6,n));
    const exacta=[],quinella=[],trifecta=[],trio=[];
    for(let a=0;a<Math.min(3,top.length);a++)for(let b=0;b<Math.min(4,top.length);b++)if(a!==b){exacta.push([nums[top[a]],nums[top[b]]]);if(top[a]<top[b])quinella.push([nums[top[a]],nums[top[b]]]);}
    const t=top.slice(0,4);
    for(let a=0;a<t.length;a++)for(let b=0;b<t.length;b++)for(let c=0;c<t.length;c++)if(a!==b&&b!==c&&a!==c&&trifecta.length<12)trifecta.push([nums[t[a]],nums[t[b]],nums[t[c]]]);
    for(let a=0;a<t.length;a++)for(let b=a+1;b<t.length;b++)for(let c=b+1;c<t.length;c++)trio.push([nums[t[a]],nums[t[b]],nums[t[c]]]);
    return {ok:true,q20,q5,lmi:m,score,marketCenter:nums[marketCenter],lateMoney:nums[lateMoney],sleeper:nums[sleeper],candidates:{quinella:quinella.slice(0,5),exacta:exacta.slice(0,6),trio:trio.slice(0,5),trifecta:trifecta.slice(0,8)},assumptions:{weights:{lateShare:.50,lmi:.30,crossPool:.15,popularity:.05},note:'후보 점수 가중치는 조정 가능 기본값이며 백테스트 후 보정 대상입니다.'}};
  }
  global.RacingAnalysis={validateOdds,impliedShares,lmi,analyze};
  if(typeof module!=='undefined')module.exports=global.RacingAnalysis;
})(typeof window!=='undefined'?window:globalThis);
