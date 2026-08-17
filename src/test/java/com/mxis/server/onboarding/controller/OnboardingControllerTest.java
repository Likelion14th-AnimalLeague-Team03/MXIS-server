package com.mxis.server.onboarding.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mxis.server.common.security.JwtAuthenticationFilter;
import com.mxis.server.common.security.JwtTokenProvider;
import com.mxis.server.config.SecurityConfig;
import com.mxis.server.onboarding.dto.OnboardingProductResponse;
import com.mxis.server.onboarding.service.OnboardingService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OnboardingController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class OnboardingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private OnboardingService onboardingService;

    private String accessToken;

    @BeforeEach
    void setUp() {
        accessToken = jwtTokenProvider.createAccessToken(1L, "user@mxis.com");
    }

    @Test
    void getProducts_success_returnsProductSelectionFields() throws Exception {
        given(onboardingService.getProducts(eq(1L))).willReturn(List.of(
                new OnboardingProductResponse(
                        20L,
                        "https://example.com/product.png",
                        "Stark Backpack",
                        "canvas",
                        "Visetos Canvas",
                        "Cognac",
                        "MMKAAVE01CO001",
                        null,
                        true)));

        mockMvc.perform(get("/api/v1/onboarding/products")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].productId", is(20)))
                .andExpect(jsonPath("$.data[0].productImageUrl", is("https://example.com/product.png")))
                .andExpect(jsonPath("$.data[0].materialDisplayName", is("Visetos Canvas")))
                .andExpect(jsonPath("$.data[0].isPrimary", is(true)));
    }

    @Test
    void getProducts_requiresAuth_returns401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/onboarding/products"))
                .andExpect(status().isUnauthorized());
    }
}
