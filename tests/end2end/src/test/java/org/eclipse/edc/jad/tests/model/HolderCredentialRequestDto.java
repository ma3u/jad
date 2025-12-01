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

package org.eclipse.edc.jad.tests.model;

import org.eclipse.edc.identityhub.spi.credential.request.model.RequestedCredential;

import java.util.List;

public record HolderCredentialRequestDto(String issuerDid, String holderPid, String issuerPid, String status,
                                         List<RequestedCredential> typesAndFormats) {
}