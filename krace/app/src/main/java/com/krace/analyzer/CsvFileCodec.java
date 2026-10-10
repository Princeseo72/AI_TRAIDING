package com.krace.analyzer;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
/** Android SAF stream decoder. Strict UTF-8 first, then Korean CP949. No binary XLSX. */
public final class CsvFileCodec {
 private CsvFileCodec(){}
 public static String readCsv(InputStream input,int limit,String displayName)throws IOException{
  if(input==null)throw new IOException("선택한 문서를 읽을 수 없습니다");
  if(limit<512)throw new IllegalArgumentException("비정상 파일 크기 제한");
  if(displayName!=null && !displayName.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(csv|txt)$"))
    throw new IOException("CSV/TXT 파일만 선택할 수 있습니다: "+displayName);
  try(InputStream in=input;ByteArrayOutputStream data=new ByteArrayOutputStream()){
   byte[] chunk=new byte[8192];int n,total=0;
   while((n=in.read(chunk))!=-1){
    total+=n;if(total>limit)throw new IOException("CSV 파일 크기 제한 초과 ("+limit+"바이트)");
    data.write(chunk,0,n);
   }
   byte[] b=data.toByteArray();
   if(b.length==0)throw new IOException("선택한 CSV 파일이 비어 있습니다");
   if(b.length>=2 && b[0]=='P'&&b[1]=='K')throw new IOException("엑셀 XLSX 압축파일입니다. CSV UTF-8로 내보내세요");
   if(b.length>=4 && b[0]==(byte)0xD0&&b[1]==(byte)0xCF)throw new IOException("엑셀 XLS는 CSV가 아닙니다");
   if(b.length>=2 && ((b[0]==(byte)0xFF&&b[1]==(byte)0xFE)||(b[0]==(byte)0xFE&&b[1]==(byte)0xFF))){
    throw new IOException("UTF-16 CSV는 지원하지 않습니다. CSV UTF-8로 저장하세요");
   }
   for(byte x:b)if(x==0)throw new IOException("CSV 파일에 바이너리/NUL 데이터가 포함됨");
   int bom=b.length>=3&&b[0]==(byte)0xEF&&b[1]==(byte)0xBB&&b[2]==(byte)0xBF?3:0;
   String decoded;
   try {decoded=decode(b,bom,b.length-bom,StandardCharsets.UTF_8);}
   catch(CharacterCodingException ex){
    try {decoded=decode(b,0,b.length,Charset.forName("MS949"));}
    catch(CharacterCodingException sub){throw new IOException("UTF-8/CP949로 읽을 수 없는 CSV입니다");}
   }
   if(decoded.trim().isEmpty())throw new IOException("CSV 데이터 없음");
   return decoded;
  }
 }
 private static String decode(byte[] data,int start,int len,Charset charset)throws CharacterCodingException{
  return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data,start,len)).toString();
 }
}