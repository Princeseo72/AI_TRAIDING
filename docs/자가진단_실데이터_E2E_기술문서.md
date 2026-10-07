# PEGASUS v4 자가진단·실데이터 조회·메뉴 E2E 기술문서

## 1. 목적
이 문서는 2026-10-07 실제 Android 설치본에서 발생한 `2026-10-03 제주 2R` 경주정보 조회 실패를 기준으로 원인, 수정방향, 자가진단, 중복조회 방지, 메뉴별 검증 기준을 고정한다.

핵심 원칙:
- Build SUCCESS와 기능 SUCCESS를 분리한다.
- HTTP 200과 Parser 정상은 분리한다.
- 경주 전 단계에서 결과 데이터를 읽지 않는다.
- 실제 Android에서 메뉴 1→6 전체 경로를 E2E 검증하지 않은 기능은 실전 PASS로 표시하지 않는다.
- 과거 Replay에서 실제 결과는 Prediction Snapshot 동결 후에만 공개한다.

## 2. 2026-10-03 제주 2R 장애

### 증상
Android 화면:
`경주정보 이중조회 실패 / 검빛: 검빛 경주 식별 실패: 2026-10-03 제주 2R [type=6:/2 | type=5:/2 | type=7:/2 | type=1:/2] / KRA: KRA 경주 식별 실패`

`:/2` 의미:
- 경주번호 2는 Parser가 읽음.
- visible date는 빈 문자열.
- 기존 `identityMatches()`가 날짜 불일치로 정상 응답을 폐기.

### 원인
1. Android/Mobile UA에서 검빛 응답의 visible date가 Parser가 기대한 형식으로 항상 노출되지 않음.
2. 기존 identity 판정은 visible date를 절대조건으로 사용.
3. KRA fallback은 `ThisWeekWeight.do` 중심이라 과거경주 Replay fallback으로 부적합.
4. CI live smoke가 실제 Android Web/Jsoup 응답 차이를 충분히 재현하지 못함.
5. 일부 테스트가 런타임 동작이 아니라 함수/문자열 존재 여부만 확인.

### 수정
visible date가 비어 있을 때만 다음 조건을 모두 만족하면 요청 경주로 인정:
- response location URL의 `m_date` 또는 `racedate`가 요청일과 일치
- 지역 일치
- 경주번호 일치
- 실제 출전마 표 존재

다른 날짜가 실제로 파싱된 경우에는 URL이 맞아도 절대 허용하지 않는다.

회귀 고정 케이스:
- date: 2026-10-03
- region: 제주
- raceNo: 2
- primary type: 6

## 3. 검빛 중복 조회 원인과 수정

### 기존
경주선택에서:
1. `chulma_detail.html` 출전표 조회
2. `result_detail.html` 결과 페이지를 다시 조회해 취소마 탐색
3. 네트워크 예외 시 `getPrimary()`가 같은 URL 최대 2회 재시도
4. type identity 실패 시 6/5/7/1 후보를 순차 조회

따라서 사용자 관점에서 검빛을 여러 번 검색하는 현상이 발생할 수 있었다.

### 수정 정책
경주 전 Field Load:
- 검빛 `chulma_detail`만 조회
- 취소/제외는 출전표 상태 + KRA 변경정보 사용
- `result_detail`은 절대 호출하지 않음
- 결과 페이지는 경주 종료 후 `fetchRaceResult()`에서만 사용

효과:
- 불필요한 검빛 2차 조회 제거
- Historical Replay의 결과 누수 방지
- 조회시간 및 장애 표면 감소

### 재조회가 허용되는 경우
- 사용자가 T20/T5 단계 이동 시 출전마 변경 검증
- cache TTL 이후 새 상태 확인
- primary type identity가 실제 실패한 경우 fallback type
- 실제 네트워크 예외에 대한 제한된 retry

## 4. 시작 자가진단

기존 사전체크의 단순 홈페이지 HTTP probe는 폐기한다.

검빛 진단:
1. 실제 canary `2026-10-03 제주 2R type=6` 1회 요청
2. HTTP 성공
3. `identityMatches` 성공
4. 출전마 표 탐지
5. 실제 runner row 탐지
6. 위 조건을 모두 만족해야 GUMVIT PASS

KRA는 보조 데이터원 연결상태를 별도 표시한다.

엔진/모듈:
- JS asset 계약
- GumvitPageParser
- KraRaceParser
- ScratchDetector
- StorageBridge
- RaceDbHelper
를 검사한다.

검빛 HTTP만 성공하고 Parser가 실패하면 PASS가 아니라 BLOCK/WARN 대상으로 취급한다.

## 5. 앱 내부 자가진단

`성능 재검증` 버튼은 다음을 동시에 검사한다.
- SQLite health
- 저장/결과/모델 상태
- 검빛 HTTP
- 검빛 identity/parser
- 핵심 Java module
- pre-race result_detail 중복조회 차단 정책
- 진단 소요시간

표시 예:
`자가진단 정상 · DB PASS · GUMVIT PARSER PASS · MODULE PASS · 중복 결과조회 차단`

## 6. 메뉴별 절차 검증

### 메뉴 1 경주 선택
PASS:
- 요청 날짜/지역/경주번호 일치
- active runner >= 2
- 마번 중복 없음
- 제외마가 active list에 없음
- sourceProvider 표시
- parser 진단 통과

FAIL 시 메뉴 2 진입 금지.

### 메뉴 2 T20 입력
PASS:
- 메뉴1 field 재검증
- active runner만 입력 가능
- 제외마 key 차단
- WIN T20 전체 유효
- snapshot timestamp/field identity 보존

### 메뉴 3 T5 입력
PASS:
- T20 snapshot 존재
- field 재검증
- WIN T5 전체 유효
- T20과 T5의 runner universe 동일
- 제외마 없음

### 메뉴 4 연산 처리
PASS:
- 입력/출전마 검증
- Market T20/T5
- LMI
- Fundamental
- Rating
- Track Bias
- Hist status
- Blend
- Calibration
- Ordered Finish
- Final123
- Final8
- NaN/Infinity 없음

### 메뉴 5 최종 출력
PASS:
- PEGASUS 1/2/3
- top ordered scenario
- Final8 정확히 8개: 복승2/쌍승2/삼복2/삼쌍2
- 모든 조합 active runner only
- Prediction Snapshot immutable
- 결과 미조회 상태에서 실제착순 데이터 없음

### 메뉴 6 저장 기록
PASS:
- 저장 후 재조회 동일
- Prediction Snapshot 변경 없음
- 실제 결과는 별도 outcome으로 attach
- 결과 후 closed-loop update
- 앱 재시작 후 DB 복원

## 7. Historical Replay 검증
실전 PASS 전 필수.

순서:
1. 출전마/제외마 fixture
2. T20 주입
3. T20 snapshot
4. T5 주입
5. 분석
6. Final123/Final8 동결
7. prediction hash/timestamp 기록
8. 실제 결과 공개
9. 적중/순위오차/승식 대조
10. closed-loop update
11. next-race materialization

금지:
- 분석 전 result_detail 조회
- 실제 착순을 feature에 포함
- 과거 Prediction Snapshot 수정

## 8. CI 판정
최종 배포 PASS는 아래를 분리 표시한다.
- SOURCE/PARSER
- UNIT
- INTEGRATION
- REPLAY
- UI CONTRACT
- APK BUILD
- SIGNATURE
- PACKAGE ID
- ANDROID 10
- ARTIFACT

`BUILD SUCCESS`만으로 실전 PASS라고 표시하지 않는다.

## 9. 현재 한계
- 5년 Hist 원시데이터 학습은 아직 연결되지 않았으므로 `HIST_PENDING`.
- Calibration 학습데이터가 없으면 `CALIBRATION_PENDING`.
- Ordered model 학습이 없으면 `PL_FALLBACK`.
- 실제 기기 메뉴 1→6 E2E를 완료하기 전에는 실전검증 PASS를 선언하지 않는다.

## 10. 변경 추적
- Gumvit visible-date 누락 대응: parser identity 보강
- pre-race `result_detail` 호출 제거
- startup Gumvit parser canary 진단 추가
- 앱 내부 source/parser selfDiagnose 추가
- 중복 결과조회 차단 회귀테스트 추가

이 문서는 이후 PEGASUS v4 계열 수정 시 회귀 기준으로 사용한다.
