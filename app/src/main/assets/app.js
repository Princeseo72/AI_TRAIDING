const POOL_LABELS={WIN:'단승',QUINELLA:'복승',EXACTA:'쌍승',TRIO:'삼복승',TRIFECTA:'삼쌍승'};
const OPTIONAL=['QUINELLA','EXACTA','TRIO','TRIFECTA'];
const S={step:0,race:{date:new Date().toISOString().slice(0,10),region:'서울',number:1},horses:[],excludedHorses:[],pools:{},result:null,dirty:false,gumvit:{loading:false,source:'',sourceProvider:'',fallbackFrom:'',error:'',verified:false},postVerify:null,postRaceResult:null,savedId:null,timer:null,processTimer:null,analysisEpoch:0,maintenance:null,mlStatus:null,mlEvent:null,lastAnalysisMs:0,lastLoadMs:0,lastTrainingMs:0,preRaceContext:null,regionalProfile:null,contextVersion:null,dataFreshness:null,pegasusResult:null,closedLoop:null};
const steps=['경주 선택','20분 전 입력','5분 전 입력','연산 처리','최종 출력','저장 기록'];
const $=s=>document.querySelector(s),esc=s=>String(s??'').replace(/[&<>"']/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]));
const pct=v=>Number.isFinite(v)?`${(v*100).toFixed(1)}%`:'-';
function toast(t){const e=$('#toast');if(!e)return;e.textContent=t;e.classList.add('show');setTimeout(()=>e.classList.remove('show'),2400)}
function initPools(){S.pools={WIN:{T20:S.horses.map(h=>({key:String(h.number),odds:''})),T5:S.horses.map(h=>({key:String(h.number),odds:''}))}};OPTIONAL.forEach(p=>S.pools[p]={T20:[],T5:[]})}
function clearRuntime(){clearTimeout(S.timer);clearInterval(S.processTimer);S.timer=null;S.processTimer=null}
function resetRaceVerification(){clearRuntime();S.analysisEpoch++;S.horses=[];S.excludedHorses=[];S.pools={};S.result=null;S.pegasusResult=null;S.closedLoop=null;S.postVerify=null;S.postRaceResult=null;S.savedId=null;S.mlEvent=null;S.preRaceContext=null;S.regionalProfile=null;S.contextVersion=null;S.dataFreshness=null;S.dirty=false;S.gumvit={loading:false,source:'',sourceProvider:'',fallbackFrom:'',error:'',verified:false}}
function runnerGuardOk(){
  if(!window.RunnerGuard)return false;
  return RunnerGuard.validateNoExcludedLeak(S.pools,S.horses,S.excludedHorses).ok;
}
function winValid(snap){return S.gumvit.verified&&runnerGuardOk()&&S.pools.WIN?.[snap]?.length>0&&RacingAnalysis.validateEntries(S.pools.WIN[snap],'WIN').ok}
function invalidate(){clearRuntime();S.analysisEpoch++;S.result=null;S.pegasusResult=null;S.closedLoop=null;S.postVerify=null;S.postRaceResult=null;S.savedId=null;S.mlEvent=null;S.dirty=true;if(S.step===4)S.step=2}
function can(i){if(i===0||i===5)return true;if(i===1)return S.gumvit.verified&&S.horses.length>0;if(i===2)return winValid('T20');if(i===4)return !!S.result;return i===3}
function tabs(){const n=$('#tabs');n.innerHTML=steps.map((x,i)=>`<button data-i="${i}" ${!can(i)&&i!==S.step?'disabled':''} class="${S.step===i?'active':''}">${i+1}. ${x}</button>`).join('');n.querySelectorAll('button').forEach(b=>b.onclick=()=>{const i=+b.dataset.i;if(can(i)||i===S.step){S.step=i;render()}})}
function headerActions(){return `<div class="row"><button id="health" class="secondary">성능 재검증</button><button id="cleanup" class="secondary">데이터 정리</button><button id="exitApp" class="danger">종료</button></div>`}
function render(){tabs();$('#statusBadge').textContent=S.gumvit.loading?'검빛 검증 중':S.postRaceResult?'경주결과/ML 확인':S.postVerify?.verified?'검증 완료':S.result?'분석 완료':S.dirty?'수정됨':'대기';$('#app').innerHTML=`<section class="card toolbar">${headerActions()}${S.maintenance?`<span class="sub">${esc(S.maintenance)}</span>`:''}</section>`+[raceView,t20View,t5View,processView,resultView,historyView][S.step]();bind()}
function loadPreRaceContext(){
  S.preRaceContext=null;S.regionalProfile=null;S.contextVersion=null;S.dataFreshness=null;
  if(!window.AndroidStore||!S.gumvit.verified)return null;
  try{
    const raw=AndroidStore.getPreRaceContext(S.race.date,S.race.region,S.race.number,JSON.stringify(S.horses));
    const c=JSON.parse(raw);
    if(!c.ok)throw new Error(c.error||'사전 Context 생성 실패');
    S.preRaceContext=c;S.regionalProfile=c.regionalProfile||null;S.contextVersion=c.contextVersion||null;S.dataFreshness=c.dataFreshness||null;
    return c;
  }catch(e){
    S.preRaceContext={ok:false,error:String(e.message||e),historicalPrior:{status:'HIST_PENDING'},regionalProfile:{status:'UNAVAILABLE',sampleCount:0},uncertainty:{level:'LOW',reason:'Context 없음'}};
    return S.preRaceContext;
  }
}
function preRaceBriefing(){
  if(!S.gumvit.verified)return '';
  const c=S.preRaceContext;
  if(!c)return '<section class="card"><h2>PEGASUS 사전 브리핑</h2><span class="warn">사전 Context 준비 중</span></section>';
  if(!c.ok)return `<section class="card"><h2>PEGASUS 사전 브리핑</h2><span class="warn">Fallback · ${esc(c.error||'Context 없음')}</span></section>`;
  const rp=c.regionalProfile||{},hist=c.historicalPrior||{},ch=c.champion||{},tb=c.trackBias||{},fresh=c.dataFreshness||{},u=c.uncertainty||{};
  const r100=rp.recent100||{},r20=rp.recent20||{};
  return `<section class="card briefing"><div class="row spread"><h2>PEGASUS 사전 브리핑</h2><span class="${u.level==='HIGH'?'warn':'good'}">신뢰도 ${esc(u.level||'LOW')}</span></div>
    <div class="grid g4">
      <div><label>지역 프로파일</label><b>${esc(rp.status||'미준비')}</b><small>${rp.sampleCount||0}경주</small></div>
      <div><label>Hist 상태</label><b class="${hist.status==='HIST_PENDING'?'warn':'good'}">${esc(hist.status||'HIST_PENDING')}</b><small>유사 ${hist.similarRaceCount||0}경주</small></div>
      <div><label>Champion</label><b>${esc(ch.modelVersion||'BASELINE')}</b><small>${esc(ch.status||'FALLBACK')}</small></div>
      <div><label>Track Bias</label><b>${esc(tb.status||'없음')}</b><small>당일 ${tb.sampleCount||0}경주</small></div>
    </div>
    <div class="briefStats">
      <span>최근100 1인기 승률 ${r100.favoriteWinRate==null?'-':pct(r100.favoriteWinRate)}</span>
      <span>최근20 1인기 승률 ${r20.favoriteWinRate==null?'-':pct(r20.favoriteWinRate)}</span>
      <span>Context ${esc(c.contextVersion||'-')}</span>
    </div>
    <div class="sub">데이터 최신 · Hist ${esc(fresh.hist??'미연결')} · 지역 ${esc(fresh.regional??'표본없음')} · Rating ${fresh.ratingRows||0}건</div>
  </section>`;
}
function raceView(){const provider=S.gumvit.sourceProvider==='KRA'?'KRA 보완':'검빛';const st=S.gumvit.loading?'<span class="warn">검빛/KRA에서 날짜·경주·출전마를 검증 중…</span>':S.gumvit.error?`<span class="bad">${esc(S.gumvit.error)}</span>`:S.gumvit.verified?`<span class="good">✓ ${esc(S.race.date)} ${S.race.number}R 검증 · 유효 출전 ${S.horses.length}두 · 출처 ${esc(provider)}</span>`:'<span class="sub">검빛 우선, 실패 시 KRA 공식정보로 자동 보완합니다.</span>';return `<section class="card"><h2>경주 정보</h2><div class="grid g3"><div><label>날짜</label><input id="date" type="date" value="${S.race.date}"></div><div><label>지역</label><select id="region"><option>서울</option><option>부산경남</option><option>제주</option></select></div><div><label>경주번호</label><input id="raceNo" type="number" min="1" max="20" value="${S.race.number}"></div></div><div class="row spread"><div>${st}</div><button id="loadGumvit" class="primary">경주정보 검증·불러오기</button></div>${S.gumvit.source?`<div class="sub source">${esc(S.gumvit.source)}</div>`:''}</section>${preRaceBriefing()}<section class="card"><h2>유효 출전마</h2><div class="tableWrap"><table><thead><tr><th>마번</th><th>마명</th><th>전적</th><th>조교사</th><th>기수</th><th>인기도</th></tr></thead><tbody>${S.horses.map(h=>`<tr><td>${h.number}</td><td>${esc(h.name)}</td><td>${esc(h.record)}</td><td>${esc(h.trainer)}</td><td>${esc(h.jockey)}</td><td>${esc(h.popularity)}</td></tr>`).join('')||'<tr><td colspan="6">검증된 경주를 불러오세요.</td></tr>'}</tbody></table></div><div class="row right"><button id="to20" class="primary" ${S.gumvit.verified&&S.horses.length>=3?'':'disabled'}>20분 전 배당 입력 →</button></div></section>`}
function normalizeHorse(h){return {number:+h.number,name:h.name||'',record:h.record||'',trainer:h.trainer||'',jockey:h.jockey||'',expert:h.expert||'',popularity:h.popularity||'',active:true}}
function comboNumbers(k){return String(k||'').match(/\d+/g)?.map(Number)||[]}
function requireRunnerGuard(){if(!window.RunnerGuard)throw new Error('출전마 보호 모듈 누락');return RunnerGuard}
function guardedActiveHorses(horses,excluded){return requireRunnerGuard().activeHorses(horses||[],excluded||[]).map(normalizeHorse)}
function sanitizeCurrentPools(){const g=requireRunnerGuard();S.pools=g.sanitizePools(S.pools,S.horses,S.excludedHorses);return g.validateNoExcludedLeak(S.pools,S.horses,S.excludedHorses)}
function assertNoRunnerLeak(){const g=requireRunnerGuard(),v=g.validateNoExcludedLeak(S.pools,S.horses,S.excludedHorses);if(!v.ok)throw new Error(`${v.error}: ${v.pool} ${v.snapshot} ${v.key}`);return true}
function syncFreshRace(d){
  requireRunnerGuard();
  const excluded=d.excludedHorses||[];
  const fresh=guardedActiveHorses(d.horses||[],excluded);
  const oldNos=S.horses.map(h=>h.number).join(','),newNos=fresh.map(h=>h.number).join(','),changed=oldNos!==newNos,old=S.pools;
  S.excludedHorses=excluded;
  if(changed){
    const active=new Set(fresh.map(h=>h.number)),t20=new Map((old.WIN?.T20||[]).map(x=>[+x.key,x.odds])),t5=new Map((old.WIN?.T5||[]).map(x=>[+x.key,x.odds]));
    S.horses=fresh;initPools();
    S.pools.WIN.T20.forEach(x=>x.odds=t20.get(+x.key)||'');
    S.pools.WIN.T5.forEach(x=>x.odds=t5.get(+x.key)||'');
    OPTIONAL.forEach(p=>{
      const a20=old[p]?.T20||[],a5=old[p]?.T5||[];
      a20.forEach((x,i)=>{if(comboNumbers(x.key).every(n=>active.has(n))){
        S.pools[p].T20.push({...x});S.pools[p].T5.push({key:x.key,odds:a5[i]?.odds||''})
      }})
    });
    S.result=null;S.postVerify=null;S.postRaceResult=null;S.savedId=null;S.analysisEpoch++;
  }else S.horses=fresh;
  sanitizeCurrentPools();
  S.gumvit={loading:false,source:d.source||S.gumvit.source,sourceProvider:d.sourceProvider||S.gumvit.sourceProvider||'GUMVIT',fallbackFrom:d.fallbackFrom||'',error:'',verified:true};loadPreRaceContext();
  return changed
}
function fetchVerifiedRace(){if(!window.AndroidRace)throw new Error('경주정보 수집 모듈을 사용할 수 없습니다.');const d=JSON.parse(AndroidRace.fetchRace(S.race.date,S.race.region,S.race.number));if(!d.ok||!d.verified)throw new Error(d.error||'검빛/KRA 경주 검증 실패');if(d.requestedDate!==d.actualDate||+d.requestedRaceNo!==+d.actualRaceNo)throw new Error('경주정보 응답 날짜/경주 불일치');return d}
function refreshField(label){try{const d=fetchVerifiedRace(),changed=syncFreshRace(d);if(changed)toast(`${label}: 출전마 변경 반영 · 현재 ${S.horses.length}두`);return true}catch(e){S.gumvit.verified=false;S.gumvit.error=String(e.message||e);toast(`${label} 실패: ${S.gumvit.error}`);render();return false}}
function loadGumvit(){const date=$('#date').value,region=$('#region').value,raceNo=+$('#raceNo').value;resetRaceVerification();S.race={date,region,number:raceNo};S.gumvit.loading=true;render();setTimeout(()=>{try{const d=fetchVerifiedRace();requireRunnerGuard();S.excludedHorses=d.excludedHorses||[];S.horses=guardedActiveHorses(d.horses||[],S.excludedHorses);if(S.horses.length<3)throw new Error('유효 출전마가 3두 미만입니다.');initPools();sanitizeCurrentPools();assertNoRunnerLeak();S.gumvit={loading:false,source:d.source||'',sourceProvider:d.sourceProvider||'GUMVIT',fallbackFrom:d.fallbackFrom||'',error:'',verified:true};loadPreRaceContext();refreshMlStatus();toast(`검증 완료: ${S.gumvit.sourceProvider==='KRA'?'KRA 보완':'검빛'} · 유효 출전 ${S.horses.length}두`)}catch(e){resetRaceVerification();S.race={date,region,number:raceNo};S.gumvit.error=String(e.message||e)}render()},30)}
function winTable(snap){return `<div class="tableWrap"><table><thead><tr><th>마번</th><th>마명</th><th>${snap==='T20'?'20분':'5분'} 단승 배당</th></tr></thead><tbody>${(S.pools.WIN?.[snap]||[]).map((r,i)=>`<tr><td>${r.key}</td><td>${esc(S.horses[i]?.name)}</td><td><input data-pool="WIN" data-snap="${snap}" data-i="${i}" inputmode="decimal" value="${esc(r.odds)}"></td></tr>`).join('')}</tbody></table></div>`}
function optionalTable(pool,snap){const rows=S.pools[pool]?.[snap]||[];return `<section class="poolBox"><div class="row spread"><h3>${POOL_LABELS[pool]}</h3>${snap==='T20'?`<button class="secondary addRow" data-pool="${pool}">+ 조합 추가</button>`:''}</div><div class="tableWrap"><table class="compact"><thead><tr><th>조합</th><th>배당</th>${snap==='T20'?'<th></th>':''}</tr></thead><tbody>${rows.map((r,i)=>`<tr><td>${snap==='T20'?`<input data-key="1" data-pool="${pool}" data-snap="${snap}" data-i="${i}" value="${esc(r.key)}">`:esc(S.pools[pool].T20[i]?.key||r.key)}</td><td><input data-pool="${pool}" data-snap="${snap}" data-i="${i}" inputmode="decimal" value="${esc(r.odds)}"></td>${snap==='T20'?`<td><button class="danger delRow" data-pool="${pool}" data-i="${i}">삭제</button></td>`:''}</tr>`).join('')||'<tr><td colspan="3" class="sub">선택 입력</td></tr>'}</tbody></table></div></section>`}
function t20View(){return `<section class="card"><h2>20분 전 배당</h2><div class="good">검빛/KRA 최신 재확인 출전마 ${S.horses.length}두만 입력 대상</div>${winTable('T20')}</section>${OPTIONAL.map(p=>optionalTable(p,'T20')).join('')}<div class="row right"><button id="to5" class="primary">5분 전 배당 입력 →</button></div>`}
function poolStatus(pool){const a=S.pools[pool];if(!a||!a.T20.length)return 'INSUFFICIENT_DATA';const v20=RacingAnalysis.validateEntries(a.T20,pool),v5=RacingAnalysis.validateEntries(a.T5,pool);return v20.ok&&v5.ok?'READY':'INSUFFICIENT_DATA'}
function t5View(){return `<section class="card"><h2>5분 전 배당</h2><p class="sub">5분 단계 진입 시 출전취소/제외를 다시 확인합니다. 입력 수정 시 기존 분석은 폐기됩니다.</p>${winTable('T5')}</section>${OPTIONAL.map(p=>optionalTable(p,'T5')).join('')}<section class="card"><h3>승식 상태</h3>${OPTIONAL.map(p=>`<div>${POOL_LABELS[p]}: <b class="${poolStatus(p)==='READY'?'good':'sub'}">${poolStatus(p)}</b></div>`).join('')}</section><div class="row right"><button id="analyze" class="primary" ${winValid('T5')?'':'disabled'}>분석 재실행</button></div>`}
function processView(){return `<section class="card"><h2>연산 처리</h2><div class="progress"><i id="bar"></i></div><div id="logs"></div></section>`}
function activeModelForAnalysis(){try{if(!window.AndroidStore||!window.RacingML)return null;const x=JSON.parse(AndroidStore.getActiveModel(S.race.region));if(!x.ok)return null;const g=x.global||RacingML.createBaselineModel('GLOBAL'),r=x.regional&&x.regional!==null?x.regional:null;return RacingML.composeModel(g,r)}catch(e){return null}}
function contextSignalsForAnalysis(){
  const c=S.preRaceContext||{},ratings=c.ratingState?.horses||{},rp=c.regionalProfile||{},hp=c.historicalPrior||{};
  const by={};
  S.horses.forEach(h=>{
    const r=ratings[String(h.number)]||ratings[h.number]||{};
    const norm=x=>Number.isFinite(+x)?Math.max(0,Math.min(1,(+x+3)/6)):0;
    by[h.number]={
      horseRatingPrior:norm(r.horseMu??r.mu),
      jockeyRatingPrior:norm(r.jockeyMu),
      trainerRatingPrior:norm(r.trainerMu),
      regionalPrior:Number(rp.favoriteWinRate??rp.recent20?.favoriteWinRate)||0,
      historicalPrior:Number(hp.horsePriors?.[String(h.number)]??hp.horsePriors?.[h.number])||0
    };
  });
  return by;
}
function fundamentalPriorsForAnalysis(){
  const out={};S.horses.forEach(h=>{out[h.number]={recordPrior:0,jockeyPrior:0,trainerPrior:0}});return out;
}
function buildInput(){
  const g=requireRunnerGuard(),safe=g.sanitizePools(S.pools,S.horses,S.excludedHorses),guard=g.validateNoExcludedLeak(safe,S.horses,S.excludedHorses);
  if(!guard.ok)throw new Error(`${guard.error}: ${guard.pool} ${guard.snapshot} ${guard.key}`);
  const pools={WIN:{T20:safe.WIN.T20.map(x=>({...x,odds:+x.odds})),T5:safe.WIN.T5.map(x=>({...x,odds:+x.odds}))}};
  OPTIONAL.forEach(p=>{const a=safe[p];if(a&&a.T20.length&&RacingAnalysis.validateEntries(a.T20,p).ok&&RacingAnalysis.validateEntries(a.T5,p).ok)pools[p]={T20:a.T20.map(x=>({key:x.key,odds:+x.odds})),T5:a.T5.map((x,i)=>({key:a.T20[i]?.key||x.key,odds:+x.odds}))}});
  return {horseNumbers:S.horses.map(h=>h.number),popularity:S.horses.map(h=>Number(h.popularity)||null),pools,learningModel:activeModelForAnalysis(),contextSignals:contextSignalsForAnalysis(),fundamentalPriors:fundamentalPriorsForAnalysis()}
}
function buildPegasusInput(){
  const b=buildInput();
  return {horses:S.horses.map(h=>({...h,active:true,excluded:false})),pools:b.pools,preRaceContext:S.preRaceContext||{historicalPrior:{status:'HIST_PENDING'},regionalProfile:{sampleCount:0}},legacyResult:S.result};
}
function start(){
  if(!refreshField('분석 전 출전마 재검증'))return;
  try{sanitizeCurrentPools();assertNoRunnerLeak()}catch(e){toast('제외마 차단: '+(e.message||e));render();return}
  if(!winValid('T20')||!winValid('T5'))return toast('단승 20분/5분 배당을 모두 입력하세요.');
  clearRuntime();const epoch=++S.analysisEpoch;S.result=null;S.pegasusResult=null;S.closedLoop=null;S.postVerify=null;S.postRaceResult=null;S.savedId=null;S.mlEvent=null;S.step=3;render();
  const logs=['입력/출전마 검증','Hist Context','Regional Profile','Fundamental','Rating','Track Bias','Market T20/T5','Live Odds','Blend','Calibration','Ordered Finish','Final Eight','Model Integrity'];
  let i=0,st=performance.now();
  S.processTimer=setInterval(()=>{
    if(epoch!==S.analysisEpoch){clearRuntime();return}
    const box=$('#logs');if(!box){clearRuntime();return}
    box.innerHTML+=`<div class="log">✓ ${logs[i]}</div>`;
    $('#bar').style.width=`${((i+1)/logs.length)*100}%`;i++;
    if(i===logs.length){
      clearRuntime();
      const legacy=RacingAnalysis.analyzeRace(buildInput());
      const pegasus=window.PegasusEngine?PegasusEngine.analyze(buildPegasusInput()):{ok:false,errors:['PEGASUS 엔진 누락']};
      S.lastAnalysisMs=performance.now()-st;if(epoch!==S.analysisEpoch)return;
      S.result=legacy;S.pegasusResult=pegasus;S.dirty=false;S.step=(legacy.ok&&pegasus.ok)?4:2;
      if(!pegasus.ok)toast('PEGASUS 분석 실패: '+(pegasus.errors||[]).join(' / '));
      render();
    }
  },45)
}
function predCards(title,rows,labels){return `<section class="card"><h2>${title}</h2><div class="predictionGrid">${(rows||[]).map((x,i)=>`<div class="predBox"><span>${labels[i]||`${i+1}착`}</span><b>${x.horseNumber}번</b><small>score ${Number(x.score||0).toFixed(3)}</small></div>`).join('')}</div></section>`}
function finalEightView(){const f=S.result?.finalCombinations||{};return `<section class="card"><h2>최종 승식 8조합</h2><div class="final8">${OPTIONAL.map(p=>`<div class="betGroup"><h3>${POOL_LABELS[p]} <small>2조합</small></h3>${(f[p]||[]).map((x,i)=>`<div class="comboRow"><b>${i+1}. ${esc(x.key)}</b><span class="source ${x.source==='ACTUAL_POOL'?'actual':'derived'}">${esc(x.source)}</span><small>${Number(x.score||0).toFixed(3)}</small></div>`).join('')}</div>`).join('')}</div></section>`}
function evidenceView(){const rp=S.result?.rolePrediction||[];return `<section class="card"><h3>핵심 근거</h3><div class="evidenceList">${rp.map(x=>{const f=x.evidence||{};return `<div><b>${x.horseNumber}번</b> · Q20 ${pct(f.winShareT20)} → Q5 ${pct(f.winShareT5)} · LMI ${pct(f.winLmi)} · CrossPool ${Number(f.crossPoolDivergence||0).toFixed(3)}</div>`}).join('')}</div></section>`}
function refreshMlStatus(){try{if(!window.AndroidStore)return;const x=JSON.parse(AndroidStore.getMlStatus(S.race.region));if(x.ok)S.mlStatus=x}catch(e){}}
function flattenWeightDelta(delta){
  const rows=[];Object.entries(delta||{}).forEach(([score,features])=>{
    if(features&&typeof features==='object')Object.entries(features).forEach(([feature,value])=>{if(Number.isFinite(+value))rows.push([`${score}.${feature}`,+value])});
    else if(Number.isFinite(+features))rows.push([score,+features]);
  });return rows.sort((a,b)=>Math.abs(b[1])-Math.abs(a[1])).slice(0,6);
}
function metricBlock(title,m){
  if(!m)return '';
  return `<div class="metricBlock"><b>${title}</b><span>1착 ${pct(m.first)}</span><span>TOP3 ${pct(m.roleTop3??m.top3)}</span><span>복승 ${pct(m.quinella)}</span><span>쌍승 ${pct(m.exacta)}</span><span>삼복 ${pct(m.trio)}</span><span>삼쌍 ${pct(m.trifecta)}</span></div>`;
}
function mlPanel(){
  const m=S.mlStatus||{},active=m.global,gt=m.globalTraining,rt=m.regionalTraining,last=S.mlEvent||m.lastEvent;
  const samples=gt?.sampleCount??m.trainedExamples??0,regionalSamples=rt?.sampleCount??0,waiting=samples<(window.RacingML?.CONFIG?.minSamples||20);
  const rawDelta=last?.globalTraining?.weightDelta||last?.weightDelta||{},bars=flattenWeightDelta(rawDelta).map(([k,v])=>`<div class="deltaRow"><span>${esc(k)}</span><i class="deltaBar ${v>=0?'pos':'neg'}" style="width:${Math.min(100,Math.abs(v)*5000)}%"></i><b>${v>=0?'+':''}${Number(v).toFixed(4)}</b></div>`).join('');
  return `<section class="card mlPanel"><div class="row spread"><h2>ML 학습 상태</h2><span class="${waiting?'warn':'good'}">${waiting?'학습 대기 / 기본모델 사용':'학습모델 적용'}</span></div>
  <div class="grid g4"><div><label>활성 모델</label><b>${esc(active?.modelVersion||'ML-v1-GLOBAL')}</b></div><div><label>누적 학습</label><b>${samples}경주</b></div><div><label>지역 학습</label><b>${esc(rt?.modelVersion||'대기')} · ${regionalSamples}경주</b></div><div><label>학습시간</label><b>${Number(S.lastTrainingMs||0).toFixed(1)}ms</b></div></div>
  ${bars?`<h3>최근 가중치 변화</h3>${bars}`:'<div class="sub">검증된 경주결과가 누적되면 가중치 변화가 표시됩니다.</div>'}
  <div class="mlMetrics">${metricBlock('최근20',m.rolling20)}${metricBlock('누적',m.cumulative||gt?.metrics)}</div>
  </section>`;
}
function verifyAfterAnalysis(){if(!S.result)return toast('분석 결과가 없습니다.');try{const expected=JSON.stringify(S.horses.map(h=>h.number)),v=JSON.parse(AndroidRace.verifyRace(S.race.date,S.race.region,S.race.number,expected));S.postVerify=v;if(v.verified)toast(`검빛 대조 완료 · 출전 ${v.activeCount}두`);else toast(v.error||'검빛 대조 실패')}catch(e){S.postVerify={verified:false,error:String(e.message||e)}}render()}
function actualTop3(){return (S.postRaceResult?.finishers||[]).slice().sort((a,b)=>a.rank-b.rank).slice(0,3).map(x=>+x.number)}
function comparePredictions(){
  if(!S.postRaceResult||!S.pegasusResult?.ok)return '';
  const a=actualTop3(),p=S.pegasusResult;
  const market=(p.engineCompare?.market||[]).map(x=>x.horseNumber);
  const fund=(p.engineCompare?.fundamental||[]).map(x=>x.horseNumber);
  const final=(p.final123||[]).map(x=>x.horseNumber);
  const row=(name,arr)=>`<tr><td>${name}</td>${[0,1,2].map(i=>{const n=arr[i];return `<td class="${n===a[i]?'good':'bad'}">${n||'-'}번 ${n===a[i]?'✓':'✕'}</td>`}).join('')}</tr>`;
  return `<section class="card"><h2>예측 vs 실제</h2><div class="tableWrap compactResult"><table><thead><tr><th>구분</th><th>1착 (${a[0]||'-'})</th><th>2착 (${a[1]||'-'})</th><th>3착 (${a[2]||'-'})</th></tr></thead><tbody>${row('시장·배당',market)}${row('펀더멘털',fund)}${row('PEGASUS Final',final)}</tbody></table></div></section>`;
}
function closedLoopView(){
  const c=S.closedLoop;if(!c?.ok)return '';
  const stepSet=new Set(c.steps||[]);
  const names=['결과 검증','지역 프로파일 갱신','Rating 갱신','Track Bias 상태 갱신','Blend/Calibration 갱신','Drift 검사','Next-Race Feature Materialization'];
  return `<section class="card"><div class="row spread"><h2>폐쇄루프 학습</h2><span class="good">다음 경주 준비 ${esc(c.materialization?.status||'READY_PARTIAL')}</span></div>
    <div class="learningSteps">${names.map(x=>`<div class="log">${stepSet.has(x)?'✓':'!'} ${x}</div>`).join('')}</div>
    <div class="sub">Drift ${esc(c.drift?.state||'미평가')} · Context ${esc(c.materialization?.contextVersion||'-')} · ${Number(c.durationMs||0).toFixed(1)}ms</div>
  </section>`;
}
function outcomeView(){
  const o=S.postRaceResult;if(!o)return '';
  const top=(o.finishers||[]).slice(0,5),p=o.payouts||{},f=S.pegasusResult?.finalEight||{};
  return `<section class="card"><div class="row spread"><h2>검빛 경주후 결과</h2><span class="good">✓ 확정 결과 저장</span></div><div class="row">${top.map(x=>`<span class="resultChip"><b>${x.rank}착 ${x.number}번</b> ${esc(x.name)}</span>`).join('')}</div><div class="tableWrap compactResult"><table><thead><tr><th>승식</th><th>실제조합</th><th>배당</th><th>최종8 적중</th></tr></thead><tbody>${Object.entries(p).filter(([k])=>OPTIONAL.includes(k)).map(([k,v])=>`<tr><td>${POOL_LABELS[k]}</td><td><b>${esc(v.key)}</b></td><td>${v.odds}</td><td>${(f[k]||[]).some(x=>x.key===v.key)?'<span class="good">적중</span>':'<span class="sub">미적중</span>'}</td></tr>`).join('')}</tbody></table></div></section>${comparePredictions()}${closedLoopView()}`;
}
function trainFromResult(){
  if(!S.savedId||!S.postRaceResult||!S.result||!window.RacingML||!window.AndroidStore)return;
  const started=performance.now();
  try{
    const state=JSON.parse(AndroidStore.getLearningState(S.race.region));if(!state.ok)throw new Error(state.error||'학습상태 조회 실패');
    const globalBase=state.globalTraining&&state.globalTraining!==null?state.globalTraining:RacingML.createBaselineModel('GLOBAL');
    const regionalBase=state.regionalTraining&&state.regionalTraining!==null?state.regionalTraining:RacingML.createBaselineModel(S.race.region);
    const top3=actualTop3();if(top3.length<3)throw new Error('실제 TOP3 부족');
    const actual={top3,payouts:S.postRaceResult.payouts||{},finishers:S.postRaceResult.finishers||[]};
    const finishPrediction=(S.pegasusResult?.final123||[]).map(x=>({horseNumber:+x.horseNumber,position:+x.position,probability:+x.probability}));
    const example={raceKey:`${S.race.date}|${S.race.region}|${S.race.number}`,region:S.race.region,features:S.result.featureVectors,prediction:{rolePrediction:S.result.rolePrediction,oddsPrediction:S.result.oddsPrediction,finishPrediction,finalCombinations:S.result.finalCombinations},actual};
    const globalTraining=RacingML.trainCandidate({model:globalBase,example});
    const regionalTraining=RacingML.trainCandidate({model:regionalBase,example});
    if(!globalTraining.ok)throw new Error(globalTraining.error||'글로벌 학습 실패');
    if(!regionalTraining.ok)throw new Error(regionalTraining.error||'지역 학습 실패');
    globalTraining.modelFrom=globalBase.modelVersion;globalTraining.beforeMetrics=globalBase.metrics;
    regionalTraining.modelFrom=regionalBase.modelVersion;regionalTraining.beforeMetrics=regionalBase.metrics;
    const payload={actual,evaluation:globalTraining.evaluation,globalTraining,regionalTraining};
    const saved=JSON.parse(AndroidStore.recordTrainingBundle(+S.savedId,JSON.stringify(payload)));
    if(!saved.ok&&String(saved.error||'').includes('중복')){S.mlEvent={globalTraining,regionalTraining,promotionReason:'이미 학습된 경주'};return}
    if(!saved.ok)throw new Error(saved.error||'학습저장 실패');
    S.lastTrainingMs=Number(saved.durationMs)||performance.now()-started;
    S.mlEvent={globalTraining,regionalTraining};
    refreshMlStatus();
  }catch(e){S.lastTrainingMs=performance.now()-started;S.mlEvent={error:String(e.message||e),promotionReason:'ML 실패 - 기본 분석 유지'}}
}
function fetchRaceResult(silent=false){
  if(!S.savedId){if(!silent)toast('먼저 분석을 저장하거나 저장 기록을 불러오세요.');return false}
  try{
    const d=JSON.parse(AndroidRace.fetchRaceResult(S.race.date,S.race.region,S.race.number));
    if(!d.ok||!d.verified)throw new Error(d.error||'경주결과 미확정');
    const a=JSON.parse(AndroidStore.attachRaceResult(+S.savedId,JSON.stringify(d)));
    if(!a.ok)throw new Error(a.error||'경주결과 저장 실패');
    S.postRaceResult=d;
    trainFromResult();
    const loop=JSON.parse(AndroidStore.applyClosedLoopUpdate(+S.savedId));
    S.closedLoop=loop.ok?loop:{ok:false,error:loop.error||'폐쇄루프 갱신 실패'};
    if(!silent)toast(loop.ok?'결과대조·학습·다음 경주 준비 완료':'결과 저장 완료 · 폐쇄루프 일부 실패');
    render();return true
  }catch(e){if(!silent)toast('경주결과 확인: '+(e.message||e));return false}
}
function pegProb(v){return Number.isFinite(+v)?(100*(+v)).toFixed(1)+'%':'-'}
function pegasusFinalView(){
  const p=S.pegasusResult;if(!p?.ok)return '<section class="card"><h2>PEGASUS 최종 예상</h2><span class="bad">최종 엔진 결과 없음</span></section>';
  const top=p.final123||[],joint=top[0]?.jointProbability;
  return `<section class="card pegasusFinal"><div class="row spread"><h2>PEGASUS 최종 예상</h2><span class="${p.uncertainty?.level==='LOW'?'warn':'good'}">신뢰도 ${esc(p.uncertainty?.level||'LOW')}</span></div>
    <div class="predictionGrid">${top.map((x,i)=>`<div class="predBox"><span>${i+1}착</span><b>${x.horseNumber}번</b><small>${pegProb(x.probability)}</small></div>`).join('')}</div>
    <div class="row spread"><b>TOP 순열 ${top.map(x=>x.horseNumber).join(' → ')}</b><span>Joint ${pegProb(joint)}</span></div>
    <div class="sub">${esc(p.calibration?.status||'CALIBRATION_PENDING')} · ${esc(p.ordered?.status||'PL_FALLBACK')} · Hist ${esc(p.histStatus||'HIST_PENDING')}</div>
  </section>`;
}
function pegasusEightView(){
  const f=S.pegasusResult?.finalEight||{};return `<section class="card"><h2>최종 승식 8조합</h2><div class="final8">${OPTIONAL.map(pool=>`<div class="betGroup"><h3>${POOL_LABELS[pool]} <small>2조합</small></h3>${(f[pool]||[]).map((x,i)=>`<div class="comboRow"><b>${i+1}. ${esc(x.key)}</b><span class="source derived">${esc(x.source)}</span><small>${pegProb(x.probability)}</small></div>`).join('')}</div>`).join('')}</div></section>`;
}
function engineCompareView(){
 const p=S.pegasusResult;if(!p?.ok)return '';
 const row=(name,a,status)=>`<tr><td>${name}</td><td>${a?.[0]?.horseNumber||'-'}</td><td>${a?.[1]?.horseNumber||'-'}</td><td>${a?.[2]?.horseNumber||'-'}</td><td>${esc(status)}</td></tr>`;
 return `<section class="card"><h2>엔진 비교</h2><div class="tableWrap"><table class="compact"><thead><tr><th>엔진</th><th>1착</th><th>2착</th><th>3착</th><th>상태</th></tr></thead><tbody>
 ${row('시장·배당 예상',p.engineCompare.market,'MARKET')}
 ${row('펀더멘털 예상',p.engineCompare.fundamental,p.fundamental?.status||'FUND')}
 ${row('Final Blend',p.engineCompare.final,p.calibration?.status||'FINAL')}
 </tbody></table></div></section>`;
}
function probabilityTableView(){
 const p=S.pegasusResult;if(!p?.ok)return '';
 const ids=Object.keys(p.ordered?.p1||{}).map(Number).sort((a,b)=>(p.ordered.p1[b]||0)-(p.ordered.p1[a]||0)).slice(0,5);
 return `<section class="card"><h2>TOP5 확률</h2><div class="tableWrap"><table class="compact"><thead><tr><th>마번</th><th>P1</th><th>P2</th><th>P3</th><th>시장P</th></tr></thead><tbody>${ids.map(h=>`<tr><td>${h}번</td><td>${pegProb(p.ordered.p1[h])}</td><td>${pegProb(p.ordered.p2[h])}</td><td>${pegProb(p.ordered.p3[h])}</td><td>${pegProb(p.market.p1[h])}</td></tr>`).join('')}</tbody></table></div></section>`;
}
function resultView(){const r=S.result,p=S.pegasusResult;if(!r||!p?.ok)return '<section class="card">분석 결과가 없습니다.</section>';const vv=S.postVerify?.verified?`<span class="good">✓ 검빛 재대조 통과</span>`:`<span class="bad">저장 전 검빛 경주 대조검증 필요</span>`,saved=S.savedId?`<span class="good">저장됨 #${S.savedId}</span>`:'';return `${pegasusFinalView()}${pegasusEightView()}${engineCompareView()}${probabilityTableView()}${preRaceBriefing()}${evidenceView()}${mlPanel()}<section class="card"><div class="row spread"><b>PEGASUS ${esc(p.engineVersion)} · ${(S.lastAnalysisMs||0).toFixed(1)}ms</b><div>${vv} ${saved}</div></div><div class="row"><button id="verifyGumvit" class="secondary">검빛 경주 대조검증</button>${S.savedId?'<button id="fetchRaceResult" class="primary">경주결과 대조·학습</button>':''}</div></section>${outcomeView()}<div class="row right"><button id="recalc" class="secondary">배당 수정</button>${S.savedId?'':`<button id="approve" class="primary" ${S.postVerify?.verified?'':'disabled'}>최종 승인 및 저장</button>`}</div>`}function records(){try{return window.AndroidStore?JSON.parse(AndroidStore.listAnalyses()):[]}catch(e){return []}}
function loadAnalysis(id,showToast=true){try{const r=JSON.parse(AndroidStore.getAnalysis(+id));if(!r.ok)throw new Error(r.error);const p=r.payload;clearRuntime();S.analysisEpoch++;S.race={date:p.raceDate,region:p.region,number:+p.raceNumber};requireRunnerGuard();S.excludedHorses=p.excludedHorses||[];S.horses=guardedActiveHorses(p.horses||[],S.excludedHorses);S.pools=RunnerGuard.sanitizePools(p.pools||{},S.horses,S.excludedHorses);assertNoRunnerLeak();S.result=p.result||null;S.pegasusResult=p.pegasusResult||p.predictionSnapshot?.pegasusResult||null;S.preRaceContext=p.preRaceContext||p.predictionSnapshot?.preRaceContext||S.preRaceContext;S.gumvit={loading:false,source:'저장 기록',error:'',verified:true};S.postVerify={verified:true,source:'저장 시 검증 완료'};S.postRaceResult=p.postRaceResult||null;S.savedId=+r.id;S.lastLoadMs=+r.loadMs||0;S.dirty=false;S.step=4;refreshMlStatus();if(showToast)toast(`빠르게 불러오기 ${S.lastLoadMs.toFixed(1)}ms`);render();return true}catch(e){toast('불러오기 실패: '+e.message);return false}}
function loadAndFetchResult(id){if(loadAnalysis(id,false))fetchRaceResult(false)}
function historyView(){if(!S.mlStatus)refreshMlStatus();const rows=records();return `${mlPanel()}<section class="card"><h2>저장 기록</h2><div class="tableWrap"><table><thead><tr><th>날짜</th><th>지역</th><th>경주</th><th>축마</th><th>유입마</th><th>경주후</th><th></th></tr></thead><tbody>${rows.map(x=>`<tr><td>${esc(x.raceDate)}</td><td>${esc(x.region)}</td><td>${x.raceNumber}R</td><td>${x.marketCenter}번</td><td>${x.lateMoney}번</td><td>${x.hasOutcome?'<span class="good">결과/학습</span>':'<span class="sub">결과대기</span>'}</td><td><div class="row"><button class="secondary loadAnalysis" data-id="${x.id}">빠르게 불러오기</button><button class="primary fetchOutcome" data-id="${x.id}">경주결과 대조</button></div></td></tr>`).join('')||'<tr><td colspan="7">저장 기록 없음</td></tr>'}</tbody></table></div></section>`}
function scheduleAnalysis(){return false}
function save(){if(!S.postVerify?.verified)return toast('검빛 경주 대조검증을 먼저 통과해야 합니다.');if(!S.pegasusResult?.ok)return toast('PEGASUS 최종 분석이 없습니다.');try{sanitizeCurrentPools();assertNoRunnerLeak()}catch(e){return toast('제외마 차단: '+(e.message||e))}const p={raceDate:S.race.date,region:S.race.region,raceNumber:S.race.number,status:'approved',marketCenter:S.result.roles?.marketCenter?.horseNumber||0,lateMoney:S.result.roles?.lateMoney?.horseNumber||0,horses:S.horses,excludedHorses:S.excludedHorses,pools:S.pools,result:S.result,pegasusResult:S.pegasusResult,preRaceContext:S.preRaceContext,contextVersion:S.contextVersion,analysisVersion:S.result.analysisVersion};try{const r=JSON.parse(AndroidStore.saveAnalysis(JSON.stringify(p)));if(!r.ok)throw new Error(r.error);S.savedId=+r.id;refreshMlStatus();toast('PEGASUS 불변 예측 저장 완료');fetchRaceResult(true);S.step=5;render()}catch(e){toast('저장 실패: '+e.message)}}
function healthCheck(){try{
  const r=JSON.parse(AndroidStore.healthCheck());
  const src=window.AndroidRace?.selfDiagnose?JSON.parse(AndroidRace.selfDiagnose()):{ok:false,error:'source diagnostic unavailable'};
  const db=r.ok?'DB PASS':'DB FAIL', parser=src.gumvitParser?'GUMVIT PARSER PASS':'GUMVIT PARSER FAIL', modules=src.modules?'MODULE PASS':'MODULE FAIL';
  S.maintenance=(r.ok&&src.ok?'자가진단 정상':'자가진단 경고')+` · ${db} · ${parser} · ${modules} · 중복 결과조회 ${src.duplicateResultLookup===false?'차단':'확인필요'} · 저장 ${r.records||0} / 결과 ${r.outcomes||0} / ML ${r.models||0}모델·${r.trainedExamples||0}학습 · DB ${((r.dbBytes||0)/1024).toFixed(1)}KB · Source ${Number(src.durationMs||0).toFixed(0)}ms · DB ${Number(r.durationMs||0).toFixed(1)}ms`;
  if(src.error)S.maintenance+=` · ${src.error}`;
}catch(e){S.maintenance='자가진단 실패: '+(e.message||e)}render()}
function cleanupData(){try{const r=JSON.parse(AndroidStore.cleanupData());S.maintenance=r.ok?`데이터 정리 완료 · ACTIVE 모델 보존 · ${Number(r.durationMs).toFixed(1)}ms`:`정리 실패: ${r.error}`;}catch(e){S.maintenance='정리 실패'}render()}
function closeApp(){clearRuntime();S.analysisEpoch++;if(window.AndroidApp?.closeApp)AndroidApp.closeApp();else toast('종료 모듈을 사용할 수 없습니다.')}
function bind(){const d=$('#date');if(d)d.onchange=e=>{const v=e.target.value;resetRaceVerification();S.race.date=v;render()};const rg=$('#region');if(rg){rg.value=S.race.region;rg.onchange=e=>{const v=e.target.value;resetRaceVerification();S.race.region=v;render()}}const rn=$('#raceNo');if(rn)rn.onchange=e=>{const v=+e.target.value;resetRaceVerification();S.race.number=v;render()};if($('#loadGumvit'))$('#loadGumvit').onclick=loadGumvit;if($('#to20'))$('#to20').onclick=()=>{if(refreshField('20분 입력 전 재검증')){S.step=1;render()}};if($('#to5'))$('#to5').onclick=()=>{if(!refreshField('5분 입력 전 재검증'))return;if(!winValid('T20'))return toast('20분 단승 배당을 모두 입력하세요.');S.step=2;render()};document.querySelectorAll('.addRow').forEach(b=>b.onclick=()=>{const p=b.dataset.pool;S.pools[p].T20.push({key:'',odds:''});S.pools[p].T5.push({key:'',odds:''});invalidate();render()});document.querySelectorAll('.delRow').forEach(b=>b.onclick=()=>{const p=b.dataset.pool,i=+b.dataset.i;S.pools[p].T20.splice(i,1);S.pools[p].T5.splice(i,1);invalidate();render()});document.querySelectorAll('[data-pool][data-i]').forEach(e=>e.onchange=()=>{const p=e.dataset.pool,s=e.dataset.snap,i=+e.dataset.i;if(e.dataset.key){try{requireRunnerGuard();if(!RunnerGuard.isKeyAllowed(e.value,S.horses,S.excludedHorses)){S.pools[p].T20[i].key='';S.pools[p].T5[i].key='';invalidate();toast('제외/비활성 마번 조합은 입력할 수 없습니다.');render();return}S.pools[p].T20[i].key=e.value;S.pools[p].T5[i].key=e.value}catch(err){toast('조합 검증 실패: '+(err.message||err));return}}else S.pools[p][s][i].odds=e.value;invalidate();render()});if($('#analyze'))$('#analyze').onclick=start;if($('#recalc'))$('#recalc').onclick=()=>{invalidate();S.step=2;render()};if($('#verifyGumvit'))$('#verifyGumvit').onclick=verifyAfterAnalysis;if($('#fetchRaceResult'))$('#fetchRaceResult').onclick=()=>fetchRaceResult(false);if($('#approve'))$('#approve').onclick=save;document.querySelectorAll('.loadAnalysis').forEach(b=>b.onclick=()=>loadAnalysis(b.dataset.id));document.querySelectorAll('.fetchOutcome').forEach(b=>b.onclick=()=>loadAndFetchResult(b.dataset.id));if($('#health'))$('#health').onclick=healthCheck;if($('#cleanup'))$('#cleanup').onclick=cleanupData;if($('#exitApp'))$('#exitApp').onclick=closeApp}
render();
