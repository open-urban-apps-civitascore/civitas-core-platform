package de.civitascore.portal.service.removal;

import java.util.UUID;

/** The link from a Pipeline to a Data sink it writes into. */
public record PipelineDataSinkLink(UUID pipelineId, UUID dataSinkId) {}
