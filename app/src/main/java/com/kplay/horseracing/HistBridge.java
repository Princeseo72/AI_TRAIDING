package com.kplay.horseracing.gumvit;
import android.app.*;import android.content.*;import android.net.Uri;import android.webkit.*;import org.json.*;
public final class HistBridge{
 public static final int PICK_HIST=6001;private final MainActivity a;private final HistRepository repo;HistBridge(MainActivity x){a=x;repo=new HistRepository(x);}
 @JavascriptInterface public String getStatus(){return repo.status().toString();}
 @JavascriptInterface public void chooseHistFile(){a.runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");a.startActivityForResult(i,PICK_HIST);});}
 @JavascriptInterface public String trainFiveYear(){try{return repo.trainSummary().toString();}catch(Exception e){try{return new JSONObject().put("ok",false).put("error",e.getMessage()).toString();}catch(Exception x){return "{\"ok\":false}";}}}
 @JavascriptInterface public String getHorsePriors(String region,String beforeDate,String horses){try{return new JSONObject().put("ok",true).put("horsePriors",repo.priors(track(region),beforeDate,new JSONArray(horses))).toString();}catch(Exception e){try{return new JSONObject().put("ok",false).put("error",e.getMessage()).toString();}catch(Exception x){return "{\"ok\":false}";}}}
 void onPicked(Uri u){try{final String s=repo.importFrom(u).toString();a.runOnUiThread(()->a.dispatchHistEvent("pegasus-hist-import",s));}catch(Exception e){try{a.dispatchHistEvent("pegasus-hist-import",new JSONObject().put("ok",false).put("error",e.getMessage()).toString());}catch(Exception ignored){}}}
 private String track(String r){if(r==null)return"SEOUL";if(r.contains("제주"))return"JEJU";if(r.contains("영천"))return"YEONGCHEON";if(r.contains("부산")||r.contains("부경"))return"BUSAN_GYEONGNAM";return"SEOUL";}
}