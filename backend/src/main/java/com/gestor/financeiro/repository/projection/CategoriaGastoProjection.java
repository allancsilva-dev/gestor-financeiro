package com.gestor.financeiro.repository.projection;

import java.math.BigDecimal;

/**
 * `categorias.valor_gasto` é verdade materializada: escrita na criação da transação e estornada na
 * exclusão. Se um caminho novo (importação, reversão de lote) esquecer de mexer nela, o número da
 * tela deixa de bater com o extrato sem nada acusar — por isso a reconciliação passou a conferir.
 */
public interface CategoriaGastoProjection {
    Long getCategoriaId();
    BigDecimal getValorMaterializado();
    BigDecimal getValorLancado();
}
