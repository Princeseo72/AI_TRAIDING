package com.krace.analyzer;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class RankEngineTest {
 private RankEngine.Race race(int n){
  ArrayList<RankEngine.Runner> list=new ArrayList<>();
  for(int i=0;i<n;i++){
   RankEngine.Runner h=new RankEngine.Runner(i+1,"말"+(i+1));h.rating=65+i*3;h.avg=76-i*.4;h.best=75-i*.45;
   h.recent=77-i*.35;h.early=13.8-i*.1;h.late=14.2-i*.08;h.recentLate=14-i*.08;
   h.weight=53+i*.3;h.starts=20;h.wins=2+i;h.layoffWeeks=4;h.recentPlace=n-i;h.recentField=n;
   h.finish=n-i;list.add(h);
  }
  return new RankEngine.Race("2026-01-01|B|1",list);
 }
 @Test public void probabilitiesSumToOne(){
  RankEngine.Prediction p=RankEngine.predict(race(7),RankEngine.PRIOR);
  double e=0,t=0;for(RankEngine.Ticket x:p.exacta)e+=x.score;for(RankEngine.Ticket x:p.trifecta)t+=x.score;
  assertEquals(1.,e,1e-9);assertEquals(1.,t,1e-9);
  assertEquals(42,p.exacta.size());assertEquals(210,p.trifecta.size());
 }
 @Test public void orderingAlwaysDistinct(){
  RankEngine.Prediction p=RankEngine.predict(race(9),RankEngine.PRIOR);
  for(RankEngine.Ticket x:p.trifecta)assertTrue(x.first!=x.second&&x.first!=x.third&&x.second!=x.third);
 }
 @Test public void noOutputForMissingCriticalHorseData(){
  RankEngine.Race r=race(5);r.runners.get(1).avg=Double.NaN;r.runners.get(1).best=Double.NaN;r.runners.get(1).recent=Double.NaN;
  try{RankEngine.predict(r,RankEngine.PRIOR);fail();}catch(IllegalArgumentException ok){assertTrue(ok.getMessage().contains("누락"));}
 }
 @Test public void missingOptionalNotInvented(){
  RankEngine.Race r=race(5);
  r.runners.get(1).late=Double.NaN;
  RankEngine.Prediction p=RankEngine.predict(r,RankEngine.PRIOR);
  assertTrue(p.missingCells>0);assertFalse(p.warning.isEmpty());
 }
 @Test public void pressureIsPenalizedWhenAnotherLeaderMatches(){
  RankEngine.Race r=race(5);
  for(RankEngine.Runner h:r.runners)h.early=14.4;
  r.runners.get(0).early=13.2;
  double solo=RankEngine.features(r)[0][5];
  r.runners.get(1).early=13.3;
  double crowded=RankEngine.features(r)[0][5];
  assertTrue("crowded pace must be worse than solo",crowded<solo);
 }
 @Test public void historicalWithoutEnoughRacesMustReject(){
  String head="date,track,race,num,name,finish,rating,weight,bestSec,avgSec,recentSec,earlySec,lateSec,recentLateSec,starts,wins,layoffWeeks,recentPlace,recentField\n";
  for(int i=1;i<=6;i++)head+="2026-01-01,B,1,"+i+",H"+i+","+i+",70,54,74,75,75,13.8,13.8,14,20,4,3,2,8\n";
  try{RankEngine.fitHistorical(head);fail();}catch(IllegalArgumentException ok){assertTrue(ok.getMessage().contains("최소 40"));}
 }
 @Test public void validationRejectsDuplicatedNumbers(){
  RankEngine.Race r=race(5);r.runners.get(2).no=r.runners.get(0).no;
  try{RankEngine.predict(r,RankEngine.PRIOR);fail();}catch(IllegalArgumentException ok){assertTrue(ok.getMessage().contains("중복"));}
 }
}