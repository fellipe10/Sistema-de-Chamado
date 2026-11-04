/*
 * Telas de administração (Etapa 5). JavaScript só para conforto: tudo funciona sem ele
 * (o servidor confere todas as regras).
 *
 * - Usuários: esconde o campo "Área" quando o perfil escolhido é Administrador.
 * - Expediente: botão "Limpar" de cada período apaga início e fim.
 */
(function () {
    'use strict';

    // ---------- Usuários: área só para atendente e gestor ----------
    var campoArea = document.getElementById('campoArea');
    if (campoArea) {
        var radios = document.querySelectorAll('input[name="perfil"]');
        var atualizar = function () {
            var escolhido = document.querySelector('input[name="perfil"]:checked');
            var admin = escolhido && escolhido.value === 'ADMIN';
            campoArea.hidden = !!admin;
        };
        radios.forEach(function (r) { r.addEventListener('change', atualizar); });
        atualizar();
    }

    // ---------- Expediente: limpar um período ----------
    document.querySelectorAll('[data-limpar-periodo]').forEach(function (botao) {
        botao.hidden = false;
        botao.addEventListener('click', function () {
            var linha = botao.closest('.periodo');
            if (!linha) return;
            linha.querySelectorAll('input[type="time"]').forEach(function (campo) { campo.value = ''; });
        });
    });
})();
