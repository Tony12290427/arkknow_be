package com.arknow.llm.service;

/**
 * AI-generated post description service.
 * <p>
 * Given the full text of a knowledge post, generates a concise
 * Chinese summary no longer than 50 characters.
 */
public interface KnowPostDescriptionService {

    /**
     * Generates a short Chinese description for the given content.
     *
     * @param content the full post body (Markdown)
     * @return a description of at most 50 Chinese characters
     */
    String generateDescription(String content);
}
