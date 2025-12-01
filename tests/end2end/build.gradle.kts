/*
 *  Copyright (c) 2023 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 *
 */

plugins {
    java
}

dependencies {
    runtimeOnly(libs.jackson.databind)
    testImplementation(libs.edc.spi.catalog)
    testImplementation(libs.edc.ih.spi.credentials)
    testImplementation(libs.edc.ih.spi.participantcontext)
    testImplementation(libs.edc.junit)
    testImplementation(libs.jackson.annotations)
    testImplementation(libs.awaitility)
    testImplementation(libs.restAssured)
}

edcBuild {
    publish.set(false)
}
