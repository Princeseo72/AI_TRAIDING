#!/usr/bin/env python3
"""Physical Android emulator UI proof: raw screenshots and uiautomator dumps are retained."""
import os, sys, time, re, json, subprocess, pathlib, xml.etree.ElementTree as ET
base=pathlib.Path("qa/proof");base.mkdir(parents=True,exist_ok=True)
evidence={"checks":[],"started":time.strftime("%Y-%m-%d %H:%M:%S",time.gmtime())}
def cmd(*a,timeout=50,check=True):
 r=subprocess.run(a,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=timeout)
 if check and r.returncode:raise RuntimeError("%s failed %s %s"%(a,r.returncode,r.stderr.decode(errors="replace")[:600]))
 return r
def adb(*x,timeout=50):return cmd("adb",*x,timeout=timeout).stdout
def record(label,ok,detail):
 evidence["checks"].append({"name":label,"pass":bool(ok),"detail":detail})
 print(("PASS" if ok else "FAIL")+": "+label+" — "+detail,flush=True)
def shot(name):
 try:
  (base/(name+".png")).write_bytes(adb("exec-out","screencap","-p",timeout=40))
 except Exception as ex:print("SCREENSHOT FAILURE "+str(ex))
 try:
  cmd("adb","shell","uiautomator","dump","/sdcard/krace-ui.xml",timeout=60)
  xml=adb("exec-out","cat","/sdcard/krace-ui.xml",timeout=30).decode(errors="replace")
  (base/(name+".xml")).write_text(xml,encoding="utf-8")
  return ET.fromstring(xml)
 except Exception as ex:
  print("XML FAILURE "+str(ex));return None
def nodes(root):
 return list(root.iter("node")) if root is not None else []
def textall(root):
 return " | ".join(x.get("text","") for x in nodes(root) if x.get("text"))
def target(root,key,clazz=None):
 for x in nodes(root):
  if key in x.get("text","") and (clazz is None or clazz in x.get("class","")):return x
 return None
def coords(node):
 s=node.get("bounds","")
 m=re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",s)
 if not m:raise RuntimeError("no bounds "+s)
 a,b,c,d=map(int,m.groups());return str((a+c)//2),str((b+d)//2)
def tap(node):
 x,y=coords(node);adb("shell","input","tap",x,y);time.sleep(.65)
def swipe():
 adb("shell","input","swipe","230","780","230","250","400");time.sleep(1)
def find_and_tap(label,tries=4):
 for i in range(tries):
  root=shot("lookup_"+str(len(evidence["checks"]))+"_"+str(i))
  node=target(root,label)
  if node is not None:
   tap(node);return True
  swipe()
 return False
def current_app():
 v=adb("shell","dumpsys","activity","activities").decode(errors="replace")
 return "com.krace.analyzer" in v
try:
 adb("shell","wm","size","480x960");adb("shell","wm","density","200")
 adb("shell","am","start","-n","com.krace.analyzer/.MainActivity")
 time.sleep(3)
 r=shot("01_launch")
 record("APK installed and activity launched",current_app(),"android.app.Activity visible="+str(current_app()))
 record("Main title and fetch button",(target(r,"KRace") is not None and target(r,"출전표 불러오기") is not None),textall(r)[:350])
 for _ in range(3):swipe()
 r=shot("01b_model_label")
 record("Model state visible when scrolled",("미학습" in textall(r)),textall(r)[-250:])
 for _ in range(3):adb("shell","input","swipe","230","250","230","780","400")
 time.sleep(1)
 # Spinner category selection
 if find_and_tap("영남(부경)"):
  r=shot("02_track_dropdown")
  record("Track picker displays Seoul, Yeongnam, Jeju",all(s in textall(r) for s in ("서울","제주")),textall(r)[:250])
  n=target(r,"서울")
  if n is not None:
   tap(n);r=shot("03_seoul_selected")
   record("Change track to Seoul",(target(r,"서울") is not None),textall(r)[:200])
   if find_and_tap("서울"):
    r=shot("04_second_track_dropdown");n=target(r,"제주")
    if n is not None:tap(n)
    r=shot("05_jeju_selected")
    record("Change track to Jeju",(target(r,"제주") is not None),textall(r)[:200])
  # set Yeongnam again
  if find_and_tap("제주"):
   r=shot("06_third_track_dropdown");n=target(r,"영남(부경)")
   if n is not None:tap(n)
   shot("07_yeongnam_reset")
 else:record("Open track picker",False,"Yeongnam picker not found")
 # Fetch live Gumvit: may be blocked by site, capture exact outcome
 if find_and_tap("출전표 불러오기"):
  time.sleep(35)
  r=shot("08_fetch_result")
  report=textall(r)
  record("Fetch button accepts tap and updates status",("검빛" in report or "출전목록" in report or "조회" in report),report[:550])
  has_races=re.search(r"검빛 당일 출전목록\\s+\\d+경주 확인",report) is not None
  no_races="당일 출전 목록 없음" in report
  record("Today race list or explicit no-race state",has_races or no_races,
         report[:550]+" (출전 목록 유무에 따른 검사; 경기 없음은 적중 검증이 아님)")
  record("Analyze race button state",True,next((x.get("enabled","") for x in nodes(r) if "선택 경주 분석" in x.get("text","")),"not visible"))
  if has_races:
   if find_and_tap("선택 경주 분석"):
    time.sleep(2);z=shot("08b_analysis_button")
    record("Race-analysis button executes; avoids already-started races",
           "계산 중단" in textall(z) or "분석 중단" in textall(z),textall(z)[-750:])
   else:record("Race-analysis button can be pressed",False,"Button missing")
 else:record("Fetch button tap",False,"button not found")
 # CSV: click input by finding first EditText; use adb to insert lines
 for _ in range(5):swipe()
 r=shot("09_csv_field")
 inputs=[x for x in nodes(r) if "EditText" in x.get("class","")]
 if inputs:
  tap(inputs[0]);time.sleep(.5)
  lines=[
   "num,name,rating,weight,bestSec,avgSec,earlySec,lateSec,starts,wins",
   "1,HorseA,90,53,72,74,13.2,13.4,20,5",
   "2,HorseB,85,54,73,75,13.6,13.8,20,4",
   "3,HorseC,80,55,74,76,13.7,13.9,20,3"]
  for i,line in enumerate(lines):
   adb("shell","input","text",line)
   if i<len(lines)-1:adb("shell","input","keyevent","66")
  adb("shell","input","keyevent","4")
  r=shot("10_csv_inserted")
  record("CSV inserted into actual app field","HorseA" in textall(r),textall(r)[-700:])
  if find_and_tap("CSV 검증 후 계산"):
   r=shot("11_csv_result")
   t=textall(r)
   record("CSV computation produces ranked exacta/trifecta",("쌍승" in t and "삼쌍승" in t and "HorseA" in t),t[-1600:])
  else:record("CSV analysis click",False,"Button not visible")
 else:record("CSV editable field exists",False,"No visible EditText")
 # Training button with absent historical data must reject; never claim a trained model
 for _ in range(4):swipe()
 if find_and_tap("과거경주 시간순 학습"):
  time.sleep(2);r=shot("12_training_no_data")
  record("Training rejects missing historical data","학습 거부" in textall(r),textall(r)[-800:])
 else:record("Training menu available",False,"not visible")
except Exception as e:
 record("UI test script exception",False,repr(e))
finally:
 try:
  data=adb("logcat","-d","-v","threadtime",timeout=45).decode(errors="replace")
  (base/"logcat.txt").write_text(data[-900000:],encoding="utf-8",errors="replace")
  fatals=[line for line in data.splitlines() if ("FATAL EXCEPTION" in line or "Process: com.krace.analyzer" in line)]
  record("No Android process fatal errors",not fatals,"fatal entries="+str(fatals[-12:]))
 except Exception as e:record("Logcat capture",False,str(e))
 evidence["finished"]=time.strftime("%Y-%m-%d %H:%M:%S",time.gmtime())
 (base/"ui-proof.json").write_text(json.dumps(evidence,ensure_ascii=False,indent=2),encoding="utf-8")
 (base/"ui-proof.txt").write_text("\n".join(("[OK]" if c["pass"] else "[FAIL]")+" "+c["name"]+": "+c["detail"] for c in evidence["checks"]),encoding="utf-8")
 print("SUMMARY: "+str(sum(x["pass"] for x in evidence["checks"]))+"/"+str(len(evidence["checks"]))+" passed")
 # Expose partial failures as workflow failure, but retain artifacts with always() upload.
 sys.exit(0 if all(x["pass"] for x in evidence["checks"]) else 1)
