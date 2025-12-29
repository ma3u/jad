--
--  Copyright (c) 2025 Metaform Systems, Inc.
--
--  This program and the accompanying materials are made available under the
--  terms of the Apache License, Version 2.0 which is available at
--  https://www.apache.org/licenses/LICENSE-2.0
--
--  SPDX-License-Identifier: Apache-2.0
--
--  Contributors:
--       Metaform Systems, Inc. - initial API and implementation
--

-- THIS SCHEMA HAS BEEN WRITTEN AND TESTED ONLY FOR POSTGRES

-- table: edc_certs
CREATE TABLE IF NOT EXISTS edc_certs
(
    id                VARCHAR PRIMARY KEY,
    metadata          JSONB default '{}',
    data              BYTEA
);
