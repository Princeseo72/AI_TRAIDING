package com.krace.analyzer;
import java.util.*;
import java.util.regex.*;
/** Pegasus release data manifest, not a racing prediction CSV and not the referenced payload. */
public final class ReleaseManifest {
 private static final String HEADER="relative_path,size,sha256,category,version,required_by,distribution_action";
 public static class Receipt {
  public final int entries;
  public final long bytes;
  public final boolean hasPayload=false;
  public final String category;
  public final boolean matchesAttachedManifest;
  Receipt(int n,long b,String c,boolean exact){entries=n;bytes=b;category=c;matchesAttachedManifest=exact;}
  public String toString(){
    return "PEGASUS 파일목록 접수 완료\n\n"
    +"검증된 파일 항목: "+entries+"개\n"
    +"목록에 표시된 파일 크기 합계: "+String.format(java.util.Locale.KOREA,"%,d",bytes)+" bytes\n"
    +"분류: "+category+"\n"+(matchesAttachedManifest?"첨부된 2026-10-11 원본 매니페스트와 해시 일치\n":"별도 매니페스트로 검증됨 (원본 해시는 다름)\n")+"\n"
    +"중요: 이 CSV는 파일의 경로·크기·해시 목록입니다.\n"
    +"실제 DB·JSONL·과거경주 기록 데이터는 이 CSV에 들어 있지 않습니다.\n"
    +"원본 파일들이 없으므로 착순 학습이나 결과 계산에 직접 사용할 수 없습니다.\n"
    +"경주 예측에는 KRA·검빛 자동수집 또는 출전마 CSV를 사용하세요.";
  }
 }
 private ReleaseManifest(){}
 public static boolean isManifest(String csv){
  if(csv==null)return false;
  String first=csv.replace("\uFEFF","").replace("\r","").split("\n",2)[0].trim();
  return first.equals(HEADER);
 }
 public static Receipt inspect(String csv){
  if(!isManifest(csv))throw new IllegalArgumentException("PEGASUS 매니페스트 헤더 불일치");
  String[] lines=csv.replace("\uFEFF","").replace("\r","").split("\n");
  Set<String> paths=new HashSet<>();long total=0;int entries=0;String type="";
  Pattern sha=Pattern.compile("[0-9a-fA-F]{64}");
  for(int i=1;i<lines.length;i++){
   if(lines[i].trim().isEmpty())continue;
   String[] p=lines[i].split(",",-1);
   if(p.length!=7)throw new IllegalArgumentException("매니페스트 "+(i+1)+"행 열 7개 필요");
   String file=p[0].trim().replace('\\','/');
   if(file.isEmpty()||file.startsWith("/")||file.contains("../")||file.contains("/..")||
       file.startsWith("..")||file.contains("//"))
     throw new IllegalArgumentException("매니페스트 상대경로 오류: "+(i+1));
   if(!paths.add(file))throw new IllegalArgumentException("매니페스트 중복 경로: "+file);
   if(!sha.matcher(p[2]).matches())throw new IllegalArgumentException("SHA-256 오류: "+(i+1));
   try{
    long n=Long.parseLong(p[1]);
    if(n<0)throw new NumberFormatException();
    total=Math.addExact(total,n);
   }catch(Exception ex){throw new IllegalArgumentException("파일 크기 오류: "+(i+1));}
   if(entries==0)type=p[3].trim();entries++;
  }
  if(entries==0)throw new IllegalArgumentException("매니페스트 파일목록 비어 있음");
  boolean exact=false;
  try{
   String normalized=csv.replace("\uFEFF","");
   java.security.MessageDigest d=java.security.MessageDigest.getInstance("SHA-256");
   byte[] result=d.digest(normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8));
   StringBuilder hex=new StringBuilder();
   for(byte b:result)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&0xff));
   exact=entries==6023&&hex.toString().equals("2acf9b39c3c93d2ac9eb7a2b051d2721223033176b764f7bd97f238eb3ab290a");
  }catch(Exception e){throw new IllegalArgumentException("매니페스트 해시 검증 실패",e);}
  return new Receipt(entries,total,type,exact);
 }
}
