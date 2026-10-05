# Cross-Pool Analysis Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current single-WIN LMI ranker with a PRD-aligned multi-pool engine that analyzes WIN / QUINELLA / EXACTA / TRIO / TRIFECTA T20/T5 money flow and produces evidence-backed candidates.

**Architecture:** Keep the Android WebView shell and SQLite bridge, but replace the JS analysis layer with a pool-aware engine and structured input UI. WIN remains mandatory; combination pools are analyzed only when valid T20/T5 data exists. Candidate results must come from actual pool selections, never mechanical permutations.

**Tech Stack:** Android Java, WebView, JavaScript, SQLite, Jsoup, Node.js tests, GitHub Actions/Gradle.

**Spec:** `docs/superpowers/specs/2026-10-05-cross-pool-analysis-design.md`

## Global Constraints
- Preserve exact PRD formulas for implied money share and LMI.
- Preserve actual horse numbers, including gaps/scratches.
- Never synthesize missing Cross-Pool data as zero evidence.
- WIN T20/T5 is mandatory; missing combination pools return `INSUFFICIENT_DATA`.
- Candidate rankings come from real selections within that betting pool.
- Weights are configurable assumptions, not canonical PRD constants.
- Input edits invalidate stale results.
- Persist only explicitly approved results.

## Review Focus
- Partial pool snapshots: reject/mark insufficient rather than calculate synthetic LMI.
- Duplicate/canonical combo keys: unordered pools normalize, ordered pools preserve direction.
- Scratched horse numbers: result keys must still map to real horse numbers.
- Large/invalid odds: reject before analysis.
- Missing combination pools: final UI must say unavailable rather than invent combinations.

---

### Task 1: Pool-aware analysis engine and tests

**Files:**
- Modify: `app/src/main/assets/analysis.js`
- Replace: `test-analysis.js`

**Interfaces:**
- Consumes: `{horseNumbers, popularity, pools:{WIN,QUINELLA,EXACTA,TRIO,TRIFECTA}}`
- Produces: `RacingAnalysis.analyzeRace(input)`, `canonicalKey(poolType,key)`, `normalizeSnapshot(entries)`, `computeLmi(t20,t5)`.

- [ ] Write failing Node tests for PRD share/LMI formulas, combo key canonicalization, unavailable pools, sleeper divergence, market center, fade, and real horse numbers.
- [ ] Run `node test-analysis.js` and verify failure against the old engine.
- [ ] Implement pool-normalization, LMI, horse exposure, divergence, roles, and real-selection candidate scoring.
- [ ] Run `node test-analysis.js` and require all tests PASS.
- [ ] Commit engine + tests.

### Task 2: Structured T20/T5 multi-pool input UI

**Files:**
- Modify: `app/src/main/assets/app.js`
- Modify: `app/src/main/assets/index.html`
- Modify: `app/src/main/assets/style.css`

**Interfaces:**
- Consumes: Gumvit horse metadata plus user-entered pool selections/odds.
- Produces: structured `pools` input passed to `RacingAnalysis.analyzeRace`.

- [ ] Add UI tests/assertions through static checks in `test-analysis.js` for required pool labels and no vertical-paste dependency.
- [ ] Implement WIN rows and compact structured tables for QUINELLA/EXACTA/TRIO/TRIFECTA T20/T5 selections.
- [ ] Auto-run analysis only after valid mandatory WIN T5; optional pools are included only when both snapshots validate.
- [ ] On every edit, invalidate and hide stale results.
- [ ] Render `INSUFFICIENT_DATA` explicitly for unavailable pools.
- [ ] Run Node tests and commit UI changes.

### Task 3: Evidence-rich result rendering

**Files:**
- Modify: `app/src/main/assets/app.js`

**Interfaces:**
- Consumes: `analyzeRace()` result.
- Produces: role cards, per-pool candidate tables, numeric evidence, unavailable-pool indicators.

- [ ] Add fixtures asserting each candidate contains `q20`, `q5`, `lmi`, `score`, and evidence.
- [ ] Render market center, late money, fade, sleeper, structural anchor.
- [ ] Render candidate tables only from actual pool selections.
- [ ] Include numeric evidence and analysis/config version.
- [ ] Run Node tests and commit.

### Task 4: Persistence schema upgrade

**Files:**
- Modify: `app/src/main/java/com/kplay/horseracing/gumvit/RaceDbHelper.java`
- Modify: `app/src/main/java/com/kplay/horseracing/gumvit/StorageBridge.java`

**Interfaces:**
- Consumes: approved analysis JSON.
- Produces: normalized race/pool/result records plus backward-compatible history listing.

- [ ] Raise DB version and create `races`, `horses`, `pool_snapshots`, `selection_metrics`, `horse_metrics`, `analysis_results`, `analysis_history`.
- [ ] Keep `analysis_records` only as compatibility/migration fallback.
- [ ] Save only approved results; reject missing result/version.
- [ ] Confirm history still loads.
- [ ] Commit persistence changes.

### Task 5: APK build and artifact verification

**Files:**
- Modify only if required: `.github/workflows/android-build.yml`, `app/build.gradle`

**Interfaces:**
- Consumes: passing repository state.
- Produces: installable debug APK artifact.

- [ ] Run GitHub Actions Node analysis tests.
- [ ] Build `:app:assembleDebug`.
- [ ] Verify `app/build/outputs/apk/debug/app-debug.apk` exists.
- [ ] Upload APK artifact.
- [ ] Download artifact, extract APK, verify ZIP/APK structure and SHA-256.
- [ ] Deliver the APK download link.
