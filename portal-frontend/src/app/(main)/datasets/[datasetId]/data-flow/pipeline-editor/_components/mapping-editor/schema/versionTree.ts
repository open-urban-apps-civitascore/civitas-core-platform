import type { DatastructureVersion } from '@/types/datastructures'

import type { SchemaTree } from '../_types'
import { umlDiagramToSchemaTree } from './adapter'
import { ModelResolutionError, modelToSchemaTree } from './modelAdapter'

export interface VersionSchemaTree {
  tree: SchemaTree
  /**
   * The persisted model exists but is structurally unresolvable, so the tree was derived from the
   * UML diagram instead. The two snapshots may diverge — mappings drawn against the diagram tree
   * can miss the record shape the engine derives from the model, so callers must surface this.
   */
  isModelBroken: boolean
}

/**
 * Prefers the persisted JSON-Schema model — the artifact the engine adapters interpret, whose
 * document root is the data structure itself — over the UML diagram. The diagram remains the
 * source for versions without a model (unsaved drafts) and the flagged fallback for structurally
 * broken models. Only {@link ModelResolutionError} engages the fallback; walker bugs propagate.
 */
export const versionToSchemaTree = (
  version: Pick<DatastructureVersion, 'model' | 'styles'> | undefined,
  fallbackName: string,
): VersionSchemaTree => {
  let isModelBroken = false
  if (version?.model) {
    try {
      return { tree: modelToSchemaTree(version.model, fallbackName), isModelBroken: false }
    } catch (error) {
      if (!(error instanceof ModelResolutionError)) throw error
      isModelBroken = true
      console.warn(`mapping editor: model of '${fallbackName}' unresolvable, falling back to the diagram tree`, error)
    }
  }
  return { tree: umlDiagramToSchemaTree(version?.styles ?? null, fallbackName), isModelBroken }
}
