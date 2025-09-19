import { flexRender, SortDirection, Table } from '@tanstack/react-table'
import React, { ComponentProps } from 'react'

import { Table as ShadCnTable, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../ui/table'

export const getAriaSort = (sorting: false | SortDirection) => {
  switch (sorting) {
    case 'asc':
      return 'ascending'
    case 'desc':
      return 'descending'
    default:
      return 'none'
  }
}

export interface DataTableProps<T> extends ComponentProps<'table'> {
  table: Table<T>
}

export const DataTable = <T,>(props: DataTableProps<T>) => {
  const { table, ...tableProps } = props
  return (
    <ShadCnTable aria-labelledby="datasets-subheading" {...tableProps}>
      <TableHeader>
        {table.getHeaderGroups().map(group => (
          <TableRow key={group.id}>
            {group.headers.map((header, i) => (
              <TableHead
                key={header.id}
                scope="col"
                className="text-primary-light"
                aria-sort={getAriaSort(header.column.getIsSorted())}
                style={header.column.columnDef.meta?.style}
              >
                {flexRender(header.column.columnDef.header, header.getContext())}
              </TableHead>
            ))}
          </TableRow>
        ))}
      </TableHeader>
      <TableBody>
        {table.getRowModel().rows.map(row => (
          <TableRow className="h-16" key={row.id}>
            {row.getVisibleCells().map(cell => (
              <TableCell className="whitespace-normal" key={cell.id}>
                {flexRender(cell.column.columnDef.cell, cell.getContext())}
              </TableCell>
            ))}
          </TableRow>
        ))}
      </TableBody>
    </ShadCnTable>
  )
}
