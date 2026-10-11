#!/usr/bin/env python3
"""Physical evidence: NobleEthan emblem intro and launcher -> working KRace activity."""
import pathlib,subprocess,time,json,xml.etree.ElementTree as ET
out=pathlib.Path("qa/proof-brand");out.mkdir(parents=True,exist_ok=True)
checks=[]
def cmd(*args):
 p=subprocess.run(args,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=60)
 if p.returncode:raise RuntimeError(" ".join(args)+p.stderr.decode(errors="replace")[-350:])
 return p.stdout
def adb(*args):return cmd("adb",*args)
def check(title,passed,detail=""):
 checks.append({"test":title,"pass":bool(passed),"detail":detail[:600]})
 print(("PASS" if passed else "FAIL")+": "+title+" — "+detail[:200],flush=True)
try:
 adb("shell","am","force-stop","com.krace.analyzer")
 adb("shell","am","start","-n","com.krace.analyzer/.SplashActivity")
 # Capture image promptly, before the 1550 ms splash finishes.
 (out/"01_nobleethan_intro.png").write_bytes(adb("exec-out","screencap","-p"))
 window=adb("shell","dumpsys","activity","activities").decode(errors="replace")
 check("NobleEthan splash activity launched",".SplashActivity" in window,window[-220:])
 check("Intro screenshot captured",(out/"01_nobleethan_intro.png").stat().st_size>10000,
       str((out/"01_nobleethan_intro.png").stat().st_size)+" bytes")
 time.sleep(2.2)
 (out/"02_krace_main.png").write_bytes(adb("exec-out","screencap","-p"))
 adb("shell","uiautomator","dump","/sdcard/brand.xml")
 text=adb("exec-out","cat","/sdcard/brand.xml").decode(errors="replace")
 (out/"02_krace_main.xml").write_text(text,encoding="utf-8")
 check("Intro automatically opens the main race menu",("KRace" in text and "출전표 불러오기" in text),text[-650:])
 check("No fatal crash",("MainActivity" in adb("shell","dumpsys","activity","activities").decode(errors="replace")), "Activity resumed")
except Exception as e:check("Intro test exception",False,repr(e))
(out/"result.json").write_text(json.dumps({"checks":checks,"passed":sum(t["pass"] for t in checks),"total":len(checks)},ensure_ascii=False,indent=2),encoding="utf-8")
print("BRAND_UI_RESULT "+str(sum(t["pass"] for t in checks))+"/"+str(len(checks)),flush=True)
raise SystemExit(0 if all(t["pass"] for t in checks) else 1)
