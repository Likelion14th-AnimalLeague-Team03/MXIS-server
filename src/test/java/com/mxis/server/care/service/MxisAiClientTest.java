package com.mxis.server.care.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mxis.server.care.config.MxisAiServiceProperties;
import com.mxis.server.care.dto.SensorPeriod;
import com.mxis.server.product.entity.Product;
import java.net.http.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MxisAiClientTest {
    private final HttpClient http = mock(HttpClient.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final MxisAiServiceProperties properties = new MxisAiServiceProperties();
    private final Product product = mock(Product.class);
    private MxisAiClient client;

    @BeforeEach void setUp() {
        properties.setBaseUrl("http://localhost:8765/");
        properties.setInternalApiKey("test-only-internal-key");
        client = new MxisAiClient(properties, mapper, new AiResponseMapper(mapper), http);
        when(product.getId()).thenReturn(7L);
        when(product.getProductName()).thenReturn("Test product");
        when(product.getMaterialId()).thenReturn("natural_leather");
    }

    @Test void reusesTransportAndPreservesExistingEndpointAndAuthentication() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(AiResponseMapperTest.validJson());
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        assertThat(client.getCareSummary(product, 3L, SensorPeriod.THIRTY_DAYS, List.of()).productCondition().score()).isEqualTo(92);
        client.getCareSummary(product, 3L, SensorPeriod.THIRTY_DAYS, List.of());
        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(requests.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(requests.getValue().uri().toString()).isEqualTo("http://localhost:8765/ai/care-summary");
        assertThat(requests.getValue().headers().firstValue("X-MXIS-AI-Key")).contains("test-only-internal-key");
    }

    @Test void classifiesTimeoutWithoutMakingAnAdditionalLlmRequest() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new HttpTimeoutException("timeout"));
        assertThatThrownBy(() -> client.getCareSummary(product, 3L, SensorPeriod.THIRTY_DAYS, List.of()))
                .isInstanceOfSatisfying(AiServiceException.class, ex -> assertThat(ex.getCategory()).isEqualTo(AiServiceException.Category.TIMEOUT));
        verify(http, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test void errorDoesNotExposeProviderResponseBody() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(503);
        when(response.body()).thenReturn("upstream private body");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        assertThatThrownBy(() -> client.getCareSummary(product, 3L, SensorPeriod.THIRTY_DAYS, List.of()))
                .isInstanceOf(AiServiceException.class).hasMessage("AI service HTTP 503");
    }
}
