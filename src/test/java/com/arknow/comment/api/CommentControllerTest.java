package com.arknow.comment.api;

import com.arknow.MapperMockConfiguration;
import com.arknow.knowpost.id.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CommentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MapperMockConfiguration.class)
class CommentControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private SnowflakeIdGenerator snowflakeIdGenerator;

    @Test
    void listCommentsShouldReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/comments?postId=1"))
                .andExpect(status().isOk());
    }
}
