(function(global){
  'use strict';
  const CONFIG={learningRate:.02,maxDeltaPerRace:.015,minSamples:20,regionalMinSamples:12,weightMin:-.25,weightMax:.65,regularization:.01,regressionTolerance:.02,rollingWindow:20};
  const FEATURES=['share','lmi','crossPool','popularity','stability','structure','horseRating','jockeyRating','trainerRating','regionalPrior','historicalPrior'];
  const SCORES=['DarkHorseScore','FavoriteScore','AbilityScore','WinScore','Place2Score','Place3Score'];
  const BASE={share:.24,lmi:.16,crossPool:.13,popularity:.07,stability:.06,structure:.05,horseRating:.09,jockeyRating:.05,trainerRating:.04,regionalPrior:.05,historicalPrior:.06};
  const clamp=(x,a,b)=>Math.max(a,Math.min(b,x));
  const avg=a=>a.length?a.reduce((s,x)=>s+x,0)/a.length:0;

  function baselineMatrix(){
    const out={};SCORES.forEach(s=>out[s]={...BASE});return out;
  }
  function normalizeMatrix(weights){
    if(weights&&SCORES.every(s=>weights[s]&&typeof weights[s]==='object')){
      const out={};SCORES.forEach(s=>out[s]={...BASE,...weights[s]});return out;
    }
    const legacy={...BASE,...(weights||{})},out={};SCORES.forEach(s=>out[s]={...legacy});return out;
  }
  function blankMetrics(){return{first:0,second:0,third:0,roleExact123:0,roleTop3:0,oddsFirst:0,oddsSecond:0,oddsThird:0,oddsExact123:0,oddsTop3:0,quinella:0,exacta:0,trio:0,trifecta:0,verified:0}}
  function createBaselineModel(region='GLOBAL'){return{modelVersion:`ML-v1-${region}`,region,status:'ACTIVE',sampleCount:0,weights:baselineMatrix(),metrics:blankMetrics(),seenRaceKeys:[]}}

  function poolHit(pred,actual,pool){
    const key=actual?.payouts?.[pool]?.key;if(!key)return 0;
    return (pred?.finalCombinations?.[pool]||[]).some(x=>String(x.key)===String(key))?1:0;
  }
  function evaluate(pred,actual){
    const role=(pred?.rolePrediction||[]).map(x=>+x.horseNumber),finish=(pred?.finishPrediction||pred?.oddsPrediction||[]).map(x=>+x.horseNumber),top=(actual?.top3||[]).map(Number);
    if(top.length<3)return null;
    const set=new Set(top),roleTop3=role.filter(x=>set.has(x)).length/3,finishExact=finish.length>=3&&finish.slice(0,3).every((x,i)=>x===top[i]);
    return{
      first:+(finish[0]===top[0]),second:+(finish[1]===top[1]),third:+(finish[2]===top[2]),
      roleExact123:0,roleTop3,
      oddsFirst:+(finish[0]===top[0]),oddsSecond:+(finish[1]===top[1]),oddsThird:+(finish[2]===top[2]),
      oddsExact123:+finishExact,oddsTop3:finish.filter(x=>set.has(x)).length/3,
      quinella:poolHit(pred,actual,'QUINELLA'),exacta:poolHit(pred,actual,'EXACTA'),
      trio:poolHit(pred,actual,'TRIO'),trifecta:poolHit(pred,actual,'TRIFECTA')
    };
  }

  function horseSignal(f={}){
    const l=Number(f.winLmi)||0,d=Number(f.crossPoolDivergence)||0;
    return{
      share:clamp(Number(f.winShareT5)||0,0,1),
      lmi:clamp(l/(1+Math.abs(l)),-1,1),
      crossPool:clamp(d,-1,1),
      popularity:clamp(Number(f.popularityPrior)||0,0,1),
      stability:clamp(Number(f.stability)||0,0,1),
      structure:clamp(Number(f.structureEvidence)||0,0,1),
      horseRating:clamp(Number(f.horseRatingPrior)||0,0,1),
      jockeyRating:clamp(Number(f.jockeyRatingPrior)||0,0,1),
      trainerRating:clamp(Number(f.trainerRatingPrior)||0,0,1),
      regionalPrior:clamp(Number(f.regionalPrior)||0,0,1),
      historicalPrior:clamp(Number(f.historicalPrior)||0,0,1)
    };
  }
  const TARGET={
    DarkHorseScore:{source:'rolePrediction',index:0,actual:0},
    FavoriteScore:{source:'rolePrediction',index:1,actual:1},
    AbilityScore:{source:'rolePrediction',index:2,actual:2},
    WinScore:{source:'finishPrediction',fallback:'oddsPrediction',index:0,actual:0},
    Place2Score:{source:'finishPrediction',fallback:'oddsPrediction',index:1,actual:1},
    Place3Score:{source:'finishPrediction',fallback:'oddsPrediction',index:2,actual:2}
  };
  function updateMetrics(before={},ev){
    const verified=(before.verified||0)+1,out={...blankMetrics(),...before,verified};
    for(const k of Object.keys(ev))out[k]=(((before[k]||0)*(verified-1))+(Number(ev[k])||0))/verified;
    return out;
  }
  function regresses(before={},after={}){
    if((before.verified||0)<1)return false;
    const comboBefore=avg(['quinella','exacta','trio','trifecta'].map(k=>before[k]||0));
    const comboAfter=avg(['quinella','exacta','trio','trifecta'].map(k=>after[k]||0));
    return (after.first||0)+CONFIG.regressionTolerance<(before.first||0)
      ||(after.roleTop3||0)+CONFIG.regressionTolerance<(before.roleTop3||0)
      ||comboAfter+CONFIG.regressionTolerance<comboBefore;
  }
  function trainCandidate({model,example,seenRaceKeys}){
    if(!model)return{ok:false,error:'학습 모델 없음'};
    if(!example?.actual||!(example.actual.top3||[]).length)return{ok:false,error:'검증된 경주결과 없음'};
    const seen=seenRaceKeys||new Set(model.seenRaceKeys||[]);
    if(seen.has(example.raceKey))return{ok:false,error:'중복 학습 경주'};
    const ev=evaluate(example.prediction,example.actual);if(!ev)return{ok:false,error:'실제 1·2·3착 부족'};
    const region=model.region||example.region||'GLOBAL',current=normalizeMatrix(model.weights),weights={},delta={};
    const top=example.actual.top3.map(Number),features=example.features||{};
    for(const score of SCORES){
      weights[score]={};delta[score]={};
      const t=TARGET[score],actualHorse=top[t.actual],src=example.prediction?.[t.source]||example.prediction?.[t.fallback]||[],predHorse=Number(src?.[t.index]?.horseNumber);
      const a=horseSignal(features[actualHorse]),p=horseSignal(features[predHorse]);
      for(const k of FEATURES){
        const gradient=(a[k]||0)-(p[k]||0);
        const regularize=CONFIG.regularization*((current[score][k]||0)-BASE[k]);
        const raw=CONFIG.learningRate*gradient-regularize;
        delta[score][k]=clamp(raw,-CONFIG.maxDeltaPerRace,CONFIG.maxDeltaPerRace);
        weights[score][k]=clamp((current[score][k]||0)+delta[score][k],CONFIG.weightMin,CONFIG.weightMax);
      }
    }
    const sampleCount=(model.sampleCount||0)+1,metrics=updateMetrics(model.metrics||{},ev);
    const threshold=region==='GLOBAL'?CONFIG.minSamples:CONFIG.regionalMinSamples;
    const promoted=sampleCount>=threshold&&!regresses(model.metrics||{},metrics);
    const status=promoted?'ACTIVE':(sampleCount<threshold?'TRAINING':'REJECTED');
    const candidate={...model,region,status,sampleCount,weights,metrics,modelVersion:`ML-v${sampleCount+1}-${region}`,seenRaceKeys:[...(model.seenRaceKeys||[]),example.raceKey]};
    return{ok:true,candidate,weightDelta:delta,evaluation:ev,promoted,promotionReason:promoted?'guardrail-pass':(sampleCount<threshold?`최소표본 대기 ${sampleCount}/${threshold}`:'성능 가드 거절')};
  }
  function composeModel(globalModel,regionalModel){
    const g=globalModel||createBaselineModel('GLOBAL'),gm=normalizeMatrix(g.weights),regionalActive=!!regionalModel&&regionalModel.status==='ACTIVE'&&(regionalModel.sampleCount||0)>=CONFIG.regionalMinSamples,rm=regionalActive?normalizeMatrix(regionalModel.weights):baselineMatrix(),weights={};
    for(const score of SCORES){
      weights[score]={};
      for(const k of FEATURES)weights[score][k]=clamp(gm[score][k]+(regionalActive?(rm[score][k]-BASE[k]):0),CONFIG.weightMin,CONFIG.weightMax);
    }
    return{...g,weights,modelVersion:regionalActive?`${g.modelVersion}+${regionalModel.modelVersion}`:g.modelVersion,globalWeightVersion:g.modelVersion,regionalWeightVersion:regionalActive?regionalModel.modelVersion:null,regionAdjustmentActive:regionalActive,regionalSampleCount:regionalModel?.sampleCount||0};
  }
  global.RacingML={CONFIG,BASE,FEATURES,SCORES,createBaselineModel,evaluate,trainCandidate,composeModel,regresses,normalizeMatrix};
  if(typeof module!=='undefined')module.exports=global.RacingML;
})(typeof window!=='undefined'?window:globalThis);
