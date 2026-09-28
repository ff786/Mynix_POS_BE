package com.mynix.backend.controller;

import com.mynix.backend.dto.newsletter.NewsletterSubscriberResponse;
import com.mynix.backend.service.impl.NewsletterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Newsletter subscribers for admins (see SecurityConfig). */
@RestController
@RequestMapping("/api/newsletter-subscribers")
@RequiredArgsConstructor
public class NewsletterAdminController {

    private final NewsletterService newsletterService;

    @GetMapping
    public List<NewsletterSubscriberResponse> list() {
        return newsletterService.list();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        newsletterService.delete(id);
    }
}
