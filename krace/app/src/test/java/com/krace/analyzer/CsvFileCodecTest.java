package com.krace.analyzer;
import static org.junit.Assert.*;
import org.junit.Test;
import java.io.*;
import java.nio.charset.*;
public class CsvFileCodecTest {
 private String csv="num,name,rating,weight,bestSec,avgSec,earlySec,lateSec,starts,wins\n"+
 "1,홍길동,85,52.5,1:12.3,73.2,13.0,13.8,20,5\n"+
 "2,비상,74,55,73.1,74.4,13.8,14.5,16,3\n"+
 "3,도약,70,54,73.5,75.0,14.1,14.0,12,2\n";
 private String read(byte[] b,String name)throws Exception{return CsvFileCodec.readCsv(new ByteArrayInputStream(b),100000,name);}
 @Test public void unicodeUtf8BomWorksWithPrediction()throws Exception{
  byte[] src=csv.getBytes("UTF-8");
  byte[] b=new byte[src.length+3];b[0]=(byte)239;b[1]=(byte)187;b[2]=(byte)191;
  System.arraycopy(src,0,b,3,src.length);
  String v=read(b,"경주.csv");
  assertEquals(3,CsvInput.parseManual(v).runners.size());
  assertEquals("홍길동",CsvInput.parseManual(v).runners.get(0).name);
  assertEquals(6,RankEngine.predict(CsvInput.parseManual(v),RankEngine.PRIOR).trifecta.size());
 }
 @Test public void koreanCp949Reads()throws Exception{
  String v=read(csv.getBytes("MS949"),"한국경마.CSV");
  assertEquals("홍길동",CsvInput.parseManual(v).runners.get(0).name);
 }
 @Test public void rejectsZipDisguisedAsCsv(){
  try{read(new byte[]{'P','K',3,4,0},"fake.csv");fail();}
  catch(Exception e){assertTrue(e.getMessage().contains("XLSX"));}
 }
 @Test public void rejectsUnknownBinary(){
  try{read(new byte[]{'a',0,'b'},"x.csv");fail();}
  catch(Exception e){assertTrue(e.getMessage().contains("바이너리"));}
 }
 @Test public void rejectsOversizedFile()throws Exception{
  try{CsvFileCodec.readCsv(new ByteArrayInputStream(new byte[1024]),512,"too.csv");fail();}
  catch(Exception e){assertTrue(e.getMessage().contains("크기 제한"));}
 }
 @Test public void allowsCrLf()throws Exception{
  String v=read(csv.replace("\n","\r\n").getBytes("UTF-8"),"x.txt");
  assertEquals(3,CsvInput.parseManual(v).runners.size());
 }
 @Test public void rejectsXlsxExtension(){
  try{read(csv.getBytes("UTF-8"),"race.xlsx");fail();}
  catch(Exception e){assertTrue(e.getMessage().contains("CSV/TXT"));}
 }
}