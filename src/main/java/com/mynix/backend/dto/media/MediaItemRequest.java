package com.mynix.backend.dto.media;

import com.mynix.backend.model.ProductMediaType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** One entry of a product's photo/video list, in display order. */
@Data
public class MediaItemRequest {

    @NotNull
    private ProductMediaType type;

    /** An uploaded file (from POST /api/media/uploads). */
    @Size(max = 200)
    private String storageKey;

    /** An https image link, or a YouTube link. */
    @Size(max = 1000)
    private String url;

    @Size(max = 160, message = "Keep image descriptions within 160 characters")
    private String altText;

    /** Show for every option of a variable product. */
    private boolean shared;
}
