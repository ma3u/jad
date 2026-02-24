# Project Guidelines

## Code Style
- Primary language is Java with Gradle Kotlin DSL build files (`*.gradle.kts`).
- Follow Google Java Style as enforced by Checkstyle in [config/checkstyle/checkstyle.xml](../config/checkstyle/checkstyle.xml).
- Keep 4-space indentation, avoid tabs, and respect configured naming/import rules (including custom import order).
- Preserve existing package conventions under `org.eclipse.edc.*` (examples: [extensions/api/mgmt/src/main/java](../extensions/api/mgmt/src/main/java), [tests/end2end/src/test/java](../tests/end2end/src/test/java)).

## Architecture
- JAD is a Kubernetes-based dataspace demonstrator with control plane, data plane, IdentityHub, IssuerService, Keycloak, Vault, PostgreSQL, NATS, and CFM components (see [README.md](../README.md)).
- Gradle modules are split into launchers, extensions, and end-to-end tests (see [settings.gradle.kts](../settings.gradle.kts)).
- Runtime launchers use `org.eclipse.edc.boot.system.runtime.BaseRuntime` (example: [launchers/controlplane/build.gradle.kts](../launchers/controlplane/build.gradle.kts)).
- Deployment manifests are organized by infra vs app workloads in [k8s/base](../k8s/base) and [k8s/apps](../k8s/apps).

## Build and Test
- Build all images from source: `./gradlew dockerize` (documented in [README.md](../README.md)).
- Run end-to-end tests: `./gradlew test -DincludeTags="EndToEndTest"` (see [README.md](../README.md)).
- Typical local deploy flow:
  - `kubectl apply -f k8s/base/`
  - wait infra pods
  - `kubectl apply -f k8s/apps/`
  - wait seed jobs
- Cleanup deployment: `kubectl delete -k k8s/`.

## Project Conventions
- Keep Java toolchain compatibility with project settings (Java 21 configured in [build.gradle.kts](../build.gradle.kts)).
- Prefer module-local changes: extension code in `extensions/*`, runtime composition in `launchers/*`, scenario tests in `tests/end2end/*`.
- End-to-end test coverage is tag-based (`@EndToEndTest`) under [tests/end2end/src/test/java](../tests/end2end/src/test/java).
- OpenAPI contracts in [openapi](../openapi) define externally exposed management/identity/issuer/provision/tenant APIs.

## Integration Points
- Control plane, data plane, identity hub, and issuer service are exposed via Gateway hostnames/routes in [k8s/apps](../k8s/apps).
- Core service integration relies on Vault, PostgreSQL, NATS, and Keycloak config maps (example: [k8s/apps/controlplane-config.yaml](../k8s/apps/controlplane-config.yaml)).
- CFM-related configuration and onboarding flows are in [k8s/apps](../k8s/apps) and request collections under [requests/EDC-V Onboarding](../requests/EDC-V Onboarding).

## Security
- Treat `k8s` manifests as security-sensitive: they include dev credentials/tokens and OAuth/JWKS wiring (examples: [k8s/base/vault.yaml](../k8s/base/vault.yaml), [k8s/base/keycloak.yaml](../k8s/base/keycloak.yaml)).
- Do not “harden by default” in random edits; preserve local-dev assumptions unless explicitly asked.
- Never introduce production credentials into repo files; keep existing dev-only secrets patterns unchanged.
- For auth-related changes, verify consistency across Keycloak issuer URLs, JWKS endpoints, Vault JWT roles, and OpenAPI security declarations.
