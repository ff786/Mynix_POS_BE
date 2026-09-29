package com.mynix.backend.dto.media;

import com.mynix.backend.model.ProductMediaType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MediaResponse {

    private ProductMediaType type;
    /** Set for uploads (send it back unchanged when saving the product). */
    private String storageKey;
    /** Where the photo/video can be viewed (uploads: the public storage address). */
    private String url;
    private String youtubeId;
    private String altText;
    private boolean shared;
}
