package com.krace.analyzer;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class CsvInputTest {
 static String base="num,name,rating,weight,bestSec,avgSec,earlySec,lateSec,starts,wins\n"+
    "1,HorseA,90,53,72,74,13.2,13.4,20,5\n"+
    "2,HorseB,85,54,73,75,13.6,13.8,20,4\n"+
    "3,HorseC,80,55,74,76,13.7,13.9,20,3\n";
 void reject(String csv,String match){
   try{CsvInput.parseManual(csv);fail("Expected error: "+match);}
   catch(IllegalArgumentException ex){assertTrue("received: "+ex.getMessage(),ex.getMessage().contains(match));}
 }
 @Test public void normalCsvGivesRankedOrders(){
    RankEngine.Race r=CsvInput.parseManual(base);
    RankEngine.Prediction p=RankEngine.predict(r,RankEngine.PRIOR);
    assertEquals(3,r.runners.size());assertEquals(6,p.exacta.size());assertEquals(6,p.trifecta.size());
    assertEquals(1.,p.exacta.stream().mapToDouble(x->x.score).sum(),1e-10);
    assertEquals(1.,p.trifecta.stream().mapToDouble(x->x.score).sum(),1e-10);
    assertTrue(RankEngine.summary(p,false,null).contains("삼쌍승"));
 }
 @Test public void bomAndWindowsLineEndings() {
    assertEquals(3,CsvInput.parseManual("\uFEFF"+base.replace("\n","\r\n")).runners.size());
 }
 @Test public void quotedCommaName(){
    assertEquals("Horse,A",CsvInput.parseManual(base.replace("HorseA","\"Horse,A\"")).runners.get(0).name);
 }
 @Test public void minuteSecondAccepted(){
    assertEquals(72.4,CsvInput.parseManual(base.replace(",72,74,",",1:12.4,74,")).runners.get(0).best,1e-8);
 }
 @Test public void keyboardNumberAliasAccepted(){
    assertEquals(3,CsvInput.parseManual(base.replace("num,name","number,name")).runners.size());
 }
 @Test public void invalidHeaderRejected(){ reject(base.replace("earlySec","early December"),"earlySec"); }
 @Test public void badDecimalIntRejected(){ reject(base.replace("1,HorseA","1.5,HorseA"),"정수"); }
 @Test public void duplicateNumbersRejected(){ reject(base.replace("2,HorseB","1,HorseB"),"중복"); }
 @Test public void emptyCriticalRunnerRejected(){
    reject(base.replace("2,HorseB,85,54,73,75","2,HorseB,85,54,,"),"누락");
 }
 @Test public void invalidWeightRejected(){ reject(base.replace("HorseB,85,54","HorseB,85,90"),"범위"); }
 @Test public void invalidStartsWinsRejected(){ reject(base.replace("20,5","3,5"),"전적"); }
 @Test public void infinityRejected(){ reject(base.replace("HorseB,85,54","HorseB,85,Infinity"),"범위"); }
 @Test public void fractionRecordRejected(){ reject(base.replace("HorseC,80","HorseC,80.5"),"정수"); }
 @Test public void quotedMultilineParses(){ assertEquals(3,CsvInput.parseManual(base.replace("HorseA","\"Horse\nA\"")).runners.size()); }
 @Test public void invalidQuoteRejected(){ reject(base.replace("HorseB","Horse\"B"),"인용부호"); }
 @Test public void optionalRecentAndTrainingField(){
   String v=base.replace("num,name,rating,weight,bestSec,avgSec,earlySec,lateSec,starts,wins",
       "num,name,rating,weight,bestSec,avgSec,earlySec,lateSec,starts,wins,recentSec,training14");
   v=v.replace("20,5\n","20,5,74.3,43\n").replace("20,4\n","20,4,,\n").replace("20,3\n","20,3,,\n");
   assertEquals(74.3,CsvInput.parseManual(v).runners.get(0).recent,1e-8);
 }
}
