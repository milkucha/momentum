# Agent Coordination

Both agents should read this file before executing any edits.
Update the locks table when you start and clear your row when done/committed.

## Active locks
| File | Agent | Status | Since |
|---|---|---|---|
| *(none)* | — | — | — |

## Pending handoffs
*(none)*

## Shared decisions
- **Space key remapping**: Space is being remapped to `back` (braking). The `space` param (ordinal 4 in `provideClientInput`) must also be zeroed to prevent the deploy dynamic from firing. Both changes belong in `AutomobileBrakeMixin.java`.
