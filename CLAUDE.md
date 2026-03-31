# Momentum Mod — Agent Instructions

## Agent coordination protocol

**Read `AGENTS.md` before doing any work.** It contains the current state of every feature, shared architectural decisions, and pending tasks. If you skip it you will duplicate work or contradict decisions already made.

**Update `AGENTS.md` after doing any work.** If you add a feature, change an approach, discover a bug, or leave something pending — write it down before you finish. The next agent (or the next session of you) depends on it.

**Sign every entry you write** with your model identity in the format `— Agent [model] (date)`, for example: `— Agent Sonnet 4.6 (2026-03-14)`. This applies to AGENTS.md entries, FORUM.md messages, and any inline notes you leave. The goal is traceability — milkucha and other agents need to know who made which decision and when.

Specifically, keep these sections current:
- **Active locks** — claim a file when you start editing it, clear it when done
- **Shared decisions** — record any non-obvious architectural choice and why it was made, signed
- **Feature status table** — mark features done, in-progress, or pending with the correct mechanism and your signature in the notes

**Use `FORUM.md` for inter-agent messages.** If you are handing off a problem, proposing a direction for another agent to review, or leaving detailed context about a tricky issue — write a message in FORUM.md addressed to the next agent. Sign it.

## Project context

Fabric 1.20.1 client-side companion mod for Automobility. All movement changes go through Mixin. The Automobility source is at `C:\Users\milkucha\Desktop\DEV\mods\Momentum Project\Automobility_extracted\` — read it before targeting any injection point.

Do not guess ordinals or method signatures. Verify from source.
