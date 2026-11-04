/*
 * Fila em Kanban — atualização automática do quadro (só visualização).
 *
 * - A cada N segundos (data-intervalo, padrão 30) busca GET /atendimento/quadro com os mesmos filtros
 *   e troca o conteúdo de cada coluna, sem recarregar a página (filtros e rolagem continuam onde estavam).
 * - Aba escondida/minimizada: para de buscar. Ao voltar: atualiza na hora e retoma o ciclo.
 *   (Assim um computador esquecido aberto deixa a sessão de 8 h vencer normalmente.)
 * - Card novo ou que mudou de coluna: destaque por alguns segundos (classe "destaque").
 * - Atualiza "Atualizado às", o total do cabeçalho e o contador da fila no menu lateral.
 * - Sessão encerrada/vencida (o servidor responde com o login) → recarrega a página, e a pessoa cai no login.
 * - Sem conexão ou erro no servidor → mantém o quadro e mostra "Sem conexão com o servidor. Tentando de novo…".
 *
 * Sem JavaScript a página funciona normalmente; só não se atualiza sozinha.
 */
(function () {
    'use strict';

    var status = document.getElementById('kanbanStatus');
    var quadro = document.getElementById('quadro');
    if (!status || !quadro || !window.fetch || !window.DOMParser) {
        return;
    }

    var url = status.getAttribute('data-url-quadro');
    var intervalo = (parseInt(status.getAttribute('data-intervalo'), 10) || 30) * 1000;
    var horaEl = status.querySelector('[data-hora-atualizacao]');
    var erroEl = status.querySelector('[data-erro-conexao]');
    var botaoAgora = status.querySelector('[data-atualizar-agora]');
    var textoAuto = status.querySelector('.kanban-auto');
    var MENSAGEM_ERRO = 'Sem conexão com o servidor. Tentando de novo…';

    var timer = null;
    var buscando = false;

    if (textoAuto) {
        textoAuto.hidden = false;
    }

    /** id do chamado → coluna em que ele está agora. */
    function mapaDeCards(raiz) {
        var mapa = {};
        raiz.querySelectorAll('.kanban-coluna').forEach(function (coluna) {
            var chave = coluna.getAttribute('data-coluna');
            coluna.querySelectorAll('li[data-id]').forEach(function (li) {
                mapa[li.getAttribute('data-id')] = chave;
            });
        });
        return mapa;
    }

    function aplicar(novo) {
        var antes = mapaDeCards(quadro);

        // Troca o conteúdo de cada coluna (o contêiner do quadro fica: a rolagem lateral não volta ao início)
        novo.querySelectorAll('.kanban-coluna').forEach(function (colunaNova) {
            var chave = colunaNova.getAttribute('data-coluna');
            var colunaAtual = quadro.querySelector('.kanban-coluna[data-coluna="' + chave + '"]');
            if (colunaAtual) {
                colunaAtual.innerHTML = colunaNova.innerHTML;
            }
        });

        // Destaca o que entrou ou mudou de coluna
        quadro.querySelectorAll('.kanban-coluna').forEach(function (coluna) {
            var chave = coluna.getAttribute('data-coluna');
            coluna.querySelectorAll('li[data-id]').forEach(function (li) {
                if (antes[li.getAttribute('data-id')] !== chave) {
                    var card = li.querySelector('.kanban-card');
                    if (card) {
                        card.classList.add('destaque');
                    }
                }
            });
        });

        // Hora e totais
        if (horaEl && novo.getAttribute('data-hora')) {
            horaEl.textContent = novo.getAttribute('data-hora');
        }
        var totalCabecalho = document.querySelector('strong[data-total-quadro]');
        if (totalCabecalho && novo.getAttribute('data-total-quadro') !== null) {
            totalCabecalho.textContent = novo.getAttribute('data-total-quadro');
        }
        atualizarContadorMenu(parseInt(novo.getAttribute('data-total-fila'), 10));
    }

    /** Contador ao lado de "Fila de chamados" no menu lateral (some quando é zero). */
    function atualizarContadorMenu(total) {
        if (isNaN(total)) {
            return;
        }
        document.querySelectorAll('a[href="/atendimento"]').forEach(function (link) {
            var contador = link.querySelector('.contador');
            if (total > 0) {
                if (!contador) {
                    contador = document.createElement('span');
                    contador.className = 'contador';
                    link.appendChild(contador);
                }
                contador.textContent = total;
            } else if (contador) {
                contador.remove();
            }
        });
    }

    function atualizar() {
        if (buscando) {
            return;
        }
        buscando = true;
        status.classList.add('atualizando');
        fetch(url, {
            credentials: 'same-origin',
            cache: 'no-store',
            headers: { 'X-Requested-With': 'fetch' }
        })
            .then(function (resposta) {
                // Sessão vencida/encerrada: o servidor manda para o login (ou para a troca de senha)
                if (resposta.redirected || resposta.status === 401 || resposta.status === 403) {
                    window.location.reload();
                    return null;
                }
                if (!resposta.ok) {
                    throw new Error('HTTP ' + resposta.status);
                }
                return resposta.text();
            })
            .then(function (html) {
                if (html === null) {
                    return;
                }
                var doc = new DOMParser().parseFromString(html, 'text/html');
                var novo = doc.getElementById('quadro');
                if (!novo) {
                    // Veio outra página no lugar do quadro (ex.: login): recarrega para mostrar o certo
                    window.location.reload();
                    return;
                }
                aplicar(novo);
                if (erroEl) {
                    erroEl.textContent = '';
                }
            })
            .catch(function () {
                if (erroEl) {
                    erroEl.textContent = MENSAGEM_ERRO;
                }
            })
            .then(function () {
                buscando = false;
                status.classList.remove('atualizando');
            });
    }

    function parar() {
        if (timer) {
            clearInterval(timer);
            timer = null;
        }
    }

    function agendar() {
        parar();
        if (document.visibilityState === 'visible') {
            timer = setInterval(atualizar, intervalo);
        }
    }

    document.addEventListener('visibilitychange', function () {
        if (document.visibilityState === 'visible') {
            atualizar();
            agendar();
        } else {
            parar();
        }
    });

    if (botaoAgora) {
        botaoAgora.addEventListener('click', function (evento) {
            evento.preventDefault();
            atualizar();
            agendar();
        });
    }

    agendar();
})();
