package com.arknow.relation.api;

import com.arknow.MapperMockConfiguration;
import com.arknow.relation.service.RelationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RelationController.class)
@Import(MapperMockConfiguration.class)
class RelationControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private RelationService relationService;

    @Test
    void relationStatusWithoutAuthShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/relation/status?toUserId=1"))
                .andExpect(status().is4xxClientError());
    }
}
