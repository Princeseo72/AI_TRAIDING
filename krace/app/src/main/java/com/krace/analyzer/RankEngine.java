package com.krace.analyzer;
import java.util.*;
import java.io.*;
/**
 * Pre-race only, leakage-guarded relative ranking engine.
 * Predictive weights have no claimed skill until chronological holdout is supplied.
 */
public final class RankEngine {
 public static final String[] KEYS={"distanceAverage","recentRace","historicalBest","closing200","opening200",
  "pacePressure","closingFade","classRating","weight","smoothedWinRate","layoff","recentPlace","training14","vetHistory","jockeyWinRate"};
 public static final int K=KEYS.length;
 public static final double[] PRIOR={.30,.20,.07,.14,.10,.16,.10,.11,.06,.05,.06,.06,0,0,0};
 private static final double[] FLOORS={1.0,1.0,1.0,.25,.25,.15,.25,5.0,1.0,.05,2.0,.15,15.0,.2,.05};
 private static final double EPS=1e-12;
 private RankEngine(){}
 public static class Runner {
   public int no,rating,starts=-1,wins=-1,recentPlace=-1,recentField=-1,layoffWeeks=-1,finish=-1;
   public String name="";
   public double avg=Double.NaN,best=Double.NaN,recent=Double.NaN,early=Double.NaN,late=Double.NaN,
    recentLate=Double.NaN,weight=Double.NaN,training14=Double.NaN,vetRisk=Double.NaN;
   public int jockeyWins=-1,jockeyRides=-1;
   public Runner(int n,String s){no=n;name=s;}
 }
 public static class Race {
   public final String id;public final ArrayList<Runner> runners;
   public Race(String id,ArrayList<Runner> r){this.id=id;this.runners=r;}
 }
 public static class Ticket {
   public int first,second,third;public double score;
   Ticket(int a,int b,int c,double d){first=a;second=b;third=c;score=d;}
   public String toString(){return third==0?first+" → "+second:first+" → "+second+" → "+third;}
 }
 public static class Prediction {
   public final ArrayList<Ticket> exacta=new ArrayList<>(),trifecta=new ArrayList<>();
   public final HashMap<Integer,Double> scores=new HashMap<>();
   public final HashMap<Integer,double[]> contributions=new HashMap<>();
   public int runnersWithDistance,missingCells;public boolean partial=false;public String warning="";
 }
 public static class Evaluation{
   public int trainRaces,testRaces,exactHits,tripleHits;
   
   public double exactRate(){return testRaces>0?(double)exactHits/testRaces:Double.NaN;}
   public double tripleRate(){return testRaces>0?(double)tripleHits/testRaces:Double.NaN;}
 }
 public static class Fitted{
   public final double[] weights;public final Evaluation eval;
   public Fitted(double[] w,Evaluation e){weights=w;eval=e;}
 }
 static boolean good(double v){return !Double.isNaN(v)&&!Double.isInfinite(v);}
 public static void validate(Race race,boolean needFinish){
   if(race==null||race.runners==null||race.runners.size()<3||race.runners.size()>20)
    throw new IllegalArgumentException("출전마 3~20두 필요");
   HashSet<Integer> set=new HashSet<>();boolean[] places=new boolean[4];
   for(Runner h:race.runners){
    if(h.no<1||h.no>30||!set.add(h.no))throw new IllegalArgumentException("중복·오류 마번");
    if(!good(h.avg)&&!good(h.best)&&!good(h.recent))
       throw new IllegalArgumentException("마번 "+h.no+" 거리/최근 주파기록 누락: 계산 중단");
    if(good(h.weight)&&(h.weight<45||h.weight>65))throw new IllegalArgumentException("부담중량 오류");
    if(h.rating<0||h.rating>150)throw new IllegalArgumentException("레이팅 범위 오류");
    if(h.starts>=0&&(h.wins<0||h.wins>h.starts))throw new IllegalArgumentException("전적 오류");
    if(h.jockeyRides>=0&&(h.jockeyWins<0||h.jockeyWins>h.jockeyRides))throw new IllegalArgumentException("기수 전적 오류");
    if(needFinish&&h.finish>0&&h.finish<=3){if(places[h.finish])throw new IllegalArgumentException("입상 순서 중복");places[h.finish]=true;}
    if(needFinish&&h.finish<=0)throw new IllegalArgumentException("결과 누락: "+h.no);
   }
   if(needFinish&&!(places[1]&&places[2]&&places[3]))throw new IllegalArgumentException("1~3착 정보 누락");
 }
 private static double[] base(Runner h,double pacePressure){
   double[] f=new double[K];
   f[0]=good(h.avg)?-h.avg:Double.NaN;
   f[1]=good(h.recent)?-h.recent:Double.NaN;
   f[2]=good(h.best)?-h.best:Double.NaN;
   f[3]=good(h.late)?-h.late:Double.NaN;
   f[4]=good(h.early)?-h.early:Double.NaN;
   f[5]=pacePressure;
   f[6]=good(h.recentLate)&&good(h.late)?-(h.recentLate-h.late):Double.NaN;
   f[7]=h.rating>0?h.rating:Double.NaN;
   f[8]=good(h.weight)?-h.weight:Double.NaN;
   f[9]=h.starts>0?(h.wins+1.)/(h.starts+8.):Double.NaN;
   f[10]=h.layoffWeeks>=0? -Math.max(0,h.layoffWeeks-8):Double.NaN;
   f[11]=h.recentPlace>0&&h.recentField>1?-(h.recentPlace-1.)/(h.recentField-1.):Double.NaN;
   f[12]=good(h.training14)?h.training14:Double.NaN;
   f[13]=good(h.vetRisk)?-h.vetRisk:Double.NaN;
   f[14]=h.jockeyRides>0?(h.jockeyWins+1.)/(h.jockeyRides+8.):Double.NaN;
   return f;
 }
 /** Each race is normalized independently; the mean of an unknown feature is not asserted to be real. */
 public static double[][] features(Race race){return features(race,false);}
 public static double[][] features(Race race,boolean allowPartial){
   if(allowPartial)validatePartial(race);else validate(race,false);
   int n=race.runners.size();double[][] raw=new double[n][K],out=new double[n][K];
   double fastest=Double.POSITIVE_INFINITY;
   for(Runner h:race.runners)if(good(h.early))fastest=Math.min(fastest,h.early);
   int crowd=0;
   for(Runner h:race.runners)if(good(h.early)&&h.early<=fastest+.30)crowd++;
   for(int i=0;i<n;i++){
    Runner h=race.runners.get(i);
    // A single speed horse gets mild advantage; multiple nearly equal fast starters receive pressure penalty.
    double p=good(h.early)&&good(fastest)?
       (h.early<=fastest+.30 ? (crowd==1?+.7:-.55*Math.min(3,crowd-1)):0):Double.NaN;
    raw[i]=base(h,p);
   }
   for(int j=0;j<K;j++){
    double sum=0,variance=0;int m=0;
    for(int i=0;i<n;i++)if(good(raw[i][j])){sum+=raw[i][j];m++;}
    if(m==0)continue;
    double mean=sum/m;
    for(int i=0;i<n;i++)if(good(raw[i][j]))variance+=(raw[i][j]-mean)*(raw[i][j]-mean);
    double sd=Math.max(FLOORS[j],Math.sqrt(variance/m));
    for(int i=0;i<n;i++)
      out[i][j]=good(raw[i][j])?Math.max(-2.5,Math.min(2.5,(raw[i][j]-mean)/sd)):0;
   }
   return out;
 }
 /** Partial data mode uses only observed features; never invents race times. */
 public static Prediction predictPartial(Race r,double[] w){return score(r,w,true);}
 public static Prediction predict(Race r,double[] w){return score(r,w,false);}
 private static void validatePartial(Race race){
   if(race==null||race.runners==null||race.runners.size()<3||race.runners.size()>20)
     throw new IllegalArgumentException("출전마 3~20두 필요");
   Set<Integer> seen=new HashSet<>();
   int evidence=0;
   for(Runner h:race.runners){
     if(h.no<1||h.no>30||!seen.add(h.no)||h.name==null||h.name.trim().isEmpty())
       throw new IllegalArgumentException("마번/마명 누락 또는 중복");
     if(good(h.weight)&&(h.weight<45||h.weight>65))throw new IllegalArgumentException("부담중량 범위 오류");
     if(h.rating<0||h.rating>150)throw new IllegalArgumentException("레이팅 범위 오류");
     if(good(h.avg)||good(h.best)||good(h.recent)||h.rating>0||h.starts>0||good(h.weight))evidence++;
   }
   if(evidence!=race.runners.size())
     throw new IllegalArgumentException("출전마 기본 기록 일부 미확보: 임의 순위 생성 금지");
 }
 private static Prediction score(Race r,double[] w,boolean partial){
   if(w==null||w.length!=K)throw new IllegalArgumentException("가중치 길이 오류");
   double[][] x=features(r,partial);
   Prediction ans=new Prediction();ans.partial=partial;
   double sum=0;
   double[] mass=new double[x.length];
   int availableDistance=0;
   for(int i=0;i<x.length;i++){
    Runner h=r.runners.get(i);
    if(good(h.avg)||good(h.best))availableDistance++;
    for(int j=0;j<K;j++)if(!good(base(h,0)[j]))ans.missingCells++;
    double score=0;double[] parts=new double[K];
    for(int j=0;j<K;j++){if(!good(w[j])||Math.abs(w[j])>8)throw new IllegalArgumentException("가중치 오류");parts[j]=w[j]*x[i][j];score+=parts[j];}
    ans.scores.put(h.no,score);ans.contributions.put(h.no,parts);
    mass[i]=Math.exp(Math.max(-5,Math.min(5,score)));sum+=mass[i];
   }
   ans.runnersWithDistance=availableDistance;
   if(partial){
     // Where no speed data exists, distinguish carefully: provisional ordering only.
     ans.warning="정보제한 잠정순위: 동일거리 기록 "+availableDistance+"/"+r.runners.size()+
       "두, 누락값 "+ans.missingCells+"개. 기록 없는 마필은 추정으로 채우지 않음. 실전 적중률 미검증.";
     double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
     for(Double xScore:ans.scores.values()){min=Math.min(min,xScore);max=Math.max(max,xScore);}
     if(max-min<.00001)throw new IllegalArgumentException("마필간 구분 가능한 전개·기록 정보 없음. 잠정순위 생성 금지");
   }else if(ans.missingCells>0)ans.warning="미수집 변수 "+ans.missingCells+"개는 0 기여로 처리. 예측 신뢰도 낮음.";
   for(int i=0;i<x.length;i++)for(int j=0;j<x.length;j++){
    if(i==j)continue;
    double e=mass[i]/sum * mass[j]/(sum-mass[i]);
    ans.exacta.add(new Ticket(r.runners.get(i).no,r.runners.get(j).no,0,e));
    for(int k=0;k<x.length;k++)if(k!=i&&k!=j){
      double t=e*mass[k]/(sum-mass[i]-mass[j]);
      ans.trifecta.add(new Ticket(r.runners.get(i).no,r.runners.get(j).no,r.runners.get(k).no,t));
    }
   }
   ans.exacta.sort((a,b)->Double.compare(b.score,a.score));
   ans.trifecta.sort((a,b)->Double.compare(b.score,a.score));
   return ans;
 }
 private static String[] split(String s){return s.split(",",-1);}
 private static double num(String s){try{if(s.trim().isEmpty())return Double.NaN;return Double.parseDouble(s.trim());}catch(Exception e){return Double.NaN;}}
 private static int integer(String s,int missing){double d=num(s);return good(d)?(int)d:missing;}
 /** CSV columns are pre-race snapshots. DO NOT supply values calculated from the result being predicted. */
 public static ArrayList<Race> readHistorical(String csv){
   String[] lines=csv.replace("\r","").split("\n");
   if(lines.length<2)throw new IllegalArgumentException("과거 경주 CSV가 비어 있습니다");
   String[] header=split(lines[0]);HashMap<String,Integer> col=new HashMap<>();
   for(int i=0;i<header.length;i++)col.put(header[i].trim(),i);
   String[] required={"date","track","race","num","name","finish","rating","weight","bestSec","avgSec","recentSec",
    "earlySec","lateSec","recentLateSec","starts","wins","layoffWeeks","recentPlace","recentField"};
   for(String q:required)if(!col.containsKey(q))throw new IllegalArgumentException("과거 CSV 필수열 누락: "+q);
   TreeMap<String,ArrayList<Runner>> map=new TreeMap<>();
   for(int i=1;i<lines.length;i++){
    if(lines[i].trim().isEmpty())continue;
    String[] c=split(lines[i]);if(c.length<header.length)throw new IllegalArgumentException("과거 CSV "+(i+1)+"행 열 부족");
    String date=c[col.get("date")].trim();
    if(!date.matches("\\d{4}-\\d{2}-\\d{2}"))throw new IllegalArgumentException("날짜 형식 오류");
    String id=date+"|"+c[col.get("track")].trim()+"|"+c[col.get("race")].trim();
    Runner h=new Runner(integer(c[col.get("num")],-1),c[col.get("name")].trim());
    h.finish=integer(c[col.get("finish")],-1);h.rating=integer(c[col.get("rating")],0);
    h.weight=num(c[col.get("weight")]);h.best=num(c[col.get("bestSec")]);h.avg=num(c[col.get("avgSec")]);
    h.recent=num(c[col.get("recentSec")]);h.early=num(c[col.get("earlySec")]);h.late=num(c[col.get("lateSec")]);
    h.recentLate=num(c[col.get("recentLateSec")]);h.starts=integer(c[col.get("starts")],-1);
    h.wins=integer(c[col.get("wins")],-1);h.layoffWeeks=integer(c[col.get("layoffWeeks")],-1);
    h.recentPlace=integer(c[col.get("recentPlace")],-1);h.recentField=integer(c[col.get("recentField")],-1);
    h.training14=col.containsKey("training14")?num(c[col.get("training14")]):Double.NaN;
    h.vetRisk=col.containsKey("vetRisk")?num(c[col.get("vetRisk")]):Double.NaN;
    h.jockeyRides=col.containsKey("jockeyRides")?integer(c[col.get("jockeyRides")],-1):-1;
    h.jockeyWins=col.containsKey("jockeyWins")?integer(c[col.get("jockeyWins")],-1):-1;
    map.computeIfAbsent(id,k->new ArrayList<>()).add(h);
   }
   ArrayList<Race> result=new ArrayList<>();
   for(Map.Entry<String,ArrayList<Runner>> e:map.entrySet()){
    Race r=new Race(e.getKey(),e.getValue());validate(r,true);result.add(r);
   }
   return result;
 }
 public static Fitted fitHistorical(String csv){
   ArrayList<Race> rows=readHistorical(csv);
   if(rows.size()<40)throw new IllegalArgumentException("과거 완료 경주 최소 40개 필요. 현재 "+rows.size());
   // Group sorted by race date, always train earlier dates and evaluate later dates.
   String cutoff=rows.get((int)Math.floor(rows.size()*.75)).id.substring(0,10);
   ArrayList<Race> train=new ArrayList<>(),test=new ArrayList<>();
   for(Race r:rows)if(r.id.substring(0,10).compareTo(cutoff)<0)train.add(r);else test.add(r);
   if(train.size()<25||test.size()<10)throw new IllegalArgumentException("날짜 분리 학습/검증 표본 부족");
   double[] w=PRIOR.clone();
   // Pairwise logistic rank learning; no result-dependent fields may appear in features.
   for(int epoch=0;epoch<55;epoch++){
    double eta=.065/(1+.035*epoch);
    for(Race r:train){
      double[][] x=features(r);
      for(int i=0;i<r.runners.size();i++)for(int j=0;j<r.runners.size();j++){
       int fi=r.runners.get(i).finish,fj=r.runners.get(j).finish;
       if(i==j||fi>=fj||fi>3)continue;
       double diff=0;for(int k=0;k<K;k++)diff+=w[k]*(x[i][k]-x[j][k]);
       double grad=1./(1.+Math.exp(Math.min(20,Math.max(-20,diff))));
       for(int k=0;k<K;k++){
        w[k]+=eta*(grad*(x[i][k]-x[j][k])-.001*w[k]);
        w[k]=Math.max(-2,Math.min(2,w[k]));
       }
      }
    }
   }
   Evaluation e=new Evaluation();e.trainRaces=train.size();e.testRaces=test.size();
   for(Race r:test){
     Prediction p=predict(r,w);int a=-1,b=-1,c=-1;
     for(Runner h:r.runners){if(h.finish==1)a=h.no;if(h.finish==2)b=h.no;if(h.finish==3)c=h.no;}
     Ticket x=p.exacta.get(0),t=p.trifecta.get(0);
     if(x.first==a&&x.second==b)e.exactHits++;
     if(t.first==a&&t.second==b&&t.third==c)e.tripleHits++;
   }
   return new Fitted(w,e);
 }
 public static String summary(Prediction p,boolean trained,Evaluation e){
   StringBuilder s=new StringBuilder();
   if(!trained)s.append("경고: 미학습 임시 가중치. 예측 적중률 검증 안 됨.\n");
   if(p.partial)s.append("**제한적 자료로 산출한 임시 착순 순위입니다. 예측 정확도 입증 없음.**\n");
   else s.append("과거 경주 시계열 검증: 훈련 ").append(e.trainRaces).append("경주/검증 ").append(e.testRaces)
    .append("경주; 쌍승 ").append(e.exactHits).append("/").append(e.testRaces)
    .append(" 삼쌍승 ").append(e.tripleHits).append("/").append(e.testRaces).append("\n");
   s.append("쌍승 ").append(p.exacta.get(0)).append("\n");
   s.append("삼쌍승 ").append(p.trifecta.get(0)).append("\n");
   s.append("삼쌍승 차선 ").append(p.trifecta.get(1)).append("\n");
   s.append("분석 항목: 평균/최근/최고/종반/초반/선행경합/종반감속/레이팅/중량/승률/공백/최근착순/조교량/진료/기수성적\n");
   if(!p.warning.isEmpty())s.append(p.warning).append("\n");
   s.append("각 마번 변수별 기여값:\n");
   ArrayList<Integer> order=new ArrayList<>(p.scores.keySet());
   order.sort((a,b)->Double.compare(p.scores.get(b),p.scores.get(a)));
   for(int n:order){
    s.append(n).append("번 점수 ").append(String.format(Locale.US,"%.3f",p.scores.get(n))).append(" | ");
    double[] parts=p.contributions.get(n);
    for(int i=0;i<K;i++)if(Math.abs(parts[i])>.001)
       s.append(KEYS[i]).append("=").append(String.format(Locale.US,"%+.2f",parts[i])).append(" ");
    s.append("\n");
   }
   s.append("※ 계산한 조합 수치는 모델 가정상의 값이며 실제 배당·적중 확률이 아닙니다.\n");
   return s.toString();
 }
}