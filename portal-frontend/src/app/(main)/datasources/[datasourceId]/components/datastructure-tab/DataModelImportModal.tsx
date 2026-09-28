import { DialogProps } from '@radix-ui/react-dialog'
import {
  CellContext,
  createColumnHelper,
  getCoreRowModel,
  getExpandedRowModel,
  getSortedRowModel,
  PaginationState,
  RowSelectionState,
  SortingState,
  useReactTable,
} from '@tanstack/react-table'
import { CircleCheckBig, CircleDashed } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'

import { useGetDatastructures } from '@/app/services/api/datastructures/clientRequests'
import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { DataTable } from '@/components/table/DataTable'
import { ExpanderCell } from '@/components/table/expander-cell/ExpanderCell'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useQueryParams } from '@/hooks/use-query-params'
import { DATASTRUCTURE_STATUS_TYPES, DatastructuresListData } from '@/types/datastructures'
import { mapDatastructuresApiToListData } from '@/utils/datastructures'
import { isPageIndexHigherThanTotalPages, resolveUpdater } from '@/utils/table'

export interface SelectedDatastructureVersion {
  datastructureId: string
  name: string
  version: string
}

interface DataModelImportModalProps extends DialogProps {
  selectedVersion: string | null
  datasourceTitle: string
  onSelectVersion: (selection: RowSelectionState, selectedVersion?: SelectedDatastructureVersion) => void
  isUpdating?: boolean
  // If true, the import button is enabled when a saved datastructure gets unselected
  canRemoveSelection?: boolean
}

export const DataModelImportModal = (props: DataModelImportModalProps) => {
  const {
    selectedVersion,
    datasourceTitle,
    open,
    onOpenChange = () => {},
    onSelectVersion,
    isUpdating = false,
    canRemoveSelection = true,
  } = props
  const t = useTranslations('datasources.dataModel.importModal')
  const tCommon = useTranslations('common')
  const tDatastructures = useTranslations('datastructures')
  const tVersion = useTranslations('datastructureVersions')
  const [pageIndex, setPageIndex] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [sorting, setSorting] = useState<SortingState>([])
  const [searchString, setSearchString] = useState('')
  const [selection, setSelection] = useState<RowSelectionState>({})
  const { getApiRequestParams } = useQueryParams()

  useEffect(() => {
    setSelection(open && selectedVersion ? { [selectedVersion]: true } : {})
  }, [open, selectedVersion])

  const apiParams = getApiRequestParams({ pageIndex, pageSize, sorting, search: searchString })

  const { data: datastructuresData, isFetching: isFetchingDatasources } = useGetDatastructures({
    params: apiParams,
  })

  const selectableDatastructures = (datastructuresData?.data || []).map(datastructure => ({
    ...datastructure,
    dataStructureVersions: datastructure.dataStructureVersions.filter(version => version.version !== null),
  }))

  const datastructures = mapDatastructuresApiToListData(selectableDatastructures)

  const findSelectedVersion = (rowId: string | undefined): SelectedDatastructureVersion | undefined => {
    for (const datastructure of datastructures) {
      const version = datastructure.versions.find(item => `${datastructure.id}/${item.id}` === rowId)
      if (version?.versionNumber) {
        return { datastructureId: datastructure.id, name: datastructure.name, version: version.versionNumber }
      }
    }
    return undefined
  }

  const handleConfirm = () => {
    onSelectVersion(selection, findSelectedVersion(Object.keys(selection)[0]))
  }

  const rowCount = datastructuresData?.totalElements || 0
  const totalPages = Math.ceil(rowCount / pageSize)

  useEffect(() => {
    if (isPageIndexHigherThanTotalPages(pageIndex, totalPages)) {
      setPageIndex(totalPages - 1)
    }
  }, [pageIndex, totalPages])

  const handlePagination = (newPagination: PaginationState) => {
    setPageIndex(newPagination.pageIndex)
    setPageSize(newPagination.pageSize)
  }

  // for accessibility: avoid losing focus after toggeling checkboxes via keyboard
  const setFocus = (elementId: string) => {
    requestAnimationFrame(() => {
      const element = document.getElementById(elementId)
      element?.focus()
    })
  }

  const columnHelper = createColumnHelper<DatastructuresListData>()

  const columns = [
    columnHelper.accessor('id', {
      header: 'id',
      cell: info => info.getValue(),
      enableHiding: true,
    }),
    columnHelper.accessor('name', {
      header: ({ column }) => <SortableTableHeader column={column} title={tDatastructures('tableHeaders.name')} />,
      cell: ({ row }: CellContext<DatastructuresListData, unknown>) =>
        row.depth > 0 ? (
          <div className="flex items-center pl-6 gap-3 font-medium">
            <Checkbox
              checked={row.getIsSelected()}
              onCheckedChange={value => {
                row.toggleSelected(!!value)
                setFocus(row.id)
              }}
              aria-label={`Select datastructure ${row.original.name}`}
              id={row.id}
            />
            <span>{row.original.name}</span>
          </div>
        ) : (
          <ExpanderCell row={row} className="font-medium">
            {row.original.name}
          </ExpanderCell>
        ),
      meta: {
        style: {
          minWidth: '200px',
          color: 'var(--foreground)',
          fontWeight: '500',
        },
      },
    }),
    columnHelper.accessor('description', {
      header: tDatastructures('tableHeaders.description'),
      cell: info => info.getValue(),
      meta: {
        style: {
          minWidth: '200px',
        },
      },
    }),
    columnHelper.accessor('source', {
      header: tDatastructures('tableHeaders.source'),
      cell: info => (info.getValue() ? tVersion(`source.${info.getValue()}`) : '-'),
      meta: {
        style: {
          width: '10%',
          minWidth: '100px',
        },
      },
    }),
    columnHelper.accessor('versionNumber', {
      header: tDatastructures('tableHeaders.versionNumber'),
      cell: info => info.getValue() || '-',
      meta: {
        style: {
          minWidth: '100px',
        },
      },
    }),
    columnHelper.accessor('status', {
      header: tDatastructures('tableHeaders.status'),
      cell: info => {
        const status = info.getValue()
        return (
          <div className="flex items-center gap-2">
            {status === DATASTRUCTURE_STATUS_TYPES.DRAFT ? (
              <CircleDashed className="text-muted-foreground" size={16} />
            ) : (
              <CircleCheckBig className="text-muted-foreground" size={16} />
            )}
            {tCommon(`status.${info.getValue()}`)}
          </div>
        )
      },
      meta: {
        style: {
          minWidth: '120px',
        },
      },
    }),
  ]

  const table = useReactTable({
    getRowId: (row, _, parent) => (!parent ? row.id : `${parent.id}/${row.id}`),
    columns: columns,
    data: datastructures,
    rowCount,
    state: {
      pagination: { pageIndex, pageSize },
      sorting,
      rowSelection: selection,
    },
    initialState: {
      columnVisibility: {
        id: false,
      },
    },
    manualPagination: true,
    manualSorting: true,
    getSubRows: row => row.versions || [],
    getExpandedRowModel: getExpandedRowModel(),
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    onPaginationChange: updater => {
      handlePagination(resolveUpdater(updater, { pageIndex, pageSize }))
    },
    onRowSelectionChange: updater => {
      const next = resolveUpdater(updater, selection)
      const selectedIds = Object.entries(next)
        .filter(([, isSelected]) => isSelected)
        .map(([id]) => id)
      if (selectedIds.length === 0) {
        setSelection({})
        return
      }
      const lastSelectedId = selectedIds[selectedIds.length - 1]
      setSelection({ [lastSelectedId]: true })
    },
    onSortingChange: updater => setSorting(resolveUpdater(updater, sorting)),
  })

  // Import button disabled when loading, no datastructure selected or current selection is equal to saved selection
  // When canRemoveSelection is true and a saved datastructure gets unselected, the import button is enabled (datastructure is removed)
  const isImportButtonDisabled =
    isUpdating ||
    isFetchingDatasources ||
    Object.keys(selection)[0] === selectedVersion ||
    (JSON.stringify(selection) === '{}' && !selectedVersion) ||
    (JSON.stringify(selection) === '{}' && !canRemoveSelection)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        data-testid="dataModelImportModal"
        className="block sm:max-w-[95%] sm:w-[95%] md:max-w-[1061px] h-[80%] max-h-[743px]  [--title-height:64px] [--button-height:60px]"
      >
        <DialogHeader>
          <DialogTitle>{t('title')}</DialogTitle>
          <DialogDescription>{t('to', { name: datasourceTitle })}</DialogDescription>
        </DialogHeader>
        <SearchHeader
          searchString={searchString}
          onChangeSearchString={newSearchString => setSearchString(newSearchString)}
          className="my-2"
        />
        <div className="h-[calc(100%-var(--search-height)-var(--title-height)-var(--button-height))]">
          {isUpdating ? (
            <LoadingSpinner className="h-full" />
          ) : (
            <DataTable
              table={table}
              pageIndex={pageIndex}
              pageSize={pageSize}
              totalPages={totalPages}
              isLoading={isFetchingDatasources}
            />
          )}
        </div>
        <ActionButtons
          confirmButtonType="button"
          onConfirmClick={handleConfirm}
          onCancelClick={() => onOpenChange(false)}
          isConfirmButtonDisabled={isImportButtonDisabled}
          hasCard={false}
          confirmButtonTitle={tCommon('actions.import')}
        />
      </DialogContent>
    </Dialog>
  )
}
