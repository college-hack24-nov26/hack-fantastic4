# Vision

Vision is an offline-first Android visual assistance project for people with visual impairments. The planned application combines camera observations, on-device inference, spoken guidance, and accessible controls. It must communicate uncertainty and must not promise that every hazard will be detected.

This repository currently contains a **shared foundation**, not an implemented Android application or trained model.

## Repository layout

| Path | Purpose |
| --- | --- |
| `android-app/` | Kotlin Android application, owned by the UI track |
| `ml/` | Training, evaluation, and export work, owned by the ML track |
| `contracts/` | Proposed, versioned interchange schemas requiring joint review |
| `models/` | Model delivery guidance; no model artifact is committed |
| `docs/` | Project context, architecture, and integration notes |

## Branches and collaboration

`main` is the stable integration branch. The UI laptop owns `ui` and `android-app/`; the ML laptop owns `ml` and `ml/`. Shared files in `contracts/`, `docs/`, and `models/` require coordination. Changes to `main` should be reviewed through pull requests. Neither track should change the other's implementation or rewrite shared history.

## Getting started

Clone the shared repository, fetch remote branches, and check out your assigned branch. Read [project context](docs/PROJECT_CONTEXT.md), [architecture](docs/ARCHITECTURE.md), and [integration guidance](docs/INTEGRATION.md) before implementation. Android and ML setup instructions will be added in their respective directories when those projects exist.

The schemas under `contracts/` are **draft 0.1.0 proposals**, not approved integration requirements. Model input and output details remain open.
