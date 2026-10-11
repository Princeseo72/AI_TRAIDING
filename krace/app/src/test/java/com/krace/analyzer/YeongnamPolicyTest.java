package com.krace.analyzer;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class YeongnamPolicyTest {
 @Test public void meet3IncludesYeongcheon(){
  assertTrue(YeongnamPolicy.accepts("B","영천"));
  assertTrue(YeongnamPolicy.accepts("B","부경"));
  assertTrue(YeongnamPolicy.accepts("B","부산경남"));
  assertFalse(YeongnamPolicy.accepts("S","영천"));
  assertFalse(YeongnamPolicy.accepts("B","제주"));
 }
 @Test public void onlyExactHorseNumberAndNameJoin(){
  ArrayList<MainActivity.Horse> a=new ArrayList<>(),b=new ArrayList<>();
  a.add(new MainActivity.Horse(1,"예시가",50,55,""));
  a.add(new MainActivity.Horse(2,"예시나",50,55,""));
  b.add(new MainActivity.Horse(1,"예시가",50,55,""));
  b.add(new MainActivity.Horse(2,"다른말",50,55,""));
  assertEquals(1,YeongnamPolicy.matchedRunners(a,b));
 }
}