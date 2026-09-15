# ---- build ----
FROM node:22-alpine@sha256:c610fcdfb1d5b4740dd70c284ed3cb16bb857e0f7166196e36a5501df7a3aa32 AS build
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
