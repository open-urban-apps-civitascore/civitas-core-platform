export * from '@tanstack/react-table'

declare module '@tanstack/react-table' {
  interface ColumnMeta {
    style?: React.CSSProperties
    className?: string
  }
}
