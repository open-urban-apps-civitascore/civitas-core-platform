/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2026 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.keycloak;

public enum ErrorCode {
  UNKNOWN_RESOURCE_TYPE,
  UNSUPPORTED_OPERATION,
  PROCESSING_ERROR,
  REALM_CREATE_FAILED,
  REALM_UPDATE_FAILED,
  REALM_DELETE_FAILED,
  CLIENT_CREATE_FAILED,
  CLIENT_UPDATE_FAILED,
  CLIENT_DELETE_FAILED,
  USER_CREATE_FAILED,
  USER_UPDATE_FAILED,
  USER_DELETE_FAILED,
  ROLE_CREATE_FAILED,
  ROLE_UPDATE_FAILED,
  ROLE_DELETE_FAILED,
  GROUP_CREATE_FAILED,
  GROUP_DELETE_FAILED,
  GROUP_UPDATE_FAILED;
}
