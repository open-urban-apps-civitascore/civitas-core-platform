import pino from 'pino'

/**
 * Centralized logger for the application
 *
 * Log levels (from most to least severe):
 * - error: Application errors, failures
 * - warn: Warning conditions (auth failures, invalid requests)
 * - info: Important business events (requests, successful operations)
 * - debug: Detailed debugging information (only for development)
 *
 * Controlled by LOG_LEVEL environment variable (defaults to 'info')
 */
export const logger = pino({
  level: process.env.LOG_LEVEL || 'info',
  // In production, use JSON for better parsing by log aggregation tools
  // In development, use pretty print for readability
  transport:
    process.env.NODE_ENV === 'development'
      ? {
          target: 'pino-pretty',
          options: {
            colorize: true,
            translateTime: 'HH:MM:ss',
            ignore: 'pid,hostname',
          },
        }
      : undefined,
})

/**
 * Extracts safe logging context from request
 * Excludes sensitive information like tokens and full headers
 */
export const GetRequestLogContext = (request: { url: string; method: string; headers: Headers }) => {
  const headersObj: Record<string, string> = {}
  request.headers.forEach((value, key) => {
    headersObj[key] = value
  })

  return {
    url: request.url,
    method: request.method,
    // Only log non-sensitive headers
    userAgent: headersObj['user-agent'],
    contentType: headersObj['content-type'],
    forwardedProto: headersObj['x-forwarded-proto'],
    forwardedHost: headersObj['x-forwarded-host'],
  }
}
