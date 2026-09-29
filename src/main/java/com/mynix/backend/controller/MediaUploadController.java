package com.mynix.backend.controller;

import com.mynix.backend.dto.media.UploadedMediaResponse;
import com.mynix.backend.service.MediaUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Photo and video uploads from the product form (admins only, see SecurityConfig). */
@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaUploadController {

    private final MediaUploadService mediaUploadService;

    @PostMapping(path = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadedMediaResponse upload(@RequestParam("file") MultipartFile file) {
        return mediaUploadService.upload(file);
    }
}
