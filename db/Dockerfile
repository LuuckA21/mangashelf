# Preserve the upstream PostgreSQL major, entrypoint and data layout.
FROM postgres:18-alpine@sha256:d3e1620b530c944afa6e887d22eb899824da68e19c52024bf98f5220c88a65b2

# Use signed packages from this image's Alpine branch. The upstream standalone
# gosu embeds an old Go runtime; Alpine maintains a rebuilt package of gosu.
RUN apk upgrade --no-cache libcrypto3 libssl3 libuuid \
    && apk add --no-cache 'gosu>=1.19-r5' \
    && rm /usr/local/bin/gosu \
    && gosu nobody true
