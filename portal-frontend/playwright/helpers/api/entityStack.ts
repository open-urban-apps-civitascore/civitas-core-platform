/**
 * Creates a complete entity stack (datastructure → version → datasource → dataset + pipeline)
 * where each entity is in AVAILABLE/READY state, suitable for testing status transition gating.
 *
 * The stack is built bottom-up: datastructure version must be AVAILABLE before datasource
 * can be published, and datasource must be AVAILABLE before dataset can be published.
 */
import { ApiClient } from './apiClient'
import { type TestResources } from './testSetup'

const buildUmlModel = (modelName: string, uri: string) =>
  [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<uml:Model xmi:version="20131001"',
    '  xmlns:xmi="http://www.omg.org/spec/XMI/20131001"',
    '  xmlns:uml="http://www.eclipse.org/uml2/5.0.0/UML"',
    `  xmi:id="_e2e_model" name="${modelName}" URI="${uri}">`,
    `  <packagedElement xmi:type="uml:Package" xmi:id="_e2e_pkg" name="e2e-pkg"`,
    '    URI="http://civitas.org/model/e2e-pkg">',
    '    <packagedElement xmi:type="uml:Class" xmi:id="_e2e_cls" name="E2EClass">',
    '      <ownedAttribute xmi:id="_e2e_attr" name="value" visibility="private">',
    '        <type xmi:type="uml:PrimitiveType"',
    '          href="http://www.omg.org/spec/UML/20131001/PrimitiveTypes.xmi#String"/>',
    '      </ownedAttribute>',
    '    </packagedElement>',
    '  </packagedElement>',
    '</uml:Model>',
  ].join('\n')

export type EntityStack = {
  datastructure: { id: string; name: string }
  version: { id: string }
  datasource: { id: string; name: string }
  dataset: { id: string; name: string }
  pipeline: { id: string }
}

/**
 * Creates a full entity stack with all entities published/available.
 *
 * Final states:
 * - DataStructure: DRAFT (parent status independent of version)
 * - DataStructureVersion: AVAILABLE
 * - DataSource: AVAILABLE
 * - Dataset: READY (published, not released — release triggers a saga)
 */
export const createAvailableEntityStack = async (
  adminApi: ApiClient,
  resources: TestResources,
  suffix = Date.now().toString(),
): Promise<EntityStack> => {
  // 1. Datastructure + version → publish version
  const datastructure = await adminApi.createDatastructure({
    name: `E2E-ds-${suffix}`,
    description: 'E2E test datastructure',
  })
  resources.datastructureIds.push(datastructure.id)

  const modelName = `E2EModel-${suffix}`
  const modelUri = `http://civitas.org/model/${modelName}/1.0.0`
  const version = await adminApi.createDatastructureVersion(datastructure.id, {
    version: '1.0.0',
    description: 'E2E version',
    modelAtlasUri: modelUri,
    modelName,
    model: buildUmlModel(modelName, modelUri),
  })
  await adminApi.publishDatastructureVersion(datastructure.id, version.id)
  await adminApi.publishDatastructure(datastructure.id)

  // 2. Datasource linked to the available version → publish
  const datasource = await adminApi.createDatasource({
    name: `E2E-src-${suffix}`,
    description: 'E2E test datasource',
    connectorType: 'MQTT',
    configuration: {
      urls: ['tcp://broker:1883'],
      topics: ['e2e/#'],
      qos: 1,
      keepalive: '30s',
      user: 'e2e',
      password: 'e2e',
      // eslint-disable-next-line @typescript-eslint/naming-convention -- MQTT connector config uses snake_case keys
      client_id: `e2e-${suffix}`,
      // eslint-disable-next-line @typescript-eslint/naming-convention -- MQTT connector config uses snake_case keys
      connect_timeout: '5s',
      tls: { enabled: false },
    },
    dataStructureVersionId: version.id,
  })
  resources.datasourceIds.push(datasource.id)
  await adminApi.publishDatasource(datasource.id)

  // 3. Dataset + pipeline with linked datasource → publish (DRAFT → READY)
  const dataset = await adminApi.createDataset({
    name: `E2E-dset-${suffix}`,
    description: 'E2E test dataset',
  })
  resources.datasetIds.push(dataset.id)

  const pipeline = await adminApi.createPipeline(dataset.id, {
    name: `E2E-pipeline-${suffix}`,
    description: 'E2E test pipeline',
    dataSourceIds: [datasource.id],
  })
  await adminApi.publishDataset(dataset.id)

  // 4. Unpublish dataset and datasource back to DRAFT so tests can enter edit mode.
  //    The datastructure + version stay AVAILABLE (can't unpublish while version is referenced).
  //    Order: dataset first (depends on datasource), then datasource.
  await adminApi.unpublishDataset(dataset.id)
  await adminApi.unpublishDatasource(datasource.id)

  return { datastructure, version, datasource, dataset, pipeline }
}

/**
 * Creates a standalone datastructure with an available version, suitable for
 * testing the datastructure status dropdown. Not linked to any datasource,
 * so the datastructure itself stays in DRAFT and can be edited.
 */
export const createDraftDatastructureWithAvailableVersion = async (
  adminApi: ApiClient,
  resources: TestResources,
  suffix = Date.now().toString(),
) => {
  const datastructure = await adminApi.createDatastructure({
    name: `E2E-ds-standalone-${suffix}`,
    description: 'E2E standalone datastructure',
  })
  resources.datastructureIds.push(datastructure.id)

  const modelName = `E2EStandalone-${suffix}`
  const modelUri = `http://civitas.org/model/${modelName}/1.0.0`
  const version = await adminApi.createDatastructureVersion(datastructure.id, {
    version: '1.0.0',
    description: 'E2E version',
    modelAtlasUri: modelUri,
    modelName,
    model: buildUmlModel(modelName, modelUri),
  })
  await adminApi.publishDatastructureVersion(datastructure.id, version.id)

  return { datastructure, version }
}
