package com.arknow.search.api;

import com.arknow.MapperMockConfiguration;
import com.arknow.search.service.AiSearchService;
import com.arknow.search.service.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MapperMockConfiguration.class)
class SearchControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private SearchService searchService;
    @MockBean private AiSearchService aiSearchService;

    @Test
    void searchShouldReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/search?keyword=test"))
                .andExpect(status().isOk());
    }
}
