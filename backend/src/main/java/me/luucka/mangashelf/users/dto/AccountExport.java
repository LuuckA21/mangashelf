package me.luucka.mangashelf.users.dto;

import java.time.Instant;
import java.util.List;
import me.luucka.mangashelf.collection.dto.CollectionEntry;
import me.luucka.mangashelf.users.Role;

public record AccountExport(
    String username, Role role, Instant createdAt, List<CollectionEntry> collection) {}
