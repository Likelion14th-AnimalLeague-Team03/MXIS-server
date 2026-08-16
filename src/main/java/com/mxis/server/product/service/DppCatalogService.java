package com.mxis.server.product.service;

import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.product.dto.ProductRecognizeResponse;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * MCM Digital Product Passport(DPP) 인식 스텁.
 *
 * 실제로는 MCM이 Aura Blockchain Consortium 인프라를 통해 운영하는 DPP 조회 API를 호출해야 하지만,
 * 그 외부 연동은 이 MVP 범위 밖이라 인메모리 데모 카탈로그로 대체한다.
 * 실 연동 시 이 클래스를 외부 API 클라이언트 구현으로 교체하면 된다.
 */
@Component
public class DppCatalogService {

    private static final Map<String, ProductRecognizeResponse> DEMO_CATALOG = Map.of(
            "MCM-DPP-0001", new ProductRecognizeResponse(
                    "MCM-DPP-0001", "MCM Aren Shopper", "AREN-SHP-001", "canvas",
                    List.of("coated_canvas"), "Cognac",
                    "https://static.mcmworldwide.com/demo/aren-shopper.jpg"),
            "MCM-DPP-0002", new ProductRecognizeResponse(
                    "MCM-DPP-0002", "MCM Milla Tote", "MILLA-TOTE-002", "natural_leather",
                    List.of(), "Black",
                    "https://static.mcmworldwide.com/demo/milla-tote.jpg")
    );

    public ProductRecognizeResponse recognize(String dppCode) {
        ProductRecognizeResponse recognized = DEMO_CATALOG.get(dppCode);
        if (recognized == null) {
            throw new BusinessException(ErrorCode.DPP_NOT_RECOGNIZED);
        }
        return recognized;
    }
}
