package com.mynix.backend.dto.media;

import com.mynix.backend.model.ProductMediaType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UploadedMediaResponse {

    private ProductMediaType type;
    private String storageKey;
    private String url;
    private String contentType;
    private long sizeBytes;
}
