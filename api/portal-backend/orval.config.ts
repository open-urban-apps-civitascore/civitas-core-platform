import { defineConfig } from 'orval'

export default defineConfig({
  portalBackend: {
    input: {
      target: './openapi.yaml',
    },
    output: {
      target: './generated/queries',
      schemas: './generated/types',
      client: 'react-query',
      httpClient: 'axios',
      mode: 'tags-split',
      clean: true,
      prettier: true,
      override: {
        mutator: {
          path: './axios-instance.ts',
          name: 'customInstance',
        },
        query: {
          useQuery: true,
          useMutation: true,
        },
      },
    },
  },
  portalBackendFetch: {
    input: {
      target: './openapi.yaml',
    },
    output: {
      target: './generated/fetch',
      schemas: './generated/types',
      client: 'fetch',
      mode: 'tags-split',
      prettier: true,
    },
  },
  portalBackendMock: {
    input: {
      target: './openapi.yaml',
    },
    output: {
      target: './generated/mocks',
      mode: 'tags-split',
      prettier: true,
      mock: true,
    },
  },
  portalBackendZod: {
    input: {
      target: './openapi.yaml',
    },
    output: {
      target: './generated/schemas',
      client: 'zod',
      mode: 'tags-split',
      fileExtension: '.zod.ts',
      prettier: true,
    },
  },
})
