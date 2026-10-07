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
