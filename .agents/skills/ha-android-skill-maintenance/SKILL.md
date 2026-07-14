---
name: ha-android-skill-maintenance
description: Home Assistant Android agent instructions upkeep. Use when feedback contradicts or is missing from AGENTS.md or a skill, when a recurring review comment has no skill rule, when a skill statement turned stale, or when adding or renaming a skill.
---

# HA Android Skill Maintenance

Use this skill to keep `AGENTS.md` and the project skills accurate as the codebase and conventions evolve.

## When to Propose an Update

Surface a skill update — don't let the learning evaporate — when:

- The user corrects behavior that a skill should have prevented, or gives guidance no skill covers.
- The user says a point keeps coming up in reviews, or asks you to mine review history, and no skill states the rule (mechanism over reminder).
- A skill statement conflicts with the current code: renamed class, removed utility, changed workflow. Verify against the code before proposing the fix.
- A convention gets decided in a PR discussion that the skills don't reflect yet.

Before proposing, read the skills that own the topic and check for an existing or conflicting rule — search the real directory `.agents/skills/`, not `.claude/skills`: that path is a symlink recursive grep won't traverse, so searching it reports false "no rule exists". A new convention that contradicts an existing rule must be surfaced as a conflict, not silently appended.

Propose the update explicitly to the user; never rewrite skills silently as a side effect of other work.

## Structure

- Skills live in `.agents/skills/<name>/SKILL.md` with `name` and `description` frontmatter. The description gives one sentence of context then "Use when ..." triggers — never a summary of the skill's content.
- `AGENTS.md` holds only always-on content: commands, condensed architecture, core standards, and the skill routing list. Detailed guidance belongs in the matching skill, and the routing list must stay in sync when a skill is added or renamed.
- Entry points are symlinks: `CLAUDE.md`, `GEMINI.md`, `.github/copilot-instructions.md`, and `.junie/guidelines.md` all point to `AGENTS.md`; `.claude/skills` points to `.agents/skills` (kept tracked via the `!.claude/skills` gitignore exception).
- Keep skills concise (aim under ~800 words). State a rule once in the skill that owns the topic and cross-reference from others instead of duplicating.

## Dedicated PR

Updates to `AGENTS.md` or the skills always ship as their own PR (for example `feature/skills-update-<topic>`), never mixed into a feature or fix PR. Put the evidence — review comment links, the failing example, the correction — in the PR description, not in the skill body.
