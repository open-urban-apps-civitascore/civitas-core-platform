/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage;

import de.civitascore.configadapter.exception.FatalAdapterException;
import java.util.List;

/**
 * Turns a raw (non-record) source payload into records. Inserted only when the source does not
 * declare {@link SourceCapability#EMITS_RECORDS} and the sink consumes {@link SinkInput#RECORDS}.
 */
public final class ConvertRecordStage implements TransformStage {

  @Override
  public StageResult build(BuildContext ctx) throws FatalAdapterException {
    Processor convert = ctx.loadProcessor(Fragment.CONVERT_RECORD, "success");
    return new StageResult(List.of(convert), List.of(convert));
  }
}
