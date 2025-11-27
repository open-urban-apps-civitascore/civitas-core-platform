/**
 * Copyright (c) 2012 - 2025 Data In Motion and others. All rights reserved.
 *
 * <p>This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * <p>SPDX-License-Identifier: EPL-2.0
 *
 * <p>Contributors: Data In Motion - initial API and implementation
 */
package com.civitas.configadapter.adapter;

import com.civitas.configadapter.ConfigBase;
import com.civitas.configadapter.messaging.EventPublisher;
import com.civitas.configadapter.model.ConfigEvent;
import java.util.List;

public interface ConfigAdapter extends ConfigBase {

  void processConfigEvent(String topic, ConfigEvent event);

  List<String> getSubscribedTopics();

  void setEventPublisher(EventPublisher publisher);
}
