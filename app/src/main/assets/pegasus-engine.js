(function(global){
'use strict';
const VERSION='PEGASUS-ENGINE-v1';
const VERSIONS={marketVersion:'MARKET-v2',fundamentalVersion:'FUND-v1',blendVersion:'BLEND-FALLBACK-v1',calibrationVersion:'CAL-PENDING-v1',orderedFinishVersion:'ORDERED-PL-FALLBACK-v1'};
const EPS=1e-12,clamp=(x,a,b)=>Math.max(a,Math.min(b,x));
const sum=a=>a.reduce((s,x)=>s+x,0);
const normalize=o=>{const vals=Object.values(o).map(v=>Math.max(EPS,Number(v)||0)),z=sum(vals)||1,out={};Object.keys(o).forEach((k,i)=>out[k]=vals[i]/z);return out};
const softmax=o=>{const ks=Object.keys(o),m=Math.max(...ks.map(k=>Number(o[k])||0)),e={};ks.forEach(k=>e[k]=Math.exp((Number(o[k])||0)-m));return normalize(e)};
function parseRecord(s){const m=String(s||'').match(/(\d+)\s*전\s*\(\s*(\d+)\s*\/\s*(\d+)\s*\)/);return m?{starts:+m[1],wins:+m[2],places:+m[3]}:{starts:0,wins:0,places:0}}
function marketSnapshot(rows){const inv={};for(const r of rows||[]){const o=Number(r.odds);if(!(o>0))throw new Error('유효하지 않은 단승 배당');inv[String(r.key)]=1/o}return normalize(inv)}
function marketLayer(pools){
 const t20=marketSnapshot(pools?.WIN?.T20),t5=marketSnapshot(pools?.WIN?.T5),lmi={};
 for(const k of Object.keys(t5))lmi[k]=t20[k]>0?(t5[k]-t20[k])/t20[k]:0;
 return{status:'READY',p20:t20,p1:t5,lmi,rank:rankProb(t5)};
}
function rankProb(p){return Object.entries(p).sort((a,b)=>b[1]-a[1]||(+a[0]-+b[0])).map(([horseNumber,probability],i)=>({horseNumber:+horseNumber,position:i+1,probability}))}
function ratingSignal(context,horse){
 const rs=context?.ratingState||{};
 const byHorse=rs.horses||{};
 const x=byHorse[String(horse.number)]||byHorse[horse.number];
 if(!x||!Number.isFinite(Number(x.mu)))return{value:0,status:'PENDING'};
 return{value:clamp(Number(x.mu)/10,-1,1),status:'READY',sigma:Number(x.sigma)};
}
function fundamentalLayer(horses,context){
 const raw={},evidence={};
 for(const h of horses){
  const r=parseRecord(h.record),starts=Math.max(1,r.starts),winRate=r.wins/starts,placeRate=(r.wins+r.places)/starts;
  const pop=Number(h.popularity),popPrior=Number.isFinite(pop)&&pop>0?1/pop:0;
  const rating=ratingSignal(context,h);
  const base=.52*winRate+.24*placeRate+.10*popPrior+.14*(.5+.5*rating.value);
  raw[String(h.number)]=Math.max(EPS,base);
  evidence[String(h.number)]={record:r,winRate,placeRate,popularityPrior:popPrior,rating};
 }
 const p1=normalize(raw);
 return{status:'AVAILABLE_FEATURES_ONLY',p1,rank:rankProb(p1),evidence,limitations:['5년 Hist 미연결 시 현재 출전표 전적/인기도/저장 Rating만 사용']};
}
function blendLayer(model,market,context){
 const trained=context?.blendModel;
 let a=.30,b=.70,status='DEFAULT_FALLBACK';
 if(trained&&trained.status==='ACTIVE'&&Number.isFinite(+trained.a)&&Number.isFinite(+trained.b)){a=+trained.a;b=+trained.b;status='TRAINED'}
 const u={};for(const k of Object.keys(market)){u[k]=a*Math.log(Math.max(EPS,model[k]||EPS))+b*Math.log(Math.max(EPS,market[k]||EPS))}
 return{status,a,b,p1:softmax(u)};
}
function calibrationLayer(blend,context){
 const cal=context?.calibrationModel;
 if(!cal||cal.status!=='ACTIVE')return{status:'CALIBRATION_PENDING',method:'IDENTITY_FALLBACK',p1:{...blend}};
 const strength=clamp(Number(cal?.params?.strength)||0,0,.5),n=Object.keys(blend).length,uniform=n?1/n:0,raw={};
 for(const k of Object.keys(blend))raw[k]=(1-strength)*blend[k]+strength*uniform;
 return{status:'CALIBRATED',method:cal.method||'stored',strength,p1:normalize(raw)};
}
function orderedLayer(p1){
 const horses=Object.keys(p1).map(Number),scenarios=[];
 for(const i of horses)for(const j of horses)if(j!==i)for(const k of horses)if(k!==i&&k!==j){
   const pi=p1[i],den2=Math.max(EPS,1-pi),pj=p1[j]/den2,den3=Math.max(EPS,1-pi-p1[j]),pk=p1[k]/den3;
   scenarios.push({order:[i,j,k],probability:pi*pj*pk});
 }
 scenarios.sort((a,b)=>b.probability-a.probability||a.order.join('-').localeCompare(b.order.join('-')));
 const p2={},p3={};horses.forEach(h=>{p2[h]=0;p3[h]=0});
 scenarios.forEach(s=>{p2[s.order[1]]+=s.probability;p3[s.order[2]]+=s.probability});
 return{status:'PL_FALLBACK',correction:'UNTRAINED_POSITION_CORRECTION',p1:{...p1},p2:normalize(p2),p3:normalize(p3),scenarios};
}
function keyUnordered(a){return a.slice().sort((x,y)=>x-y).join('-')}
function aggregateCombos(ordered){
 const q={},e={},t={},tf={};
 for(const s of ordered.scenarios){
  const [a,b,c]=s.order,p=s.probability;
  const qk=keyUnordered([a,b]),ek=a+'>'+b,tk=keyUnordered([a,b,c]),tfk=a+'>'+b+'>'+c;
  q[qk]=(q[qk]||0)+p;e[ek]=(e[ek]||0)+p;t[tk]=(t[tk]||0)+p;tf[tfk]=(tf[tfk]||0)+p;
 }
 const top=(pool,o)=>Object.entries(o).sort((a,b)=>b[1]-a[1]).slice(0,2).map(([key,probability])=>({pool,key,probability,source:'ORDERED_PROBABILITY',ev:null,evStatus:'ODDS_REQUIRED'}));
 return{QUINELLA:top('QUINELLA',q),EXACTA:top('EXACTA',e),TRIO:top('TRIO',t),TRIFECTA:top('TRIFECTA',tf)};
}
function positionFinal(ordered){
 const first=rankProb(ordered.p1)[0],used=new Set([first.horseNumber]);
 const second=rankProb(ordered.p2).find(x=>!used.has(x.horseNumber));used.add(second.horseNumber);
 const third=rankProb(ordered.p3).find(x=>!used.has(x.horseNumber));
 const best=ordered.scenarios[0];
 return best?best.order.map((h,i)=>({horseNumber:h,position:i+1,probability:i===0?ordered.p1[h]:i===1?ordered.p2[h]:ordered.p3[h],jointProbability:best.probability})):[first,second,third];
}
function uncertainty(context,market,fund){
 const hist=context?.historicalPrior?.status,reg=context?.regionalProfile?.sampleCount||0,spread=Object.keys(market).reduce((s,k)=>s+Math.abs((market[k]||0)-(fund[k]||0)),0)/2;
 let level='MID',reasons=[];if(hist==='HIST_PENDING'){level='LOW';reasons.push('5년 Hist 미연결')}if(reg<20){level='LOW';reasons.push('지역 표본 부족')}if(spread>.35){level='LOW';reasons.push('시장/펀더멘털 불일치 큼')}return{level,reasons,modelDisagreement:spread};
}
function analyze(input){
 try{
  const horses=(input?.horses||[]).filter(h=>h&&h.active!==false&&h.excluded!==true);
  if(horses.length<3)return{ok:false,errors:['유효 출전마가 3두 미만']};
  const active=new Set(horses.map(h=>+h.number));
  for(const r of [...(input?.pools?.WIN?.T20||[]),...(input?.pools?.WIN?.T5||[])])if(!active.has(+r.key))return{ok:false,errors:['제외/비활성 마번 배당 유입']};
  const market=marketLayer(input.pools),fundamental=fundamentalLayer(horses,input.preRaceContext||{}),blend=blendLayer(fundamental.p1,market.p1,input.preRaceContext||{}),calibration=calibrationLayer(blend.p1,input.preRaceContext||{}),ordered=orderedLayer(calibration.p1),final123=positionFinal(ordered),finalEight=aggregateCombos(ordered),unc=uncertainty(input.preRaceContext||{},market.p1,fundamental.p1);
  return{ok:true,engineVersion:VERSION,versions:{...VERSIONS},market,fundamental,blend,calibration,ordered,final123,finalEight,uncertainty:unc,
   engineCompare:{market:market.rank.slice(0,3),fundamental:fundamental.rank.slice(0,3),final:ordered.scenarios[0]?.order.map((h,i)=>({horseNumber:h,position:i+1}))||[]},
   contextVersion:input.preRaceContext?.contextVersion||null,histStatus:input.preRaceContext?.historicalPrior?.status||'HIST_PENDING'};
 }catch(e){return{ok:false,errors:[String(e?.message||e)]}}
}
global.PegasusEngine={analyze,marketSnapshot,parseRecord,VERSION,VERSIONS};
if(typeof module!=='undefined')module.exports=global.PegasusEngine;
})(typeof window!=='undefined'?window:globalThis);
