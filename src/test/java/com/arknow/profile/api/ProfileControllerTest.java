package com.arknow.profile.api;

import com.arknow.MapperMockConfiguration;
import com.arknow.profile.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MapperMockConfiguration.class)
class ProfileControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private ProfileService profileService;

    @Test
    void getPublicProfileShouldReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/profile/1"))
                .andExpect(status().isOk());
    }
}
