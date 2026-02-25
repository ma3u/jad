# JAD Deployment Report

## Table of Contents

- [1. Environment](#1-environment)
- [2. Components](#2-components)
- [3. Fixes Summary](#3-fixes-summary)
- [4. Operational Gotchas](#4-operational-gotchas)
  - [4.1 kubectl Context Switches](#41-kubectl-context-switches-critical)
  - [4.2 IssuerService Seed — DO NOT Re-run](#42-issuerservice-seed--do-not-re-run)
  - [4.3 CFM Pod Labels vs Deployment Names](#43-cfm-pod-labels-vs-deployment-names)
- [5. Standard Reset Procedure](#5-standard-reset-procedure)
- [6. DB Schema Note](#6-db-schema-note)

---

## 1. Environment

- **Cluster**: KinD `kind-edcv`, namespace `edc-v`
- **Gateway**: Traefik (`traefik` namespace), port-forward: `kubectl -n traefik port-forward svc/traefik 8080:80`
- **JDK**: OpenJDK 24.0.2 Temurin (containers), Temurin 21 (local Gradle)
- **EDC**: 0.16.0-SNAPSHOT · **NATS**: 2.12.4 JetStream
- **Last verified**: 2026-02-25 — Bruno 29/29 ✅ · E2E 3/3 ✅

## 2. Components

| Deployment | Notes |
|---|---|
| controlplane, dataplane, identityhub, issuerservice | EDC services |
| cfm-provision-manager, cfm-tenant-manager, cfm-agents | CFM orchestration |
| redline | Redline — has its own seed job |
| keycloak, postgres, nats, vault, jad-web-ui | Infrastructure |

---

## 3. Fixes Summary

In the order they were applied:

| # | What | File(s) changed | Why it broke |
|---|---|---|---|
| 1 | `provision-manager-seed` payload shape | `k8s/apps/provision-manager-seed-job.yaml` | Activities were passed as a list; API requires a map keyed by orchestration type |
| 2 | `redline-seed` bounded wait loop | `k8s/apps/redline-seed-job.yaml` | Seed script waited indefinitely for participants to go ACTIVE; needed a max-attempts cap with warn-and-continue |
| 3 | `issuerservice-seed` readiness polling | `k8s/apps/issuerservice-seed-job.yaml` | Poll interval was 5s; reduced to 1s to speed up the init-container phase |
| 4 | Dataplane SSL truststore | `k8s/apps/dataplane.yaml` | JDK 24 didn't pick up the default `cacerts` automatically — HTTPS calls to external URLs failed with PKIX errors |
| 5 | VPA onboarding timeout | `ParticipantOnboarding.java` line 58 | Hard-coded 20s await for VPA "active" was too tight on KinD; raised to 60s |
| 6 | Bruno collection (16+ .bru files) | `requests/EDC-V Onboarding/**` | Cell ID extraction, hardcoded vars, consumer roles, vault response condition, cert upload path, 415 on query-certs, VPA polling pattern (QuickJS), credential spec format, discriminator direction |
| 7 | CFM pod restart after DB wipe | Ops procedure | PM/TM state machines cache NATS consumer offsets in memory; without a pod restart after a DB wipe they replay against stale state. Also: `cells`, `orchestration_definitions`, `activity_definitions` were missing from the cleanup |
| 8 | Missing `data_address_alias` column | Controlplane DB (manual ALTER) | `CREATE TABLE IF NOT EXISTS` never adds new columns to existing tables — EDC 0.16.0-SNAPSHOT added this column but the DB was created from the older schema |

---

## 4. Operational Gotchas

### 4.1 kubectl Context Switches (CRITICAL)

kubectl silently switches context when other kubernetes clusters of parallel projects are registered (e.g. `aks-cassa-multienv-admin` in Azure). This caused `rollout restart` to run against AKS during this session.

- Always check: `kubectl config current-context`
- Fix: `kubectl config use-context kind-edcv`
- Recovery: `kubectl -n edc-v rollout undo deployment/<name>` on the correct context

### 4.2 IssuerService Seed — DO NOT Re-run

The issuer identity (`participant_context`, `did_resources`, `keypair_resource`, `credential_definitions`, `attestation_definitions`, `edc_sts_client`) lives in the `issuerservice` DB and persists across resets. Re-seeding causes the IS API to return 409 and the pod hangs `Running` indefinitely.

- **Only run on**: first-ever deployment to an empty DB
- **If stuck**: `kubectl -n edc-v delete job issuerservice-seed` — IS continues working without it

### 4.3 CFM Pod Labels vs Deployment Names

The pod selector labels differ from the deployment names:

| Deployment name | Pod label to use |
|---|---|
| `cfm-provision-manager` | `app=provision-manager` |
| `cfm-tenant-manager` | `app=tenant-manager` |
| `cfm-agents` | `app=cfm-agents` |

---

## 5. Standard Reset Procedure

```bash
# 0. Always verify context
kubectl config use-context kind-edcv

# 1. Clean all 5 databases (FK-aware order)
kubectl -n edc-v exec deployment/postgres -- psql -U cfm -d cfm -c \
  "DELETE FROM orchestration_entries; DELETE FROM participant_profiles; DELETE FROM dataspace_profiles; DELETE FROM tenants; DELETE FROM cells; DELETE FROM orchestration_definitions; DELETE FROM activity_definitions;"
kubectl -n edc-v exec deployment/postgres -- psql -U ih -d identityhub -c \
  "DELETE FROM edc_holder_credentialrequest; DELETE FROM edc_credential_offers; DELETE FROM credential_resource; DELETE FROM keypair_resource; DELETE FROM edc_sts_client; DELETE FROM edc_participant_context_config; DELETE FROM did_resources; DELETE FROM participant_context;"
kubectl -n edc-v exec deployment/postgres -- psql -U cp -d controlplane -c \
  "DELETE FROM edc_edr_entry; DELETE FROM edc_transfer_process; DELETE FROM edc_contract_negotiation; DELETE FROM edc_contract_agreement; DELETE FROM edc_contract_definitions; DELETE FROM edc_policydefinitions; DELETE FROM edc_asset; DELETE FROM edc_data_plane_instance; DELETE FROM edc_cel_expression; DELETE FROM edc_participant_context_config; DELETE FROM participant_context;"
kubectl -n edc-v exec deployment/postgres -- psql -U dp -d dataplane -c \
  "DELETE FROM edc_accesstokendata; DELETE FROM edc_certs;"
# IS: keep issuer identity — only delete transactional data
kubectl -n edc-v exec deployment/postgres -- psql -U issuer -d issuerservice -c \
  "DELETE FROM edc_issuance_process; DELETE FROM holders;"

# 2. Force-delete CFM pods (flush in-memory state machine state)
kubectl -n edc-v delete pod -l app=provision-manager --force --grace-period=0
kubectl -n edc-v delete pod -l app=tenant-manager --force --grace-period=0
kubectl -n edc-v delete pod -l app=cfm-agents --force --grace-period=0

# 3. Restart EDC services
kubectl -n edc-v rollout restart deployment/controlplane deployment/identityhub deployment/issuerservice

# 4. Wait for Ready, then re-seed PM and TM
sleep 30
cd k8s/apps
kubectl -n edc-v delete job provision-manager-seed 2>/dev/null; kubectl apply -f provision-manager-seed-job.yaml
sleep 15
kubectl -n edc-v delete job tenant-manager-seed 2>/dev/null; kubectl apply -f tenant-manager-seed-job.yaml

# 5. Remove any stuck IS seed job (do NOT re-apply it)
kubectl -n edc-v delete job issuerservice-seed 2>/dev/null || true

# 6. Verify
kubectl -n edc-v get jobs --no-headers
```

---

## 6. DB Schema Note

When rebuilding EDC images, check for schema drift. New columns added in newer snapshots will not be applied to existing tables by `CREATE TABLE IF NOT EXISTS`. Extract the expected schema from the JAR and compare with `\d <table>`:

```bash
kubectl -n edc-v exec deployment/controlplane -- unzip -p /app/edc-controlplane.jar transfer-process-schema.sql
kubectl -n edc-v exec deployment/postgres -- psql -U cp -d controlplane -c "\d edc_transfer_process"
```

Applied fix for this deployment: `ALTER TABLE edc_transfer_process ADD COLUMN data_address_alias TEXT;`

