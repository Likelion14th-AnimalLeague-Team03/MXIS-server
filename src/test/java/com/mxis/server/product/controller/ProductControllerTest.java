package com.mxis.server.product.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.common.security.JwtAuthenticationFilter;
import com.mxis.server.common.security.JwtTokenProvider;
import com.mxis.server.config.SecurityConfig;
import com.mxis.server.product.dto.ProductRecognizeRequest;
import com.mxis.server.product.dto.ProductRecognizeResponse;
import com.mxis.server.product.dto.ProductRegisterRequest;
import com.mxis.server.product.dto.ProductResponse;
import com.mxis.server.product.service.ProductService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private ProductService productService;

    private String accessToken;

    @BeforeEach
    void setUp() {
        accessToken = jwtTokenProvider.createAccessToken(1L, "user@mxis.com");
    }

    private ProductResponse sampleProduct() {
        return new ProductResponse(20L, "DPP-001", "트리케 백", "MODEL-A", "natural_leather", List.of(), "블랙",
                "https://example.com/product.png", LocalDate.of(2025, 1, 1), LocalDateTime.now(), false);
    }

    private ProductResponse samplePrimaryProduct() {
        return new ProductResponse(20L, "DPP-001", "트리케 백", "MODEL-A", "natural_leather", List.of(), "블랙",
                "https://example.com/product.png", LocalDate.of(2025, 1, 1), LocalDateTime.now(), true);
    }

    @Test
    void recognize_requiresAuth_returns401WithoutToken() throws Exception {
        ProductRecognizeRequest request = new ProductRecognizeRequest("DPP-001");

        mockMvc.perform(post("/api/v1/products/recognize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recognize_success_returnsProductInfo() throws Exception {
        ProductRecognizeRequest request = new ProductRecognizeRequest("DPP-001");
        given(productService.recognize("DPP-001")).willReturn(new ProductRecognizeResponse(
                "DPP-001", "트리케 백", "MODEL-A", "natural_leather", List.of(), "블랙",
                "https://example.com/product.png"));

        mockMvc.perform(post("/api/v1/products/recognize")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productName", is("트리케 백")));
    }

    @Test
    void recognize_notRecognized_returns404() throws Exception {
        ProductRecognizeRequest request = new ProductRecognizeRequest("UNKNOWN");
        given(productService.recognize("UNKNOWN"))
                .willThrow(new BusinessException(ErrorCode.DPP_NOT_RECOGNIZED));

        mockMvc.perform(post("/api/v1/products/recognize")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("DPP_NOT_RECOGNIZED")));
    }

    @Test
    void register_blankProductName_returns400() throws Exception {
        ProductRegisterRequest request = new ProductRegisterRequest(
                "DPP-001", "", "MODEL-A", "natural_leather", List.of(), "블랙", null, null);

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_INPUT")));
    }

    @Test
    void register_success_returns201() throws Exception {
        ProductRegisterRequest request = new ProductRegisterRequest(
                "DPP-001", "트리케 백", "MODEL-A", "natural_leather", List.of(), "블랙", null,
                LocalDate.of(2025, 1, 1));
        given(productService.register(eq(1L), any(ProductRegisterRequest.class))).willReturn(sampleProduct());

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id", is(20)));
    }

    @Test
    void getMyProducts_success_returnsList() throws Exception {
        given(productService.getMyProducts(eq(1L))).willReturn(List.of(sampleProduct()));

        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].dppCode", is("DPP-001")));
    }

    @Test
    void getProduct_notFound_returns404() throws Exception {
        given(productService.getProduct(eq(1L), eq(99L)))
                .willThrow(new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        mockMvc.perform(get("/api/v1/products/99")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("PRODUCT_NOT_FOUND")));
    }

    @Test
    void getProduct_notOwned_returns403() throws Exception {
        given(productService.getProduct(eq(1L), eq(20L)))
                .willThrow(new BusinessException(ErrorCode.PRODUCT_NOT_OWNED));

        mockMvc.perform(get("/api/v1/products/20")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("PRODUCT_NOT_OWNED")));
    }

    @Test
    void getPrimaryProduct_success_returnsProduct() throws Exception {
        given(productService.getPrimaryProduct(eq(1L))).willReturn(samplePrimaryProduct());

        mockMvc.perform(get("/api/v1/products/primary")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(20)))
                .andExpect(jsonPath("$.data.isPrimary", is(true)));
    }

    @Test
    void setPrimaryProduct_success_returnsPrimaryProduct() throws Exception {
        given(productService.setPrimaryProduct(eq(1L), eq(20L))).willReturn(samplePrimaryProduct());

        mockMvc.perform(patch("/api/v1/products/20/primary")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(20)))
                .andExpect(jsonPath("$.data.isPrimary", is(true)));
    }

    @Test
    void delete_success_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/products/20")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
    }
}
