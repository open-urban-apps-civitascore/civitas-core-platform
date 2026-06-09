import type { FieldNode, SchemaTree } from '../_types'

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
