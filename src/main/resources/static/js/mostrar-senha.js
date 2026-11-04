/*
 * Botão de "olho" para mostrar/esconder a senha.
 * Uso: <button type="button" data-mostrar-senha="idDoCampo">...</button>
 */
(function () {
    document.querySelectorAll('[data-mostrar-senha]').forEach(function (botao) {
        var campo = document.getElementById(botao.getAttribute('data-mostrar-senha'));
        if (!campo) return;
        botao.addEventListener('click', function () {
            var mostrar = campo.type === 'password';
            campo.type = mostrar ? 'text' : 'password';
            var rotulo = mostrar ? 'Esconder senha' : 'Mostrar senha';
            botao.setAttribute('aria-label', rotulo);
            botao.setAttribute('title', rotulo);
            var icone = botao.querySelector('i');
            if (icone) icone.className = mostrar ? 'bi bi-eye-slash' : 'bi bi-eye';
            campo.focus();
        });
    });
})();
