# Scheduler Compatibility Design

## Goal

Refactor the plugin scheduler layer so one jar continues to run on Spigot, Paper, and Folia while raising the compile-time baseline to Paper API 1.21.x.

The new design must:

- prefer Folia global and async schedulers when they are available
- safely fall back to Bukkit/Paper schedulers on non-Folia servers
- avoid silent fallback when Folia-specific reflection fails unexpectedly
- keep Paper/Folia-specific types out of the public scheduler contract

## Current Problem

`PlatformTaskDispatcher` currently probes Folia support ad hoc inside each method. When reflection fails, it silently returns `false` and falls back to Bukkit scheduling. On Folia, that fallback can call `BukkitScheduler.runTaskTimer(...)`, which throws `UnsupportedOperationException` during plugin enable.

The current implementation also leaves async execution on the Bukkit async scheduler even when Folia's async scheduler is available.

## Design

### 1. Resolve scheduler capabilities once

Add an internal resolver that inspects the running server once during dispatcher construction and selects an execution backend.

The resolver will detect:

- Folia global scheduler support via `Server#getGlobalRegionScheduler`
- Folia async scheduler support via `Server#getAsyncScheduler`
- required methods on those scheduler objects

Detection outcomes:

- `FOLIA`: all required Folia methods are present
- `BUKKIT`: Folia scheduler APIs are absent, so Bukkit/Paper scheduling is used
- `FOLIA_BROKEN`: Folia APIs appear present but method lookup or invocation wiring fails

`FOLIA_BROKEN` is treated as an error state and must be logged explicitly before falling back.

### 2. Separate execution backends

Create an internal backend abstraction used only inside the scheduler package.

Responsibilities:

- run one-shot global tasks
- run repeating global tasks
- run async tasks
- cancel tracked tasks

Backends:

- `BukkitSchedulerBackend`
- `FoliaSchedulerBackend`

`PlatformTaskDispatcher` keeps the public `TaskDispatcher` interface and delegates to the resolved backend.

### 3. Isolate reflective Paper/Folia access

Even though the project will compile against Paper API 1.21.x, Folia-specific access remains reflective so the plugin can still load on Spigot-compatible runtimes.

Rules:

- do not expose Paper/Folia scheduler types in public interfaces
- do not store Paper/Folia-specific typed fields in eagerly loaded public classes
- keep reflective access localized to the scheduler backend/resolver package

### 4. Improve failure visibility

When a Folia scheduler method is missing because the server is not Folia, fallback is expected and no error log is needed.

When the server exposes Folia schedulers but required methods cannot be bound or invoked, log:

- which scheduler path failed
- which method failed
- the exception type and message

This prevents misleading failures later in Bukkit fallback calls.

### 5. Async path behavior

`runAsync` should use:

- Folia `AsyncScheduler` when available
- Bukkit async scheduler otherwise

Task cancellation remains keyed through `scheduledTasks`, but one-shot async tasks do not need to be tracked unless the API returns a cancellable handle that the current plugin lifecycle needs to stop later.

## Testing Strategy

Follow TDD for the resolver and backend selection logic by introducing isolated tests around reflection-driven capability resolution.

Tests should cover:

- non-Folia servers resolve to the Bukkit backend
- Folia-like servers with matching methods resolve to the Folia backend
- Folia-like servers with broken method signatures trigger the logged fallback path
- global repeating scheduling on the Folia backend never calls Bukkit repeating scheduling
- async scheduling on the Folia backend uses the async scheduler path

The tests should avoid needing a live server process. Reflection resolution should therefore be factored into units that can be exercised with fake server and scheduler objects.

## Dependency Changes

Update Maven configuration from `spigot-api` to `paper-api` on a 1.21.x line and use the Paper repository.

The runtime compatibility promise remains:

- compile target: Paper API 1.21.x
- runtime target: Spigot/Paper/Folia 1.21.x

## Verification

Before completion:

- run the new scheduler-focused tests
- run the full Maven test suite
- run a fresh package build
- report any remaining manual verification needed on live Spigot/Paper/Folia servers
