# PEGASUS Race Prediction + ML Evolution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the locked prediction/ML design so the app outputs role-based top 3, odds-based top 3, exactly 8 betting combinations, and verified online-learning feedback with visible ML status.

**Architecture:** Keep Q/LMI/Cross-Pool as the deterministic core. Add a separate prediction layer for role scores and positional scores, a bounded 8-combination generator, and a versioned online-learning subsystem persisted in SQLite. Actual race results are attached after the immutable pre-race snapshot and may update only a candidate model; promotion is guarded by validation metrics and rollback.

**Tech Stack:** Android Java, WebView HTML/CSS/JavaScript, SQLite, Node.js regression tests, GitHub Actions, Gradle 8.9/JDK 17/Android SDK 35.

**Spec:** `docs/superpowers/specs/2026-10-06-race-prediction-ml-evolution-design.md`

## Global Constraints

- Role prediction is exactly: Dark Horse 1st / Favorite 2nd / Ability Horse 3rd.
- Odds prediction is a separate unique 1st -> 2nd -> 3rd ranking.
- Final combinations are exactly 8: QUINELLA 2, EXACTA 2, TRIO 2, TRIFECTA 2.
- Excluded runners never appear in inputs, prediction, combinations, or training.
- Q and LMI formulas remain unchanged.
- Actual-pool evidence and derived-from-WIN evidence must remain distinguishable.
- A verified actual result never rewrites the original pre-race feature/prediction snapshot.
- One race cannot materially rewrite the model; updates are bounded, regularized, versioned, and reversible.
- ML failure must not block baseline analysis.
- Android 10/tablet compatibility and current PEGASUS icon/intro behavior remain intact.
- Any failed regression, menu, integrity, APK signature, or package test blocks APK publication.

## Review Focus

1. Result leakage: attaching actual results must not alter pre-race features or predictions.
2. Duplicate training: the same race/result snapshot must train at most once.
3. Small fields / duplicate role candidates: prediction must fail rather than duplicate a horse when fewer than 3 verified active runners exist.
4. Derived pool output: when optional pool odds are absent, exactly 8 combinations must still be generated and labeled `DERIVED_FROM_WIN`/`HYBRID` rather than `ACTUAL_POOL`.
5. Model regression: candidate weights must not become ACTIVE if guarded validation metrics regress beyond configured tolerance.

---

### Task 1: Prediction engine contract and feature vectors

**Files:**
- Modify: `app/src/main/assets/analysis.js`
- Modify: `test-analysis.js`

**Interfaces:**
- Consumes: verified active `horseNumbers`, WIN T20/T5, optional pool data, popularity.
- Produces: `featureVectors`, `rolePrediction`, `oddsPrediction`, `analysisVersion`, `featureSchemaVersion`, and existing core metrics.

- [ ] **Step 1: Write failing tests** for unique role top-3, unique odds top-3, excluded-number absence, unchanged Q/LMI values, and <3-runner failure.
- [ ] **Step 2: Run** `node test-analysis.js` and confirm RED.
- [ ] **Step 3: Implement feature vectors** with `winShareT20`, `winShareT5`, `winLmi`, rank/momentum/compression, Cross-Pool fields, neutral priors, and learned positional biases.
- [ ] **Step 4: Implement six score families**: `DarkHorseScore`, `FavoriteScore`, `AbilityScore`, `WinScore`, `Place2Score`, `Place3Score` with centralized configurable baseline weights.
- [ ] **Step 5: Implement unique selectors** for role and odds predictions.
- [ ] **Step 6: Re-run** `node test-analysis.js`; expect PASS.
- [ ] **Step 7: Commit** prediction-engine changes.

### Task 2: Exactly-eight combination generator

**Files:**
- Modify: `app/src/main/assets/analysis.js`
- Modify: `test-analysis.js`

**Interfaces:**
- Consumes: `rolePrediction`, `oddsPrediction`, actual optional-pool analyses when available.
- Produces: `finalCombinations` with exactly 2 entries per pool and evidence/source metadata.

- [ ] **Step 1: Write failing tests** asserting counts `{QUINELLA:2, EXACTA:2, TRIO:2, TRIFECTA:2}`, key canonicalization/order, active-runner membership, no duplicates, and source labels.
- [ ] **Step 2: Run tests** and confirm RED.
- [ ] **Step 3: Implement bounded candidate generation** from role/odds predictions plus actual pool candidates when available.
- [ ] **Step 4: Implement pool scoring and top-2 selection** with deterministic tie-breaking.
- [ ] **Step 5: Run tests**; expect PASS.
- [ ] **Step 6: Commit** final-eight generator.

### Task 3: ML model schema and immutable training snapshots

**Files:**
- Modify: `app/src/main/java/com/kplay/horseracing/RaceDbHelper.java`
- Modify: `app/src/main/java/com/kplay/horseracing/StorageBridge.java`
- Create: `test-ml-schema.js`

**Interfaces:**
- Produces SQLite tables `ml_models`, `ml_training_examples`, `ml_training_events`; storage bridge methods for active model, prediction snapshot, training attachment, ML status.

- [ ] **Step 1: Write failing schema/bridge tests** for table names, unique race-training key, ACTIVE-model preservation, and immutable pre-race snapshot storage.
- [ ] **Step 2: Run** `node test-ml-schema.js`; confirm RED.
- [ ] **Step 3: Add DB migration** and indexes for model/region/race/created_at lookups.
- [ ] **Step 4: Add storage APIs** `getActiveModel(region)`, `savePredictionSnapshot(...)`, `getMlStatus(region)`, and idempotent training-example insertion.
- [ ] **Step 5: Run schema tests**; expect PASS.
- [ ] **Step 6: Commit** persistence changes.

### Task 4: Controlled online-learning engine

**Files:**
- Create: `app/src/main/assets/ml.js`
- Create: `test-ml.js`
- Modify: `app/src/main/assets/index.html`

**Interfaces:**
- Consumes: immutable feature snapshot, prediction, verified actual top-3/result, active/global/regional model.
- Produces: candidate model, weight deltas, before/after metrics, promotion decision.

- [ ] **Step 1: Write failing tests** for no-result rejection, duplicate-race rejection, bounded per-race delta, regularization toward baseline, minimum sample threshold, regional threshold, and no same-race model leakage.
- [ ] **Step 2: Run** `node test-ml.js`; confirm RED.
- [ ] **Step 3: Implement bounded online update** with centralized `learningRate`, `maxDeltaPerRace`, `minSamples`, weight bounds, and regularization.
- [ ] **Step 4: Implement guarded promotion** comparing candidate vs active metrics over rolling validation counters/window; reject regressing candidates.
- [ ] **Step 5: Add global + region adjustment composition** with inactive regional adjustment below threshold.
- [ ] **Step 6: Run tests**; expect PASS.
- [ ] **Step 7: Commit** ML engine.

### Task 5: Race-result feedback and training transaction

**Files:**
- Modify: `app/src/main/java/com/kplay/horseracing/GumvitBridge.java`
- Modify: `app/src/main/java/com/kplay/horseracing/StorageBridge.java`
- Modify: `app/src/main/assets/app.js`
- Modify: `test-menu.js`
- Modify/Create: `test-result-learning.js`

**Interfaces:**
- Consumes: verified Gumvit result, saved pre-race snapshot, active model.
- Produces: prediction-vs-actual evaluation, training event, persisted result, refreshed ML status.

- [ ] **Step 1: Write failing tests** for verified identity/top-3 requirement, winning-combination comparison, immutable original prediction, single training per race, and training failure fallback.
- [ ] **Step 2: Run tests** and confirm RED.
- [ ] **Step 3: Implement feedback pipeline**: verify result -> evaluate role/odds/8 combos -> store evaluation -> train candidate -> promote/reject -> store event.
- [ ] **Step 4: Ensure original prediction JSON is never overwritten** by result ingestion; append evaluation/training records separately.
- [ ] **Step 5: Run tests**; expect PASS.
- [ ] **Step 6: Commit** feedback pipeline.

### Task 6: Visual final result and ML panel

**Files:**
- Modify: `app/src/main/assets/app.js`
- Modify: `app/src/main/assets/style.css`
- Modify: `test-menu.js`
- Create: `test-ml-ui.js`

**Interfaces:**
- Consumes: `rolePrediction`, `oddsPrediction`, `finalCombinations`, ML status/training event.
- Produces: fixed final result layout and visual learning indicators.

- [ ] **Step 1: Write failing UI contract tests** for fixed section order, exactly eight displayed combinations, model version/sample/status, weight-delta bars, before-vs-actual badges, and learning-wait state.
- [ ] **Step 2: Run tests**; confirm RED.
- [ ] **Step 3: Replace long candidate tables** with role top-3, odds top-3, final 8 combination cards/table, evidence summary, and ML panel.
- [ ] **Step 4: Add visual learning elements** using compact HTML/CSS horizontal bars/delta indicators and rolling/cumulative metric blocks.
- [ ] **Step 5: Confirm tablet responsive behavior** without horizontal overflow on the main result cards/tables.
- [ ] **Step 6: Run UI/menu tests**; expect PASS.
- [ ] **Step 7: Commit** UI changes.

### Task 7: Performance, memory, cleanup, and health checks

**Files:**
- Modify: `app/src/main/java/com/kplay/horseracing/StorageBridge.java`
- Modify: `app/src/main/java/com/kplay/horseracing/RaceDbHelper.java`
- Modify: `app/src/main/assets/app.js`
- Create: `test-performance-contract.js`

**Interfaces:**
- Produces: health metrics including DB size, model sample counts, analysis/training/load duration; cleanup that preserves ACTIVE model.

- [ ] **Step 1: Write failing tests** for metadata-only history list, indexed ML queries, orphan-training cleanup, ACTIVE-model preservation, bounded rolling metrics, and duration fields.
- [ ] **Step 2: Run test**; confirm RED.
- [ ] **Step 3: Implement optimized queries/aggregates** and transactional cleanup.
- [ ] **Step 4: Extend health-check UI/data** with ML sample/model/training/analysis/load performance.
- [ ] **Step 5: Run performance-contract tests**; expect PASS.
- [ ] **Step 6: Commit** performance changes.

### Task 8: Full menu-by-menu regression and release gate

**Files:**
- Modify: `.github/workflows/android-build.yml`
- Modify: `test-menu.js`
- Modify: `test-branding.js`
- Create: `test-integrity.js`
- Modify: `app/build.gradle`

**Interfaces:**
- Produces: release-blocking CI and new APK artifact.

- [ ] **Step 1: Expand menu regression** to verify race selection, exclusion filter, T20, T5, reanalysis, role/odds predictions, exact 8 combinations, save, fast load, Gumvit verify, result attach, ML train/status, health check, cleanup, exit.
- [ ] **Step 2: Add integrity tests** for no excluded horse leakage, no duplicate training, immutable snapshots, exactly-eight contract, ordered/unordered keys, ML fallback, and model rollback/promotion guard.
- [ ] **Step 3: Add all tests to CI before APK build** so any failure blocks packaging.
- [ ] **Step 4: Bump package/version** without breaking Android 10/tablet support or PEGASUS branding/intro.
- [ ] **Step 5: Run full CI**: JS analysis/ML/menu/integrity/branding tests -> Gradle assembleDebug -> `apksigner verify` -> APK ZIP integrity -> `apkanalyzer` package/minSdk checks.
- [ ] **Step 6: Inspect workflow logs** and fix every failure; no waived failures.
- [ ] **Step 7: Download artifact and independently verify file type, unzip integrity, signing block, package ID, minSdk, and SHA-256.
- [ ] **Step 8: Final menu-by-menu completion report** listing PASS/FAIL for every functional area; distribute APK only if all are PASS.
- [ ] **Step 9: Commit** release-gate changes.

## Completion Gate

Implementation is complete only when all of the following are true:

- role prediction returns 3 unique verified active horses;
- odds prediction returns 3 unique verified active horses;
- exactly 8 final combinations are displayed and stored;
- actual/derived/hybrid evidence source is inspectable;
- verified result comparison evaluates all predictions and combinations;
- ML update is bounded, idempotent, versioned, reversible, and visually displayed;
- result leakage and same-race training leakage tests pass;
- all menu tests pass individually;
- health/cleanup/load tests pass;
- Android 10/tablet branding tests pass;
- APK build/signature/package/integrity checks pass;
- no APK is published if any required check fails.
