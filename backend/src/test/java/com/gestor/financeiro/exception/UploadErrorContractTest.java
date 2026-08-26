package com.gestor.financeiro.exception;

import com.gestor.financeiro.model.enums.ImportFailureCode;
import com.gestor.financeiro.service.importacao.ImportParsingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Falha de upload precisa chegar ao cliente como erro dele, com código estável.
 * Antes destes handlers, todas caíam no catch-all e viravam 500 INTERNAL_ERROR.
 */
class UploadErrorContractTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RotasQueFalham())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void arquivoAcimaDoLimiteViraPayloadTooLarge() throws Exception {
        mockMvc.perform(get("/teste/upload-grande"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("UPLOAD_TOO_LARGE"));
    }

    @Test
    void multipartMalformadoViraBadRequest() throws Exception {
        mockMvc.perform(get("/teste/multipart-quebrado"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MULTIPART"));
    }

    @Test
    void parteObrigatoriaAusenteViraBadRequest() throws Exception {
        mockMvc.perform(get("/teste/sem-arquivo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUEST_PART"));
    }

    @Test
    void falhaDeParsingViraUnprocessableComCodigoDeFalha() throws Exception {
        mockMvc.perform(get("/teste/parsing"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IMPORT_PARSING_FAILED"))
                .andExpect(jsonPath("$.details.failureCode").value("ROW_LIMIT_EXCEEDED"));
    }

    @RestController
    static class RotasQueFalham {
        @GetMapping("/teste/upload-grande")
        void grande() {
            throw new MaxUploadSizeExceededException(10_485_760L);
        }

        @GetMapping("/teste/multipart-quebrado")
        void quebrado() {
            throw new MultipartException("corpo interrompido");
        }

        @GetMapping("/teste/sem-arquivo")
        void semArquivo() throws MissingServletRequestPartException {
            throw new MissingServletRequestPartException("file");
        }

        @GetMapping("/teste/parsing")
        void parsing() throws ImportParsingException {
            throw new ImportParsingException(ImportFailureCode.ROW_LIMIT_EXCEEDED, "Limite de registros excedido");
        }
    }
}
