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

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class Orchestration {
    private String id;
    private String correlationId;
    private int state;
    private String stateTimestamp;
    private String createdTimestamp;
    private String orchestrationType;
    private List<Step> steps;
    private ProcessingData processingData;
    private Map<String, Object> outputData;
    private Map<String, Object> completed;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public String getStateTimestamp() {
        return stateTimestamp;
    }

    public void setStateTimestamp(String stateTimestamp) {
        this.stateTimestamp = stateTimestamp;
    }

    public String getCreatedTimestamp() {
        return createdTimestamp;
    }

    public void setCreatedTimestamp(String createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }

    public String getOrchestrationType() {
        return orchestrationType;
    }

    public void setOrchestrationType(String orchestrationType) {
        this.orchestrationType = orchestrationType;
    }

    public List<Step> getSteps() {
        return steps;
    }

    public void setSteps(List<Step> steps) {
        this.steps = steps;
    }

    public ProcessingData getProcessingData() {
        return processingData;
    }

    public void setProcessingData(ProcessingData processingData) {
        this.processingData = processingData;
    }

    public Map<String, Object> getOutputData() {
        return outputData;
    }

    public void setOutputData(Map<String, Object> outputData) {
        this.outputData = outputData;
    }

    public Map<String, Object> getCompleted() {
        return completed;
    }

    public void setCompleted(Map<String, Object> completed) {
        this.completed = completed;
    }

    public static class Step {
        private List<Activity> activities;

        public List<Activity> getActivities() {
            return activities;
        }

        public void setActivities(List<Activity> activities) {
            this.activities = activities;
        }
    }

    public static class Activity {
        private String id;
        private String type;
        private String discriminator;
        private List<String> dependsOn;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDiscriminator() {
            return discriminator;
        }

        public void setDiscriminator(String discriminator) {
            this.discriminator = discriminator;
        }

        public List<String> getDependsOn() {
            return dependsOn;
        }

        public void setDependsOn(List<String> dependsOn) {
            this.dependsOn = dependsOn;
        }
    }

    public static class ProcessingData {
        @JsonProperty("cfm.participant.id")
        private String participantId;
        @JsonProperty("cfm.vpa.data")
        private List<VpaData> vpaData;
        @JsonProperty("clientID.apiAccess")
        private String clientIdApiAccess;
        @JsonProperty("clientID.vaultAccess")
        private String clientIdVaultAccess;

        public String getParticipantId() {
            return participantId;
        }

        public void setParticipantId(String participantId) {
            this.participantId = participantId;
        }

        public List<VpaData> getVpaData() {
            return vpaData;
        }

        public void setVpaData(List<VpaData> vpaData) {
            this.vpaData = vpaData;
        }

        public String getClientIdApiAccess() {
            return clientIdApiAccess;
        }

        public void setClientIdApiAccess(String clientIdApiAccess) {
            this.clientIdApiAccess = clientIdApiAccess;
        }

        public String getClientIdVaultAccess() {
            return clientIdVaultAccess;
        }

        public void setClientIdVaultAccess(String clientIdVaultAccess) {
            this.clientIdVaultAccess = clientIdVaultAccess;
        }
    }

    public static class VpaData {
        private String cellId;
        private String externalCellId;
        private String id;
        private String vpaType;

        public String getCellId() {
            return cellId;
        }

        public void setCellId(String cellId) {
            this.cellId = cellId;
        }

        public String getExternalCellId() {
            return externalCellId;
        }

        public void setExternalCellId(String externalCellId) {
            this.externalCellId = externalCellId;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getVpaType() {
            return vpaType;
        }

        public void setVpaType(String vpaType) {
            this.vpaType = vpaType;
        }
    }

}
