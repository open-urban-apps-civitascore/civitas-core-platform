/**
 * The SensorThings (STA) envelope paths a mapping may target when it feeds a FROST sink — a mirror
 * of the deploy engine's closed `StaTargetCatalog`, used by pipeline validation only. The target
 * datastructure itself stays a normal, user-modelled structure; the engine accepts exactly these
 * paths and rejects everything else at deploy, so validation surfaces the same constraints at edit
 * time.
 */

/**
 * One envelope group. Required-ness in the STA envelope is conditional — a group's required paths
 * (most importantly the find-or-create lookup keys `reference`/`name`) must be mapped only once the
 * group is touched at all — which the unconditional `targetRequiredFields` snapshot cannot express.
 * The pipeline validation rule `validateFrostMappingCoversStaGroups` owns that semantics.
 */
export interface StaGroup {
  /** The group's array path (a mapping may not target it directly — element-wise only). */
  readonly arrayPath: string
  /** The element prefix every field path of the group starts with. */
  readonly pathPrefix: string
  /** Paths that must all be assigned once any path of the group is assigned. */
  readonly requiredPaths: readonly string[]
  /** Paths the engine accepts but does not demand. */
  readonly optionalPaths: readonly string[]
}

export const STA_GROUPS: readonly StaGroup[] = [
  {
    arrayPath: '$.things',
    pathPrefix: '$.things[',
    requiredPaths: ['$.things[].name', '$.things[].description', '$.things[].properties.reference'],
    optionalPaths: [],
  },
  {
    arrayPath: '$.observations',
    pathPrefix: '$.observations[',
    requiredPaths: [
      '$.observations[].result',
      '$.observations[].parameters.reference',
      '$.observations[].parameters.name',
    ],
    optionalPaths: ['$.observations[].phenomenonTime', '$.observations[].resultTime'],
  },
]

/**
 * Every target path the engine's envelope compiler accepts — anything else fails the deploy saga,
 * so validation rejects it at edit time.
 */
export const STA_ALLOWED_TARGET_PATHS: ReadonlySet<string> = new Set(
  STA_GROUPS.flatMap(group => [...group.requiredPaths, ...group.optionalPaths]),
)
