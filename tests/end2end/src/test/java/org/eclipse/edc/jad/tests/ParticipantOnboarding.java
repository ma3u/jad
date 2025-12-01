/*
 *  Copyright (c) 2025 Metaform Systems, Inc.
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Metaform Systems, Inc. - initial API and implementation
 *
 */

package org.eclipse.edc.jad.tests;

import org.eclipse.edc.identityhub.spi.participantcontext.model.CreateParticipantContextResponse;
import org.eclipse.edc.jad.tests.model.HolderCredentialRequestDto;
import org.eclipse.edc.spi.monitor.Monitor;

import java.util.Base64;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.jad.tests.DataTransferTest.API_ADMIN_KEY;
import static org.eclipse.edc.jad.tests.DataTransferTest.BASE_URL;
import static org.eclipse.edc.jad.tests.DataTransferTest.loadResourceFile;
import static org.eclipse.edc.jad.tests.KeycloakApi.createKeycloakAdminToken;
import static org.eclipse.edc.jad.tests.KeycloakApi.createKeycloakUser;
import static org.eclipse.edc.jad.tests.KeycloakApi.getAccessToken;

public record ParticipantOnboarding(String participantContextId, String participantContextDid, String issuerId,
                                    String issuerApiKey, Monitor monitor) {

    public String participantContextIdBase64() {
        return Base64.getEncoder().encodeToString(participantContextId.getBytes());
    }

    public void execute(String credentialDefinitionId) {
        var accessToken = createKeycloakAdminToken();
        monitor.info("Configuring Vault Access in Keycloak");
        createKeycloakUser(participantContextId + "-vault", participantContextId, participantContextId + "-secret", "participant", accessToken);
        monitor.info("Configuring API Access in Keycloak");
        createKeycloakUser(participantContextId, participantContextId, participantContextId + "-secret", "participant", accessToken);

        monitor.info("Create holder in IssuerService");
        createHolder();

        monitor.info("Onboard onto IdentityHub");
        var ihPc = createParticipantInIdentityHub();
        monitor.info("Onboard onto Control Plane");
        createParticipantInControlPlane(ihPc);

        monitor.info("Create credential request");
        var holderPid = createCredentialRequest(ihPc.apiKey(), credentialDefinitionId);

        monitor.info("Wait for credential issuance");
        waitForCredentialIssuance(ihPc.apiKey(), holderPid);
        monitor.info("Credential issued successfully");
    }

    private void waitForCredentialIssuance(String apiKey, String holderPid) {
        await().atMost(20, SECONDS)
                .pollInterval(1, SECONDS).until(() -> {
                    var response = given()
                            .baseUri(BASE_URL)
                            .contentType("application/json")
                            .header("x-api-key", apiKey)
                            .get("/cs/api/identity/v1alpha/participants/%s/credentials/request/%s".formatted(participantContextIdBase64(), holderPid))
                            .then()
                            .log().ifValidationFails()
                            .statusCode(200)
                            .extract()
                            .body()
                            .as(HolderCredentialRequestDto.class);
                    return "ISSUED".equals(response.status());
                });
    }

    private String createCredentialRequest(String apiKey, String credentialDefinitionId) {
        var holderPid = UUID.randomUUID().toString();
        given()
                .baseUri(BASE_URL)
                .contentType("application/json")
                .header("x-api-key", apiKey)
                .body("""
                        {
                            "issuerDid": "did:web:issuerservice.edc-v.svc.cluster.local%%3A10016:issuer",
                            "holderPid": "%s",
                            "credentials": [{
                                "format": "VC1_0_JWT",
                                "type": "MembershipCredential",
                                "id": "%s"
                            }]
                        }
                        """.formatted(holderPid, credentialDefinitionId))
                .post("/cs/api/identity/v1alpha/participants/%s/credentials/request".formatted(participantContextIdBase64()))
                .then()
                .log().ifValidationFails()
                .statusCode(201);
        return holderPid;
    }

    /**
     * Onboards the participant in the control plane.
     *
     * @deprecated will be replaced by the proper Management API call in due time
     */
    @Deprecated
    private void createParticipantInControlPlane(CreateParticipantContextResponse identityhubClient) {
        var template = loadResourceFile("create_participant_controlplane.json");
        var requestBody = template.replace("{{participant_context_id}}", participantContextId)
                .replace("{{participant_context_did}}", participantContextDid)
                .replace("{{tenant_clientSecret}}", identityhubClient.clientSecret())
                .replace("{{tenant_clientId}}", identityhubClient.clientId());

        var accessToken = getAccessToken("admin", "edc-v-admin-secret", "management-api:read management-api:write identity-api:read identity-api:write").accessToken();
        given()
                .baseUri(BASE_URL)
                .contentType("application/json")
                .auth().oauth2(accessToken)
                .body(requestBody)
                .post("/cp/api/mgmt/v1alpha/participants")
                .then()
                .log().ifValidationFails()
                .statusCode(201);
    }

    private CreateParticipantContextResponse createParticipantInIdentityHub() {
        var template = loadResourceFile("create_participant_identityhub.json");
        var requestBody = template
                .replace("{{participant_context_id}}", participantContextId)
                .replace("{{participant_context_did}}", participantContextDid)
                .replace("{{participant_context_id_base64}}", participantContextIdBase64());

        return given()
                .baseUri(BASE_URL)
                .contentType("application/json")
                .header("x-api-key", API_ADMIN_KEY)
                .body(requestBody)
                .post("/cs/api/identity/v1alpha/participants")
                .then()
                .log().ifValidationFails()
                .statusCode(200)
                .extract()
                .body().as(CreateParticipantContextResponse.class);
    }

    private void createHolder() {
        given()
                .baseUri(BASE_URL)
                .contentType("application/json")
                .header("x-api-key", issuerApiKey)
                .body("""
                        {
                             "did": "%s",
                             "holderId": "%s",
                             "name": "%s tenant"
                         }""".formatted(participantContextDid, participantContextDid, participantContextId))
                .post("/issuer/admin/api/admin/v1alpha/participants/%s/holders".formatted(issuerIdBase64()))
                .then()
                .statusCode(201);
    }

    private String issuerIdBase64() {
        return Base64.getEncoder().encodeToString(issuerId.getBytes());
    }
}
