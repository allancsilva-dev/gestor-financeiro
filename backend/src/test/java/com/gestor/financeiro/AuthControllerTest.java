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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
                        "password", "123456"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.email").value("alice@teste.com"));
    }

    @Test
    void register_deveFalharQuandoEmailDuplicado() throws Exception {
        usuarioRepository.save(TestDataFactory.usuario("Alice", "alice@teste.com", passwordEncoder.encode("123456")));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "nome", "Alice 2",
                        "email", "alice@teste.com",
                        "password", "123456"
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
                        "password", "123"
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
            .andExpect(cookie().exists("refreshToken"));
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
        usuarioRepository.save(TestDataFactory.usuario("Alice", "alice@teste.com", passwordEncoder.encode("123456")));

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .header("X-Forwarded-For", "10.0.0.13")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of(
                            "email", "alice@teste.com",
                            "password", "senha-errada"
                    ))))
                .andExpect(status().isUnprocessableEntity());
        }

        mockMvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", "10.0.0.13")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "alice@teste.com",
                        "password", "senha-errada"
                ))))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "60"));
    }

    @Test
    void refreshToken_deveRenovarComCookieValido() throws Exception {
        usuarioRepository.save(TestDataFactory.usuario("Alice", "alice@teste.com", passwordEncoder.encode("123456")));

        String setCookie = mockMvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", "10.0.0.14")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "alice@teste.com",
                        "password", "123456"
                ))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader("Set-Cookie");

        String refreshToken = extrairValorCookie(setCookie);

        mockMvc.perform(post("/api/auth/refresh-token")
                .cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(cookie().exists("refreshToken"));
    }

    @Test
    void refreshToken_deveFalharComTokenRevogado() throws Exception {
        usuarioRepository.save(TestDataFactory.usuario("Alice", "alice@teste.com", passwordEncoder.encode("123456")));

        String setCookie = mockMvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", "10.0.0.15")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "alice@teste.com",
                        "password", "123456"
                ))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader("Set-Cookie");

        String refreshToken = extrairValorCookie(setCookie);

        mockMvc.perform(post("/api/auth/logout")
                .cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshToken)))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh-token")
                .cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshToken)))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                        .andExpect(jsonPath("$.message").value("Sessão invalidada por segurança"));
    }

    @Test
    void logout_deveLimparCookie() throws Exception {
        usuarioRepository.save(TestDataFactory.usuario("Alice", "alice@teste.com", passwordEncoder.encode("123456")));

        String setCookie = mockMvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", "10.0.0.16")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "alice@teste.com",
                        "password", "123456"
                ))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader("Set-Cookie");

        String refreshToken = extrairValorCookie(setCookie);

        String logoutSetCookie = mockMvc.perform(post("/api/auth/logout")
                .cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Logout realizado com sucesso"))
            .andReturn()
            .getResponse()
            .getHeader("Set-Cookie");

        assertThat(logoutSetCookie).contains("Max-Age=0");
    }

    private String extrairValorCookie(String setCookie) {
        assertThat(setCookie).isNotNull();
        String parChaveValor = setCookie.split(";", 2)[0];
        return parChaveValor.split("=", 2)[1];
    }
}
