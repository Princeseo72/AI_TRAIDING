package com.kplay.horseracing.gumvit;
import android.content.Context;import org.json.JSONObject;import java.io.*;import java.nio.charset.StandardCharsets;
public final class HistModelStore{
 private final File dir,champion,challenger;
 HistModelStore(Context c){dir=new File(c.getFilesDir(),"pegasus_hist/models");champion=new File(dir,"HIST_CHAMPION.json");challenger=new File(dir,"HIST_CHALLENGER.json");}
 synchronized JSONObject saveCandidate(JSONObject model,boolean eligible)throws Exception{
  if(!dir.exists()&&!dir.mkdirs())throw new IOException("HIST model 폴더 생성 실패");
  writeAtomic(challenger,model.put("role","CHALLENGER"));
  boolean promoted=false;if(eligible){writeAtomic(champion,new JSONObject(model.toString()).put("role","CHAMPION"));promoted=true;}
  return new JSONObject().put("challengerPath",challenger.getAbsolutePath()).put("championPath",champion.getAbsolutePath()).put("promoted",promoted);
 }
 synchronized JSONObject champion(){try{return champion.exists()?read(champion):new JSONObject().put("status","NO_HIST_CHAMPION");}catch(Exception e){return new JSONObject().put("status","HIST_CHAMPION_INVALID").put("error",e.getMessage());}}
 private static JSONObject read(File f)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();try(InputStream in=new FileInputStream(f)){byte[]x=new byte[8192];for(int n;(n=in.read(x))>0;)b.write(x,0,n);}return new JSONObject(new String(b.toByteArray(),StandardCharsets.UTF_8));}
 private static void writeAtomic(File f,JSONObject o)throws Exception{File t=new File(f.getParentFile(),f.getName()+".tmp");try(OutputStream out=new FileOutputStream(t)){out.write(o.toString().getBytes(StandardCharsets.UTF_8));out.flush();}if(f.exists()&&!f.delete())throw new IOException("기존 model 교체 실패");if(!t.renameTo(f))throw new IOException("model atomic move 실패");}
}