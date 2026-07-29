import type { RootResolutionFailure } from '@/components/uml-modeler/services/umlContainment'
import type { DatastructureVersion } from '@/types/datastructures'

import type { SchemaTree } from '../_types'
import { umlDiagramToSchemaTree } from './adapter'
import { ModelResolutionError, modelToSchemaTree } from './modelAdapter'

export interface VersionSchemaTree {
  readonly tree: SchemaTree
  /**
   * The persisted model exists but is structurally unresolvable, so the tree was derived from the
   * UML diagram instead. The diagram tree is not the artifact the engine reads — the structure
   * should be fixed before deploying against it, and callers must surface this. `false` only
   * means no broken model was involved: a version without any model also yields `false` with a
   * diagram-derived tree.
   */
  readonly isModelBroken: boolean
  /**
   * Set when the tree came from the diagram and the diagram has no resolvable root (the state a
   * draft saved without a model is in). The tree is then empty; callers must surface the reason.
   */
  readonly diagramFailure: RootResolutionFailure | null
}

/**
 * Prefers the persisted JSON-Schema model — the artifact the engine adapters interpret — over the
 * UML diagram. The diagram remains the source for versions without a model (unsaved drafts) and
 * the flagged fallback for structurally broken models. Only {@link ModelResolutionError} engages
 * the fallback; walker bugs propagate.
 */
export const versionToSchemaTree = (
  version: Pick<DatastructureVersion, 'model' | 'styles'> | undefined,
  fallbackName: string,
): VersionSchemaTree => {
  let isModelBroken = false
  if (version?.model) {
    try {
      return { tree: modelToSchemaTree(version.model, fallbackName), isModelBroken: false, diagramFailure: null }
    } catch (error) {
      if (!(error instanceof ModelResolutionError)) throw error
      isModelBroken = true
      console.warn(`mapping editor: model of '${fallbackName}' unresolvable, falling back to the diagram tree`, error)
    }
  }
  const { tree, failure } = umlDiagramToSchemaTree(version?.styles ?? null, fallbackName)
  return { tree, isModelBroken, diagramFailure: failure }
}
