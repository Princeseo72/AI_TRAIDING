# PEGASUS 5년 HIST 학습·적용 기술기준서
문서상태: IMPLEMENTATION CONTRACT / 변경 불변 기준
제정일: 2026-10-07
적용대상: PEGASUS v4 이후 HIST 학습·실전 분석 엔진
우선원칙: 기존 기능 유지 + 본 문서에 명시된 HIST 계층만 추가/개선

## 1. 목적
5년 수집기에서 생성되는 PEGASUS_HIST_V1 원본 DB를 장기 학습 데이터로 사용한다.
HIST는 정답 DB가 아니다. 각 과거 경주의 예측시점 이전에 알 수 있었던 정보만으로 조건부 확률을 만들고,
Prediction Snapshot 이후 실제 결과를 공개하여 오차를 학습하는 Historical Replay 원천 DB다.

최종 사이클:
5Y Collector -> Immutable HIST DB -> Import Validation -> Feature Builder -> Historical Replay
-> Global/Regional Candidate -> Walk-Forward Validation -> Champion/Challenger
-> PEGASUS T20 -> T5 Movement -> Cross-Pool -> Calibration -> Ordered Finish
-> Final123/Final8 -> Immutable Prediction Snapshot -> Actual Outcome -> Closed Loop

## 2. 변경금지 / 호환성
기존 다음 기능은 삭제·이름변경·의미변경 금지:
- 메뉴 1 경주선택 / 2 T20 / 3 T5 / 4 연산 / 5 최종출력 / 6 저장기록
- 제외마 처리
- T20/T5 입력 및 저장
- Final123
- Final8 = QUINELLA 2 + EXACTA 2 + TRIO 2 + TRIFECTA 2
- 실제결과 입력 및 기존 Closed Loop
- 기존 저장기록/Prediction Snapshot 계약
공유코드 수정이 불가피하면 기존 호출계약과 입출력은 유지하고 후방호환 필드만 추가한다.

## 3. HIST 원본 DB 계약
schema_version = PEGASUS_HIST_V1
원본은 READ-ONLY / APPEND-ONLY 취급한다. PEGASUS가 원본 race/runner/odds 행을 UPDATE/DELETE해서는 안 된다.
Import 시 schema_version, source_hash, race_uid, runner_uid, odds_uid, 날짜범위, 중복, 지역코드, 승식코드, 마번조합을 검증한다.
검증 실패 DB는 HIST_READY로 승격 금지.

track_code:
SEOUL
BUSAN_GYEONGNAM
JEJU
YEONGCHEON

region_group:
CAPITAL
YEONGNAM
JEJU

BUSAN_GYEONGNAM 과 YEONGCHEON은 절대 동일 track_code로 합치지 않는다.
영천 개장 이전 자료를 YEONGCHEON 과거 데이터로 생성하지 않는다.

pool_code:
WIN
PLACE
QUINELLA
EXACTA
QUINELLA_PLACE
TRIO
TRIFECTA

조합 마번은 horse_no_1/2/3 + selection_key + selection_ordered로 보존한다.
EXACTA/TRIFECTA는 순서형, QUINELLA/QUINELLA_PLACE/TRIO는 비순서형이다.

## 4. 신규 계층
기존 PEGASUS 전체 재작성 금지. 아래 독립 계층을 추가한다.
1) HistRepository: PEGASUS_HIST_V1 read-only import/query
2) HistFeatureBuilder: 현재 경주 시점 이전 HIST feature 생성
3) HistoricalReplayEngine: 시간순 replay
4) HistModelStore: Global/Regional Candidate/Champion 분리
5) CrossPoolStatistics: 7승식 조건부 시장정보
6) ProbabilityCalibration: 확률보정
7) ChampionChallengerGuard: 승격/rollback
8) OrderedFinish adapter: 보정 확률을 기존 최종 착순계층으로 전달

## 5. Feature 계약
현재 경주 race_time을 cutoff로 한다.
WHERE historical_event_time < current_race_time 조건을 강제한다.
현재/미래 실제착순, 확정배당, 결과 기반 파생값을 pre-race feature에 사용 금지.

기본 후보 feature:
horse_hist_win_rate
horse_hist_top3_rate
horse_distance_win_rate
horse_track_win_rate
horse_recent_form_score
jockey_hist_win_rate
trainer_hist_win_rate
regional_prior
historical_prior
rating_prior

시장 feature:
win_market_prior
place_market_prior
quinella_strength
exacta_strength
quinella_place_strength
trio_strength
trifecta_strength

표본수가 작은 통계는 Bayesian shrinkage/regularization으로 전체 평균 방향 보정한다.
단순 승률/적중률을 표본수 무시하고 그대로 feature로 사용 금지.

## 6. T20/T5 계약
T20 = 초기 시장 기준선.
T5 = 마감 근접 시장상태.
핵심은 T5 절대 인기순위가 아니라 T20 대비 변화다.

raw implied probability = 1 / odds
승식별 overround/시장마진을 정규화한 확률을 별도 계산한다.

필수 변화 feature:
delta_probability = P_T5 - P_T20
delta_rank = rank_T20 - rank_T5
movement_velocity
cross_pool_confirmation

WIN 하나의 움직임으로 최종착순을 결정 금지.
7승식에서 동반되는 신호와 상충되는 신호를 분리한다.
T5 최저배당순을 Final123으로 직접 복사하는 경로는 영구 금지.

## 7. Historical Replay 계약
Random train/test split 금지.
시간순 Walk-Forward만 허용한다.

각 과거경주 순서:
A. 해당 경주 직전까지의 데이터만 조회
B. Feature 생성
C. Prediction 생성
D. Immutable Prediction Snapshot 저장
E. 그 후 Actual Outcome 공개
F. Error Attribution
G. Candidate 학습/Calibration 업데이트
H. 다음 경주 진행

과거 실제 결과를 같은 경주의 prediction feature에 넣으면 DATA_LEAKAGE_FAIL.

## 8. 모델 역할 분리
HIST = 장기 기억/기본기
Regional = 지역 조건
Rating/Fundamental = 말/기수/조교사/조건 실력
T20/T5 = 현재 시장
Cross-Pool = 현재 시장의 다승식 확인
Live Closed Loop = 최근 오차 적응

모델 식별자는 최소:
HIST-GLOBAL-vN
HIST-SEOUL-vN
HIST-BUSAN_GYEONGNAM-vN
HIST-JEJU-vN
HIST-YEONGCHEON-vN
ML-LIVE-vN

Regional 표본 부족 시 Global fallback.
영천처럼 신규/저표본 지역을 가짜 장기모델 READY로 표시 금지.

## 9. PEGASUS 실전 결합 위치
Fundamental = horse/jockey/trainer/record
HistPrior = 5Y 조건부 과거확률
RatingRegional = rating + regional
BaseProbability = Fundamental + HistPrior + RatingRegional

Market20 = T20 normalized market prior
Movement = T20->T5 delta
CrossPool = seven-pool confirmation
MarketAdjusted = BaseProbability + bounded Market20 + bounded Movement + bounded CrossPool

LearnedResidual = 검증된 과거 오차학습 보정
FinalProbability = Calibration(MarketAdjusted + bounded LearnedResidual)

ML/HIST가 전체 예측을 단독 결정 금지.
LearnedResidual은 기존 확률의 제한된 보정계층으로 사용하고 guardrail을 둔다.

## 10. 착순 모델
말별 단일 score 내림차순을 그대로 1/2/3착으로 변환하는 방식 금지.
Ordered Finish는 조건부 순열확률을 평가한다.

P(A first)
P(B second | A first)
P(C third | A first, B second)

EXACTA/TRIFECTA HIST는 순서확률 학습에 사용.
QUINELLA/QUINELLA_PLACE/TRIO HIST는 비순서 상위권 결합확률 학습에 사용.

출력은 기존 Final123/Final8 계약을 유지한다.

## 11. Champion / Challenger
5년 학습 완료 자체는 Champion 승격조건이 아니다.
Candidate와 현재 Champion을 동일한 holdout/walk-forward 구간에서 비교한다.

필수 평가:
LogLoss
Brier Score
Top1 hit rate
Top3 hit rate
Ordered Top3 hit rate
지역별 성능
최소 표본수
기간별 안정성
Calibration error

통계적/운영적 승격기준을 충족할 때만 Candidate -> Champion.
한 경주 결과만으로 Champion 교체 금지.
악화 시 이전 Champion rollback 가능해야 한다.

## 12. 실전 Closed Loop
5Y HIST 학습 후에도 기존 실전 Closed Loop 유지.
실전 순서:
PreRace Context -> HIST/Regional/Rating -> T20 -> T5 -> CrossPool
-> Final Probability -> Ordered Finish -> Final123/Final8
-> Immutable Snapshot -> Actual Outcome -> Error Attribution
-> Live Candidate update -> 다음 경주

Actual Outcome은 Snapshot 생성 이후에만 학습계층에 접근 가능.

## 13. UI/상태 표시
사전 브리핑에서 내부 enum만 노출하지 않는다.
최소 표시:
HIST DB 상태/기간
race 수
runner 수
odds 수
Global model version/status
Regional model/status/sample
Calibration status
Track Bias status/sample
Data Leakage gate
마지막 학습/검증 시각

HIST_PENDING/HIST_READY는 실제 DB 상태와 검증결과에 의해 결정한다. 가짜 READY 금지.

## 14. Release Gate
다음이 전부 PASS 전에는 HIST 적용 APK를 완료판으로 배포 금지:
- PEGASUS_HIST_V1 schema/import integrity
- immutable/source_hash/duplicate test
- 7 pool parser/selection ordering test
- point-in-time feature cutoff test
- actual-result leakage = 0
- walk-forward replay test
- Global/Regional fallback test
- T20/T5 movement test
- Cross-Pool test
- calibration test
- Champion/Challenger promotion/rollback test
- training-serving parity
- excluded runner leak = 0
- Final123 active unique = 3
- Final8 exactly 8
- NaN/Infinity = 0
- 기존 메뉴/저장/복원 회귀 PASS
- Android build/package compatibility PASS

## 15. 변경 기록 의무
각 구현 배치마다 이 문서 또는 연결된 변경기술서에 반드시 기록:
기준 commit SHA
변경 후 commit SHA
변경 목적
변경 파일/함수
변경 전 동작
변경 후 동작
유지한 기존 기능
DB/API/UI 호환성
테스트와 결과
Workflow/Run ID
Artifact digest
APK SHA-256

기록 없는 변경은 완료로 판정하지 않는다.

## 16. 절대 금지
- 배당 인기순 = 예상착순
- T5 FINAL 배당을 T20/T5 변화 없이 단독 사용
- 현재/미래 실제결과를 pre-race feature로 사용
- 과거 Prediction Snapshot 사후변경
- 제외마 Final123/Final8 유입
- 한 경주만으로 Champion 교체
- 없는 Hist/Regional/Track 데이터를 READY로 표시
- BUSAN_GYEONGNAM 과 YEONGCHEON 원본 track identity 혼합
- 원본 PEGASUS_HIST_V1 변조
- 검증 없이 '학습 완료/성능 개선/적중률 향상' 선언

이 문서는 이후 5Y HIST -> PEGASUS 적용 구현의 기준 계약으로 사용한다.


## 17. PEGASUS v6.0.0 구현 계약 / 데이터 입력·학습
배포 목표 applicationId=com.kplay.horseracing.crosspool.v600, versionName=6.0.0.

### 입력
Collector export 파일 PEGASUS_HIST_V1.sqlite는 Android Storage Access Framework로 사용자가 명시 선택한다.
PEGASUS는 선택 파일을 자기 private files/pegasus_hist/PEGASUS_HIST_V1.sqlite로 복사한다.
외부 수집기 private/external-files 경로를 직접 참조하지 않는다.
설치복사 후 schema_version, SHA-256, race/runner/odds count, track_code, pool_code 검증 실패 시 HIST_INVALID.

### 학습 역할
Collector V1에 저장된 FINAL odds + finish_rank는 장기 HIST 통계/확률/Ordered Finish 사전정보용이다.
Collector V1에 존재하지 않는 T20/T5 과거 snapshot을 생성/추정/위조하지 않는다.
T20->T5 movement 학습은 PEGASUS 실전 snapshot/Closed Loop가 담당한다.

5Y 학습 산출:
- Global + SEOUL/BUSAN_GYEONGNAM/JEJU/YEONGCHEON 분리
- 7 pool sample/mean odds/implied mass
- horse historical sample, Bayesian win/top3 prior
- race_date < targetDate cutoff 강제
- outcome rows가 없는 DB는 Candidate/Champion 학습완료 판정 금지

### 실전 적용
HIST_READY일 때 현재 출전마 horse_name을 point-in-time 조회하여 historicalPrior.horsePriors로 주입한다.
기존 Fundamental -> Learned -> T20/T5 Market -> Blend -> Calibration -> Ordered Finish -> Final123/Final8 흐름 유지.
HIST prior는 단독 착순결정 금지.

### 변경 파일
HistRepository.java 신규: private immutable copy/read-only validation/statistical query.
HistBridge.java 신규: file picker/train/status/prior JS bridge.
MainActivity.java: AndroidHist bridge + ACTION_OPEN_DOCUMENT result 전달만 additive.
app.js: HIST 입력/학습 UI, 상태, point-in-time prior 주입.
build.gradle/workflow/index.html: v6.0.0 identity.
기존 Gumvit/Scratch/메뉴1~6/T20/T5/snapshot/result-learning 계약은 변경하지 않는다.


v6 isolated batch build trigger. Release status remains NOT_VERIFIED until blocking CI completes.

- 69ed0a40: 실제 buildPegasusInput 경로에 validated HIST prior 주입 누락을 blocking test가 발견하여 수정. UI-only 연결 금지 계약 확인.

- v6 회귀감사: T5 자동분석 금지 테스트는 기존 noop 함수 정의 자체를 오탐하므로 호출 유무를 검사하도록 테스트 교정. 실제 T5 입력 동작은 변경하지 않음.

- f6f60dc5: v6 UI 삽입 중 발생한 legacy scheduleAnalysis noop 중복 정의 제거. T5 수동 분석 계약 자체는 변경 없음.

- dfc1826f: Branding/Android 회귀검사는 기존 v4.1.0 package/version 고정값만 v6.0.0 계약으로 갱신. 아이콘/Intro/회전/minSdk 검사는 유지.
