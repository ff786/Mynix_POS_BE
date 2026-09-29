package com.mynix.backend.dto.variant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VariantGroupRequest {

    /** Listing name on the website, e.g. "Gem Box 2x2". */
    @NotBlank(message = "Enter a group name")
    @Size(max = 150)
    private String name;

    /** e.g. Colour, Size, Grit. Defaults to "Option". */
    @Size(max = 40)
    private String optionName;
}
