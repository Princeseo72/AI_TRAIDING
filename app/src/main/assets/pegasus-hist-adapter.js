(function(root){
'use strict';
const SCHEMA='PEGASUS_HIST_V1';
function validate(manifest,races){
 const errors=[],seen=new Set();
 if(!manifest||manifest.schema_version!==SCHEMA)errors.push('SCHEMA_MISMATCH');
 if(!manifest||!manifest.source_hash)errors.push('SOURCE_HASH_MISSING');
 for(const r of races||[]){
  if(!r.race_uid||seen.has(r.race_uid))errors.push('RACE_UID_INVALID');
  seen.add(r.race_uid);
  if(!['SEOUL','BUSAN_GYEONGNAM','JEJU','YEONGCHEON'].includes(r.track_code))errors.push('TRACK_INVALID');
  const runners=new Set();
  for(const h of r.runners||[]){
   if(!h.runner_uid||runners.has(h.runner_uid)||!(h.horse_no>=1&&h.horse_no<=30))errors.push('RUNNER_INVALID');
   runners.add(h.runner_uid);
  }
 }
 return {ok:errors.length===0,status:errors.length?'HIST_PENDING':'IMPORT_VALIDATED',errors,raceCount:seen.size};
}
function buildPrior(races,request){
 const cutoff=request.race_time;
 if(!cutoff||!/^\d{4}-\d\d-\d\dT/.test(cutoff))throw Error('RACE_TIME_REQUIRED');
 const stats=new Map();let samples=0;
 for(const race of races||[]){
  if(race.track_code!==request.track_code||!race.race_time||race.race_time>=cutoff)continue;
  for(const h of race.runners||[]){
   const name=String(h.horse_name||'').trim();
   if(!name||!Number.isInteger(h.finish_position))continue;
   const s=stats.get(name)||{starts:0,wins:0,top3:0};
   s.starts++;s.wins+=Number(h.finish_position===1);s.top3+=Number(h.finish_position<=3);stats.set(name,s);samples++;
  }
 }
 const horsePriors={},evidence={};
 for(const h of request.runners||[]){
  const name=String(h.horse_name||h.name||'').trim(),n=String(h.horse_no||h.number),s=stats.get(name)||{starts:0,wins:0,top3:0};
  horsePriors[n]=(s.wins+1)/(s.starts+10);
  evidence[n]={starts:s.starts,wins:s.wins,top3:s.top3};
 }
 return {status:samples?'HIST_FEATURES_AVAILABLE':'HIST_PENDING',source:SCHEMA,runnerSampleCount:samples,horsePriors,evidence,cutoff};
}
const api={SCHEMA,validate,buildPrior};
if(typeof module!=='undefined')module.exports=api;
root.PegasusHistAdapter=api;
})(typeof window!=='undefined'?window:globalThis);
