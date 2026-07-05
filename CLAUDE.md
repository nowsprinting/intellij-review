# Development Guidelines

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

[Re:VIEW](https://github.com/kmuto/review)フォーマットを扱うネイティブ言語プラグイン

## Project Structure

## Special directories

Special directories outside of plugin projects:

- docs/
  - spec/       # Specification documents
  - design/     # Design documents
  - plans/      # Plan files

## Language Guidelines

- **English**: File names and type/member signatures, README.md
- **Japanese**: Test method names, code comments, and all files under `docs/`

## Skill Guidelines

<important if="Feature implementation planning (writing or modifying a feature implementation plan in plan mode)">
- Add the following final step at the end of the `## Development Workflow` section in the plan file (adjust the step number to follow the last existing step):

  ```markdown
  ### Step N: Finalize

  1. Append `## Implementation Notes` to the end of the plan file and write the following:
      - Design decisions: choices you made where the spec was ambiguous
      - Deviations: places where you intentionally departed from the spec, and why
      - Tradeoffs: alternatives you considered and why you picked what you did
      - Test changes: Test code modified during the implementation phase
      - Open questions: anything you'd want me to confirm or revise
  2. Copy this plan file to `docs/plans/yyyy-MM-dd-<plan-name>.md`
  3. Update `docs/spec/` and `docs/design/` to reflect this plan's changes, if applicable
  4. Append an entry to `CHANGELOG.md` under `## [Unreleased]`
      - Do not document changes whose paths start with `.github/` or `src/test/` (test code changes are excluded from the changelog)
  ```
</important>
