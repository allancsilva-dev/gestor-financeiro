package com.gestor.financeiro.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class LocalE2eAssistantFaultFilterTest {
    private final LocalE2eAssistantFaultFilter filter = new LocalE2eAssistantFaultFilter(new ObjectMapper());

    @Test
    void mensagemRetryPerdePrimeiraRespostaDepoisDoServicoERetornaReplay() throws Exception {
        MockHttpServletResponse first = executeMessage("assistant:message:1", "e2e retry mercado 50 hoje", 41L);
        MockHttpServletResponse replay = executeMessage("assistant:message:1", "e2e retry mercado 50 hoje", 41L);

        assertThat(first.getStatus()).isEqualTo(503);
        assertThat(replay.getStatus()).isEqualTo(201);
        assertThat(replay.getContentAsString()).contains("\"id\":41");
    }

    @Test
    void confirmacaoArmadaPerdePrimeiraRespostaEEntregaReplay() throws Exception {
        executeMessage("assistant:message:2", "e2e confirm retry mercado 50 hoje", 77L);

        MockHttpServletResponse first = executeConfirm(77L);
        MockHttpServletResponse replay = executeConfirm(77L);

        assertThat(first.getStatus()).isEqualTo(503);
        assertThat(replay.getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse executeMessage(String key, String text, long draftId) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/assistant/messages");
        request.addHeader(IdempotencyFilter.HEADER, key);
        request.setContentType("application/json");
        request.setContent(("{\"text\":\"" + text + "\"}").getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            req.getInputStream().readAllBytes();
            HttpServletResponse http = (HttpServletResponse) res;
            http.setStatus(201);
            http.setContentType("application/json");
            http.getWriter().write("{\"draft\":{\"id\":" + draftId + "}}");
        };
        filter.doFilter(request, response, chain);
        return response;
    }

    private MockHttpServletResponse executeConfirm(long draftId) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/v1/assistant/drafts/" + draftId + "/confirm");
        request.setContentType("application/json");
        request.setContent("{\"version\":1}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            HttpServletResponse http = (HttpServletResponse) res;
            http.setStatus(200);
            http.setContentType("application/json");
            http.getWriter().write("{\"confirmationId\":1}");
        };
        filter.doFilter(request, response, chain);
        return response;
    }
}
