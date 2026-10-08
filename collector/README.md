# PEGASUS HIST 5Y COLLECTOR
PEGASUS 본체와 분리된 Android 수집기.

고정 track_code:
- SEOUL
- BUSAN_GYEONGNAM
- JEJU
- YEONGCHEON (2026-09-13 이후)
상위 region_group: CAPITAL / YEONGNAM / JEJU.

고정 pool_code:
WIN, PLACE, QUINELLA, EXACTA, QUINELLA_PLACE, TRIO, TRIFECTA.

불변키:
race_uid = KRA:{track_code}:{yyyyMMdd}:R{race_no}
runner_uid = race_uid:H{horse_no}
odds_uid = race_uid:{pool_code}:{selection_key}:FINAL

DB schema_version=PEGASUS_HIST_V1. 원본행 UPDATE 금지. 동일 source_hash는 INSERT OR IGNORE.
PEGASUS는 exported DB의 schema_version을 확인하고 read-only import한다.

Build trigger: isolated collector v1 batch.

CI SDK repair trigger.

Parser compile repair trigger.


## v1.1.0 수집/전달 장애 교정 (2026-10-08)
- RCA-1: v1.0.0은 KRA 경주목록 ScoretableScoreList.do를 개별 raceDate/raceNo 수집원처럼 사용했다. 개별 결과/승식 데이터는 ScoretableDetailList.do?meet=&realRcDate=&realRcNo= 를 사용하도록 교정.
- RCA-2: checkpoint 테이블이 있었지만 수집 루프가 읽거나 갱신하지 않아 '이어받기'가 실제 resume가 아니었다. 지역별 next_date를 저장/사용.
- RCA-3: getExternalFilesDir 내보내기는 다른 앱/사용자가 접근하기 어렵다. ACTION_CREATE_DOCUMENT로 PEGASUS_HIST_V1.sqlite를 사용자가 선택한 위치에 직접 저장.
- RCA-4: Collector 내부 DB 공유는 read-only HistShareProvider(content://com.kplay.pegasus.histcollector.hist/current)를 유지.
- 네트워크/파싱 실패 시 기존 DB 삭제 금지. 체크포인트 이전 저장분 유지. 언제든 파일 내보내기 가능.
- PEGASUS는 Provider 실패 시 SAF 파일선택 fallback을 사용하고 본체 분석은 fail-open.
- 기존 v1.0.0 실기기 DB는 실제 상세 endpoint 기반 완전 데이터라고 간주하지 않는다. v1.1.0 수집 결과의 race/runner/odds/7 pool 검증 후 학습 승인.
