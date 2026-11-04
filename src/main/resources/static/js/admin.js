/*
 * Telas de administração (Etapa 5). JavaScript só para conforto: tudo funciona sem ele
 * (o servidor confere todas as regras).
 *
 * - Usuários: esconde o campo "Área" quando o perfil escolhido é Administrador.
 * - Usuários (novo): sugere o "Usuário" a partir do nome enquanto o admin não editar o campo
 *   (mesma regra de RegrasLogin.sugerir; o servidor confirma se está livre e acrescenta -2, -3...).
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

    // ---------- Usuários: sugestão do usuário (login) a partir do nome ----------
    var campoLogin = document.querySelector('input[data-sugerir-login]');
    var campoNome = document.getElementById('nome');
    if (campoLogin && campoNome) {
        var PARTICULAS = ['da', 'das', 'de', 'di', 'do', 'dos', 'du', 'e'];
        var semAcento = function (texto) {
            return texto.normalize('NFD').replace(/[\u0300-\u036f]/g, '');
        };
        // Igual a RegrasLogin.sugerir (Java): primeiro nome + "." + último sobrenome
        var sugerir = function (nome) {
            var limpo = semAcento(nome || '').toLowerCase()
                .replace(/['\u2019`\u00b4]/g, '')
                .replace(/[^a-z]+/g, ' ')
                .trim();
            if (limpo === '') {
                return '';
            }
            var palavras = limpo.split(/ +/);
            var sobrenome = null;
            for (var i = palavras.length - 1; i > 0; i--) {
                if (PARTICULAS.indexOf(palavras[i]) < 0) {
                    sobrenome = palavras[i];
                    break;
                }
            }
            var sugestao = sobrenome ? palavras[0] + '.' + sobrenome : palavras[0];
            return sugestao.slice(0, 30).replace(/[.-]+$/, '');
        };
        // Igual a RegrasLogin.normalizar: sem espaços nas pontas, minúsculo, sem acento
        var normalizar = function (texto) {
            return semAcento(texto.trim()).toLowerCase();
        };

        // Já veio preenchido (voltou do servidor com erro)? Então é do admin: não sobrescrever.
        var editado = campoLogin.value.trim() !== '';
        var espera = null;
        var url = campoLogin.getAttribute('data-sugerir-login');

        var conferirNoServidor = function () {
            if (!window.fetch || !url) {
                return;
            }
            var nome = campoNome.value;
            fetch(url + '?nome=' + encodeURIComponent(nome), { credentials: 'same-origin' })
                .then(function (r) { return r.ok ? r.text() : null; })
                .then(function (livre) {
                    if (livre !== null && !editado && campoNome.value === nome) {
                        campoLogin.value = livre;
                    }
                })
                .catch(function () { /* sem rede: fica a sugestão local */ });
        };

        campoNome.addEventListener('input', function () {
            if (editado) {
                return;
            }
            campoLogin.value = sugerir(campoNome.value);
            clearTimeout(espera);
            espera = setTimeout(conferirNoServidor, 350);
        });
        campoLogin.addEventListener('input', function () {
            // Apagou o campo? Volta a sugerir a partir do nome.
            editado = campoLogin.value.trim() !== '';
        });
        campoLogin.addEventListener('change', function () {
            campoLogin.value = normalizar(campoLogin.value);
        });
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
