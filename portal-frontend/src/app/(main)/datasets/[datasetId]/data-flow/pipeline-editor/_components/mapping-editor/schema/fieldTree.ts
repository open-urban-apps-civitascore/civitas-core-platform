import type { FieldNode, SchemaTree } from '../_types'

/**
 * The paths of the required leaf fields. Recurses only into required objects/arrays, so an optional
 * container does not force its children to be mapped. Used to snapshot the target's required fields
 * on the mapping node for synchronous pipeline validation.
 */
export const requiredFieldPaths = (tree: SchemaTree): string[] => {
  const paths: string[] = []
  const walk = (fields: FieldNode[]) => {
    for (const field of fields) {
      if (!field.required) continue
      if (field.children && field.children.length > 0) {
        walk(field.children)
      } else {
        paths.push(field.path)
      }
    }
  }
  walk(tree.fields)
  return paths
}

/** Flattens a field tree into a path → field lookup. */
export const flattenTree = (tree: SchemaTree): Map<string, FieldNode> => {
  const map = new Map<string, FieldNode>()
  const walk = (fields: FieldNode[]) => {
    for (const field of fields) {
      map.set(field.path, field)
      if (field.children) walk(field.children)
    }
  }
  walk(tree.fields)
  return map
}

/**
 * Two object/array field subtrees are structurally compatible when every target
 * child has a source child with the EXACT same name and a matching type
 * (recursively for nested objects/arrays). Auto-mapping an object only makes
 * sense when the nested field names line up exactly, so name equality is required.
 */
export const objectFieldsCompatible = (source: FieldNode, target: FieldNode): boolean => {
  // Exact-type equality also covers geometries, which are first-class types now
  // (Point, Polygon, …), so a Point can't structurally auto-map onto a Polygon.
  if (source.type !== target.type) return false

  const targetChildren = target.children ?? []
  if (targetChildren.length === 0) return true

  const sourceByName = new Map((source.children ?? []).map(child => [child.name, child]))

  return targetChildren.every(targetChild => {
    const sourceChild = sourceByName.get(targetChild.name)
    if (!sourceChild) return false
    if (sourceChild.type !== targetChild.type) return false
    if (targetChild.children?.length) return objectFieldsCompatible(sourceChild, targetChild)
    return true
  })
}

/**
 * Resolves the source counterpart of a target leaf reached through a connected
 * ancestor object. `relativeName` is the dot-joined chain of field names from the
 * connected object down to the leaf. Returns the matching source field, or
 * undefined when no exact-name counterpart exists.
 */
export const resolveCoveredLeaf = (source: FieldNode, relativeNames: string[]): FieldNode | undefined => {
  let current: FieldNode | undefined = source
  for (const name of relativeNames) {
    current = current?.children?.find(child => child.name === name)
    if (!current) return undefined
  }
  return current
}
