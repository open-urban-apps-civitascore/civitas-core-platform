package com.civitas.configadapter.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfigEvent (
    @JsonProperty("metadata") Metadata metadata,
    @JsonProperty("payload") Payload payload
) {}
