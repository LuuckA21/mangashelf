package me.luucka.mangashelf.catalog.dto;

import java.util.List;

public record SeriesDetail(String id, String title, List<EditionResponse> editions) {}
