package com.mxis.server.product.service;

import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.product.dto.ProductRecognizeResponse;
import com.mxis.server.product.dto.ProductRegisterRequest;
import com.mxis.server.product.dto.ProductResponse;
import com.mxis.server.product.entity.Product;
import com.mxis.server.product.entity.ProductDevice;
import com.mxis.server.product.repository.ProductDeviceRepository;
import com.mxis.server.product.repository.ProductRepository;
import com.mxis.server.user.entity.User;
import com.mxis.server.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductDeviceRepository productDeviceRepository;
    private final UserRepository userRepository;
    private final DppCatalogService dppCatalogService;

    public ProductRecognizeResponse recognize(String dppCode) {
        return dppCatalogService.recognize(dppCode);
    }

    @Transactional
    public ProductResponse register(Long userId, ProductRegisterRequest request) {
        User user = userRepository.findActiveById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Product product = new Product(
                user,
                request.dppCode(),
                request.productName(),
                request.modelCode(),
                request.material(),
                request.color(),
                request.imageUrl(),
                request.purchasedAt());

        return ProductResponse.from(productRepository.save(product));
    }

    public List<ProductResponse> getMyProducts(Long userId) {
        return productRepository.findAllActiveByUserId(userId).stream()
                .map(ProductResponse::from)
                .toList();
    }

    public ProductResponse getProduct(Long userId, Long productId) {
        return ProductResponse.from(getOwnedProduct(userId, productId));
    }

    @Transactional
    public void delete(Long userId, Long productId) {
        Product product = getOwnedProduct(userId, productId);

        // 제품을 삭제하면 연결돼 있던 기기들도 함께 연결 해제 처리한다 (detached_at 기록).
        // 그렇지 않으면 삭제된 제품에 기기가 "연결된 상태"로 남는 정합성 문제가 생긴다.
        for (ProductDevice link : productDeviceRepository.findActiveByProductId(productId)) {
            link.detach();
        }

        product.softDelete();
    }

    Product getOwnedProduct(Long userId, Long productId) {
        Product product = productRepository.findActiveById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (!product.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_OWNED);
        }
        return product;
    }
}
