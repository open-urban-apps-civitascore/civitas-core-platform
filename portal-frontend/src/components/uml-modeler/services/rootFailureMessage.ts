import type { RootResolutionFailure } from './umlContainment'

type Translate = (key: string, values?: Record<string, string>) => string

/**
 * Renders a {@link RootResolutionFailure} as user-facing text. Expects a translator scoped to the
 * `umlModeler` namespace so every export/save surface phrases the same failure identically.
 */
export const rootFailureMessage = (t: Translate, failure: RootResolutionFailure): string => {
  switch (failure.code) {
    case 'noRoot':
      return t('rootValidation.noRoot')
    case 'ambiguousRoot':
      return t('rootValidation.ambiguousRoot', { names: failure.candidateNames.join(', ') })
    case 'unreachable':
      return t('rootValidation.unreachable', {
        root: failure.rootName,
        names: failure.unreachableNames.join(', '),
      })
    default: {
      // A new RootResolutionFailure variant must fail the build here, not silently return undefined.
      const exhaustive: never = failure
      return exhaustive
    }
  }
}
