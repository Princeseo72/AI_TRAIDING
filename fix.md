# PEGASUS FIX 기준서

최종 갱신: 2026-10-07  
적용 대상: PEGASUS Android V4.x 이후 모든 수정  
우선순위: 이 문서는 장애 수정/검증의 실행 기준이다. 기존 설계 의도는 `docs/배당디자인최종.md`를 따른다. 충돌 시 기능 설계는 최종설계 문서, 장애 재현·수정·배포 게이트는 본 문서를 따른다.

## 0. 현재 판정
- 컴파일/설치 가능 여부와 실기능 정상 여부를 절대 동일시하지 않는다.
- 2026-10-04 서울 1R은 검빛 웹에서 11두가 실제 노출된다. 앱의 `유효 출전마 없음`은 데이터 부재가 아니라 조회/응답/DOM/파싱 경로 결함이다.
- KRA `ThisWeek...` 계열은 현재/금주 보조 소스이며 과거 Replay의 보편적 fallback으로 간주하지 않는다.
- 5년 Hist 원천 데이터는 아직 미연결이다. 연결 전에는 반드시 HIST_PENDING/CALIBRATION_PENDING/PL_FALLBACK을 표시하며 정확도를 조작하거나 READY로 표시하지 않는다.
- APK는 아래 Release Gate 전부 PASS 전 배포 금지.

## 1. 확인된 결함과 근거

### FIX-001 검빛 실데이터가 존재해도 앱에서 0두
재현: 2026-10-04 / 서울 / 1R.
웹 근거: 검빛 chulma_detail에는 1경주, 2026-10-04, 서울 경마장과 1~11번 출전마 및 인기도 표가 존재한다.
현 코드 위험:
1. Android UA/세션/권한/리다이렉트 등에 따라 서버 응답 DOM이 달라질 수 있다.
2. `findEntryTable()`은 특정 헤더 조합에 의존한다.
3. 실패 시 실제 응답의 title/location/status/body fingerprint/table headers를 보존하지 않아 원인 추적이 불가능하다.
4. 단순 재시도는 동일한 잘못된 응답을 반복할 수 있다.

필수 수정:
- HTTP status/final URL/title/body length/table count/header fingerprints 기록.
- 로그인/상품구매/권한없음/봇차단/빈 페이지를 DATA_EMPTY와 분리하여 RESPONSE_BLOCKED 또는 DOM_CHANGED로 판정.
- 출전표와 인기도표를 별도 parser strategy로 지원하고, 최소 `마번+마명`을 anchor로 한 뒤 기수/조교사/전적은 헤더 매핑으로 추출.
- 동일 마번 중복, 1~30 범위, 빈 마명, 요청 날짜/지역/경주번호를 모두 검증.
- 성공 조건은 `horses.length >= 3`가 아니라 실제 표의 유효 row를 모두 추출하고 중복/제외마 검증까지 통과하는 것.
- 2026-10-04 서울1R을 영구 regression fixture/canary로 추가.

### FIX-002 KRA fallback 범위 오류
- `ThisWeekWeight.do`, `Riding.do`는 과거 임의 날짜 Replay fallback으로 신뢰하지 않는다.
- live/current-week와 historical replay source policy를 분리한다.
- 소스가 해당 날짜를 지원하지 않으면 SOURCE_UNSUPPORTED로 명시하고 '경주 없음'으로 오판하지 않는다.

### FIX-003 CI PASS와 실제 기능 PASS 혼동
기존 일부 테스트는 함수명/문구/table 존재를 `includes()`로 검사한다.
필수:
- contract test와 runtime test를 별도 표시.
- 메뉴 E2E는 실제 함수 실행과 state transition을 검증.
- live diagnostic은 continue-on-error 금지 전환 전까지 Release Gate PASS로 계산하지 않는다.
- 네트워크 live test 실패를 숨긴 APK는 실전검증 PASS 표시 금지.

### FIX-004 역할 태그와 예상착순 혼동
금지: DarkHorseScore=1착, FavoriteScore=2착, AbilityScore=3착으로 강제.
- rolePrediction은 ROLE TAG 전용.
- 최종 예상착순은 PEGASUS `final123` / ordered finish 전용.
- UI/저장/평가/학습에서 두 개념을 혼합하지 않는다.

### FIX-005 학습 대상 오류
발견: 결과 학습이 PEGASUS final123 대신 legacy oddsPrediction을 사용할 수 있었다.
수정 기준:
- `finishPrediction = PEGASUS final123`.
- 실제 TOP3와 finishPrediction을 비교해 Win/Place2/Place3 계열을 평가/학습.
- rolePrediction은 역할 분석 참고값이며 착순 정답으로 사용 금지.
- 실제 결과는 prediction snapshot 생성 이후에만 결합.

## 2. 메뉴별 필수 E2E

### 메뉴1 경주 선택
PASS:
- 날짜/지역/경주 편성 검증.
- sourceProvider/final URL/identity 표시.
- 유효 출전마 전원: 마번/마명 필수, 가능한 전적/조교사/기수.
- 제외마 별도 분리.
- 0두/구조변경/권한응답/네트워크/미편성을 서로 다른 오류코드로 표시.
- 같은 사용자 동작에서 불필요한 검빛 중복 조회 금지. cache/fingerprint로 횟수 확인.

### 메뉴2 T20
PASS:
- 메뉴1 active runner와 정확히 동일한 마번 universe.
- 누락/중복/0/음수/비수치/제외마 입력 차단.
- snapshot timestamp 및 race identity 저장.
- T20 이후 실제 결과 데이터 접근 금지.

### 메뉴3 T5
PASS:
- T20 universe와 T5 universe 동일.
- T20/T5 snapshot 분리/불변.
- LMI 계산 finite.
- 제외마 재검증.
- T5 이후에도 actual result는 분석 입력 금지.

### 메뉴4 연산 처리
PASS:
- Market/Fundamental/Rating/TrackBias/Hist status/Blend/Calibration/Ordered Finish 순서 실행.
- 모든 확률 finite, 정규화 합 허용오차 내.
- final123 정확히 3두, 중복 없음, active runner만.
- 역할 태그가 착순을 강제하지 않음.
- 복승2/쌍승2/삼복2/삼쌍2 정확히 8개.
- 조합 내 중복마/제외마/없는 마번 없음.
- 동일 입력 반복 시 deterministic 결과.
- Hist 미연결이면 fallback 상태를 숨기지 않음.

### 메뉴5 최종 출력
PASS:
- PEGASUS final123을 최상단 최종 예상착순으로 표시.
- top ordered scenario, P1/P2/P3, engine compare, Final8, 근거, uncertainty, fallback 상태 표시.
- legacy rolePrediction을 최종 착순으로 표시 금지.
- Prediction Snapshot을 이 시점에 동결하고 hash/identity/version 저장.
- 이후 actual result로 snapshot 변경 금지.

### 메뉴6 저장 기록
PASS:
- race identity, runners, excluded, T20, T5, engine versions, context, final123, final8, snapshot 저장.
- 앱 재시작 후 동일 데이터 복원.
- 데이터 정리 시 ACTIVE 모델 보존 규칙 검증.
- 저장된 과거 prediction을 실제결과 결합 후에도 변경 금지.

## 3. 결과 대조 및 학습 Closed Loop
순서 강제:
1. immutable Prediction Snapshot 확인
2. 실제 결과 fetch/identity 검증
3. actual top3/payout 저장
4. final123 vs actual 평가
5. Final8 적중 평가
6. error attribution
7. global candidate 학습
8. regional candidate 학습
9. 최소 표본/회귀 guardrail
10. champion 승격 또는 reject/rollback
11. regional profile 갱신
12. rating 갱신
13. track bias 갱신
14. drift 기록
15. next-race feature materialization
16. 다음 경주 context에서 갱신 version 확인

학습 불변조건:
- 동일 raceKey 2회 학습 금지.
- 결과 없는 경주 학습 금지.
- actual result를 pre-race feature로 사용 금지.
- 최소표본 전 ACTIVE 승격 금지.
- 성능 guardrail 악화 시 승격 금지.
- global/regional 모델 version과 sampleCount 추적.
- 모든 weight delta finite 및 maxDeltaPerRace 제한.
- 학습 전후 prediction snapshot 동일.

## 4. Historical Replay
목적: 평일에도 실경주 lifecycle 검증.
- 실제 과거 출전마/제외마/T20/T5 fixture를 사용.
- 실제 착순은 분석 완료 및 snapshot hash 생성 전 test process에서 접근 불가.
- 분석 후에만 actual result reveal.
- parser fixture와 engine fixture를 분리해 사이트 변경과 엔진 회귀를 구분.
- 최소 서울/부산경남/제주 각각 복수 경주, 출전취소 포함 케이스, 신마/저표본 케이스 포함.
- 정확도는 표본수와 함께 보고. 한두 경주 적중으로 엔진 PASS 판정 금지.

## 5. Self Diagnosis
앱 내 자가진단은 다음을 각각 PASS/WARN/FAIL로 보여야 한다.
- Network
- Gumvit HTTP
- Gumvit response fingerprint
- Gumvit parser canary
- KRA live/current-week support
- JS analysis runtime smoke
- PEGASUS runtime smoke
- ML runtime smoke
- SQLite read/write/transaction
- Prediction snapshot immutability
- Closed-loop schema/version
- app/build/version/package
자가진단 실패 시 해당 기능으로 진행을 막고 구체적 오류코드와 진단정보를 제공한다.

## 6. 테스트 계층
L1 deterministic unit: parser/math/runner guard/canonical key.
L2 JS runtime: T20→T5→analysis→PEGASUS→Final123→Final8.
L3 learning runtime: actual reveal→evaluation→candidate→guardrail→duplicate block.
L4 DB integration: save/snapshot/result/training/closed-loop/reload.
L5 historical replay: 실제 과거 fixture로 메뉴1~6 lifecycle.
L6 live shadow: 실제 경주일 T20/T5 시점에 prediction freeze 후 결과 대조.
L7 Android package/device: Android10 설치/실행/자가진단/메뉴 터치/재시작 복원.

## 7. Release Gate
다음 중 하나라도 미통과면 APK 배포 금지:
- deterministic unit PASS
- parser regression fixture PASS
- JS runtime PASS
- learning runtime PASS
- DB integration PASS
- historical replay PASS
- 메뉴1~6 E2E PASS
- excluded runner leak 0
- actual-result leakage 0
- NaN/Infinity 0
- Final123 unique active 3
- Final8 exactly 8
- snapshot immutability PASS
- duplicate learning block PASS
- Android build/signature/minSdk/package PASS
- 실제 기기 설치/실행 검증은 별도 DEVICE_PENDING/PASS로 명시. 확인하지 않고 PASS 금지.

## 8. 현재 이미 반영된 수정
- post-race ML 평가/학습에 PEGASUS `final123`을 `finishPrediction`으로 전달.
- ML evaluate가 finishPrediction을 우선 사용.
- rolePrediction을 착순 정답으로 평가하지 않도록 분리.
- `test-learning-runtime.js` 추가: PEGASUS Final123/Final8, finite, 학습, 최소표본, 중복학습, finishPrediction wiring runtime 검증.
- CI ML 단계에 위 runtime test를 필수 추가.
- 이 변경이 포함된 CI run 37594580011은 성공. 단, 이는 본 Release Gate 전체 완료를 뜻하지 않는다.

## 9. 완료 선언 규칙
금지 문구: '완벽', '100%', '실전검증 완료' (L1~L7 근거 없을 때).
완료 보고는 표로 `항목 / 테스트 / 근거 / 상태(PASS|FAIL|PENDING)`를 제시한다.
FAIL/PENDING이 하나라도 있으면 원인과 다음 수정 대상을 함께 기록한다.

<!-- release-gate rerun: 2026-10-07 parser identity fix -->


## 10. ALL-GO 감사에서 추가 확인된 차단항목 (2026-10-07)
아래 항목은 구현 문자열 존재만으로 PASS 금지. 실제 runtime/DB/replay 근거가 있어야 한다.
- AG-001: dual-source live diagnostic의 continue-on-error 제거. 실패 시 APK build 차단.
- AG-002: 5년 Hist 원천/인덱스가 없으면 Historical Engine READY 금지. 실제 Hist ingestion + manifest + point-in-time retrieval 필요.
- AG-003: getPreRaceContext는 rating row count가 아니라 출전마별 horse/jockey/trainer 상태를 실제 반환.
- AG-004: blend_models/calibration_models를 context에 로드하고 PEGASUS engine이 실제 사용.
- AG-005: post-race closed loop에 calibration/blend candidate update와 champion/challenger guardrail 포함.
- AG-006: Track Bias는 gate/pace/section feature 없으면 READY 금지. 데이터 존재 시에만 실제 bias 계산.
- AG-007: Historical Replay은 서울/부경/제주 실제 fixture + T20/T5 + hidden outcome으로 분석 전 결과접근 차단 검증.
- AG-008: Training-serving parity는 offline fixture와 Android/JS runtime의 Q/LMI/rating/regional/blend/calibrated P1 수치 비교.
- AG-009: Golden Race regression, DB integration/reload, immutable snapshot hash, duplicate learning, actual leakage를 blocking gate로 실행.
- AG-010: L7 실제 Android device/emulator 메뉴 터치/재시작 복원 검증 전 DEVICE_PASS 금지.

<!-- ALL-GO batch integration gate -->

<!-- ALL-GO compile repair rerun -->

<!-- ALL-GO finite-rating rerun -->

## FIX-006 Public pre-race source boundary
- Pre-race runner lookup MUST use only the publicly accessible chulma_detail entry table without login.
- Do not enter, request, parse, or depend on any paid/premium area.
- Text from unrelated page sections MUST NOT determine runner-table validity.
- Production and live regression MUST share GumvitPageParser.parseEntries().
- Android production UA/referrer live regression for 제주 2026-10-03 1R MUST parse exactly 10 runners before APK release.

<!-- Android screenshot root-cause full batch -->

<!-- public-source full batch rerun -->

<!-- rerun after regression fixture compile repair -->

- FIX-006 root cause refinement: Gumvit uses nested layout tables; parser must select the innermost public runner table, never an outer layout/premium container.
<!-- nested-table full batch -->

- Parser compatibility: no CSS :scope dependency; resolve owning table by DOM ancestry for bundled jsoup compatibility.
<!-- jsoup-compatible full batch -->


## FIX-007 Same-day scratch is a hard boundary
- Runner list = public entry table MINUS explicit public race-change/말취소 numbers.
- Gumvit public horse_weight_news 출전표 변경/말취소 is a mandatory supplemental source.
- KRA change bulletin is additional evidence, not the only same-day scratch source.
- A scratched runner MUST disappear from WIN T20/T5 rows, optional combinations, Final123, Final8, save snapshot, and ML features.
- When field refresh removes a scratched runner, its blank odds MUST NOT block T20→T5 or analysis.
- Regression fixture: 제주 3R, horse 7 explicit cancellation => active=false and no odds row.
<!-- scratch-hard-boundary full batch -->
