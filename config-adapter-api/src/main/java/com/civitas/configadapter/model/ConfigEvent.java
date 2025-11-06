package com.civitas.configadapter.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ConfigEvent(
    @JsonProperty("action") String action,
    @JsonProperty("realm") String realm,
    @JsonProperty("resourceType") String resourceType,
    @JsonProperty("resourceId") String resourceId,
    @JsonProperty("data") Object data
) {
}
