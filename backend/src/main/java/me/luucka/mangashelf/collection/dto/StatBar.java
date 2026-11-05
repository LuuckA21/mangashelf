package me.luucka.mangashelf.collection.dto;

/** One bar of a chart: owned volumes for a key (publisher name, enum name, or null for "Altri"). */
public record StatBar(String key, long volumes) {}
