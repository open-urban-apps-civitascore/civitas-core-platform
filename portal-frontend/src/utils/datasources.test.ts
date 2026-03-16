import { describe, expect, it, vi } from 'vitest'

import { getSelectedDatastructureVersion } from './datasources'

describe('getSelectedDatastructureVersion', () => {
  it('returns datastructureId and versionId for a single selected row', () => {
    const selection = {
      'datastructure-id/version-id': true,
    }

    const result = getSelectedDatastructureVersion(selection)

    expect(result).toEqual({
      datastructureId: 'datastructure-id',
      versionId: 'version-id',
    })
  })

  it('returns null ids when no row is selected', () => {
    const result = getSelectedDatastructureVersion({})

    expect(result).toEqual({
      datastructureId: null,
      versionId: null,
    })
  })

  it('returns null ids and logs an error when multiple rows are selected', () => {
    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    const selection = {
      'datastructure-id-1/version-id-1': true,
      'datastructure-id-2/version-id-2': true,
    }

    const result = getSelectedDatastructureVersion(selection)

    expect(result).toEqual({
      datastructureId: null,
      versionId: null,
    })
    expect(consoleErrorSpy).toHaveBeenCalledWith(
      'Invalid datastructure version selection: expected exactly one selected row.',
      {
        selection,
      },
    )

    consoleErrorSpy.mockRestore()
  })

  it('returns null ids and logs an error when the selected row id has too many segments', () => {
    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    const selection = {
      'datastructure-id/version-id/unexpected': true,
    }

    const result = getSelectedDatastructureVersion(selection)

    expect(result).toEqual({
      datastructureId: null,
      versionId: null,
    })
    expect(consoleErrorSpy).toHaveBeenCalledWith('Invalid datastructure version row id format.', {
      selectedId: 'datastructure-id/version-id/unexpected',
      selection,
    })

    consoleErrorSpy.mockRestore()
  })

  it('returns null ids and logs an error when the selected row id is missing a versionId', () => {
    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    const selection = {
      'datastructure-id/': true,
    }

    const result = getSelectedDatastructureVersion(selection)

    expect(result).toEqual({
      datastructureId: null,
      versionId: null,
    })
    expect(consoleErrorSpy).toHaveBeenCalledWith('Invalid datastructure version row id format.', {
      selectedId: 'datastructure-id/',
      selection,
    })

    consoleErrorSpy.mockRestore()
  })
})
