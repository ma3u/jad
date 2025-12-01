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
import org.eclipse.edc.jad.tests.model.CatalogResponse;
import org.eclipse.edc.junit.annotations.EndToEndTest;
import org.eclipse.edc.spi.monitor.ConsoleMonitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.jad.tests.KeycloakApi.createKeycloakAdminToken;
import static org.eclipse.edc.jad.tests.KeycloakApi.createKeycloakUser;
import static org.eclipse.edc.jad.tests.KeycloakApi.getAccessToken;

/**
 * This test class executes a series of REST requests against several components to verify that an end-to-end
 * data transfer works. It assumes that the deployment to a local KinD cluster has already been performed, but no other
 * manipulation of the cluster has been done.
 * <p>
 */
@EndToEndTest
public class DataTransferTest {

    public static final String ISSUER_CLIENT_ID = "issuer";
    public static final String ISSUER_CLIENT_SECRET = "issuer-secret";

    static final String BASE_URL = "http://127.0.0.1";
    static final String API_ADMIN_KEY = "c3VwZXItdXNlcg==.c3VwZXItc2VjcmV0LWtleQo=";

    static String loadResourceFile(String resourceName) {
        try (var is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
            if (is == null) {
                throw new RuntimeException("Resource not found: " + resourceName);
            }
            return new String(is.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testDataTransfer() {
        var monitor = new ConsoleMonitor(ConsoleMonitor.Level.DEBUG, true);
        var kcAdminToken = createKeycloakAdminToken();

        //create issuer user in KC
        monitor.withPrefix("Issuer").info("Creating issuer user in Keycloak");
        createKeycloakUser(ISSUER_CLIENT_ID, ISSUER_CLIENT_ID, ISSUER_CLIENT_SECRET, "participant", kcAdminToken);
        var issuerTenant = createIssuerTenant();
        var participantIdBase64 = Base64.getEncoder().encodeToString(ISSUER_CLIENT_ID.getBytes());
        monitor.withPrefix("Issuer").info("Creating attestation and credential definitions");
        var attestationDefId = createAttestationDefinition(participantIdBase64, issuerTenant.apiKey());
        var credentialDefId = createCredentialDefId(attestationDefId, participantIdBase64, issuerTenant.apiKey());

        // onboard consumer
        monitor.info("Onboarding consumer");
        var po = new ParticipantOnboarding("consumer", "did:web:identityhub.edc-v.svc.cluster.local%3A7083:consumer", ISSUER_CLIENT_ID, issuerTenant.apiKey(), monitor.withPrefix("Consumer"));
        po.execute(credentialDefId);

        // onboard provider
        monitor.info("Onboarding provider");
        var providerPo = new ParticipantOnboarding("provider", "did:web:identityhub.edc-v.svc.cluster.local%3A7083:provider", ISSUER_CLIENT_ID, issuerTenant.apiKey(), monitor.withPrefix("Provider"));
        providerPo.execute(credentialDefId);

        // perform data transfer
        monitor.info("Starting data transfer");
        var accessToken = getAccessToken("consumer", "consumer-secret", "management-api:read");
        var catalog = given()
                .baseUri(BASE_URL)
                .auth().oauth2(accessToken.accessToken())
                .contentType("application/json")
                .body("""
                        {
                          "counterPartyDid": "did:web:identityhub.edc-v.svc.cluster.local%3A7083:provider"
                        }
                        """)
                .post("/cp/api/mgmt/v1alpha/participants/consumer/catalog")
                .then()
                .statusCode(200)
                .extract().body()
                .as(CatalogResponse.class);

        monitor.info("Catalog received, starting data transfer");
        var offerId = catalog.datasets().get(0).offers().get(0).id();
        assertThat(offerId).isNotNull();

        //download dummy data
        var jsonResponse = given()
                .baseUri(BASE_URL)
                .auth().oauth2(getAccessToken("consumer", "consumer-secret", "management-api:write").accessToken())
                .body("""
                        {
                            "providerId":"did:web:identityhub.edc-v.svc.cluster.local%%3A7083:provider",
                            "policyId": "%s"
                        }
                        """.formatted(offerId))
                .contentType("application/json")
                .post("/cp/api/mgmt/v1alpha/participants/consumer/data")
                .then()
                .statusCode(200)
                .extract().body().asPrettyString();
        assertThat(jsonResponse).isNotNull();
    }

    private String createCredentialDefId(String attestationDefId, String participantIdBase64, String apiKey) {
        var template = loadResourceFile("membership_def.json");

        var id = UUID.randomUUID().toString();
        template = template.replace("{{attestation_id}}", attestationDefId);
        template = template.replace("{{id}}", id);

        given()
                .baseUri(BASE_URL)
                .header("x-api-key", apiKey)
                .contentType("application/json")
                .body(template)
                .post("/issuer/admin/api/admin/v1alpha/participants/%s/credentialdefinitions".formatted(participantIdBase64))
                .then()
                .log().ifValidationFails()
                .statusCode(201);
        return id;
    }

    private String createAttestationDefinition(String participantIdBase64, String apiKey) {
        var id = UUID.randomUUID().toString();
        var body = """
                {
                  "attestationType": "membership",
                  "configuration": {},
                  "id": "%s"
                }
                """.formatted(id);
        given()
                .baseUri(BASE_URL)
                .header("x-api-key", apiKey)
                .contentType("application/json")
                .body(body)
                .post("/issuer/admin/api/admin/v1alpha/participants/%s/attestations".formatted(participantIdBase64))
                .then()
                .log().ifValidationFails()
                .statusCode(201);
        return id;
    }

    private CreateParticipantContextResponse createIssuerTenant() {
        var template = loadResourceFile("create_participant_issuerservice.json");

        template = template.replace("{{issuer_clientId}}", ISSUER_CLIENT_ID);
        template = template.replace("{{issuer_clientSecret}}", ISSUER_CLIENT_SECRET);

        return given()
                .baseUri(BASE_URL)
                .header("x-api-key", API_ADMIN_KEY)
                .contentType("application/json")
                .body(template)
                .post("/issuer/cs/api/identity/v1alpha/participants")
                .then()
                .statusCode(200)
                .extract()
                .body().as(CreateParticipantContextResponse.class);
    }
}
