package com.mxis.server.product.controller;

import com.mxis.server.common.response.ApiResponse;
import com.mxis.server.common.security.UserPrincipal;
import com.mxis.server.product.dto.ProductRecognizeRequest;
import com.mxis.server.product.dto.ProductRecognizeResponse;
import com.mxis.server.product.dto.ProductRegisterRequest;
import com.mxis.server.product.dto.ProductResponse;
import com.mxis.server.product.service.ProductService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping("/recognize")
    public ApiResponse<ProductRecognizeResponse> recognize(@Valid @RequestBody ProductRecognizeRequest request) {
        return ApiResponse.ok(productService.recognize(request.dppCode()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> register(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ProductRegisterRequest request) {
        return ApiResponse.ok(productService.register(principal.userId(), request));
    }

    @GetMapping
    public ApiResponse<List<ProductResponse>> getMyProducts(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(productService.getMyProducts(principal.userId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> getProduct(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ApiResponse.ok(productService.getProduct(principal.userId(), id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        productService.delete(principal.userId(), id);
    }
}
