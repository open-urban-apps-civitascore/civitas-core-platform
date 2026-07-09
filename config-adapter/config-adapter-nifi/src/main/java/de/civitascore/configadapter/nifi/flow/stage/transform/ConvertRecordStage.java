/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage.transform;

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.nifi.flow.stage.BuildContext;
import de.civitascore.configadapter.nifi.flow.stage.Fragment;
import de.civitascore.configadapter.nifi.flow.stage.PayloadForm;
import de.civitascore.configadapter.nifi.flow.stage.Processor;
import de.civitascore.configadapter.nifi.flow.stage.StageResult;
import de.civitascore.configadapter.nifi.flow.stage.TransformStage;
import java.util.List;

/**
 * Turns a raw (non-record) source payload into records. Inserted structurally, never user-modelled:
 * only when the source emits a {@link PayloadForm#CONVERTIBLE_TO_RECORDS} form and the sink
 * consumes {@link PayloadForm#RECORDS}.
 */
public final class ConvertRecordStage implements TransformStage {

  @Override
  public StageResult build(BuildContext ctx) throws FatalAdapterException {
    Processor convert = ctx.loadProcessor(Fragment.CONVERT_RECORD, "success");
    return new StageResult(List.of(convert), List.of(convert));
  }
}
