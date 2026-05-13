/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.flowable.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Map;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlowableEngineFactoryTest {

  private ProcessEngine processEngine;

  @BeforeEach
  void setUp() {
    processEngine = FlowableEngineFactory.createWithH2();
  }

  @AfterEach
  void tearDown() {
    if (processEngine != null) {
      processEngine.close();
    }
  }

  @Test
  void shouldCreateFunctioningProcessEngine() {
    assertNotNull(processEngine);
    assertNotNull(processEngine.getRuntimeService());
    assertNotNull(processEngine.getRepositoryService());
    assertNotNull(processEngine.getHistoryService());
    assertNotNull(processEngine.getManagementService());
  }

  @Test
  void shouldDeployAndStartMinimalProcess() {
    RepositoryService repositoryService = processEngine.getRepositoryService();
    RuntimeService runtimeService = processEngine.getRuntimeService();

    String bpmnXml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     targetNamespace="http://civitas.de/test">
          <process id="testProcess" isExecutable="true">
            <startEvent id="start"/>
            <sequenceFlow sourceRef="start" targetRef="end"/>
            <endEvent id="end"/>
          </process>
        </definitions>
        """;

    Deployment deployment =
        repositoryService.createDeployment().addString("test.bpmn20.xml", bpmnXml).deploy();

    assertNotNull(deployment);
    assertEquals(1, repositoryService.createProcessDefinitionQuery().count());

    ProcessInstance instance = runtimeService.startProcessInstanceByKey("testProcess");
    assertNotNull(instance);
  }

  @Test
  void shouldMakeBeansAvailableToEngine() {
    var testBean = Map.of("key", "value");
    processEngine.close();

    processEngine = FlowableEngineFactory.createWithH2(Map.of("testBean", testBean));

    Object retrieved = processEngine.getProcessEngineConfiguration().getBeans().get("testBean");
    assertSame(testBean, retrieved);
  }

  @Test
  void shouldHaveAsyncExecutorDisabledForTestEngines() {
    assertNotNull(processEngine.getProcessEngineConfiguration().getAsyncExecutor());
  }
}
