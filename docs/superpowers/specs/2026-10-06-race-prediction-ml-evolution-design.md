# PEGASUS Race Prediction + ML Evolution Design

Date: 2026-10-06
Status: Locked design specification pending implementation-plan approval
Supersedes only the final-output and learning sections of the 2026-10-05 Cross-Pool design. Core Q/LMI/Cross-Pool validation rules remain in force unless explicitly overridden below.

## 1. Goal

Convert the current post-T5 analysis from a long candidate list into a compact, inspectable prediction product with three synchronized outputs:

1. **Role Prediction**: Dark Horse 1st / Favorite 2nd / Ability Horse 3rd.
2. **Odds Prediction**: an independent 1st -> 2nd -> 3rd ranking derived primarily from T20/T5 odds flow.
3. **Final 8 Combinations**: exactly two QUINELLA, two EXACTA, two TRIO, and two TRIFECTA combinations.

After a race result is verified from Gumvit, the system must compare predictions with actual finish order and payouts, update a controlled online-learning model, and show that learning process visually. The objective is to improve calibration and ranking quality over accumulated verified races, not to claim guaranteed winners.

## 2. Non-negotiable output contract

The final analysis screen must never show ten or more free-form candidates per pool.

Exactly these sections are shown:

### 2.1 Role Prediction

- `DarkHorse1st`: one horse
- `Favorite2nd`: one horse
- `Ability3rd`: one horse

Example:

`Role Prediction: 7 -> 3 -> 5`

with labels:

- 1st: Dark Horse
- 2nd: Favorite
- 3rd: Ability Horse

The three selections should be unique unless the field has fewer than three active runners, in which case analysis is invalid.

### 2.2 Odds Prediction

Independent ranking from betting-flow evidence:

`Odds Prediction: horseA -> horseB -> horseC`

This ranking may differ from the role prediction and must be preserved separately for evaluation.

### 2.3 Final 8 Combinations

Exactly eight final combinations:

- QUINELLA: 2
- EXACTA: 2
- TRIO: 2
- TRIFECTA: 2

No ninth combination may be emitted by the final-output contract.

## 3. Input and exclusion policy

Only verified active runners are eligible for ranking or combination generation.

A horse is excluded if Gumvit explicitly marks the horse with a recognized withdrawal/scratch status in the race-specific entry or verified result source.

Rules:

- no excluded horse may receive a T20/T5 input row;
- no excluded horse may appear in role prediction;
- no excluded horse may appear in odds prediction;
- no excluded horse may appear in any final combination;
- no excluded horse participates in model training for that race;
- generic HTML styling, strike-through markup, unrelated `취소` text, or page-global text must not remove a horse without row/status-level evidence.

## 4. Existing core calculations retained

### 4.1 Implied Money Share

For every pool and snapshot independently:

`Q_i = (1 / odds_i) / SUM_j(1 / odds_j)`

### 4.2 Late Money Index

`LMI_i = (Q_T5,i - Q_T20,i) / Q_T20,i`

No synthetic zero is inserted for a missing T20/T5 selection.

### 4.3 Cross-Pool

When actual QUINELLA / EXACTA / TRIO / TRIFECTA T20+T5 data exists, retain actual Cross-Pool evidence.

When an optional pool is unavailable, the engine may create **derived final combinations from horse-level WIN features**, but must mark their source as `DERIVED_FROM_WIN`, never `ACTUAL_POOL`.

This distinction must be visible in evidence and training data.

## 5. Horse feature vector

Every active horse receives a feature vector before role/rank scoring.

Required fields:

- `winShareT20`
- `winShareT5`
- `winLmi`
- `winRankT20`
- `winRankT5`
- `shareMomentum`
- `oddsCompression`
- `crossPoolDivergence`
- `comboEvidence`
- `structureEvidence`
- `directionalLeadEvidence`
- `popularityPrior`
- `recordPrior`
- `jockeyPrior`
- `trainerPrior`
- `learnedBias1st`
- `learnedBias2nd`
- `learnedBias3rd`

Trainer/jockey/record priors must remain zero-neutral until sufficient historical samples exist. They must not receive fabricated predictive value.

## 6. Separate role scores

Do not collapse all purposes into one horse score.

Each active horse receives at least:

- `DarkHorseScore`
- `FavoriteScore`
- `AbilityScore`
- `WinScore`
- `Place2Score`
- `Place3Score`

### 6.1 DarkHorseScore

Purpose: find a runner whose market rank is not the obvious favorite but whose late-money and cross-pool evidence is materially strengthening.

Primary features:

- positive WIN LMI
- positive shareMomentum
- positive Cross-Pool divergence
- combination-pool support
- popularity gap / under-recognition
- learned first-place correction

A current top favorite may not be labeled Dark Horse unless no valid alternative satisfies the minimum divergence/late-money conditions.

### 6.2 FavoriteScore

Purpose: identify stable market support suitable for the second-place role.

Primary features:

- high T5 WIN share
- stable or positive LMI
- strong popularity prior
- cross-pool presence
- low fade risk
- learned second-place correction

### 6.3 AbilityScore

Purpose: identify a third-place contender with persistent structural support rather than only short-term odds movement.

Primary features:

- stable T20/T5 support
- record/trainer/jockey priors when statistically available
- combination-pool structural presence
- low collapse/fade signal
- learned third-place correction

## 7. Role Prediction selector

Select in this order:

1. choose `DarkHorse1st` from highest valid DarkHorseScore;
2. choose `Favorite2nd` from highest FavoriteScore excluding horse 1;
3. choose `Ability3rd` from highest AbilityScore excluding horses 1 and 2.

Store full score/evidence objects, not only horse numbers.

If constraints leave no valid unique triple, analysis returns an error rather than duplicate horses.

## 8. Odds Prediction selector

Odds Prediction is separate from Role Prediction.

For each horse calculate:

- `WinScore`
- `Place2Score`
- `Place3Score`

Use T20/T5 money flow as the strongest signal and learned positional corrections as bounded modifiers.

Select unique order:

1. highest WinScore -> predicted 1st
2. highest Place2Score among remaining -> predicted 2nd
3. highest Place3Score among remaining -> predicted 3rd

This result is stored as `oddsPrediction`.

## 9. Final 8 combination generator

Combination generation uses both Role Prediction and Odds Prediction, then scores a bounded candidate set.

### 9.1 QUINELLA — exactly 2

Primary candidates:

- Role 1st + Role 2nd
- Role 1st + Role 3rd
- Odds 1st + Odds 2nd
- Odds 1st + Odds 3rd

Canonicalize unordered pairs, remove duplicates, score, output exactly top 2 when at least 3 active runners exist.

### 9.2 EXACTA — exactly 2

Primary candidates:

- Role 1st -> Role 2nd
- Odds 1st -> Odds 2nd
- Role 1st -> Odds 2nd
- Odds 1st -> Role 2nd

Preserve order, remove duplicates, score, output top 2.

### 9.3 TRIO — exactly 2

Primary candidates:

- unordered Role top 3
- unordered Odds top 3
- one mixed role/odds triple if either top-3 set duplicates the other

Remove duplicates, score, output exactly 2 distinct triples where possible.

### 9.4 TRIFECTA — exactly 2

Primary candidates:

- Role 1st -> Role 2nd -> Role 3rd
- Odds 1st -> Odds 2nd -> Odds 3rd
- bounded mixed positional alternatives when those are identical

Preserve order, output top 2 distinct ordered triples where possible.

### 9.5 Evidence

Every final combination contains:

- `source`: ACTUAL_POOL / DERIVED_FROM_WIN / HYBRID
- component horse scores
- T20/T5 Q evidence
- LMI evidence
- Cross-Pool evidence if available
- model adjustment
- final score

## 10. Machine-learning subsystem

### 10.1 Initial strategy

Use controlled online weight learning rather than an opaque neural network.

Reasons:

- current data volume is initially limited;
- weights remain inspectable;
- rollback is possible;
- feature contribution can be visualized;
- overfitting controls are straightforward.

A more complex model may be introduced only after enough verified historical samples exist and after backtesting demonstrates improvement.

### 10.2 Training eligibility

A race may train the model only when all are true:

- race/date/region/race number verified;
- actual finish order exists;
- at least top 3 finishers are verified;
- active/excluded runner set is verified;
- prediction was generated before the actual result was attached;
- feature snapshot and model version were persisted before result ingestion.

No result-derived feature may leak back into the pre-race feature snapshot.

### 10.3 Training targets

For each race record:

- exact 1st hit
- exact 2nd hit
- exact 3rd hit
- role top-3 set hit
- odds top-3 set hit
- QUINELLA hit
- EXACTA hit
- TRIO hit
- TRIFECTA hit
- finishing position per active horse

### 10.4 Online update

Maintain separate bounded weight sets for:

- DarkHorseScore
- FavoriteScore
- AbilityScore
- WinScore
- Place2Score
- Place3Score

For each verified outcome:

1. compute prediction error;
2. calculate feature contribution delta;
3. apply learning rate;
4. clip each weight to configured min/max;
5. apply regularization toward baseline;
6. evaluate candidate model against baseline on stored validation history;
7. promote only if guardrail metrics do not regress beyond allowed tolerance.

### 10.5 No one-race overreaction

A single race must not materially rewrite the model.

Required controls:

- small learning rate;
- maximum absolute weight change per race;
- minimum sample threshold before learned weights affect live predictions;
- exponential recency weighting only after the minimum sample threshold;
- rollback to last promoted model.

### 10.6 Regional learning

Store:

- global model
- Seoul adjustment
- Busan-Gyeongnam adjustment
- Jeju adjustment

Region-specific adjustment is inactive until that region has the minimum sample threshold.

## 11. Model versioning

Every prediction stores:

- `analysisVersion`
- `featureSchemaVersion`
- `modelVersion`
- `globalWeightVersion`
- `regionalWeightVersion`

Model states are immutable snapshots after promotion.

Minimum tables:

### `ml_models`
- id
- model_version
- region
- sample_count
- weights_json
- metrics_json
- status: CANDIDATE / ACTIVE / ROLLED_BACK
- created_at

### `ml_training_examples`
- race_id
- analysis_record_id
- model_version
- feature_snapshot_json
- prediction_json
- actual_result_json
- target_json
- error_json
- trained_at

### `ml_training_events`
- model_from
- model_to
- race_id
- weight_delta_json
- before_metrics_json
- after_metrics_json
- promoted
- reason
- created_at

## 12. Performance metrics

The app must track at least:

- exact 1st accuracy
- exact 2nd accuracy
- exact 3rd accuracy
- exact Role 1-2-3 accuracy
- exact Odds 1-2-3 accuracy
- Role top-3 set recall
- Odds top-3 set recall
- QUINELLA hit rate
- EXACTA hit rate
- TRIO hit rate
- TRIFECTA hit rate
- candidate-model vs active-model change

Do not represent these metrics as guaranteed future performance.

## 13. Visual ML UI

Machine learning must be visible, not hidden in storage.

Add a `ML 학습` panel to final result/history.

### 13.1 Model status card

Display:

- current model version
- global sample count
- selected region sample count
- model status
- last training result

### 13.2 Before vs after prediction

When an actual result exists:

- pre-race Role Prediction
- pre-race Odds Prediction
- actual 1st/2nd/3rd
- hit/miss badges per position
- 8-combination hit badges

### 13.3 Learning visualization

Display compact horizontal bars or delta indicators for the strongest feature changes, e.g.:

- LMI: +0.012
- CrossPool: +0.008
- Popularity: -0.004
- AbilityPrior: +0.003

Do not show every low-value internal coefficient by default.

### 13.4 Accuracy trend

Show recent rolling metrics in a compact visual block:

- last 20 verified races
- cumulative verified races
- current model vs baseline

If fewer than the minimum training samples exist, show `학습 대기 / 기본모델 사용`.

## 14. User-visible final result layout

Final screen order is fixed:

1. `역할형 예상착순`
   - 복병 1착
   - 인기마 2착
   - 실력마 3착
2. `배당형 예상착순`
   - 1st -> 2nd -> 3rd
3. `최종 승식 8조합`
   - 복승 2
   - 쌍승 2
   - 삼복승 2
   - 삼쌍승 2
4. `핵심 근거`
   - Q/LMI/Cross-Pool/role evidence
5. `ML 학습 상태`
   - model version / sample / trend / latest delta
6. after race result:
   - `예측 vs 실제`
   - learning event result

## 15. Race-result feedback pipeline

After result retrieval:

1. verify Gumvit race identity;
2. verify actual top 3;
3. verify actual winning pool combinations/payouts;
4. load immutable pre-race feature/prediction snapshot;
5. compare Role Prediction;
6. compare Odds Prediction;
7. compare all 8 final combinations;
8. persist evaluation;
9. create ML training example;
10. update candidate model;
11. evaluate candidate against active model;
12. promote or reject candidate;
13. refresh visual ML status.

The result page must never rewrite the original prediction snapshot.

## 16. Performance and memory requirements

The ML feature must not make the Android app progressively slower as history grows.

Requirements:

- do not load full historical payloads for the history list;
- training should query only required feature/target columns;
- maintain aggregate model metrics rather than recomputing all history on every screen render;
- bounded rolling validation window plus cumulative counters;
- all DB operations performed transactionally;
- no full race-history JSON retained in WebView memory;
- clear temporary JS structures after training/evaluation;
- SQLite indexes on race/model/region/created_at lookup paths;
- data cleanup must remove orphan training rows but must never delete an ACTIVE model;
- health check reports DB size, model sample counts, training duration, analysis duration, and load duration.

## 17. Integrity guardrails

Required invariants:

1. exactly one verified active-runner set per analysis snapshot;
2. excluded runners cannot leak into predictions/combinations/training;
3. T20/T5 edits invalidate prior prediction;
4. actual result never modifies the original pre-race features;
5. exactly 8 final combinations in normal races with >=3 active runners;
6. combination horse numbers must exist in the verified active set;
7. ordered pools preserve order;
8. unordered pools canonicalize numbers;
9. actual pool evidence and derived evidence are explicitly distinguished;
10. model update uses only completed verified races;
11. model promotion is versioned and reversible;
12. if ML subsystem fails, core baseline analysis still returns a result and marks ML unavailable;
13. model must not train twice on the same race/result snapshot;
14. current analysis cannot use a model version created from that same race result.

## 18. Required automated tests

### 18.1 Core math

- Q normalization sums to 1;
- LMI exact formula;
- invalid odds rejection;
- T20/T5 mismatches rejected.

### 18.2 Exclusion

- one explicitly scratched horse -> exactly that horse excluded;
- normal horse with unrelated strike-through/page text -> remains active;
- excluded horse absent from T20/T5 rows;
- excluded horse absent from all predictions/combinations.

### 18.3 Role prediction

- DarkHorse selector obeys uniqueness and late-money/cross-pool evidence;
- Favorite selector excludes selected first horse;
- Ability selector excludes selected first/second horses;
- output contains exactly three unique active horses.

### 18.4 Odds prediction

- exactly three unique active horses;
- separate positional scores used;
- no actual-result data in input.

### 18.5 Eight combinations

- QUINELLA count = 2;
- EXACTA count = 2;
- TRIO count = 2;
- TRIFECTA count = 2;
- total = 8;
- no excluded horse;
- no duplicate normalized key within same pool;
- ordered/unordered semantics correct.

### 18.6 Learning

- unverified result cannot train;
- first N samples do not alter live model before threshold;
- per-race weight change clipped;
- same race cannot train twice;
- candidate model can be rejected on metric regression;
- active model rollback works;
- regional weights inactive before threshold;
- pre-race snapshot remains immutable.

### 18.7 UI/menu

Test all menus and bridges:

- race selection
- Gumvit verification
- T20
- T5
- reanalysis
- final prediction
- 8 combinations
- Gumvit post-analysis verification
- save
- fast load
- race-result compare
- ML learning visualization
- performance health check
- data cleanup
- exit

### 18.8 Android / tablet

- Android 10 compatible;
- tablet portrait and landscape layouts;
- WebView memory remains bounded across repeated history/result/ML navigation;
- APK signing and ZIP integrity verification.

## 19. Performance acceptance thresholds

CI/device benchmark targets for normal stored-history sizes:

- core analysis engine: target < 200 ms JS compute excluding network;
- final 8-combination generation: target < 50 ms;
- single-race online learning update: target < 250 ms DB+compute excluding Gumvit network;
- saved-analysis metadata list: target < 300 ms for 200 rows;
- single saved-analysis load: target < 300 ms;
- no unbounded WebView heap growth during 50 repeated navigation/reanalysis cycles.

Threshold failures do not silently pass; health check reports them as warning/failure.

## 20. Deployment gate

No APK is considered complete unless all are true:

1. core analysis tests PASS;
2. strict exclusion tests PASS;
3. Role Prediction tests PASS;
4. Odds Prediction tests PASS;
5. exact 8-combination tests PASS;
6. ML training/version/rollback tests PASS;
7. all menu/bridge tests PASS;
8. tablet/Android 10 tests PASS;
9. performance benchmark is within threshold or explicitly reported;
10. Gradle build PASS;
11. APK signature verification PASS;
12. APK ZIP integrity PASS.

If any required test fails, artifact publishing must be blocked.

## 21. Implementation boundary

This specification intentionally uses transparent online weight learning first. It does not authorize:

- an opaque neural network without validated sample volume;
- result leakage into pre-race features;
- automatic deletion of bad model history;
- claiming guaranteed profitability or deterministic race results;
- unrestricted generation of large candidate lists.

## 22. Expected evolution path

Phase 1: deterministic feature engine + role/odds predictions + exactly 8 combinations.

Phase 2: verified-result evaluation + online bounded learning + visual model status.

Phase 3: after sufficient history, backtest whether logistic ranking / gradient-boosted ranking materially beats the transparent model. Introduce only if it passes the same deployment guardrails.

The live app always keeps the baseline deterministic model as a fallback.