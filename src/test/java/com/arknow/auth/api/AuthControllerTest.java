package com.arknow.auth.api;

import com.arknow.MapperMockConfiguration;
import com.arknow.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(MapperMockConfiguration.class)
class AuthControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private AuthService authService;

    @Test
    void loginWithInvalidCredentialsShouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType("application/json")
                .content("{\"identifierType\":\"PHONE\",\"identifier\":\"13900000000\",\"password\":\"wrong\"}"))
                .andExpect(status().is4xxClientError());
    }
}
