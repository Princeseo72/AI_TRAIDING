package com.krace.analyzer;
import org.junit.Test;
import org.jsoup.Jsoup;
import static org.junit.Assert.*;
import java.util.*;
public class ConditionDataTest{
 @Test public void trainingUsesOnlyPreRaceWorkouts(){
  String html="<div>9경주</div><div>1 벌교의꿈 진겸 / 이상영 / 52.5 (-4.5) / 한거6세 "+
   "09/27 일 관리 15분 09/28 월 관리 17분 10/03 토 관리 15분 10/08 목 관리 13분 ▶ 10/09 1 1200M 순위 5 "+
   "08/23 일 관리 14분</div><div>2 미러클마린 송경윤 / 최기홍 / 53.0 (-3) / 한거5세 "+
   "10/07 수 관리 9분 10/08 목 관리 10분 ▶ 10/09 1 1200M 순위 1</div>";
  Map<Integer,Double> x=ConditionData.training(Jsoup.parse(html),"2026-10-09");
  assertTrue(x.containsKey(1));assertTrue(x.containsKey(2));
  assertTrue(x.get(1)>x.get(2));
 }
 @Test public void vetDoesNotCountPostRaceOrAncientConditions(){
  String html="<table><tr><td>8</td><td>본다이아</td><td>2026.09.07양전구절염,요배통 2026.09.29양후지 근육통</td></tr>"+
  "<tr><td>3</td><td>강서자이언트</td><td>2026.10.15골절 2026.10.04운동기인성 피로회복(수액처치)</td></tr></table>";
  Map<Integer,Double> x=ConditionData.veterinary(Jsoup.parse(html),"2026-10-09");
  assertTrue(x.get(8)>.1);assertTrue(x.get(3)<.3);
 }
}