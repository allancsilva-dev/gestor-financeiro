package com.gestor.financeiro;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.repository.RefreshTokenRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setup() {
        refreshTokenRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @Test
    void register_deveCadastrarComSucesso() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "nome", "Alice",
                        "email", "alice@teste.com",
                        "password", "Senha1234",
                        "confirmPassword", "Senha1234"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.email").value("alice@teste.com"));
    }

    @Test
    void register_deveFalharComSenhaFraca() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "nome", "Alice",
                        "email", "alice2@teste.com",
                        "password", "12345678",
                        "confirmPassword", "12345678"
                ))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void register_deveFalharComSenhaCurta() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "nome", "Alice",
                        "email", "alice3@teste.com",
                        "password", "Ab1",
                        "confirmPassword", "Ab1"
                ))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void register_deveFalharQuandoEmailDuplicado() throws Exception {
        usuarioRepository.save(TestDataFactory.usuario("Alice", "alice@teste.com", passwordEncoder.encode("123456")));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "nome", "Alice 2",
                        "email", "alice@teste.com",
                        "password", "Senha1234",
                        "confirmPassword", "Senha1234"
                ))))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"));
    }

    @Test
    void register_deveFalharComCamposInvalidos() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "nome", "",
                        "email", "email-invalido",
                        "password", "12",
                        "confirmPassword", "12"
                ))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void login_deveRetornarAccessTokenECookieRefresh() throws Exception {
        usuarioRepository.save(TestDataFactory.usuario("Alice", "alice@teste.com", passwordEncoder.encode("123456")));

        mockMvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", "10.0.0.11")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "alice@teste.com",
                        "password", "123456"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.token").doesNotExist())
            .andExpect(cookie().exists("refreshToken"))
            .andExpect(cookie().exists("csrfToken"));
    }

    @Test
    void login_deveFalharComCredenciaisInvalidas() throws Exception {
        usuarioRepository.save(TestDataFactory.usuario("Alice", "alice@teste.com", passwordEncoder.encode("123456")));

        mockMvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", "10.0.0.12")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "alice@teste.com",
                        "password", "senha-errada"
                ))))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"));
    }

    @Test
    void login_deveAplicarRateLimit() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .header("X-Forwarded-For", "10.0.0.13")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of(
                            "email", "nao-existe" + i + "@teste.com",
                            "password", "senha-errada"
                    ))))
                .andExpect(status().isUnprocessableEntity());
        }

        mockMvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", "10.0.0.13")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "nao-existe@teste.com",
                        "password", "senha-errada"
                ))))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "60"));
    }

    @Test
    void refreshToken_deveRenovarComCookieValido() throws Exception {
        usuarioRepository.save(TestDataFactory.usuario("Alice", "alice@teste.com", passwordEncoder.encode("123456")));

        List<String> setCookies = mockMvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", "10.0.0.14")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "alice@teste.com",
                        "password", "123456"
                ))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeaders("Set-Cookie");

        String refreshToken = extrairValorCookie(setCookies, "refreshToken");
        String csrfToken = extrairValorCookie(setCookies, "csrfToken");

        mockMvc.perform(post("/api/auth/refresh-token")
                .header("X-CSRF-Token", csrfToken)
                .cookie(new jakarta.servlet.http.Cookie("csrfToken", csrfToken))
                .cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(cookie().exists("refreshToken"))
            .andExpect(cookie().exists("csrfToken"));
    }

    @Test
    void refreshToken_deveFalharSemCsrfToken() throws Exception {
        usuarioRepository.save(TestDataFactory.usuario("Alice", "alice@teste.com", passwordEncoder.encode("123456")));

        List<String> setCookies = mockMvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", "10.0.0.18")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "alice@teste.com",
                        "password", "123456"
                ))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeaders("Set-Cookie");

        String refreshToken = extrairValorCookie(setCookies, "refreshToken");

        mockMvc.perform(post("/api/auth/refresh-token")
                .cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshToken)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("CSRF_REQUIRED"));
    }

    @Test
    void refreshToken_deveFalharComTokenRevogado() throws Exception {
        usuarioRepository.save(TestDataFactory.usuario("Alice", "alice@teste.com", passwordEncoder.encode("123456")));

        List<String> setCookies = mockMvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", "10.0.0.15")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "alice@teste.com",
                        "password", "123456"
                ))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeaders("Set-Cookie");

        String refreshToken = extrairValorCookie(setCookies, "refreshToken");
        String csrfToken = extrairValorCookie(setCookies, "csrfToken");

        mockMvc.perform(post("/api/auth/logout")
                .header("X-CSRF-Token", csrfToken)
                .cookie(new jakarta.servlet.http.Cookie("csrfToken", csrfToken))
                .cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshToken)))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh-token")
                .header("X-CSRF-Token", csrfToken)
                .cookie(new jakarta.servlet.http.Cookie("csrfToken", csrfToken))
                .cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshToken)))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("TOKEN_REUSE_DETECTED"))
                        .andExpect(jsonPath("$.message").value("Sessão invalidada por segurança. Faça login novamente."));
        }

        @Test
        void refreshToken_deveDetectarReusoAposRotacao() throws Exception {
                usuarioRepository.save(TestDataFactory.usuario("Alice", "alice@teste.com", passwordEncoder.encode("123456")));

                List<String> loginSetCookies = mockMvc.perform(post("/api/auth/login")
                                .header("X-Forwarded-For", "10.0.0.17")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "email", "alice@teste.com",
                                                "password", "123456"
                                ))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getHeaders("Set-Cookie");

                String tokenA = extrairValorCookie(loginSetCookies, "refreshToken");
                String csrfToken = extrairValorCookie(loginSetCookies, "csrfToken");

                List<String> refreshSetCookies = mockMvc.perform(post("/api/auth/refresh-token")
                                .header("X-Forwarded-For", "10.0.0.17")
                                .header("X-CSRF-Token", csrfToken)
                                .cookie(new jakarta.servlet.http.Cookie("csrfToken", csrfToken))
                                .cookie(new jakarta.servlet.http.Cookie("refreshToken", tokenA)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getHeaders("Set-Cookie");

                assertThat(extrairValorCookie(refreshSetCookies, "refreshToken")).isNotEqualTo(tokenA);

                mockMvc.perform(post("/api/auth/refresh-token")
                                .header("X-Forwarded-For", "10.0.0.99")
                                .header("X-CSRF-Token", csrfToken)
                                .cookie(new jakarta.servlet.http.Cookie("csrfToken", csrfToken))
                                .cookie(new jakarta.servlet.http.Cookie("refreshToken", tokenA)))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("TOKEN_REUSE_DETECTED"))
                        .andExpect(jsonPath("$.message").value("Sessão invalidada por segurança. Faça login novamente."));

                var usuario = usuarioRepository.findByEmail("alice@teste.com").orElseThrow();
                long validTokens = refreshTokenRepository.countValidTokensByUsuario(usuario, LocalDateTime.now());
                assertThat(validTokens).isZero();
    }

    @Test
    void logout_deveLimparCookie() throws Exception {
        usuarioRepository.save(TestDataFactory.usuario("Alice", "alice@teste.com", passwordEncoder.encode("123456")));

        List<String> setCookies = mockMvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", "10.0.0.16")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "alice@teste.com",
                        "password", "123456"
                ))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeaders("Set-Cookie");

        String refreshToken = extrairValorCookie(setCookies, "refreshToken");
        String csrfToken = extrairValorCookie(setCookies, "csrfToken");

        List<String> logoutSetCookies = mockMvc.perform(post("/api/auth/logout")
                .header("X-CSRF-Token", csrfToken)
                .cookie(new jakarta.servlet.http.Cookie("csrfToken", csrfToken))
                .cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Logout realizado com sucesso"))
            .andReturn()
            .getResponse()
            .getHeaders("Set-Cookie");

        assertThat(logoutSetCookies).anySatisfy(cookie -> assertThat(cookie).contains("refreshToken=", "Max-Age=0"));
        assertThat(logoutSetCookies).anySatisfy(cookie -> assertThat(cookie).contains("csrfToken=", "Max-Age=0"));
    }

    @Test
    void login_deveBloquearContaAposFalhasConsecutivas() throws Exception {
        usuarioRepository.save(TestDataFactory.usuario("Alice", "alice@teste.com", passwordEncoder.encode("123456")));

        String ip = "10.0.0.20";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .header("X-Forwarded-For", ip + i)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of(
                            "email", "alice@teste.com",
                            "password", "senha-errada"
                    ))))
                .andExpect(status().isUnprocessableEntity());
        }

        mockMvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", "10.0.0.30")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "alice@teste.com",
                        "password", "senha-errada"
                ))))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void login_deveResetarFalhasAposSucesso() throws Exception {
        usuarioRepository.save(TestDataFactory.usuario("Alice", "alice@teste.com", passwordEncoder.encode("123456")));

        mockMvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", "10.0.0.40")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "alice@teste.com",
                        "password", "senha-errada"
                ))))
            .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", "10.0.0.41")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "alice@teste.com",
                        "password", "123456"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists());

        mockMvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", "10.0.0.42")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "alice@teste.com",
                        "password", "senha-errada"
                ))))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void resetPassword_deveFalharComSenhaFraca() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "token", "fake-token",
                        "novaSenha", "12345678"
                ))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void validateToken_deveAplicarRateLimitEmGet() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/auth/validate-token")
                    .header("X-Forwarded-For", "10.0.0.50")
                    .param("token", "fake-token-" + i))
                .andExpect(status().isUnprocessableEntity());
        }

        mockMvc.perform(get("/api/auth/validate-token")
                .header("X-Forwarded-For", "10.0.0.50")
                .param("token", "fake-token-final"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "60"));
    }

    private String extrairValorCookie(List<String> setCookies, String nome) {
        assertThat(setCookies).isNotNull();
        String setCookie = setCookies.stream()
            .filter(cookie -> cookie.startsWith(nome + "="))
            .findFirst()
            .orElseThrow();
        String parChaveValor = setCookie.split(";", 2)[0];
        return parChaveValor.split("=", 2)[1];
    }
}
