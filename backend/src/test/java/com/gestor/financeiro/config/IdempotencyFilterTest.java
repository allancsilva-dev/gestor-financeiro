package com.gestor.financeiro.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdempotencyFilterTest {

    private final IdempotencyFilter filter = new IdempotencyFilter(new ObjectMapper().findAndRegisterModules());

    @Test
    void aceitaChaveValidaEmMetodoMutavel() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/v1/recurso");
        request.addHeader(IdempotencyFilter.HEADER, "importacao:123_tentativa-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals("importacao:123_tentativa-1", request.getAttribute(IdempotencyFilter.ATTRIBUTE));
        assertEquals(200, response.getStatus());
        assertTrue(chain.getRequest() != null);
    }

    @Test
    void rejeitaChaveComEspacoSemExecutarCadeia() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/recurso");
        request.addHeader(IdempotencyFilter.HEADER, "chave insegura");
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "req-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(400, response.getStatus());
        assertNull(chain.getRequest());
        assertTrue(response.getContentAsString().contains("INVALID_IDEMPOTENCY_KEY"));
        assertTrue(response.getContentAsString().contains("req-1"));
    }

    @Test
    void ignoraHeaderEmMetodoSomenteLeitura() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/recurso");
        request.addHeader(IdempotencyFilter.HEADER, "nao-propagar");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNull(request.getAttribute(IdempotencyFilter.ATTRIBUTE));
        assertTrue(chain.getRequest() != null);
    }
}
