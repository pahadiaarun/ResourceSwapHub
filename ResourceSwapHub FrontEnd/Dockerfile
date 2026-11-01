# Build args (NEXT_PUBLIC_* values are baked into the client bundle at build
# time, so they must be provided as build args rather than runtime env vars).
FROM node:22-alpine AS build
WORKDIR /workspace

ARG NEXT_PUBLIC_WS_URL=ws://localhost:8080/ws
ENV NEXT_PUBLIC_WS_URL=$NEXT_PUBLIC_WS_URL \
    NEXT_TELEMETRY_DISABLED=1

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM node:22-alpine
WORKDIR /workspace
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1

COPY --from=build /workspace/package.json /workspace/package-lock.json ./
COPY --from=build /workspace/node_modules node_modules
COPY --from=build /workspace/.next .next
COPY --from=build /workspace/public public
COPY --from=build /workspace/next.config.ts next.config.ts

EXPOSE 3000
CMD ["npm", "run", "start"]
