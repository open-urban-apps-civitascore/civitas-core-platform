import type { SchemaTree } from '../_components/mapping-editor/_types'

/**
 * The SensorThings (STA) envelope a FROST sink consumes — the fixed mapping target for pipelines
 * that write to FROST. This mirrors the adapter's closed `StaTargetCatalog` exactly (paths, types,
 * required flags, groups): the envelope is a fixed adapter interface, not a tenant-modelled
 * datastructure, so the target tree is a constant instead of being derived from a UML diagram.
 */

/** Fixed pseudo-target URN stored in the mapping config for FROST targets. */
export const STA_TARGET_URN = 'urn:core:sta:v1.1'

/** Display name of the fixed STA target (shown where a datastructure name would appear). */
export const STA_TARGET_NAME = 'SensorThings (STA)'

/**
 * One envelope group. Required-ness in the STA envelope is conditional — a group's required paths
 * (most importantly the find-or-create lookup keys `reference`/`name`) must be mapped only once the
 * group is touched at all — which the unconditional `targetRequiredFields` snapshot cannot express.
 * The pipeline validation rule `validateFrostMappingCoversStaGroups` owns that semantics.
 */
export interface StaGroup {
  /** The group's array path (a mapping may not target it directly — element-wise only). */
  arrayPath: string
  /** The element prefix every field path of the group starts with. */
  pathPrefix: string
  /** Paths that must all be assigned once any path of the group is assigned. */
  requiredPaths: string[]
}

export const STA_GROUPS: StaGroup[] = [
  {
    arrayPath: '$.things',
    pathPrefix: '$.things[',
    requiredPaths: ['$.things[].name', '$.things[].description', '$.things[].properties.reference'],
  },
  {
    arrayPath: '$.observations',
    pathPrefix: '$.observations[',
    requiredPaths: [
      '$.observations[].result',
      '$.observations[].parameters.reference',
      '$.observations[].parameters.name',
    ],
  },
]

/**
 * The static target field tree for the mapping editor. The `required` flags are the per-group
 * catalog flags (UI badges); the saved `targetRequiredFields` snapshot stays empty for STA targets
 * because required-ness is conditional per group (see {@link StaGroup}).
 *
 * `result` is STA `any`: it accepts every scalar type — the adapter serializes numbers/booleans
 * unquoted and everything else as a string.
 */
export const staTargetSchemaTree = (): SchemaTree => ({
  name: STA_TARGET_NAME,
  fields: [
    {
      path: '$.things',
      name: 'things',
      type: 'array',
      portType: 'array',
      required: false,
      children: [
        { path: '$.things[].name', name: 'name', type: 'str', portType: 'scalar', required: true },
        { path: '$.things[].description', name: 'description', type: 'str', portType: 'scalar', required: true },
        {
          path: '$.things[].properties',
          name: 'properties',
          type: 'object',
          portType: 'object',
          required: false,
          children: [
            {
              path: '$.things[].properties.reference',
              name: 'reference',
              type: 'str',
              portType: 'scalar',
              required: true,
            },
          ],
        },
      ],
    },
    {
      path: '$.observations',
      name: 'observations',
      type: 'array',
      portType: 'array',
      required: false,
      children: [
        { path: '$.observations[].result', name: 'result', type: 'any', portType: 'scalar', required: true },
        {
          path: '$.observations[].phenomenonTime',
          name: 'phenomenonTime',
          type: 'str',
          portType: 'scalar',
          required: false,
        },
        {
          path: '$.observations[].resultTime',
          name: 'resultTime',
          type: 'str',
          portType: 'scalar',
          required: false,
        },
        {
          path: '$.observations[].parameters',
          name: 'parameters',
          type: 'object',
          portType: 'object',
          required: false,
          children: [
            {
              path: '$.observations[].parameters.reference',
              name: 'reference',
              type: 'str',
              portType: 'scalar',
              required: true,
            },
            {
              path: '$.observations[].parameters.name',
              name: 'name',
              type: 'str',
              portType: 'scalar',
              required: true,
            },
          ],
        },
      ],
    },
  ],
})
