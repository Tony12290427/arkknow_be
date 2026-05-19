package com.arknow.knowpost.api;

import com.arknow.MapperMockConfiguration;
import com.arknow.knowpost.service.KnowPostFeedService;
import com.arknow.knowpost.service.KnowPostService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(KnowPostController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MapperMockConfiguration.class)
class KnowPostControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private KnowPostService knowPostService;
    @MockBean private KnowPostFeedService feedService;

    @Test
    void feedShouldReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/knowposts/feed?page=1&size=20"))
                .andExpect(status().isOk());
    }
}
