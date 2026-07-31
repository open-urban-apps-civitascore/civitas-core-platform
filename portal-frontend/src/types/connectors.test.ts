import { CONNECTOR_TYPES } from '@/const/connectors'

import { ConnectorApiToFormSchema, ConnectorFormToApiSchema, MqttLooseSchema, MqttStrictSchema } from './connectors'

const loose = (urls: string, tls: boolean) => MqttLooseSchema.safeParse({ urls, tls })

const strict = (urls: string, tls: boolean) =>
  MqttStrictSchema.safeParse({ urls, tls, topics: 'sensors/+/temperature', qos: 1 })

describe.each([
  ['loose', loose],
  ['strict', strict],
])('MQTT %s schema broker URL validation', (_name, parse) => {
  it.each(['tcp://broker.example:1883', 'ws://broker.example:8080/mqtt', 'mqtt://broker.example:1883'])(
    'accepts plaintext URL %s when TLS is disabled',
    url => {
      expect(parse(url, false).success).toBe(true)
    },
  )

  it.each(['ssl://broker.example:8883', 'mqtts://broker.example:8883', 'wss://broker.example/mqtt'])(
    'accepts secure URL %s when TLS is enabled',
    url => {
      expect(parse(url, true).success).toBe(true)
    },
  )

  it.each([
    ['tcp://broker.example:1883', true],
    ['ws://broker.example/mqtt', true],
    ['mqtt://broker.example:1883', true],
    ['ssl://broker.example:8883', false],
    ['mqtts://broker.example:8883', false],
    ['wss://broker.example/mqtt', false],
  ])('rejects URL %s with tls=%s', (url, tls) => {
    const result = parse(url as string, tls as boolean)
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues).toContainEqual(
        expect.objectContaining({ path: ['urls'], message: 'datasources.errors.brokerTlsMismatch' }),
      )
    }
  })

  it('rejects unknown schemes', () => {
    const result = parse('https://broker.example/mqtt', true)
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues).toContainEqual(
        expect.objectContaining({ path: ['urls'], message: 'datasources.errors.unsupportedBrokerScheme' }),
      )
    }
  })

  it('checks every URL in a broker list', () => {
    expect(parse('ssl://one.example:8883, MQTTS://two.example:8883, wss://three.example/mqtt', true).success).toBe(true)
    expect(parse('tcp://one.example:1883, ssl://two.example:8883', false).success).toBe(false)
  })
})

describe('MQTT connector transformations', () => {
  it('maps the form TLS switch to tls.enabled', () => {
    const result = ConnectorFormToApiSchema.parse({
      connectorType: CONNECTOR_TYPES.MQTT,
      configuration: { urls: 'mqtts://broker.example:8883', tls: true },
    })

    expect(result.configuration.tls).toEqual({ enabled: true })
  })

  it('maps tls.enabled from the API to the form switch', () => {
    const result = ConnectorApiToFormSchema.parse({
      connectorType: CONNECTOR_TYPES.MQTT,
      configuration: { urls: ['ssl://broker.example:8883'], tls: { enabled: true } },
    })

    expect(result.configuration.tls).toBe(true)
  })
})
