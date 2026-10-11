package com.krace.analyzer;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class PartialRaceTest{
 private RankEngine.Race r(){
  ArrayList<RankEngine.Runner> h=new ArrayList<>();
  for(int i=0;i<5;i++){
   RankEngine.Runner a=new RankEngine.Runner(i+1,"마"+(i+1));
   a.weight=51+i;a.rating=60+i*4;h.add(a);
  }
  return new RankEngine.Race("2026-10-11|S|1",h);
 }
 @Test public void partialModeProducesOnlyProvisionalOrdering(){
  RankEngine.Prediction p=RankEngine.predictPartial(r(),RankEngine.PRIOR);
  assertTrue(p.partial);assertEquals(0,p.runnersWithDistance);
  assertTrue(p.warning.contains("정보제한"));assertEquals(20,p.exacta.size());
 }
 @Test public void strictStillRejectsMissingTimings(){
  try{RankEngine.predict(r(),RankEngine.PRIOR);fail();}
  catch(IllegalArgumentException e){assertTrue(e.getMessage().contains("기록 누락"));}
 }
 @Test public void zeroDistinctDataCannotInventRanking(){
  RankEngine.Race d=r();for(RankEngine.Runner h:d.runners){h.weight=55;h.rating=0;}
  try{RankEngine.predictPartial(d,RankEngine.PRIOR);fail();}
  catch(IllegalArgumentException e){assertTrue(e.getMessage().contains("구분 가능한"));}
 }
}