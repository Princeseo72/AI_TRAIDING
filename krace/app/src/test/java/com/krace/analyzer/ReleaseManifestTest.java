package com.krace.analyzer;
import org.junit.Test;
import static org.junit.Assert.*;
public class ReleaseManifestTest {
 private String csv="relative_path,size,sha256,category,version,required_by,distribution_action\n"+
  "ledger/actual_results.jsonl,6896,353416d0e3fdf5faaae18c6ce52a4ccba23db48bdd27b45386d0bcbc604498f6,INITIAL_SEED,,PEGASUS_RUNTIME,INSTALL_OR_PRESERVE\n"+
  "ledger/beta_all.npy,184,fa4a2294a7369229eb60a7f9adfc634892a09a300777786d1f847930e085dc7,INITIAL_SEED,,PEGASUS_RUNTIME,INSTALL_OR_PRESERVE\n";
 @Test public void reportsManifestNotRunners(){
  assertTrue(ReleaseManifest.isManifest(csv));
  ReleaseManifest.Receipt r=ReleaseManifest.inspect(csv);
  assertEquals(2,r.entries);
  assertEquals(7080,r.bytes);
  assertFalse(r.hasPayload);
  assertTrue(r.toString().contains("착순"));
 }
 @Test public void rejectDuplicateOrPathEscape(){
  String bad=csv+csv.split("\n")[1]+"\n";
  try{ReleaseManifest.inspect(bad);fail();}catch(IllegalArgumentException e){assertTrue(e.getMessage().contains("중복"));}
  bad=csv.replace("ledger/beta_all.npy","../etc/secrets");
  try{ReleaseManifest.inspect(bad);fail();}catch(IllegalArgumentException e){assertTrue(e.getMessage().contains("경로"));}
 }
 @Test public void normalRaceCsvNotManifest(){
  assertFalse(ReleaseManifest.isManifest("num,name,rating,weight,bestSec,avgSec,earlySec,lateSec,starts,wins\n"));
 }
}
