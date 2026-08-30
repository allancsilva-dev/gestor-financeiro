// Mock do entry `logos-bancos-br/react-native`.
//
// O entry real é ESM com `require()` de PNG, e o preset `jest-expo` não
// transforma esse arquivo dentro de node_modules — importá-lo no teste dá
// "SyntaxError: Unexpected token 'export'". Mockar aqui é mais barato e mais
// estável que abrir `transformIgnorePatterns` do preset para um pacote.
//
// As CHAVES são as reais, lidas do próprio pacote, para o teste continuar
// valendo como contrato: se o pacote parar de publicar o ISPB do PicPay, o
// teste quebra. Só os valores são falsos — nenhum teste precisa do pixel.
const fs = require('fs');
const path = require('path');

// Resolve pelo package.json, não pelo entry: `require.resolve` dentro do Jest
// passa pelo moduleNameMapper e devolveria ESTE arquivo, deixando o mapa vazio.
const raiz = path.dirname(require.resolve('logos-bancos-br/package.json'));
const fonte = fs.readFileSync(path.join(raiz, 'react-native.js'), 'utf8');

const logos = {};
for (const [, chave, arquivo] of fonte.matchAll(
  /'(\d{4,8})': require\('\.\/logos\/png\/(\d{8})\.png'\)/g,
)) {
  // Id derivado do ARQUIVO: marcas que compartilham o desenho compartilham o
  // id do asset, como acontece no Metro de verdade.
  logos[chave] = Number(arquivo);
}

module.exports = { logos, default: logos };
