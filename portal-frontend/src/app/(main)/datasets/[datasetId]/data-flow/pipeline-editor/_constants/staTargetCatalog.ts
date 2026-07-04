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
