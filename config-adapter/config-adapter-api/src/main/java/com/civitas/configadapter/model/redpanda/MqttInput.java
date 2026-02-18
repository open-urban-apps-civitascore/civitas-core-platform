/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.model.redpanda;

import com.civitas.configadapter.model.AbstractApiModel;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** MQTT source input configuration for a RedPanda Connect pipeline. */
public final class MqttInput extends AbstractApiModel {

  private List<String> urls;
  private List<String> topics;

  @JsonProperty("client_id")
  private String clientId;

  private Integer qos;

  @JsonProperty("connect_timeout")
  private String connectTimeout;

  private Integer keepalive;
  private String username;
  private String password;

  public MqttInput() {}

  public List<String> getUrls() {
    return urls;
  }

  public void setUrls(List<String> urls) {
    this.urls = urls;
  }

  public List<String> getTopics() {
    return topics;
  }

  public void setTopics(List<String> topics) {
    this.topics = topics;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  public Integer getQos() {
    return qos;
  }

  public void setQos(Integer qos) {
    this.qos = qos;
  }

  public String getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(String connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public Integer getKeepalive() {
    return keepalive;
  }

  public void setKeepalive(Integer keepalive) {
    this.keepalive = keepalive;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  @Override
  public Map<String, Object> toApiMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (urls != null) map.put("urls", urls);
    if (topics != null) map.put("topics", topics);
    if (clientId != null) map.put("client_id", clientId);
    if (qos != null) map.put("qos", qos);
    if (connectTimeout != null) map.put("connect_timeout", connectTimeout);
    if (keepalive != null) map.put("keepalive", keepalive);
    if (username != null) map.put("username", username);
    if (password != null) map.put("password", password);
    additionalProperties().forEach(map::putIfAbsent);
    return Collections.unmodifiableMap(map);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (MqttInput) obj;
    return Objects.equals(this.urls, that.urls)
        && Objects.equals(this.topics, that.topics)
        && Objects.equals(this.clientId, that.clientId)
        && Objects.equals(this.qos, that.qos)
        && Objects.equals(this.connectTimeout, that.connectTimeout)
        && Objects.equals(this.keepalive, that.keepalive)
        && Objects.equals(this.username, that.username)
        && Objects.equals(this.password, that.password)
        && Objects.equals(this.additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        urls,
        topics,
        clientId,
        qos,
        connectTimeout,
        keepalive,
        username,
        password,
        additionalProperties());
  }

  @Override
  public String toString() {
    return "MqttInput["
        + "urls="
        + urls
        + ", topics="
        + topics
        + ", clientId="
        + clientId
        + ", qos="
        + qos
        + ", password="
        + (password != null ? "[PRESENT]" : "null")
        + ", additionalProperties="
        + additionalProperties().keySet()
        + ']';
  }
}
