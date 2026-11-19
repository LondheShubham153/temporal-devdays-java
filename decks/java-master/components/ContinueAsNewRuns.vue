<!--
  ABOUTME: One Workflow ID as a chain of runs: each run's history fills, it continues as new,
  ABOUTME: and the next run starts with an empty history and only the state it was handed.

  Frontmatter usage (click 1 adds run 2, click 2 adds run 3):
    ---
    clicks: 2
    ---

    <ContinueAsNewRuns />
-->
<script setup lang="ts">
import { useSlideContext } from '@slidev/client'

const { $clicks } = useSlideContext()

const RUNS = [
  { name: 'Run 1', runId: 'Run ID 7c2e…', fill: 92 },
  { name: 'Run 2', runId: 'Run ID a41f…', fill: 92 },
  { name: 'Run 3', runId: 'Run ID e90b…', fill: 18 },
]
const HANDOFFS = ['cyclesCompleted: 41', 'cyclesCompleted: 83']

// A run is closed once the next one is on screen.
const closed = (i: number) => $clicks.value > i
</script>

<template>
  <figure class="can" aria-label="One Workflow ID, three runs. Each run's history fills up, it continues as new and passes cyclesCompleted to the next run, which starts with an empty history.">
    <div class="wid">
      <span class="label">Workflow ID</span>
      <code>subscription-42</code>
      <span class="hint">what callers see: one Workflow</span>
    </div>

    <div class="row">
      <template v-for="(run, i) in RUNS" :key="run.name">
        <div v-if="i > 0" class="handoff" :class="{ hidden: $clicks < i }">
          <code class="call">continueAsNew</code>
          <div class="arrow" />
          <span class="state">{{ HANDOFFS[i - 1] }}</span>
        </div>

        <div class="run" :class="{ hidden: $clicks < i, done: closed(i) }">
          <div class="head">
            <strong>{{ run.name }}</strong>
            <code>{{ run.runId }}</code>
          </div>
          <div class="hist-label">
            <span>History</span>
            <span v-if="run.fill > 50" class="suggested">near the limit</span>
            <span v-else>fresh</span>
          </div>
          <div class="bar"><div class="fill" :style="{ width: `${run.fill}%` }" /></div>
          <div class="status">{{ closed(i) ? 'Continued-As-New' : 'Running' }}</div>
        </div>
      </template>
    </div>
  </figure>
</template>

<style scoped>
.can { margin: 1.5rem 0 0; display: flex; flex-direction: column; gap: 1.2rem; }

.wid {
  display: flex; align-items: baseline; gap: 0.8rem; padding: 0.5rem 0.9rem;
  border: 1px dashed var(--temporal-border-hi); border-radius: 0.6rem;
}
.wid .label { font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.08em; color: var(--temporal-text-muted); }
.wid code { font-size: 1rem !important; color: var(--temporal-text-strong); }
.wid .hint { margin-left: auto; font-size: 0.8rem; color: var(--temporal-text-muted); }

.row { display: flex; align-items: stretch; gap: 0.6rem; }

.run {
  flex: 1 1 0; min-width: 0; padding: 0.8rem 0.9rem; border-radius: 0.6rem;
  border: 1px solid var(--temporal-green); background: var(--temporal-bg-elev);
  display: flex; flex-direction: column; gap: 0.45rem;
  transition: opacity 0.35s ease;
}
.run.done { border-color: var(--temporal-border); }
.head { display: flex; flex-direction: column; gap: 0.1rem; white-space: nowrap; }
.head strong { color: var(--temporal-text-strong); }
.head code { font-size: 0.75rem !important; color: var(--temporal-text-muted); }

.hist-label { display: flex; justify-content: space-between; gap: 0.5rem; white-space: nowrap; font-size: 0.75rem; color: var(--temporal-text-muted); }
.suggested { color: var(--temporal-yellow); }
.bar { height: 0.7rem; border-radius: 999px; background: var(--temporal-border); overflow: hidden; }
.fill { height: 100%; border-radius: 999px; background: var(--temporal-cyan); }
.run .fill { transition: background 0.35s ease; }
.run:not(:last-child) .fill { background: var(--temporal-yellow); }

.status { font-size: 0.8rem; color: var(--temporal-green); }
.run.done .status { color: var(--temporal-text-muted); }

.handoff {
  flex: 0 0 10rem; white-space: nowrap; display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 0.35rem; transition: opacity 0.35s ease;
}
.call { font-size: 0.75rem !important; color: var(--temporal-lavender); }
.arrow { position: relative; width: 100%; height: 2px; background: var(--temporal-lavender); }
.arrow::after {
  content: ''; position: absolute; right: -2px; top: -5px;
  border-left: 9px solid var(--temporal-lavender); border-top: 6px solid transparent; border-bottom: 6px solid transparent;
}
.state {
  font-family: var(--slidev-code-font-family, monospace); font-size: 0.7rem; padding: 0.1rem 0.5rem;
  border-radius: 999px; border: 1px solid var(--temporal-lavender); color: var(--temporal-text-strong);
}

.hidden { opacity: 0; }
</style>
