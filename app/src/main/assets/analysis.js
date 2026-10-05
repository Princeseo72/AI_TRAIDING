(function(global){
  const POOLS=['WIN','QUINELLA','EXACTA','TRIO','TRIFECTA'];
  const CFG={currentShareWeight:.45,lmiWeight:.30,divergenceWeight:.15,structureWeight:.08,popularityWeight:.02};
  const clamp=(x,a,b)=>Math.max(a,Math.min(b,x));
  const num=v=>Number(v);
  function splitKey(key){return String(key??'').trim().split(/[>\-]/).map(x=>Number(x.trim())).filter(Number.isFinite)}
  function canonicalKey(poolType,key){
    const t=String(poolType||'').toUpperCase(), a=splitKey(key);
    if(!a.length)return '';
    if(t==='WIN')return String(a[0]);
    if(t==='QUINELLA'||t==='TRIO')return a.slice().sort((x,y)=>x-y).join('-');
    if(t==='EXACTA'||t==='TRIFECTA')return a.join('>');
    return String(key||'').trim();
  }
  function validateEntries(entries,poolType){
    if(!Array.isArray(entries)||!entries.length)return {ok:false,errors:['배당 데이터가 없습니다.']};
    const seen=new Set(),errors=[];
    entries.forEach((e,i)=>{
      const k=canonicalKey(poolType,e&&e.key),o=num(e&&e.odds);
      if(!k)errors.push(`${i+1}번째 조합 키가 비어 있습니다.`);
      if(seen.has(k))errors.push(`중복 조합: ${k}`); else seen.add(k);
      if(!Number.isFinite(o)||o<=0)errors.push(`${k||i+1} 배당이 0 이하이거나 숫자가 아닙니다.`);
      if(o>9999)errors.push(`${k||i+1} 배당이 비정상적으로 큽니다.`);
    });
    return {ok:errors.length===0,errors};
  }
  function normalizeSnapshot(entries,poolType='WIN'){
    const v=validateEntries(entries,poolType); if(!v.ok)throw new Error(v.errors.join(' | '));
    const rows=entries.map(e=>({key:canonicalKey(poolType,e.key),odds:num(e.odds)}));
    const inv=rows.map(r=>1/r.odds),sum=inv.reduce((a,b)=>a+b,0);
    return rows.map((r,i)=>({...r,share:inv[i]/sum}));
  }
  function computeLmi(t20,t5){
    const a=new Map(t20.map(x=>[String(x.key),num(x.share)])),b=new Map(t5.map(x=>[String(x.key),num(x.share)]));
    const out={};
    for(const [k,e] of a){if(b.has(k)&&e>0)out[k]=(b.get(k)-e)/e}
    return out;
  }
  function sameKeys(a,b){const x=[...new Set(a.map(r=>String(r.key)))].sort(),y=[...new Set(b.map(r=>String(r.key)))].sort();return x.length===y.length&&x.every((k,i)=>k===y[i])}
  function rankRows(rows,field){return rows.slice().sort((a,b)=>(b[field]??-Infinity)-(a[field]??-Infinity))}
  function poolAnalysis(poolType,pool){
    if(!pool||!Array.isArray(pool.T20)||!Array.isArray(pool.T5)||!pool.T20.length||!pool.T5.length)return {status:'INSUFFICIENT_DATA',candidates:[]};
    const v20=validateEntries(pool.T20,poolType),v5=validateEntries(pool.T5,poolType);
    if(!v20.ok||!v5.ok)return {status:'INVALID_DATA',errors:[...v20.errors,...v5.errors],candidates:[]};
    const c20=pool.T20.map(e=>({...e,key:canonicalKey(poolType,e.key)})),c5=pool.T5.map(e=>({...e,key:canonicalKey(poolType,e.key)}));
    if(!sameKeys(c20,c5))return {status:'MISMATCHED_SELECTIONS',candidates:[]};
    const q20=normalizeSnapshot(c20,poolType),q5=normalizeSnapshot(c5,poolType),lmi=computeLmi(q20,q5);
    const m20=new Map(q20.map(x=>[x.key,x])),maxQ=Math.max(...q5.map(x=>x.share),1e-9),maxL=Math.max(...Object.values(lmi).map(x=>Math.abs(x)),1e-9);
    const rows=q5.map(x=>({key:x.key,horses:splitKey(x.key),q20:m20.get(x.key).share,q5:x.share,lmi:lmi[x.key]}));
    rows.forEach(r=>{r.baseScore=.62*(r.q5/maxQ)+.38*clamp(r.lmi/maxL,-1,1)});
    return {status:'OK',q20,q5,lmi,rows,candidates:[]};
  }
  function horseUniverse(input,win){
    if(Array.isArray(input.horseNumbers)&&input.horseNumbers.length)return input.horseNumbers.map(Number).filter(Number.isFinite);
    return win.rows.map(r=>Number(r.key)).filter(Number.isFinite);
  }
  function analyzeRace(input){
    try{
      const pools=input&&input.pools||{},win=poolAnalysis('WIN',pools.WIN);
      if(win.status!=='OK')return {ok:false,errors:[`WIN ${win.status}`].concat(win.errors||[])};
      const universe=horseUniverse(input,win),popularity=Array.isArray(input.popularity)?input.popularity:[];
      const popByHorse=new Map(universe.map((h,i)=>[h,num(popularity[i])]));
      const analyses={WIN:win};
      for(const p of POOLS.slice(1))analyses[p]=poolAnalysis(p,pools[p]);

      const winByHorse=new Map(win.rows.map(r=>[Number(r.key),r]));
      const maxWin=Math.max(...win.rows.map(r=>r.q5),1e-9),maxWinL=Math.max(...win.rows.map(r=>Math.abs(r.lmi)),1e-9);
      const metrics={}; universe.forEach(h=>metrics[h]={horseNumber:h,comboSum:0,comboCount:0,comboLateSum:0,leadSum:0,poolHits:0});
      for(const p of POOLS.slice(1)){
        const pa=analyses[p]; if(pa.status!=='OK')continue;
        const maxQ=Math.max(...pa.rows.map(r=>r.q5),1e-9),maxL=Math.max(...pa.rows.map(r=>Math.abs(r.lmi)),1e-9);
        pa.rows.forEach(r=>{
          const q=r.q5/maxQ,late=Math.max(0,r.lmi/maxL);
          r.horses.forEach((h,idx)=>{
            if(!metrics[h])metrics[h]={horseNumber:h,comboSum:0,comboCount:0,comboLateSum:0,leadSum:0,poolHits:0};
            const m=metrics[h];m.comboSum+=q;m.comboLateSum+=late;m.comboCount++;m.poolHits++;
            if((p==='EXACTA'||p==='TRIFECTA')&&idx===0)m.leadSum+=q;
          });
        });
      }
      for(const h of Object.keys(metrics).map(Number)){
        const m=metrics[h],w=winByHorse.get(h);
        m.winEvidence=w?w.q5/maxWin:0;
        m.winLate=w?clamp(w.lmi/maxWinL,-1,1):0;
        const comboBase=m.comboCount?m.comboSum/m.comboCount:0,comboLate=m.comboCount?m.comboLateSum/m.comboCount:0;
        m.comboEvidence=clamp(.55*comboBase+.35*comboLate+.10*clamp(m.leadSum/Math.max(1,m.comboCount),0,1),0,1);
        m.divergence=m.comboEvidence-m.winEvidence;
        m.structure=clamp(m.poolHits/Math.max(1,POOLS.length-1),0,1);
      }

      const winRank=rankRows(win.rows,'q5'),lmiRank=rankRows(win.rows,'lmi');
      const marketCenter=Number(winRank[0].key),lateMoney=Number(lmiRank[0].key),fade=Number(win.rows.slice().sort((a,b)=>a.lmi-b.lmi)[0].key);
      const sleeper=Object.values(metrics).slice().sort((a,b)=>b.divergence-a.divergence)[0]?.horseNumber??marketCenter;
      const structuralAnchor=Object.values(metrics).slice().sort((a,b)=>(b.structure+b.comboEvidence)-(a.structure+a.comboEvidence))[0]?.horseNumber??marketCenter;

      function roleObj(h,type){const w=winByHorse.get(h),m=metrics[h]||{};return {horseNumber:h,type,q20:w?.q20??null,q5:w?.q5??null,lmi:w?.lmi??null,divergence:m.divergence??0,comboEvidence:m.comboEvidence??0,structure:m.structure??0}}
      const roles={marketCenter:roleObj(marketCenter,'MARKET_CENTER'),lateMoney:roleObj(lateMoney,'LATE_MONEY'),fade:roleObj(fade,'FADE'),sleeper:roleObj(sleeper,'SLEEPER'),structuralAnchor:roleObj(structuralAnchor,'STRUCTURAL_ANCHOR')};

      const poolResults={};
      for(const p of POOLS){
        const pa=analyses[p];
        if(pa.status!=='OK'){poolResults[p]={status:pa.status,candidates:[],errors:pa.errors||[]};continue}
        const maxQ=Math.max(...pa.rows.map(r=>r.q5),1e-9),maxL=Math.max(...pa.rows.map(r=>Math.abs(r.lmi)),1e-9);
        const cands=pa.rows.map(r=>{
          const horses=r.horses,div=horses.length?horses.reduce((s,h)=>s+(metrics[h]?.divergence||0),0)/horses.length:0;
          const structure=horses.length?horses.reduce((s,h)=>s+(metrics[h]?.structure||0),0)/horses.length:0;
          const pops=horses.map(h=>popByHorse.get(h)).filter(x=>Number.isFinite(x)&&x>0),popScore=pops.length?pops.reduce((s,x)=>s+1/x,0)/pops.length:0;
          const score=CFG.currentShareWeight*(r.q5/maxQ)+CFG.lmiWeight*clamp(r.lmi/maxL,-1,1)+CFG.divergenceWeight*clamp(div,-1,1)+CFG.structureWeight*structure+CFG.popularityWeight*popScore;
          return {key:r.key,horses,score,evidence:{q20:r.q20,q5:r.q5,lmi:r.lmi,divergence:div,structure,popularity:popScore}};
        }).sort((a,b)=>b.score-a.score);
        poolResults[p]={status:'OK',candidates:cands.slice(0,p==='WIN'?Math.min(6,cands.length):Math.min(8,cands.length))};
      }
      const insufficientDataPools=POOLS.filter(p=>poolResults[p].status!=='OK');
      return {ok:true,analysisVersion:'2.0.0',configVersion:'cross-pool-v1',config:{...CFG},roles,horseMetrics:metrics,poolResults,insufficientDataPools};
    }catch(e){return {ok:false,errors:[String(e&&e.message||e)]}}
  }
  function validateOdds(odds){return validateEntries((odds||[]).map((o,i)=>({key:String(i+1),odds:o})),'WIN')}
  function impliedShares(odds){return normalizeSnapshot((odds||[]).map((o,i)=>({key:String(i+1),odds:o})),'WIN').map(x=>x.share)}
  function lmi(early,late){return early.map((e,i)=>e>0?(late[i]-e)/e:0)}
  function analyze(input){
    const nums=Array.isArray(input.horseNumbers)?input.horseNumbers:Array.from({length:(input.t20Odds||[]).length},(_,i)=>i+1);
    const r=analyzeRace({horseNumbers:nums,popularity:input.popularity,pools:{WIN:{T20:(input.t20Odds||[]).map((o,i)=>({key:String(nums[i]),odds:o})),T5:(input.t5Odds||[]).map((o,i)=>({key:String(nums[i]),odds:o}))}}});
    if(!r.ok)return r;
    return {ok:true,marketCenter:r.roles.marketCenter.horseNumber,lateMoney:r.roles.lateMoney.horseNumber,sleeper:r.roles.sleeper.horseNumber,q20:r.poolResults.WIN.candidates.map(c=>c.evidence.q20),q5:r.poolResults.WIN.candidates.map(c=>c.evidence.q5),lmi:r.poolResults.WIN.candidates.map(c=>c.evidence.lmi),candidates:{quinella:[],exacta:[],trio:[],trifecta:[]},analysisVersion:r.analysisVersion};
  }
  global.RacingAnalysis={canonicalKey,validateEntries,normalizeSnapshot,computeLmi,analyzeRace,validateOdds,impliedShares,lmi,analyze};
  if(typeof module!=='undefined')module.exports=global.RacingAnalysis;
})(typeof window!=='undefined'?window:globalThis);
