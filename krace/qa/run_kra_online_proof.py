#!/usr/bin/env python3
"""Physical KRA->Gumvit->ranking workflow smoke test on Android 10 emulator."""
import sys,json,re,time,subprocess,pathlib,xml.etree.ElementTree as ET
out=pathlib.Path("qa/proof-kra");out.mkdir(exist_ok=True,parents=True)
checks=[]
def call(*a,timeout=45):
 p=subprocess.run(a,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=timeout)
 if p.returncode:raise RuntimeError(" ".join(a)+" "+p.stderr.decode(errors="replace")[-500:])
 return p.stdout
def adb(*a):return call("adb",*a)
def record(label,passed,detail):
 checks.append({"name":label,"pass":bool(passed),"detail":detail[:1000]})
 print(("PASS" if passed else "FAIL")+": "+label+" "+detail[:350],flush=True)
def shot(label):
 (out/(label+".png")).write_bytes(adb("exec-out","screencap","-p"))
 call("adb","shell","uiautomator","dump","/sdcard/ui.xml",timeout=55)
 s=adb("exec-out","cat","/sdcard/ui.xml").decode(errors="replace")
 (out/(label+".xml")).write_text(s,encoding="utf-8")
 return ET.fromstring(s)
def nodes(x):return list(x.iter("node"))
def message(x):return " | ".join(n.get("text","") for n in nodes(x) if n.get("text"))
def locate(x,term):
 for n in nodes(x):
  if term in n.get("text",""):return n
 return None
def tap(n):
 m=re.search(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",n.get("bounds",""))
 if m is None:raise ValueError("bounds")
 a,b,c,d=map(int,m.groups())
 adb("shell","input","tap",str((a+c)//2),str((b+d)//2))
 time.sleep(.7)
def swipe(up=True):
 adb("shell","input","swipe","230",("830" if up else "200"),"230",("200" if up else "830"),"430")
 time.sleep(.8)
def findtap(term,tag):
 for i in range(6):
  x=shot(tag+"_"+str(i));n=locate(x,term)
  if n is not None:tap(n);return True
  swipe()
 return False
try:
 adb("shell","wm","size","480x960");adb("shell","wm","density","200")
 adb("shell","am","force-stop","com.krace.analyzer")
 adb("shell","am","start","-n","com.krace.analyzer/.MainActivity");time.sleep(2)
 record("Android APK launches",locate(shot("01_launch"),"KRace") is not None,"MainActivity")
 # Select Seoul: KRA meet=3 may represent Yeongcheon, not Busan, on Sunday.
 if findtap("영남(부경)","01_track_dropdown"):
  options=shot("01_track_options")
  city=locate(options,"서울")
  if city is not None:tap(city)
  selected=shot("01_seoul")
  record("Seoul track selection",locate(selected,"서울") is not None,message(selected))
 else:record("Track selection failed",False,"영남(부경) dropdown not accessible")
 if findtap("출전표 불러오기","02_load"):
  time.sleep(26);root=shot("03_kra_list");t=message(root)
  matched=re.search(r"KRA 출전확정\s+(\d+)경주",t)
  record("KRA official list parsed in Android",matched is not None and int(matched.group(1))>=1,t)
  if matched is not None:
   # Tap selected race spinner, then take a future race so hindsight guard does not misfire.
   race=locate(root,"1경주")
   if race is not None:
    tap(race);x=shot("04_race_picker")
    desired=locate(x,"9경주")
    if desired is None:desired=locate(x,"10경주")
    if desired is None:desired=locate(x,"8경주")
    if desired is not None:tap(desired)
    else:adb("shell","input","keyevent","4")
    x=shot("05_selected")
    record("Race number picker responds",desired is not None,message(x))
    if findtap("선택 경주 분석","06_analysis"):
     time.sleep(35);x=shot("07_result");t=message(x)
     if not any(word in t for word in ("쌍승","자동 수집 실패","계산 중단")):
      swipe(up=False);x=shot("07_result_scroll");t=message(x)
     record("Online runner+record analysis returns explicit result",
       any(word in t for word in ("쌍승","자동 수집 실패","계산 중단")),t)
     record("Exacta and trifecta actually computed","쌍승" in t and "삼쌍승" in t,t)
     record("Separate result screen with home navigation",("메인화면으로 복귀" in t and "출전표 불러오기" not in t),t)
     if findtap("메인화면으로 복귀","08_home"):
      home=shot("09_home_restored")
      record("Home menu restored after analysis",locate(home,"출전표 불러오기") is not None,message(home))
    else:record("Analyze button found",False,"button absent")
   else:record("Race spinner found",False,t)
 else:record("KRA fetch button found",False,"button absent")
except Exception as e:
 record("UI script exception",False,repr(e))
finally:
 try:
  d=adb("logcat","-d","-v","threadtime").decode(errors="replace")
  (out/"logcat.txt").write_text(d[-500000:],encoding="utf-8")
  f=[x for x in d.splitlines() if "FATAL EXCEPTION" in x or "Process: com.krace.analyzer" in x]
  record("No fatal Android error",len(f)==0,str(f[-4:]))
 except Exception as e:record("ADB logcat collected",False,repr(e))
 (out/"results.json").write_text(json.dumps({"checks":checks,"passed":sum(x["pass"] for x in checks),"total":len(checks)},ensure_ascii=False,indent=2),encoding="utf-8")
 print("KRA_UI_RESULT "+str(sum(x["pass"] for x in checks))+"/"+str(len(checks)),flush=True)
 sys.exit(0 if all(x["pass"] for x in checks) else 1)
