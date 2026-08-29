package com.example.snstimeline.file;

import java.time.OffsetDateTime;

/** {@code stored_files} の1行（docs/04_data_model.md 2.7）。 */
public record StoredFile(
    Long id,
    String storageType,
    String storageKey,
    String originalFilename,
    String contentType,
    Long sizeBytes,
    Integer width,
    Integer height,
    Long uploadedBy,
    OffsetDateTime createdAt) {}
