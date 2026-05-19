package com.arknow.counter.api;

import com.arknow.MapperMockConfiguration;
import com.arknow.counter.service.UserCounterService;
import com.arknow.counter.service.impl.CounterServiceImpl;
import com.arknow.knowpost.id.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActionController.class)
@Import(MapperMockConfiguration.class)
class ActionControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private CounterServiceImpl counterService;
    @MockBean private SnowflakeIdGenerator snowflakeIdGenerator;
    @MockBean private StringRedisTemplate stringRedisTemplate;
    @MockBean private UserCounterService userCounterService;

    @Test
    void likeWithoutAuthShouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/action/like")
                .contentType("application/json")
                .content("{\"entityType\":\"POST\",\"entityId\":\"1\"}"))
                .andExpect(status().is4xxClientError());
    }
}
