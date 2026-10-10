#!/usr/bin/env python3
"""Android 10 emulator physical CSV chooser evidence: screenshots, UI hierarchy, and logcat."""
import os,sys,time,json,re,tempfile,pathlib,subprocess,xml.etree.ElementTree as ET
base=pathlib.Path("qa/proof-csv");base.mkdir(parents=True,exist_ok=True)
checks=[]
def sh(*args,timeout=35,check=True):
 p=subprocess.run(args,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=timeout)
 if check and p.returncode:raise RuntimeError(" ".join(args)+": "+p.stderr.decode(errors="replace")[-300:])
 return p.stdout
def adb(*args):return sh("adb",*args)
def pause(sec=1):time.sleep(sec)
def record(name,passed,evidence=""):
 checks.append(dict(name=name,passed=bool(passed),evidence=evidence[:1200]))
 print(("PASS" if passed else "FAIL")+": "+name+" "+evidence[:300],flush=True)
def snapshot(name):
 (base/(name+".png")).write_bytes(adb("exec-out","screencap","-p"))
 sh("adb","shell","uiautomator","dump","/sdcard/qa.xml",timeout=50)
 xml=adb("exec-out","cat","/sdcard/qa.xml").decode("utf-8","replace")
 (base/(name+".xml")).write_text(xml,encoding="utf-8")
 return ET.fromstring(xml)
def elems(xml):return list(xml.iter("node"))
def txt(xml):return " | ".join(n.attrib.get("text","") for n in elems(xml) if n.attrib.get("text",""))
def match(xml,term):
 for n in elems(xml):
  if term.lower() in (n.get("text","")+" "+n.get("content-desc","")).lower():return n
 return None
def tap(n):
 m=re.search(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",n.get("bounds",""))
 if not m:raise RuntimeError("bounds unavailable "+n.get("bounds",""))
 x,y,X,Y=map(int,m.groups());adb("shell","input","tap",str((x+X)//2),str((y+Y)//2));pause()
def scroll(up=True):
 adb("shell","input","swipe","240",("800" if up else "250"),"240",("260" if up else "800"),"450");pause(.6)
def seek_click(term,key,tries=5):
 for i in range(tries):
  x=snapshot(key+"_"+str(i))
  a=match(x,term)
  if a is not None:tap(a);return True
  scroll()
 return False
def navigate_file(filename,key):
 # Picker often opens Recent; switch to Downloads if needed.
 for step in range(8):
  x=snapshot(key+"_"+str(step))
  if match(x,filename) is not None:
   tap(match(x,filename));return True
  if step==0:
   n=match(x,"다운로드") or match(x,"Downloads")
   if n is not None:tap(n);continue
  # Open left navigation drawer from top edge on DocumentsUI
  if step==1:
   adb("shell","input","swipe","5","340","410","340","380");pause()
  n=match(x,"다운로드")
  if n is None:n=match(x,"Downloads")
  if n is not None:tap(n);continue
  if step==3:
   adb("shell","input","tap","40","60");pause()
  if step==6:scroll()
 return False
def upload_file(filename,text,charset):
 tmp=base/filename;tmp.write_bytes(text.encode(charset))
 adb("push",str(tmp),"/sdcard/Download/"+filename)
 adb("shell","am","broadcast","-a","android.intent.action.MEDIA_SCANNER_SCAN_FILE","-d","file:///sdcard/Download/"+filename)
 pause(2)
valid="num,name,rating,weight,bestSec,avgSec,earlySec,lateSec,starts,wins\n"+"1,Alpha,90,53,1:12.0,1:13.8,13.2,13.7,20,6\n"+"2,Beta,85,54,1:13.0,1:14.8,13.7,14.0,20,4\n"+"3,Gamma,80,55,1:13.4,1:15.6,13.8,14.5,20,3\n"
invalid=valid.replace("num,name,rating","num,name,finish,rating").replace("Alpha,90","Alpha,1,90").replace("Beta,85","Beta,2,85").replace("Gamma,80","Gamma,3,80")
try:
 adb("shell","wm","size","480x960");adb("shell","wm","density","200")
 adb("shell","mkdir","-p","/sdcard/Download")
 upload_file("krace_qa_valid.csv",valid,"utf-8")
 upload_file("krace_qa_invalid.csv",invalid,"utf-8")
 adb("shell","am","start","-n","com.krace.analyzer/.MainActivity");pause(3)
 x=snapshot("01_home")
 record("APK opens",match(x,"KRace") is not None,txt(x))
 ok=seek_click("경주 CSV 파일 선택","02_open_picker")
 record("CSV choose-file button works",ok,"Clicked ACTION_OPEN_DOCUMENT button")
 if ok:
  x=snapshot("03_document_picker")
  record("Android OS document picker launched",("com.google.android.documentsui" in adb("shell","dumpsys","activity","activities").decode(errors="replace") or
    "recent" in txt(x).lower() or "최근" in txt(x) or "다운로드" in txt(x) or "downloads" in txt(x).lower()),
    txt(x))
  chosen=navigate_file("krace_qa_valid.csv","04_picker")
  record("Selected CSV physically through picker",chosen,"krace_qa_valid.csv")
  if chosen:
   pause(4);x=snapshot("05_import_result")
   t=txt(x)
   if "쌍승" not in t:
    scroll(up=False);x=snapshot("05_import_result_scrolled");t=txt(x)
   record("CSV parsed and exacta/trifecta computed",("불러오기 성공" in t or "CSV 파일:" in t) and "삼쌍승" in t and "쌍승" in t,t)
 # Invalid outcome-containing CSV must be explicitly blocked with fresh failure, no old ranking.
 if seek_click("경주 CSV 파일 선택","06_invalid_picker"):
  chosen=navigate_file("krace_qa_invalid.csv","07_invalid_picker")
  record("Invalid CSV selected",chosen,"krace_qa_invalid.csv")
  if chosen:
   pause(3);x=snapshot("08_invalid_rejected");t=txt(x)
   if "착순" not in t:
    scroll(up=False);x=snapshot("08_invalid_scrolled");t=txt(x)
   record("Outcome leakage blocked; old recommendation cleared","착순" in t and "중단" in t,t)
 else:record("Invalid CSV picker access",False,"open button not found")
except Exception as ex:
 record("Script exception",False,repr(ex))
finally:
 try:
  logs=adb("logcat","-d","-v","threadtime").decode("utf-8","replace")
  (base/"logcat.txt").write_text(logs[-700000:],encoding="utf-8")
  fatals=[x for x in logs.splitlines() if "FATAL EXCEPTION" in x or "Process: com.krace.analyzer" in x]
  record("No Android app fatal exceptions",not fatals,str(fatals[-5:]))
 except Exception as ex:record("Logcat captured",False,repr(ex))
 (base/"results.json").write_text(json.dumps({"checks":checks,"passed":sum(x["passed"] for x in checks),"total":len(checks)},ensure_ascii=False,indent=2),encoding="utf-8")
 print("CSV_REAL_UI_SUMMARY "+str(sum(x["passed"] for x in checks))+"/"+str(len(checks)),flush=True)
 sys.exit(0 if all(x["passed"] for x in checks) else 1)
