package com.arknow.knowpost.api;

import com.arknow.knowpost.api.dto.DescriptionSuggestRequest;
import com.arknow.knowpost.api.dto.DescriptionSuggestResponse;
import com.arknow.llm.service.KnowPostDescriptionService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI-assisted knowledge post endpoints.
 * <p>
 * Only activated when the LLM infrastructure (ChatClient) is available.
 * Without a configured DeepSeek API key, this controller is not registered.
 */
@RestController
@RequestMapping(path = "/api/v1/knowposts", produces = MediaType.APPLICATION_JSON_VALUE)
public class KnowPostAiController {

    private final KnowPostDescriptionService descriptionService;

    public KnowPostAiController(KnowPostDescriptionService descriptionService) {
        this.descriptionService = descriptionService;
    }

    /**
     * Generates a concise (≤50 chars) Chinese description for post content.
     * Requires authentication to prevent anonymous abuse of the LLM API.
     */
    @PostMapping(path = "/description/suggest", consumes = MediaType.APPLICATION_JSON_VALUE)
    public DescriptionSuggestResponse suggest(@Valid @RequestBody DescriptionSuggestRequest req) {
        String desc = descriptionService.generateDescription(req.content());
        return new DescriptionSuggestResponse(desc);
    }
}
