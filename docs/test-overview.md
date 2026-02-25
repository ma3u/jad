# Test Overview Report

This report covers tests for the following Eclipse Dataspace protocol specifications:

- **[Eclipse Dataspace Protocol (DSP)](https://projects.eclipse.org/projects/technology.dataspace-protocol-base)** — Catalog, Contract Negotiation, and Transfer Process protocols enabling interoperable data sharing between participants.
- **[Eclipse Dataspace Decentralized Claims Protocol (DCP)](https://projects.eclipse.org/projects/technology.dataspace-dcp)** — Credential issuance, presentation, and policy enforcement overlay over DSP.
- **[Eclipse Data Plane Signaling (DPS)](https://projects.eclipse.org/projects/technology.dataplane-signaling)** — Protocol state machine, message types, and HTTP APIs for control plane ↔ data plane communication (start, suspend, complete, terminate data flows).

## Table of Contents

1. [Scope and Test Types](#1-scope-and-test-types)
2. [Protocol Coverage Summary](#2-protocol-coverage-summary)
3. [Dataspace Protocol (DSP) Coverage](#3-dataspace-protocol-dsp-coverage)
   - [3.1 Catalog Protocol](#31-catalog-protocol)
   - [3.2 Contract Negotiation Protocol](#32-contract-negotiation-protocol)
   - [3.3 Transfer Process Protocol](#33-transfer-process-protocol)
   - [3.4 Data Plane Signaling (DPS)](#34-data-plane-signaling-dps)
4. [Decentralized Claims Protocol (DCP) Coverage](#4-decentralized-claims-protocol-dcp-coverage)
   - [4.1 Credential Issuance](#41-credential-issuance)
   - [4.2 Credential Presentation and Policy Evaluation](#42-credential-presentation-and-policy-evaluation)
   - [4.3 CEL Expression Evaluation](#43-cel-expression-evaluation)
5. [Test Case Details](#5-test-case-details)
   - [5.1 testTodoDataTransfer](#51-testtododatatransfer)
   - [5.2 testCertDataTransfer](#52-testcertdatatransfer)
   - [5.3 testTransferLimitedAccess](#53-testtransferlimitedaccess)
6. [Participant Onboarding Flow (Shared Fixture)](#6-participant-onboarding-flow-shared-fixture)
7. [Bruno Manual Test Collections](#7-bruno-manual-test-collections)
8. [What Is Not Covered](#8-what-is-not-covered)

---

## 1. Scope and Test Types

| Test type | Location | Count | Tag |
|-----------|----------|-------|-----|
| Automated end-to-end | `tests/end2end/src/test/java/.../DataTransferEndToEndTest.java` | 3 | `@EndToEndTest` |
| Shared onboarding fixture | `tests/end2end/src/test/java/.../ParticipantOnboarding.java` | — | — |
| Bruno manual collections | `requests/EDC-V Onboarding/` | ~29 | — |

All automated tests run against a live KinD cluster via port-forwarded Traefik gateway (`cp.localhost:8080`, `dp.localhost:8080`, etc.). They require all stack components to be running: ControlPlane, DataPlane, IdentityHub, IssuerService, TenantManager, ProvisionManager, Keycloak, Vault, PostgreSQL, NATS.

Run command:
```
./gradlew test -DincludeTags="EndToEndTest"
```

---

## 2. Protocol Coverage Summary

| Protocol | Sub-protocol / Feature | Tested | How |
|----------|------------------------|--------|-----|
| **DSP** | Catalog Protocol — catalog request | ✅ | All 3 test cases |
| **DSP** | Catalog Protocol — dataset / offer discovery | ✅ | All 3 test cases |
| **DSP** | Contract Negotiation Protocol — full flow to FINALIZED | ✅ | All 3 test cases (implicit via `/data` and `/transfer`) |
| **DSP** | Contract Negotiation Protocol — policy-based rejection | ✅ | `testTransferLimitedAccess` (HTTP 500 on missing VC) |
| **DSP** | Transfer Process Protocol — PUSH (HttpData) | ✅ | `testTodoDataTransfer` |
| **DSP** | Transfer Process Protocol — PULL (HttpData-PULL) | ✅ | `testCertDataTransfer` |
| **DSP** | Endpoint Data Reference (EDR) — token retrieval | ✅ | `testCertDataTransfer` (explicit EDR map) |
| **DSP** | Endpoint Data Reference (EDR) — data download with token | ✅ | `testCertDataTransfer` (auth token → dataplane) |
| **DPS** | Dataplane registration | ⚠️ | Workaround via custom `DataplaneRegistrationApiController` (DPS not fully implemented) |
| **DPS** | Transfer start signal (control plane → data plane) | ✅ | Exercised implicitly in all 3 transfer tests |
| **DPS** | Dataplane state machine (STARTED → COMPLETED) | ⚠️ | Happy path only; SUSPENDED/TERMINATED not tested |
| **DCP** | Credential issuance — VC issued by IssuerService | ✅ | `@BeforeAll` fixture — polls until `ISSUED` |
| **DCP** | Credential type: MembershipCredential | ✅ | All 3 test cases |
| **DCP** | Credential type: ManufacturerCredential | ✅ | `testTransferLimitedAccess` |
| **DCP** | Credential presentation at catalog scope | ✅ | CEL expression covers `catalog` scope |
| **DCP** | Credential presentation at contract negotiation scope | ✅ | CEL expression covers `contract.negotiation` scope |
| **DCP** | Credential presentation at transfer process scope | ✅ | CEL expression covers `transfer.process` scope |
| **DCP** | CEL expression evaluation — `MembershipCredential` | ✅ | `membership_cel_expression.json` |
| **DCP** | CEL expression evaluation — `ManufacturerCredential` | ✅ | `manufacturer_cel_expression.json` |
| **DCP** | VPA lifecycle — deploy → active | ✅ | `ParticipantOnboarding.execute()` |
| **DCP** | Participant with multiple VCs (membership + manufacturer) | ✅ | manufacturer participant in `testTransferLimitedAccess` |

---

## 3. Dataspace Protocol (DSP) Coverage

### 3.1 Catalog Protocol

The consumer requests the provider's catalog by sending its DID as the counterparty identifier:

```
POST /api/mgmt/v1alpha/participants/{consumerContextId}/catalog
Body: { "counterPartyDid": "<providerDid>" }
```

All three test cases invoke `fetchCatalog()` and assert:
- Response HTTP 200
- The returned `CatalogResponse` contains at least one `DataSet`
- Each `DataSet` carries an `Offer` with an offer ID used in the subsequent negotiation

The restricted asset in `testTransferLimitedAccess` has an open access policy (`policy-def.json` — `MembershipCredential eq active`), so it appears in the catalog for any member, but the contract policy (`policy-def-manufacturer.json` — `ManufacturerCredential eq active`) restricts who can negotiate a contract.

### 3.2 Contract Negotiation Protocol

Contract negotiation is triggered implicitly when the consumer calls:

- `/api/mgmt/v1alpha/participants/{id}/data` — initiates negotiation + transfer + data download in one call
- `/api/mgmt/v1alpha/participants/{id}/transfer` — initiates negotiation + transfer and returns the EDR

The negotiation reaches `FINALIZED` state before the transfer starts. Policy constraints are evaluated during negotiation using DCP-presented VCs.

**Policy tested:**

| Policy definition | Constraint | Used in |
|-------------------|------------|---------|
| `policy-def.json` | `MembershipCredential eq active` | `testTodoDataTransfer`, `testCertDataTransfer` (access + contract policy) |
| `policy-def.json` | `MembershipCredential eq active` | `testTransferLimitedAccess` (access policy only) |
| `policy-def-manufacturer.json` | `ManufacturerCredential eq active` | `testTransferLimitedAccess` (contract policy) |

**Rejection path tested:** a standard consumer (holding only `MembershipCredential`) attempting to negotiate a contract requiring `ManufacturerCredential` results in HTTP 500 (negotiation fails / transfer blocked).

### 3.3 Transfer Process Protocol

| Transfer type | Source type | Asset | Test |
|---------------|-------------|-------|------|
| HttpData (PUSH) | `HttpData` | `asset.json` — proxy to `jsonplaceholder.typicode.com/todos` | `testTodoDataTransfer` |
| HttpData-PULL | `HttpCertData` | `asset-cert.json` | `testCertDataTransfer` |
| HttpData (PUSH) restricted | `HttpData` | `asset-restricted.json` | `testTransferLimitedAccess` |

For the PULL transfer (`testCertDataTransfer`), the EDR (auth token + endpoint URL) is returned to the consumer, which then calls the dataplane directly:

```
POST /app/public/api/data/certs/request
Authorization: <EDR auth token>
```

### 3.4 Data Plane Signaling (DPS)

[Eclipse Data Plane Signaling](https://projects.eclipse.org/projects/technology.dataplane-signaling) defines the protocol by which the control plane instructs the data plane to start, suspend, complete, or terminate a data flow. It specifies a state machine, JSON message types, and HTTP RESTful APIs.

**Current implementation status:** DPS is not yet fully implemented in this stack. Dataplane registration uses a custom workaround endpoint (`DataplaneRegistrationApiController` in `extensions/api/mgmt`) instead of the DPS-compliant registration flow.

| DPS interaction | Triggered by | Status |
|-----------------|-------------|--------|
| Dataplane registration (`POST /api/mgmt/v4alpha/dataplanes/{participantId}`) | `registerDataPlane()` in test setup | ⚠️ Custom workaround, not DPS-spec |
| Transfer start signal (control plane → `http://dataplane.edc-v.svc.cluster.local:8083/api/control/v1/dataflows`) | DSP Transfer Process initiation | ✅ Exercised in all 3 transfer tests |
| Transfer STARTED → data available | Consumer receives EDR / data response | ✅ Verified via HTTP 200 assertions |
| Transfer COMPLETED / TERMINATED lifecycle | — | ❌ Not tested |

---

## 4. Decentralized Claims Protocol (DCP) Coverage

### 4.1 Credential Issuance

Every test participant is onboarded through a full DCP issuance flow in `@BeforeAll`. Three participants are provisioned: **consumer**, **provider**, and **manufacturer**.

Issuance flow per participant:
1. Tenant created in TenantManager
2. Participant Profile (VPA) deployed with optional roles
3. ProvisionManager orchestrates VPA lifecycle → awaits `active` state
4. VPA output includes: `holderPid`, `participantContextId`, `credentialRequest`
5. IssuerService issues VerifiableCredentials; IdentityHub credential request is polled until status `ISSUED` (max 20 s)
6. Client secret retrieved from Vault at `v1/secret/data/{participantContextId}`
7. Keycloak token created for participant (OAuth2 client credentials)

Credential types issued:
- `MembershipCredential` — issued to all participants (consumer, provider, manufacturer)
- `ManufacturerCredential` — additionally issued to the manufacturer participant (via `participantRoles: ["manufacturer"]`)

### 4.2 Credential Presentation and Policy Evaluation

VCs are evaluated by the CEL engine at three scopes, all configured in the seeded expressions:

| Scope | DSP phase |
|-------|-----------|
| `catalog` | Catalog request (access policy) |
| `contract.negotiation` | Contract negotiation (contract policy) |
| `transfer.process` | Transfer initiation |

The test `testTransferLimitedAccess` directly exercises the enforcement:

| Participant | Credentials held | Contract policy | Outcome |
|-------------|-----------------|-----------------|---------|
| consumer | MembershipCredential | ManufacturerCredential eq active | HTTP 500 — rejected |
| manufacturer | MembershipCredential + ManufacturerCredential | ManufacturerCredential eq active | HTTP 200 — allowed |

### 4.3 CEL Expression Evaluation

Two CEL expressions are seeded to the IssuerService in `@BeforeAll`:

**MembershipCredential** (`membership_cel_expression.json`):
```
ctx.agent.claims.vc
  .filter(c, c.type.exists(t, t == 'MembershipCredential'))
  .exists(c, c.credentialSubject.exists(cs, timestamp(cs.membershipStartDate) < now))
```
Checks: VC type is `MembershipCredential` AND `membershipStartDate` is in the past.

**ManufacturerCredential** (`manufacturer_cel_expression.json`):
```
ctx.agent.claims.vc
  .filter(c, c.type.exists(t, t == 'ManufacturerCredential'))
  .exists(c, c.credentialSubject.exists(cs, timestamp(cs.since) < now))
```
Checks: VC type is `ManufacturerCredential` AND `since` date is in the past.

Both expressions are registered for scopes: `catalog`, `contract.negotiation`, `transfer.process`.

---

## 5. Test Case Details

### 5.1 testTodoDataTransfer

**Purpose:** Happy-path HTTP data transfer with membership-gated access.

**Setup (provider side):**
- Asset: `asset.json` — `HttpData` type, proxies `https://jsonplaceholder.typicode.com/todos`
- Policy: `policy-def.json` — `MembershipCredential eq active` (used as both access and contract policy)
- Contract definition: `contract-def.json`
- Dataplane registered: `allowedSourceTypes: [HttpData, HttpCertData]`, `allowedTransferTypes: [HttpData-PULL]`

**Flow:**
1. Consumer fetches provider catalog → receives offer ID
2. Consumer calls `POST /api/mgmt/v1alpha/participants/{id}/data` with offer ID + provider DID + `policyType: "membership"`
3. Control plane runs DSP: catalog lookup → contract negotiation → transfer process → EDR retrieval → HTTP proxy to `jsonplaceholder.typicode.com/todos`
4. Response returned to consumer

**Assertions:**
- HTTP 200
- Response body is non-null JSON (todo items from jsonplaceholder)

**Protocols exercised:** DSP Catalog, DSP Contract Negotiation, DSP Transfer Process, DPS transfer start signal, DCP MembershipCredential evaluation

---

### 5.2 testCertDataTransfer

**Purpose:** PULL-mode transfer with explicit EDR usage.

**Setup (provider side):**
- Asset: `asset-cert.json` — `HttpCertData` source type
- Policy: `policy-def.json` — `MembershipCredential eq active`
- Contract definition: `contract-def.json`
- Dataplane registered (same as above)

**Flow:**
1. Consumer fetches provider catalog → receives offer ID
2. Consumer calls `POST /api/mgmt/v1alpha/participants/{id}/transfer` → returns EDR map `{authToken, endpoint}`
3. Consumer calls `POST /app/public/api/data/certs/request` on the dataplane directly with the EDR auth token

**Assertions:**
- `/transfer` returns HTTP 200 with EDR map containing `authToken`
- Dataplane cert request returns HTTP 200 with empty list

**Protocols exercised:** DSP Catalog, DSP Contract Negotiation, DSP Transfer Process (PULL), DPS transfer start signal, EDR token exchange, DCP MembershipCredential evaluation

---

### 5.3 testTransferLimitedAccess

**Purpose:** Role-based access control — verifies that a credential-gated asset rejects unauthorized consumers and allows authorized ones.

**Setup (provider side):**
- Asset: `asset-restricted.json` — `HttpData` type
- Access policy: `policy-def.json` — `MembershipCredential eq active` (asset visible to all members)
- Contract policy: `policy-def-manufacturer.json` — `ManufacturerCredential eq active` (only manufacturers can negotiate)
- Contract definition: `contract-def.json` linking separate access and contract policies

**Flow — negative path:**
1. Consumer (has only MembershipCredential) fetches catalog → sees the asset
2. Consumer calls `/data` with `policyType: "manufacturer"` → negotiation fails (missing ManufacturerCredential)
3. **Asserts HTTP 500** — expected rejection

**Flow — positive path:**
4. Manufacturer (has MembershipCredential + ManufacturerCredential) calls `/data`
5. **Asserts HTTP 200** — expected success

**Protocols exercised:** DSP Catalog, DSP Contract Negotiation (both pass and fail paths), DSP Transfer Process, DPS transfer start signal, DCP ManufacturerCredential evaluation (positive and negative enforcement)

---

## 6. Participant Onboarding Flow (Shared Fixture)

`ParticipantOnboarding.java` encapsulates the full DCP-based participant provisioning. It is called three times in `@BeforeAll` to set up the test participants.

```
execute(cellId, roles...)
  ├── createTenant(name)                         → TM POST /api/v1alpha1/tenants
  ├── getDataspaceProfileId()                    → TM GET  /api/v1alpha1/cells/{id}/dataspaceprofiles
  ├── deployParticipantProfile(did, roles...)    → TM POST /api/v1alpha1/tenants/{id}/participant-profiles
  ├── await VPA state == "active" (60 s)
  │     verifies: holderPid, participantContextId, credentialRequest
  ├── getVaultSecret(participantContextId)       → Vault GET v1/secret/data/{id}
  ├── createKeycloakToken(clientId, secret)      → Keycloak POST /realms/dataspace/...
  └── waitForCredentialIssuance(pcId, token, holderPid)
        → IH GET /cs/api/identity/v1alpha/participants/{pcB64}/credentials/request/{holderPid}
          polls until status == "ISSUED" (20 s)
```

Services touched: TenantManager, ProvisionManager (via VPA), Vault, Keycloak, IdentityHub, IssuerService (via IH).

---

## 7. Bruno Manual Test Collections

The `requests/EDC-V Onboarding/` directory contains Bruno collections that mirror the automated test flows for manual exploration and debugging.

| Collection | Requests |
|------------|----------|
| CFM - Provision Consumer | Create Tenant, Deploy Participant Profile, Get Participant Profile, Obtain Secret from Vault, TM Get Cells, TM Get Dataspace Profiles |
| CFM - Provision Provider | (same as consumer) |
| EDC-V Management / Prepare Consumer Participant | Seed policy, credentials, catalog setup |
| EDC-V Management / Prepare Provider Participant | Seed assets, policy, contract def, dataplane |
| EDC-V Management / Data Transfer / Http Todo | Full DSP flow for HttpData asset |
| EDC-V Management / Data Transfer / Http Certs | Full DSP flow for HttpCertData (PULL) asset |

These collections are not part of the automated test run. They serve as the manual equivalent for step-by-step verification and were used to validate the 29/29 Bruno tests during deployment.

---

## 8. What Is Not Covered

| Area | Notes |
|------|-------|
| DSP Catalog Protocol — filtering / pagination | No tests for catalog query parameters |
| DSP Contract Negotiation — consumer-initiated termination | No test for `TERMINATED` negotiation state |
| DSP Transfer Process — `SUSPENDED` / `COMPLETED` lifecycle events | Only happy-path start→active→data is tested |
| DPS — dataplane registration | Not fully implemented; workaround uses custom `DataplaneRegistrationApiController` (see [section 3.4](#34-data-plane-signaling-dps)) |
| DPS — COMPLETED / TERMINATED / SUSPENDED state transitions | Transfer lifecycle beyond STARTED not exercised |
| DCP — credential revocation | No test for revoked VC being rejected |
| DCP — credential expiry | CEL expressions check `membershipStartDate`/`since` but no test for expired VCs |
| DCP — multiple issuers / cross-tenant issuance | Only single IssuerService instance tested |
| DCP — credential re-issuance / refresh | Not tested |
| IdentityHub — DID resolution failure | No negative test for unresolvable DIDs |
| Keycloak — token expiry / renewal | Tokens are created fresh in `@BeforeAll`; no refresh tested |
| Multi-cell scenarios | Tests use a single cell; multi-cell routing not exercised |
| Concurrent transfers | No concurrency or load tests |
