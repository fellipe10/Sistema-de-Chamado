/*
 * Botão "Copiar" do código do chamado.
 *
 * O sistema roda em http:// na intranet, onde navigator.clipboard normalmente NÃO existe
 * (só funciona em https ou localhost). Por isso:
 * 1. tenta navigator.clipboard (quando houver);
 * 2. senão, seleciona o texto do código na tela e usa document.execCommand('copy');
 * 3. se nada funcionar, deixa o código selecionado e pede para a pessoa usar Ctrl+C.
 *
 * Etapa 5: também copia a senha temporária (admin/usuarios). O botão pode ter data-nome="Senha"
 * para as mensagens ("Senha copiada."); sem ele, fala de "Código".
 */
(function () {
    'use strict';

    var botao = document.querySelector('[data-copiar]');
    if (!botao) return;

    var alvo = document.getElementById(botao.dataset.copiar);
    var textoBotao = botao.querySelector('.texto');
    var aviso = document.getElementById('avisoCopia');
    var nome = botao.dataset.nome || 'Código';
    var feminino = botao.dataset.nome === 'Senha';

    function selecionarCodigo() {
        var selecao = window.getSelection();
        var intervalo = document.createRange();
        intervalo.selectNodeContents(alvo);
        selecao.removeAllRanges();
        selecao.addRange(intervalo);
    }

    function copiarSelecionando() {
        selecionarCodigo();
        try {
            return document.execCommand('copy');
        } catch (e) {
            return false;
        }
    }

    function sucesso() {
        textoBotao.textContent = 'Copiado';
        if (aviso) aviso.textContent = nome + (feminino ? ' copiada.' : ' copiado.');
        setTimeout(function () {
            textoBotao.textContent = 'Copiar';
            if (aviso) aviso.textContent = '';
        }, 2500);
    }

    function falhou() {
        selecionarCodigo();
        if (aviso) aviso.textContent = (feminino ? 'A ' : 'O ') + nome.toLowerCase()
            + (feminino ? ' está selecionada' : ' está selecionado') + '. Use Ctrl+C (ou toque e segure) para copiar.';
    }

    botao.addEventListener('click', function () {
        var codigo = alvo.textContent.trim();
        if (navigator.clipboard && window.isSecureContext) {
            navigator.clipboard.writeText(codigo).then(sucesso, function () {
                if (copiarSelecionando()) sucesso(); else falhou();
            });
        } else if (copiarSelecionando()) {
            sucesso();
        } else {
            falhou();
        }
    });
})();
