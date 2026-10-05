# Cross-Pool Horse Racing Analysis Redesign

Date: 2026-10-05
Status: Design specification for PRD-aligned redesign

## 1. Goal

Redesign the current Android odds-analysis app so that it no longer behaves as a single-win-odds LMI ranker with mechanically generated combinations. The target is the behavior described in `HorseRacing_Odds_Analysis_App_PRD.md`: derive normalized implied money share from odds, calculate Late Money across time, compare money structure across WIN / QUINELLA / EXACTA / TRIO / TRIFECTA pools, and generate evidence-backed candidates per betting pool.

The system must remain a decision-support tool rather than claim deterministic prediction.

## 2. Current defects to remove

1. `Implied Money Share` and single-pool `LMI` are implemented correctly.
2. Current `crossPoolScores` defaults to zero and therefore does not perform real Cross-Pool Divergence.
3. Current candidate generation ranks horses using an arbitrary fixed score and then mechanically creates combinations/permutations.
4. Jockey, trainer, expert/popularity information is displayed but not materially used in analysis.
5. Current 50/30/15/5 weights are not defined by the PRD and must not be represented as canonical.
6. Result evidence is too weak to explain why a pool combination was selected.

## 3. Input model

### 3.1 Race metadata
- race date
- region: Seoul / Busan-Gyeongnam / Jeju
- race number
- horse number
- horse name
- record
- trainer
- jockey
- Gumvit expert/popularity fields where available

### 3.2 Pool snapshots

Supported pools:
- WIN: single horse
- QUINELLA: unordered two-horse combination
- EXACTA: ordered two-horse combination
- TRIO: unordered three-horse combination
- TRIFECTA: ordered three-horse combination

Each pool snapshot contains:
- `poolType`
- `snapshotType`: `T20` or `T5`
- `selectionKey`: canonical horse/combo key
- `odds`

Canonical key examples:
- WIN: `7`
- QUINELLA: `3-7` with ascending normalization
- EXACTA: `7>3`
- TRIO: `3-7-11` with ascending normalization
- TRIFECTA: `7>3>11`

If a pool has no usable T20/T5 data, that pool must be marked `INSUFFICIENT_DATA`; the application must not fabricate Cross-Pool evidence from zeros.

## 4. Core calculations

### 4.1 Implied Money Share

For each pool independently and for each snapshot independently:

`Q_i = (1 / D_i) / sum_j(1 / D_j)`

Properties:
- each pool snapshot sums to 1 within floating-point tolerance;
- calculations never mix different pool types during normalization.

### 4.2 Late Money Index

For every selection that exists in both T20 and T5:

`LMI_i = (Q_T5,i - Q_T20,i) / Q_T20,i`

Store both raw LMI and percentage form.

Selections missing from either snapshot are not assigned a synthetic zero LMI; they are flagged as incomplete.

### 4.3 Current-share rank

For each pool, rank selections by `Q_T5` descending. This is the current market concentration ranking.

### 4.4 Cross-Pool Divergence

The PRD defines the concept but does not define a canonical mathematical formula. Therefore the app must separate:
- PRD-defined facts: single-pool money share, LMI, and cross-pool mismatch concept;
- implementation policy: the explicit score used to operationalize that concept.

Implementation policy for v2:

For each horse `h`, derive evidence features from available pools:

- `winRankStrength(h)`: normalized T5 WIN share/rank signal.
- `lateWin(h)`: normalized positive WIN LMI.
- `pairPresence(h)`: strength of QUINELLA/EXACTA combinations containing `h`.
- `triplePresence(h)`: strength of TRIO/TRIFECTA combinations containing `h`.
- `comboLate(h)`: aggregate positive LMI of combinations containing `h`.
- `directionalLead(h)`: exacta/trifecta evidence where `h` appears in leading position.

A horse is a Cross-Pool divergence candidate when its combination-pool evidence is materially stronger than its WIN-pool evidence.

Recommended implementation score:

`DIVERGENCE(h) = comboEvidence(h) - winEvidence(h)`

where `comboEvidence` and `winEvidence` are each normalized to `[0,1]` from observed pool data.

No hard-coded claim is made that this is the statistically optimal formula. Constants must live in one config object and be backtest-adjustable.

## 5. Horse role classification

### 5.1 Market Center
Horse with strongest current WIN money concentration (`Q_T5`) unless WIN data is unavailable.

### 5.2 Late Money Horse
Horse with strongest positive WIN LMI, supplemented by positive combination-pool LMI evidence.

### 5.3 Fade Horse
Horse with weakening WIN share and negative LMI, especially when combination-pool support also weakens.

### 5.4 Sleeper / Hidden Horse
Horse whose WIN evidence is not top-tier but whose cross-pool combination evidence is repeatedly strong.

### 5.5 Structural Anchor
Horse repeatedly present in top-ranked selections across multiple pools.

Every role must include evidence fields, not only a label.

## 6. Candidate generation by pool

Candidate rankings must come from the pool's own market data, not from mechanical permutation of top horses.

### 6.1 QUINELLA
Rank actual QUINELLA selections using:
- T5 normalized share
- QUINELLA LMI
- cross-pool consistency of both horses
- structural-anchor participation

### 6.2 EXACTA
Rank actual ordered EXACTA selections using:
- T5 normalized share
- EXACTA LMI
- first-position directional evidence
- cross-pool consistency

### 6.3 TRIO
Rank actual TRIO selections using:
- T5 normalized share
- TRIO LMI
- three-horse cross-pool support
- hidden-horse participation

### 6.4 TRIFECTA
Rank actual ordered TRIFECTA selections using:
- T5 normalized share
- TRIFECTA LMI
- positional evidence from EXACTA/TRIFECTA
- cross-pool support

If a requested pool lacks valid market data, do not manufacture candidates for that pool.

## 7. Scoring policy

The PRD says candidate ranking uses `LMI + Divergence + popularity weighting` but does not specify exact coefficients.

Therefore v2 uses configurable weights, for example:

```text
currentShareWeight
lmiWeight
divergenceWeight
popularityWeight
structureWeight
```

Defaults are implementation assumptions, clearly labeled in the UI/logs and test fixtures. They must be stored in one configuration object so historical backtesting can recalibrate them without rewriting the engine.

## 8. Popularity / jockey / trainer handling

- popularity is a secondary prior, never stronger than actual pool money-flow evidence;
- jockey/trainer fields are retained in output evidence and DB;
- until historical conditional statistics exist, jockey/trainer values must not receive fabricated predictive coefficients;
- future backtests may add trainer/jockey conditional priors after sufficient sample size.

## 9. Analysis pipeline

1. Validate race and snapshot data.
2. Normalize each pool/snapshot into implied money shares.
3. Calculate selection-level LMI for each pool.
4. Build horse-level exposure matrices from combination selections.
5. Calculate Cross-Pool divergence features.
6. Classify market center / late money / fade / sleeper / structural anchor.
7. Score real selections within each pool.
8. Generate evidence objects and human-readable summary.
9. Mark analysis version/config version.
10. Persist only after explicit approval.

## 10. Output model

Final result must include:

- Market Center horse + T20/T5 share change
- Late Money horse + LMI
- Fade horse where material
- Sleeper horse + supporting pools
- Structural Anchor
- QUINELLA candidate table
- EXACTA candidate table
- TRIO candidate table
- TRIFECTA candidate table
- per-candidate evidence:
  - T20 share
  - T5 share
  - LMI
  - divergence evidence
  - involved horse roles
- `insufficientDataPools`
- analysis/config version

Example evidence text:

`7번: WIN Q 6.2%→10.1% (LMI +62.9%), QUINELLA 3-7 LMI +71.0%, EXACTA 7>3 LMI +54.0%; WIN rank보다 조합승식 노출이 강해 Cross-Pool 잠복/유입 신호.`

## 11. UI behavior

Main flow remains:

`Race Selection -> T20 -> T5 -> Analysis -> Final Output -> History`

Changes:
- WIN T20/T5 remain mandatory for core analysis.
- Additional pool data can be entered/imported in structured pool sections.
- after final required T5 input passes validation, analysis may auto-run;
- editing any snapshot invalidates the current result;
- stale results must never remain visible after an input change;
- unavailable pool data is shown explicitly as unavailable, not synthesized.

## 12. Persistence redesign

Recommended tables:

- `races`
- `horses`
- `pool_snapshots`
  - race_id
  - pool_type
  - snapshot_type
  - selection_key
  - odds
  - implied_share
- `selection_metrics`
  - race_id
  - pool_type
  - selection_key
  - lmi
  - score
  - evidence_json
- `horse_metrics`
  - race_id
  - horse_number
  - win_share_t20
  - win_share_t5
  - win_lmi
  - divergence_score
  - role_json
- `analysis_results`
- `analysis_history`

The existing single `analysis_records` payload table may be retained temporarily for migration, but the v2 engine must not depend on that flattened schema.

## 13. Validation and tests

Required automated tests:

1. implied shares sum to 1 per pool/snapshot;
2. exact PRD LMI examples;
3. invalid zero/negative/NaN odds rejection;
4. T20/T5 selection mismatch handling;
5. unordered key canonicalization for QUINELLA/TRIO;
6. ordered key preservation for EXACTA/TRIFECTA;
7. Cross-Pool sleeper scenario: weak WIN + strong combination pools;
8. market center scenario;
9. fade scenario;
10. actual horse-number preservation with scratched/missing numbers;
11. no candidate fabrication when a pool is unavailable;
12. input edit invalidates prior result;
13. approval-only persistence;
14. regression test against existing WIN/LMI behavior.

## 14. Acceptance criteria

The redesign is accepted only if:

- Implied Money Share and LMI match PRD formulas exactly;
- Cross-Pool uses actual combination-pool data when available;
- zero-filled placeholder Cross-Pool analysis is removed;
- betting-pool candidates come from actual selections in that pool;
- every final candidate has inspectable numeric evidence;
- unavailable pools produce `INSUFFICIENT_DATA`, not invented predictions;
- actual horse numbers are preserved;
- modifying T20/T5 invalidates old results and forces recomputation;
- only approved analyses are persisted;
- automated tests pass before APK build;
- GitHub Actions produces a valid installable APK artifact.

## 15. Non-goals for this iteration

- claiming statistically proven predictive power;
- assigning fabricated coefficients to trainer/jockey data without historical samples;
- autonomous betting;
- hiding implementation assumptions from the user.
