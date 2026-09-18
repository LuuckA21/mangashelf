# ---- build ----
FROM node:25-alpine@sha256:bdf2cca6fe3dabd014ea60163eca3f0f7015fbd5c7ee1b0e9ccb4ced6eb02ef4 AS build
WORKDIR /build

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---- runtime ----
# 1.31.3 fixed CVE-2026-42533 in map/regex handling; stay on an explicit
# patched release rather than the mutable `alpine` tag.
FROM nginx:1.31.4-alpine@sha256:db35bfc6b2951e7f8a72db5db120288c127ffaeeb4a6d4b95a26fead017d5913 AS runtime-base
# Update affected OS libraries without changing nginx or Alpine major versions.
RUN apk upgrade --no-cache libcrypto3 libssl3 libexpat libuuid

FROM runtime-base AS runtime
COPY --from=build /build/dist /usr/share/nginx/html
# Files under templates/ are run through envsubst at startup and written to
# /etc/nginx/conf.d/ — native behaviour of the official image.
COPY default.conf.template /etc/nginx/templates/default.conf.template
EXPOSE 80
