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

package org.eclipse.edc.virtualized.dataplane.cert;

import org.eclipse.edc.connector.dataplane.iam.service.DataPlaneAuthorizationServiceImpl;
import org.eclipse.edc.connector.dataplane.spi.Endpoint;
import org.eclipse.edc.connector.dataplane.spi.edr.EndpointDataReferenceServiceRegistry;
import org.eclipse.edc.connector.dataplane.spi.iam.DataPlaneAuthorizationService;
import org.eclipse.edc.connector.dataplane.spi.iam.PublicEndpointGeneratorService;
import org.eclipse.edc.runtime.metamodel.annotation.Configuration;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.runtime.metamodel.annotation.Settings;
import org.eclipse.edc.spi.system.Hostname;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.eclipse.edc.virtualized.dataplane.cert.api.CertExchangePublicController;
import org.eclipse.edc.virtualized.dataplane.cert.api.CertInternalExchangeController;
import org.eclipse.edc.virtualized.dataplane.cert.store.CertStore;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;

import static org.eclipse.edc.virtualized.dataplane.cert.CertExchangeExtension.NAME;

@Extension(NAME)
public class CertExchangeExtension implements ServiceExtension {
    public static final String NAME = "Cert Exchange Extension";
    public static final String API_CONTEXT = "certs";
    private static final int DEFAULT_CERTS_PORT = 8186;
    private static final String DEFAULT_CERTS_PATH = "/api/data";


    @Setting(description = "Base url of the public public API endpoint without the trailing slash. This should point to the public certs endpoint configured.",
            required = false,
            key = "edc.dataplane.api.certs.baseurl", warnOnMissingConfig = true)
    private String publicBaseUrl;

    @Configuration
    private CertApiConfiguration apiConfiguration;

    @Inject
    private Hostname hostname;

    @Inject
    private PortMappingRegistry portMappingRegistry;

    @Inject
    private DataPlaneAuthorizationService authorizationService;
    @Inject
    private PublicEndpointGeneratorService generatorService;

    @Inject
    private EndpointDataReferenceServiceRegistry endpointDataReferenceServiceRegistry;

    @Inject
    private WebService webService;

    @Inject
    private CertStore certStore;

    @Inject
    private TransactionContext transactionContext;

    @Override
    public void initialize(ServiceExtensionContext context) {
        var portMapping = new PortMapping(API_CONTEXT, apiConfiguration.port(), apiConfiguration.path());
        portMappingRegistry.register(portMapping);

        if (publicBaseUrl == null) {
            publicBaseUrl = "http://%s:%d%s".formatted(hostname.get(), portMapping.port(), portMapping.path());
            context.getMonitor().warning("The public API endpoint was not explicitly configured, the default '%s' will be used.".formatted(publicBaseUrl));
        }
        var endpoint = Endpoint.url(publicBaseUrl);
        generatorService.addGeneratorFunction("HttpCertData", dataAddress -> endpoint);
        webService.registerResource(API_CONTEXT, new CertExchangePublicController(authorizationService, certStore, transactionContext));
        webService.registerResource("control", new CertInternalExchangeController(certStore, transactionContext));

        if (authorizationService instanceof DataPlaneAuthorizationServiceImpl dpAuthService) {
            endpointDataReferenceServiceRegistry.register("HttpCertData", dpAuthService);
        }
    }

    @Settings
    record CertApiConfiguration(
            @Setting(key = "web.http." + API_CONTEXT + ".port", description = "Port for " + API_CONTEXT + " api context", defaultValue = DEFAULT_CERTS_PORT + "")
            int port,
            @Setting(key = "web.http." + API_CONTEXT + ".path", description = "Path for " + API_CONTEXT + " api context", defaultValue = DEFAULT_CERTS_PATH)
            String path
    ) {

    }
}
