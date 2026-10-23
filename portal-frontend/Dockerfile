# Use Node.js Alpine for small image size and security
FROM node:22.23.4-alpine3.24 AS deps
WORKDIR /app
RUN corepack enable
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN --mount=type=cache,target=/root/.local/share/pnpm/store pnpm install --frozen-lockfile --prod

FROM node:22.23.4-alpine3.24
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=3000

COPY --from=deps /app/node_modules ./node_modules
COPY package.json ./
COPY .next ./.next
COPY public ./public
RUN rm -rf /usr/local/lib/node_modules/npm /usr/local/bin/npm /usr/local/bin/npx \
 && mkdir -p .next/cache && chown node:node .next/cache
USER 1000

EXPOSE 3000

CMD ["node_modules/.bin/next", "start"]
