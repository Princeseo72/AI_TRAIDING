# 경마 배당판 역산 분석 Android App

첨부 PRD/와이어프레임을 Android APK용으로 이식한 프로젝트입니다.

## 구현됨
- 6단계 UI: 경주 선택 → 20분 전 → 5분 전 → 연산 처리 → 최종 출력 → 저장 기록
- 단승 배당 세로 붙여넣기 / 셀 입력 / Enter 이동
- 입력 검증
- Implied Money Share: `(1/D_i) / Σ(1/D_j)`
- Late Money Index: `(P5-P20)/P20`
- 후보 생성: 복승 / 쌍승 / 삼복승 / 삼쌍승
- 입력 수정 시 이전 분석 결과 무효화
- 최종 승인 후 Android SQLite 저장
- 저장 기록 조회 / 삭제
- 다크 테마 모바일 UI

## 중요
PRD에는 승식별 후보의 정확한 가중치 공식이 고정되어 있지 않으므로, 현재 `analysis.js`의 후보 점수 가중치는 **조정 가능 기본값**입니다.
- 5분 전 자금점유율 50%
- LMI 30%
- Cross-Pool 15%
- 통합인기도 5%
실제 데이터 백테스트 후 보정해야 합니다.

## APK 빌드
GitHub Actions가 `app-debug.apk`를 자동 생성합니다.
