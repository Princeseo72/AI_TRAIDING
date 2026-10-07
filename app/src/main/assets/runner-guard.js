(function(global){
  'use strict';

  function numberSet(rows){
    const out=new Set();
    (rows||[]).forEach(x=>{
      const n=Number(x&&x.number);
      if(Number.isFinite(n))out.add(n);
    });
    return out;
  }

  function comboNumbers(key){
    return String(key||'').match(/\d+/g)?.map(Number).filter(Number.isFinite)||[];
  }

  function activeHorses(horses,excludedHorses){
    const excluded=numberSet(excludedHorses);
    return (horses||[]).filter(h=>{
      const n=Number(h&&h.number);
      return Number.isFinite(n)&&h.active!==false&&!h.excluded&&!excluded.has(n);
    });
  }

  function rowAllowed(row,active,excluded){
    const nums=comboNumbers(row&&row.key);
    return nums.length>0&&nums.every(n=>active.has(n)&&!excluded.has(n));
  }

  function sanitizePools(pools,activeHorseRows,excludedHorses){
    const active=numberSet(activeHorseRows);
    const excluded=numberSet(excludedHorses);
    const out={};
    Object.entries(pools||{}).forEach(([pool,value])=>{
      const src=value||{};
      out[pool]={
        T20:(src.T20||[]).filter(r=>rowAllowed(r,active,excluded)).map(r=>({...r})),
        T5:(src.T5||[]).filter(r=>rowAllowed(r,active,excluded)).map(r=>({...r}))
      };
    });
    return out;
  }

  function validateNoExcludedLeak(pools,activeHorseRows,excludedHorses){
    const active=numberSet(activeHorseRows);
    const excluded=numberSet(excludedHorses);
    for(const [pool,value] of Object.entries(pools||{})){
      for(const snap of ['T20','T5']){
        for(const row of (value&&value[snap])||[]){
          const nums=comboNumbers(row&&row.key);
          if(!nums.length||nums.some(n=>!active.has(n)||excluded.has(n))){
            return {ok:false,pool,snapshot:snap,key:String(row&&row.key||''),error:'제외/비활성 마번이 배당 입력에 포함됨'};
          }
        }
      }
    }
    return {ok:true};
  }

  function isKeyAllowed(key,activeHorseRows,excludedHorses){
    const active=numberSet(activeHorseRows);
    const excluded=numberSet(excludedHorses);
    const nums=comboNumbers(key);
    return nums.length>0&&nums.every(n=>active.has(n)&&!excluded.has(n));
  }

  const api={activeHorses,sanitizePools,validateNoExcludedLeak,isKeyAllowed,comboNumbers};
  global.RunnerGuard=api;
  if(typeof module!=='undefined')module.exports=api;
})(typeof window!=='undefined'?window:globalThis);
