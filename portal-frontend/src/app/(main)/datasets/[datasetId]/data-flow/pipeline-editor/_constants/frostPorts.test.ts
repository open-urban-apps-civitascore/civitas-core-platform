import { FrostDataSinkSchema } from '@/generated/core/datasink'

import { FROST_PORT_LOGIC, FROST_SINK_PORTS, isFrostSinkPort, logicOf, portsOfLogic } from './frostPorts'

describe('frostPorts', () => {
  it('takes the set of ports from the generated schema', () => {
    // The editor keeps no list of its own: the CORE DataSink schema publishes the ports, and the
    // backend validates against the same document.
    expect(FROST_SINK_PORTS).toEqual(FrostDataSinkSchema.shape.port.unwrap().options)
  })

  it('sorts every port into a logic class', () => {
    for (const port of FROST_SINK_PORTS) {
      expect(FROST_PORT_LOGIC).toContain(logicOf(port))
    }
  })

  it('lists every port in exactly one group', () => {
    const grouped = FROST_PORT_LOGIC.flatMap(logic => portsOfLogic(logic))

    // A port the schema publishes but no group claims would vanish from the list, and the modeller
    // would read that as a platform that cannot write it.
    expect([...grouped].sort()).toEqual([...FROST_SINK_PORTS].sort())
  })

  it('recognises a stored value that names a port', () => {
    expect(isFrostSinkPort('ThingTree')).toBe(true)
    expect(isFrostSinkPort('Everything')).toBe(false)
    expect(isFrostSinkPort(undefined)).toBe(false)
  })
})
