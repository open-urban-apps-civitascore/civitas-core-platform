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
