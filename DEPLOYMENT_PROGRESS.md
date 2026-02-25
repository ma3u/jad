# JAD Deployment Progress Report

## Environment
- **Cluster**: KinD `kind-edcv`, namespace `edc-v`
- **Gateway**: Traefik port-forwarded `8080:80`
- **JDK**: OpenJDK 24.0.2 Temurin (containers), Temurin 21 (local Gradle)
- **EDC**: 0.16.0-SNAPSHOT
- **NATS**: 2.12.4 with JetStream

## Final Status: ALL TESTS PASSING ✅

### Bruno Tests: 29/29 PASSED ✅
All 29 Bruno tests pass including `Http Todo/Get Data` (was failing with SSL error).

### E2E Tests: 3/3 PASSED ✅
- `testTodoDataTransfer` — Downloads todos from jsonplaceholder via data transfer ✅
- `testCertDataTransfer` — Sets up transfer and retrieves EDR token ✅
- `testTransferLimitedAccess` — Verifies policy-based access control (consumer rejected, manufacturer allowed) ✅

## Fixes Applied

### 1. Dataplane SSL Fix
- **File**: `k8s/apps/dataplane.yaml`
- **Fix**: Added explicit JVM truststore args to dataplane container:
  ```
  -Djavax.net.ssl.trustStore=/opt/java/openjdk/lib/security/cacerts
  -Djavax.net.ssl.trustStorePassword=changeit
  ```
- **Root cause**: JDK 24 dataplane wasn't using the default cacerts despite all required root CAs being present.

### 2. VPA Onboarding Timeout
- **File**: `tests/end2end/src/test/java/org/eclipse/edc/jad/tests/ParticipantOnboarding.java`
- **Fix**: Increased VPA await timeout from 20s → 60s (line 58)
- **Root cause**: VPA orchestration can take ~6s in local KinD; 20s was too tight for the full cycle.

### 3. PM State Machine Fix
- **Root cause**: PM internal state became stale after DB cleanup without pod restart. NATS consumer positions didn't reset.
- **Fix**: Force-delete ALL CFM pods (`provision-manager`, `tenant-manager`, `cfm-agents`) after DB cleanup, then re-seed. Pod labels differ from deployment names:
  - `app=provision-manager` (NOT `cfm-provision-manager`)
  - `app=tenant-manager` (NOT `cfm-tenant-manager`)
  - `app=cfm-agents`
- **Key discovery**: `cells`, `orchestration_definitions`, and `activity_definitions` tables were missing from the original cleanup procedure.

### 4. Database Schema Migration Fix
- **Table**: `edc_transfer_process` in controlplane DB
- **Fix**: `ALTER TABLE edc_transfer_process ADD COLUMN data_address_alias TEXT;`
- **Root cause**: `CREATE TABLE IF NOT EXISTS` in the schema SQL doesn't add new columns when the table already exists. The CP image (built from latest EDC 0.16.0-SNAPSHOT) expects `data_address_alias` but the DB was created from an older schema version that lacked it.

### 5. Bruno Fixes (16+ files modified)
1. Cell ID extraction from TM-Get Cells response
2. Hardcoded variable removal across all requests
3. Consumer participant roles fix
4. Vault secret response condition fix
5. Dataspace profiles guard
6. Upload certificate path fix
7. Query certificates 415 → proper body
8. VPA polling: sequential `await pollOnce()` pattern (30 reps × 2s for QuickJS)
9. Credential specs format in TM seed job
10. Discriminator reversal fix

### Infrastructure Fixes
- **PM seed job**: Fixed JSON shape for activity definitions
- **IssuerService**: IS seed not needed after first deployment (issuer identity data persists in `participant_context`, `did_resources`, `credential_resource`, etc.)

## Database Cleanup Procedure (Complete)
All 5 databases must be cleaned in FK-aware order, then CFM pods must be force-deleted:
```bash
# CFM (ALL tables including cells and definitions)
psql -U cfm -d cfm -c "DELETE FROM orchestration_entries; DELETE FROM participant_profiles; DELETE FROM dataspace_profiles; DELETE FROM tenants; DELETE FROM cells; DELETE FROM orchestration_definitions; DELETE FROM activity_definitions;"
# IH
psql -U ih -d identityhub -c "DELETE FROM edc_holder_credentialrequest; DELETE FROM edc_credential_offers; DELETE FROM credential_resource; DELETE FROM keypair_resource; DELETE FROM edc_sts_client; DELETE FROM edc_participant_context_config; DELETE FROM did_resources; DELETE FROM participant_context;"
# CP (includes edc_cel_expression)
psql -U cp -d controlplane -c "DELETE FROM edc_edr_entry; DELETE FROM edc_transfer_process; DELETE FROM edc_contract_negotiation; DELETE FROM edc_contract_agreement; DELETE FROM edc_contract_definitions; DELETE FROM edc_policydefinitions; DELETE FROM edc_asset; DELETE FROM edc_data_plane_instance; DELETE FROM edc_cel_expression; DELETE FROM edc_participant_context_config; DELETE FROM participant_context;"
# DP
psql -U dp -d dataplane -c "DELETE FROM edc_accesstokendata; DELETE FROM edc_certs;"
# IS (ONLY transactional data — keep issuer identity!)
psql -U issuer -d issuerservice -c "DELETE FROM edc_issuance_process; DELETE FROM holders;"
```
Then: Force-delete ALL CFM pods → Wait for Ready → Re-seed PM → Re-seed TM → (DO NOT re-seed IS)

## Files Modified
- `k8s/apps/dataplane.yaml` — SSL truststore fix
- `tests/end2end/src/test/java/org/eclipse/edc/jad/tests/ParticipantOnboarding.java` — timeout 20→60s
- `requests/EDC-V Onboarding/CFM - Provision Consumer/Get Participant Profile.bru` — removed debug console.log
- 16+ other Bruno `.bru` files (from previous session)
- Controlplane DB: `ALTER TABLE edc_transfer_process ADD COLUMN data_address_alias TEXT;`
