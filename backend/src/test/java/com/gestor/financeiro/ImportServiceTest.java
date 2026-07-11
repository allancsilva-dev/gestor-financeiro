package com.gestor.financeiro;

import com.gestor.financeiro.dto.ImportResultDto;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.MovimentoCarteiraRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.ImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ImportServiceTest {

    @Autowired
    private ImportService importService;

    @Autowired
    private TransacaoRepository transacaoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private CarteiraRepository carteiraRepository;

    @Autowired
    private MovimentoCarteiraRepository movimentoCarteiraRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Usuario usuario;
    private Carteira carteira;
    private Categoria categoria;

    @BeforeEach
    void setup() {
        movimentoCarteiraRepository.deleteAll();
        transacaoRepository.deleteAll();
        categoriaRepository.deleteAll();
        carteiraRepository.deleteAll();
        usuarioRepository.deleteAll();

        usuario = usuarioRepository.save(TestDataFactory.usuario("Alice", "alice@teste.com", "hash"));
        carteira = carteiraRepository.save(
            TestDataFactory.carteira(usuario, "Nubank", new BigDecimal("1000.00")));
        categoria = categoriaRepository.save(TestDataFactory.categoria(usuario, "Mercado"));
    }

    private MockMultipartFile csv(String conteudo) {
        return new MockMultipartFile("file", "extrato.csv", "text/csv",
            conteudo.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void importar_comCarteira_atualizaSaldoEValorGastoDaCategoria() {
        ImportResultDto result = importService.importarCsv(usuario.getId(), csv("""
            data,descricao,valor,tipo,categoria,carteira,status
            2026-07-01,Feira,150.00,saida,Mercado,Nubank,pago
            2026-07-02,Salario,3000.00,entrada,,Nubank,pago
            """), null);

        assertEquals(2, result.getImportadas());
        assertEquals(0, result.getErros());

        Carteira atualizada = carteiraRepository.findById(carteira.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("3850.00").compareTo(atualizada.getSaldo()));

        Categoria cat = categoriaRepository.findById(categoria.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("150.00").compareTo(cat.getValorGasto()));

        assertEquals(2, movimentoCarteiraRepository.count());

        List<Transacao> transacoes = transacaoRepository.findByUsuarioId(usuario.getId());
        assertEquals(2, transacoes.size());
        assertTrue(transacoes.stream().allMatch(t -> t.getStatus() == StatusPagamento.PAGO));
    }

    @Test
    void importar_reimportacao_ignoraDuplicadasSemAlterarSaldo() {
        String conteudo = """
            data,descricao,valor,tipo,carteira
            2026-07-01,Feira,150.00,saida,Nubank
            """;
        importService.importarCsv(usuario.getId(), csv(conteudo), null);
        ImportResultDto segunda = importService.importarCsv(usuario.getId(), csv(conteudo), null);

        assertEquals(0, segunda.getImportadas());
        assertEquals(1, segunda.getDuplicadas());

        Carteira atualizada = carteiraRepository.findById(carteira.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("850.00").compareTo(atualizada.getSaldo()));
        assertEquals(1, transacaoRepository.findByUsuarioId(usuario.getId()).size());
    }

    @Test
    void importar_carteiraInexistente_contaErroSemDerrubarDemaisLinhas() {
        ImportResultDto result = importService.importarCsv(usuario.getId(), csv("""
            data,descricao,valor,tipo,carteira
            2026-07-01,Feira,150.00,saida,NaoExiste
            2026-07-02,Padaria,20.00,saida,Nubank
            """), null);

        assertEquals(1, result.getErros());
        assertEquals(1, result.getImportadas());
        assertEquals(1, transacaoRepository.findByUsuarioId(usuario.getId()).size());
    }

    @Test
    void importar_linhaCancelada_eIgnorada() {
        ImportResultDto result = importService.importarCsv(usuario.getId(), csv("""
            data,descricao,valor,tipo,status
            2026-07-01,Compra estornada,99.00,saida,cancelado
            """), null);

        assertEquals(1, result.getIgnoradas());
        assertEquals(0, transacaoRepository.findByUsuarioId(usuario.getId()).size());
    }

    @Test
    void importar_carteiraPadrao_aplicadaQuandoColunaAusente() {
        ImportResultDto result = importService.importarCsv(usuario.getId(), csv("""
            data,descricao,valor,tipo
            2026-07-01,Feira,150.00,saida
            """), carteira.getId());

        assertEquals(1, result.getImportadas());
        Carteira atualizada = carteiraRepository.findById(carteira.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("850.00").compareTo(atualizada.getSaldo()));
    }

    @Test
    void importar_carteiraPadraoDeOutroUsuario_rejeitada() {
        Usuario outro = usuarioRepository.save(TestDataFactory.usuario("Bob", "bob@teste.com", "hash"));
        Carteira alheia = carteiraRepository.save(
            TestDataFactory.carteira(outro, "Alheia", BigDecimal.ZERO));

        assertThrows(ResourceNotFoundException.class, () ->
            importService.importarCsv(usuario.getId(), csv("""
                data,descricao,valor,tipo
                2026-07-01,Feira,150.00,saida
                """), alheia.getId()));
    }

    @Test
    void importar_semCarteira_naoCriaMovimento() {
        ImportResultDto result = importService.importarCsv(usuario.getId(), csv("""
            data,descricao,valor,tipo,categoria
            2026-07-01,Feira,150.00,saida,Mercado
            """), null);

        assertEquals(1, result.getImportadas());
        assertEquals(0, movimentoCarteiraRepository.count());

        // Agregado de categoria atualizado mesmo sem carteira
        Categoria cat = categoriaRepository.findById(categoria.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("150.00").compareTo(cat.getValorGasto()));
    }
}
