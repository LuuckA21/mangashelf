# Preserve the upstream PostgreSQL major, entrypoint and data layout.
FROM postgres:18-alpine@sha256:77f585114c32fbca283dc835b0596f4e52b51b4c6662d7810b2f4084f60a1873

# Use signed packages from this image's Alpine branch. The upstream standalone
# gosu embeds an old Go runtime; Alpine maintains a rebuilt package of gosu.
RUN apk upgrade --no-cache libcrypto3 libssl3 libuuid \
    && apk add --no-cache 'gosu>=1.19-r5' \
    && rm /usr/local/bin/gosu \
    && gosu nobody true
