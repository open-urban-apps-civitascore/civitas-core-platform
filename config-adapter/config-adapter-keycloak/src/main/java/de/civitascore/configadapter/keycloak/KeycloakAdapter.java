/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.keycloak;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.adapter.AbstractConfigAdapter;
import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.idm.ClientConfig;
import de.civitascore.configadapter.model.idm.GroupConfig;
import de.civitascore.configadapter.model.idm.IdmConfigValue;
import de.civitascore.configadapter.model.idm.RealmConfig;
import de.civitascore.configadapter.model.idm.RoleConfig;
import de.civitascore.configadapter.model.idm.UserConfig;
import java.util.Map;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keycloak adapter that processes configuration messages and manages Keycloak resources. Supports
 * CREATE, UPDATE, and DELETE operations for realms, clients, users, roles, and groups.
 *
 * <p>Delegates resource-specific operations to dedicated {@link KeycloakResourceHandler}
 * implementations, keeping this class focused on lifecycle management and event dispatching.
 */
public class KeycloakAdapter extends AbstractConfigAdapter {

  private static final String DEFAULT_SERVER_URL = "http://localhost:8080";

  private static final String CLIENT_ID_PROPERTY_KEY = "client.id";
  private static final String PASSWORD_PROPERTY_KEY = "password";
  private static final String USERNAME_PROPERTY_KEY = "username";
  private static final String INVITATION_CLIENT_ID_PROPERTY_KEY = "invitation.client.id";
  private static final String INVITATION_REDIRECT_URI_PROPERTY_KEY = "invitation.redirect.uri";
  private static final String REALM = "realm";
  private static final String URL_PROPERTY = "url";

  private static final Logger logger = LoggerFactory.getLogger(KeycloakAdapter.class);

  public static final String ADAPTER_NAME = "keycloak";

  private final ObjectMapper objectMapper;
  private Keycloak keycloakClient;
  private Map<ResourceType, KeycloakResourceHandler> handlers;

  public KeycloakAdapter() {
    this.objectMapper = new ObjectMapper();
    this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  @Override
  public void initialize(AdapterConfig config) {
    super.initialize(config);
    this.keycloakClient =
        KeycloakBuilder.builder()
            .serverUrl(getAdapterProperty(URL_PROPERTY, DEFAULT_SERVER_URL))
            .realm(getAdapterProperty(REALM, "master"))
            .username(getAdapterProperty(USERNAME_PROPERTY_KEY, "admin"))
            .password(getAdapterProperty(PASSWORD_PROPERTY_KEY, "admin"))
            .clientId(getAdapterProperty(CLIENT_ID_PROPERTY_KEY, "admin-cli"))
            .build();

    try {
      validateKeycloakConnection();
    } catch (FatalAdapterException e) {
      throw new IllegalStateException(e.getMessage(), e);
    }

    String invitationClientId = getAdapterProperty(INVITATION_CLIENT_ID_PROPERTY_KEY);
    String invitationRedirectUri = getAdapterProperty(INVITATION_REDIRECT_URI_PROPERTY_KEY);
    try {
      validateInvitationConfig(invitationClientId, invitationRedirectUri);
    } catch (FatalAdapterException e) {
      throw new IllegalStateException(e.getMessage(), e);
    }

    RoleSyncHelper roleSyncHelper = new RoleSyncHelper();
    GroupSyncHelper groupSyncHelper = new GroupSyncHelper();
    ResultPublisher resultPublisher =
        (event, successCode, resourceId) ->
            publishSuccessResult(event, successCode.toString(), resourceId);

    this.handlers =
        Map.of(
            ResourceType.REALM,
                new RealmResourceHandler(keycloakClient, objectMapper, resultPublisher),
            ResourceType.CLIENT,
                new ClientResourceHandler(keycloakClient, objectMapper, resultPublisher),
            ResourceType.USER,
                new UserResourceHandler(
                    keycloakClient,
                    objectMapper,
                    resultPublisher,
                    roleSyncHelper,
                    groupSyncHelper,
                    invitationClientId,
                    invitationRedirectUri),
            ResourceType.ROLE,
                new RoleResourceHandler(keycloakClient, objectMapper, resultPublisher),
            ResourceType.GROUP,
                new GroupResourceHandler(
                    keycloakClient, objectMapper, resultPublisher, roleSyncHelper));

    logger.info(
        "Keycloak adapter '{}' initialized for: {}",
        Encode.forJava(getName()),
        Encode.forJava(getAdapterProperty(URL_PROPERTY, DEFAULT_SERVER_URL)));
    if (invitationClientId != null && invitationRedirectUri != null) {
      logger.info(
          "Invitation redirect configured: clientId={}, redirectUri={}",
          Encode.forJava(invitationClientId),
          Encode.forJava(invitationRedirectUri));
    }
    logger.info(
        "Subscribed to {} Kafka topics: {}",
        getSubscribedTopics().size(),
        Encode.forJava(String.valueOf(getSubscribedTopics())));
  }

  @Override
  public String getName() {
    return ADAPTER_NAME;
  }

  @Override
  protected void doProcessConfigEvent(String topic, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    if (event == null) {
      logger.warn("Null event send to topic {}", Encode.forJava(topic));
      return;
    }
    Operation operation = event.payload().operation();
    String targetResource = event.payload().targetResource();
    String targetComponent = event.payload().targetComponent();

    logger.info(
        "Processing config event - Topic: {}, Operation: {}, TargetComponent: {}, TargetResource: {}",
        Encode.forJava(topic),
        operation,
        Encode.forJava(String.valueOf(targetComponent)),
        Encode.forJava(String.valueOf(targetResource)));

    String resourceId = extractResourceId(event);
    ResourceType type = ResourceType.fromString(targetComponent);

    logger.debug(
        "Resource info - Type: {}, Realm: {}, ID: {}",
        type,
        Encode.forJava(String.valueOf(targetResource)),
        Encode.forJava(String.valueOf(resourceId)));

    KeycloakResourceHandler handler = handlers.get(type);
    if (handler == null) {
      logger.warn("Unknown resource type: {}", type);
      throw new FatalAdapterException(AdapterErrorCode.INVALID_RESOURCE_TYPE, type);
    }

    switch (operation) {
      case CREATE -> handler.create(targetResource, event);
      case UPDATE -> handler.update(targetResource, resourceId, event);
      case DELETE -> handler.delete(targetResource, resourceId, event);
      default -> {
        logger.warn("Unknown operation: {}", operation);
        throw new FatalAdapterException(AdapterErrorCode.UNSUPPORTED_OPERATION, operation);
      }
    }
  }

  @Override
  protected String getResultType() {
    return IdmConfigValue.IDM_RESULT_TYPE;
  }

  private String extractResourceId(ConfigEvent event) throws FatalAdapterException {
    return switch (event.payload().config().value()) {
      case null -> null;
      case UserConfig userConfig -> userConfig.getId();
      case ClientConfig clientConfig -> clientConfig.getId();
      case RoleConfig roleConfig -> roleConfig.getName();
      case RealmConfig realmConfig -> realmConfig.getRealm();
      case GroupConfig groupConfig -> groupConfig.getId();
      default -> {
        logger.warn("Unknown ConfigValue class: {}", event.getClass());
        throw new FatalAdapterException(
            AdapterErrorCode.INVALID_RESOURCE_TYPE, event.getClass().getSimpleName());
      }
    };
  }

  @Override
  public void close() {
    if (keycloakClient != null) {
      keycloakClient.close();
      logger.info("Keycloak adapter closed");
    }
  }

  private void validateKeycloakConnection() throws FatalAdapterException {
    try {
      keycloakClient.serverInfo().getInfo();
      logger.info("Keycloak connection validated successfully");
    } catch (Exception e) {
      throw new FatalAdapterException(
          AdapterErrorCode.CONFIGURATION_ERROR,
          e,
          "Failed to connect to Keycloak or authenticate. "
              + "Please verify the Keycloak URL and admin credentials are correct.");
    }
  }

  private void validateInvitationConfig(String invitationClientId, String invitationRedirectUri)
      throws FatalAdapterException {
    boolean hasClientId = invitationClientId != null && !invitationClientId.isBlank();
    boolean hasRedirectUri = invitationRedirectUri != null && !invitationRedirectUri.isBlank();
    if (hasClientId != hasRedirectUri) {
      throw new FatalAdapterException(
          AdapterErrorCode.CONFIGURATION_ERROR,
          "Both 'keycloak.invitation.client.id' and 'keycloak.invitation.redirect.uri' "
              + "must be set together or neither should be set. "
              + "invitation.client.id="
              + invitationClientId
              + ", invitation.redirect.uri="
              + invitationRedirectUri);
    }
  }
}
