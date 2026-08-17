package com.mxis.server.onboarding.service;

import com.mxis.server.onboarding.dto.OnboardingProductResponse;
import com.mxis.server.product.entity.Product;
import com.mxis.server.product.repository.ProductRepository;
import com.mxis.server.user.entity.User;
import com.mxis.server.user.repository.UserRepository;
import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OnboardingService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public List<OnboardingProductResponse> getProducts(Long userId) {
        User user = userRepository.findActiveById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Long primaryProductId = user.getPrimaryProduct() == null ? null : user.getPrimaryProduct().getId();

        return productRepository.findAllActiveByUserId(userId).stream()
                .map(product -> OnboardingProductResponse.from(product, product.getId().equals(primaryProductId)))
                .toList();
    }
}
