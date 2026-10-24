# Documentation Index — ClipMark

Start here. Each topic has one canonical document. Other documents link to it rather than repeat it.

| If you want to… | Read |
|---|---|
| Understand what the app does and how to build it | [README](../README.md) |
| Know *why* features exist or were left out | [Design notes](DESIGN.md) |
| Find exact requirements and where they're implemented or tested | [SRS](01-product/SRS.md) |
| Change code safely | [System Architecture](02-architecture/SYSTEM_ARCHITECTURE.md) → [Development Guide](04-development/DEVELOPMENT_GUIDE.md) |
| Change a persisted model | [Data Storage](02-architecture/DATA_STORAGE.md) |

## Product
- [PRD](01-product/PRD.md): purpose, users, goals, feature status, user stories, constraints
- [SRS](01-product/SRS.md): numbered functional and non-functional requirements with code and test references
- [Design notes](DESIGN.md): product reasoning, feature choices, MVP vs polish, risk analysis (pre-existing)

## Architecture
- [System Architecture](02-architecture/SYSTEM_ARCHITECTURE.md): layers, components, flows, batch state machine
- [Data Storage](02-architecture/DATA_STORAGE.md): DataStore keys, JSON schemas, compatibility rules, data lifecycle
- README deep dives: [rendering](../README.md#how-watermark-rendering-works), [image pipeline](../README.md#how-images-are-processed), [metadata](../README.md#how-metadata-is-handled), [batching](../README.md#how-batch-processing-works)

## Development
- [Development Guide](04-development/DEVELOPMENT_GUIDE.md): tools, setup, commands, where to change what, debugging
- [Coding Conventions](04-development/CODING_CONVENTIONS.md)

## Testing
- [Test Plan](05-testing/TEST_PLAN.md): test inventory, environments, manual checklist, gaps

## Deployment
- [Deployment](06-deployment/DEPLOYMENT.md): release build, signing, install, versioning, rollback, shipped permissions

## Decisions
- [ADRs](07-decisions/ADR.md): 13 decisions reconstructed from code and docs

## Meta
- [Documentation Gap Report](DOCUMENTATION_GAP_REPORT.md): what existed, what was added, and open questions

## Assets
- `screenshots/`: app screenshots and sample exports used by the README
- `../samples/`: generated test images for manual testing

## Maintenance rules
* Update the doc in the same change as the code it describes. The Development Guide §5 maps code areas to docs.
* Mark anything unverified as `UNKNOWN / REQUIRES CONFIRMATION` rather than guessing.
* Test counts appear in the README, the Test Plan and the Development Guide. Update all three when tests are added.
