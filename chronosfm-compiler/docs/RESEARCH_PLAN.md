# ChronoSFM implementation research plan

## Primary hypothesis

A tick-synchronous logistics DSL can be compiled into a persistent incremental execution graph whose steady-state planning cost scales primarily with state changes rather than total network size, without reducing observable per-tick throughput.

## Hard invariants

- NEVER reduce the frequency of a trigger that is due in legacy SFM.
- In exact mode, resources transferred after N logical ticks must equal legacy SFM for the same trace.
- Unknown semantics always fall back to legacy execution.
- Planning may move off-thread later; Minecraft capability commits may not.
- No performance claim is accepted without P50/P95/P99 MSPT and throughput measurements.

## Phases

### P0 — measurement harness
Instrument ProgramContext construction, cable/network lookup, label lookup, slot gathering, candidate attempts, simulated inserts, successful commits, allocation and GC.

Benchmark topology size independently from active endpoint count and changed endpoint count.

### P1 — semantic oracle
Run legacy and Chrono executors from identical traces and compare deterministic state hashes. Add randomized SFML programs and hostile fake capabilities.

### P2 — trigger preflight
Land the #602-style lightweight trigger probe. Timers can be proven inactive without ProgramContext. Opaque triggers are always treated as due.

### P3 — compiled IR
Translate safe AST subsets into immutable TriggerPlan and TransferRegion IR. Preserve exact source ordering.

### P4 — persistent endpoint index — CORE PROTOTYPE IMPLEMENTED
Cache semantic label/resource/slot relationships while reusing SFM's existing CableNetwork capability cache.

Current research branch provides the Minecraft-independent persistent membership/revision index. The next SFM-side step is binding stable endpoint ids to real label/position/side/slot descriptors without duplicating CableNetwork's capability cache.

### P5 — persistent transfer graph — ENDPOINT BINDING PROTOTYPE IMPLEMENTED
Separate static legal transfer relationships from dynamic inventory state. Rebuild only on structural invalidation.

The current prototype caches each endpoint's dependent work-region array. Label/resource matching is performed on structural bind/rebind only; ordinary inventory/capacity revisions reuse that cached array and directly mark the dirty frontier. This is specifically aimed at infinite-resource/high-frequency worlds where endpoint state changes every tick.

### P6 — incremental invalidation — PRECISE FRONTIER PROTOTYPE IMPLEMENTED
Maintain endpoint/label/resource -> region dependency indexes. The current prototype uses composite label/resource dependencies, so one changed iron endpoint does not dirty unrelated fluid regions or every iron region globally.

The research branch now includes a 0.01%, 0.1%, 1%, 5%, 10%, 50%, 100% persistent-frontier churn sweep. This measures bookkeeping/frontier cost only; it must not be misrepresented as the final execution crossover. The full-recompute crossover will be selected only after P7 supplies real region recomputation work.

### P7 — exact-order compiled executor
Use precomputed candidate order but retain SFM ResourceType/trackers/capability commit semantics. This is the first architecture-scale TPS milestone.

### P8 — endpoint classification
OPAQUE: query normal capability every due tick.
OBSERVABLE: use reliable revisions/invalidation.
NATIVE: adapter supports reservation/bulk semantics.

### P9 — stable flow contracts
Persist stable transfer relationships across ticks. The contract must still realize the same per-tick deliveries; it is not delayed batching.

### P10 — reservation/bulk endpoint API
Prototype first against Chrono-owned fake inventories/SFM buffers. Only then consider third-party adapters.

### P11 — parallel CPU planner
Move pure IR graph maintenance and planning to immutable snapshots. The server thread never waits for a future; stale/unready plans fall back safely.

### P12 — GPU gate
Only add CUDA if CPU planning remains a measured bottleneck at 100k/1M edges. GPU may plan/filter/compact; it must never directly mutate Minecraft capabilities.

## Acceptance gates

1. State hash: exact match in exact mode.
2. Resources per logical tick: equal to legacy for exact-order path.
3. Resources per real second: never below legacy in accepted benchmark configurations.
4. P95/P99 MSPT: statistically improved or no-regression fallback.
5. Stable topology scaling: adding inactive/static topology should have near-flat steady-tick planning cost.
6. Change proportionality: low-churn cost should scale with affected regions rather than the full graph.
7. Zero new duplication/loss/starvation bugs.

## Explicit non-goals for the first milestone

- GPU execution of IItemHandler/BlockEntity code.
- Transparent parallel execution of arbitrary third-party capabilities.
- Global max-flow replacing SFM ordering semantics.
- Time Warp / PDES.
- Multi-GPU.
- Reducing trigger frequency to make TPS look better.
