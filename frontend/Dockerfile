# ---- build ----
FROM node:22-alpine AS build
WORKDIR /build

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---- runtime ----
# 1.31.3 fixed CVE-2026-42533 in map/regex handling; stay on an explicit
# patched release rather than the mutable `alpine` tag.
FROM nginx:1.31.4-alpine
COPY --from=build /build/dist /usr/share/nginx/html
# Files under templates/ are run through envsubst at startup and written to
# /etc/nginx/conf.d/ — native behaviour of the official image.
COPY default.conf.template /etc/nginx/templates/default.conf.template
EXPOSE 80
