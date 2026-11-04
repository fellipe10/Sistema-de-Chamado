/*
 * Formulário público "Abrir chamado" — JavaScript puro, sem framework.
 *
 * - Mostra uma etapa por vez (Seus dados → O problema → Revisão); o envio é um único POST.
 * - Valida cada etapa antes de avançar (o servidor valida tudo de novo).
 * - Filtra as categorias pela área escolhida.
 * - Controla os anexos (até 3, 10 MB cada, tipos aceitos) e mostra a lista escolhida.
 * - Monta o resumo da revisão com o que a pessoa preencheu.
 * - Desabilita "Enviar chamado" depois do clique (evita chamado duplicado).
 *
 * Sem JavaScript o formulário continua funcionando: as três etapas aparecem juntas.
 */
(function () {
    'use strict';

    var form = document.getElementById('formAbertura');
    if (!form) return;

    var MAX_ANEXOS = 3;
    var MAX_BYTES = 10 * 1024 * 1024;
    var EXTENSOES = ['jpg', 'jpeg', 'png', 'pdf', 'docx', 'xlsx'];
    var MAX_DESCRICAO = 4000;
    var EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

    var paineis = form.querySelectorAll('.painel-etapa');
    var indicadores = form.querySelectorAll('[data-indicador]');
    var etapaAtual = 1;

    form.classList.add('js-ativo');

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------
    function campo(id) { return document.getElementById(id); }
    function valor(id) { var c = campo(id); return c ? c.value.trim() : ''; }
    function radioMarcado(nome) { return form.querySelector('input[name="' + nome + '"]:checked'); }
    function textoSelecionado(id) {
        var s = campo(id);
        return s && s.selectedIndex > 0 ? s.options[s.selectedIndex].text : '';
    }

    function mostrarErro(nomeCampo, mensagem) {
        var alvo = form.querySelector('[data-erro-de="' + nomeCampo + '"]');
        if (alvo) alvo.textContent = mensagem || '';
        var elemento = campo(nomeCampo);
        if (elemento && elemento.type !== 'radio' && elemento.type !== 'file') {
            elemento.classList.toggle('is-invalid', !!mensagem);
        }
        if (nomeCampo === 'areaId') {
            form.querySelectorAll('.opcao-area').forEach(function (l) { l.classList.toggle('is-invalid', !!mensagem); });
        }
        if (nomeCampo === 'anexos') {
            form.querySelector('.area-anexo').classList.toggle('is-invalid', !!mensagem);
        }
    }

    function limparErro(nomeCampo) { mostrarErro(nomeCampo, ''); }

    function focarPrimeiroErro(painel) {
        var invalido = painel.querySelector('.is-invalid');
        if (!invalido) return;
        if (invalido.classList.contains('opcao-area')) {
            var radio = painel.querySelector('input[name="areaId"]');
            if (radio) radio.focus();
        } else if (invalido.classList.contains('area-anexo')) {
            campo('anexos').focus();
        } else {
            invalido.focus();
        }
    }

    // ------------------------------------------------------------------
    // Troca de etapa
    // ------------------------------------------------------------------
    function irPara(etapa, focar) {
        etapaAtual = etapa;
        paineis.forEach(function (p) {
            p.classList.toggle('ativa', Number(p.dataset.etapa) === etapa);
        });
        indicadores.forEach(function (ind) {
            var n = Number(ind.dataset.indicador);
            ind.classList.toggle('atual', n === etapa);
            ind.classList.toggle('feita', n < etapa);
            if (n === etapa) ind.setAttribute('aria-current', 'step'); else ind.removeAttribute('aria-current');
        });
        if (etapa === 3) montarResumo();
        if (focar !== false) {
            var titulo = form.querySelector('.painel-etapa.ativa h2');
            if (titulo) { titulo.setAttribute('tabindex', '-1'); titulo.focus({ preventScroll: true }); }
            form.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    }

    form.querySelectorAll('[data-ir-para]').forEach(function (botao) {
        botao.addEventListener('click', function () {
            var destino = Number(botao.dataset.irPara);
            if (destino > etapaAtual) {
                for (var e = etapaAtual; e < destino; e++) {
                    if (!validarEtapa(e)) {
                        irPara(e, false);
                        focarPrimeiroErro(form.querySelector('.painel-etapa[data-etapa="' + e + '"]'));
                        return;
                    }
                }
            }
            irPara(destino);
        });
    });

    // ------------------------------------------------------------------
    // Validação por etapa (no navegador)
    // ------------------------------------------------------------------
    function validarEtapa(etapa) {
        var ok = true;
        function exigir(condicao, nome, mensagem) {
            if (condicao) { limparErro(nome); } else { mostrarErro(nome, mensagem); ok = false; }
        }

        if (etapa === 1) {
            exigir(valor('nome') !== '', 'nome', 'Informe seu nome completo.');
            exigir(valor('setorId') !== '', 'setorId', 'Selecione seu setor.');
            var email = valor('email');
            var telefone = valor('telefone');
            if (email === '' && telefone === '') {
                mostrarErro('email', 'Informe pelo menos um contato: e-mail ou telefone.');
                campo('telefone').classList.add('is-invalid');
                ok = false;
            } else if (email !== '' && !EMAIL_RE.test(email)) {
                mostrarErro('email', 'Informe um e-mail válido.');
                campo('telefone').classList.remove('is-invalid');
                ok = false;
            } else {
                limparErro('email');
                limparErro('telefone');
            }
        }

        if (etapa === 2) {
            exigir(!!radioMarcado('areaId'), 'areaId', 'Escolha a área: TI ou Manutenção.');
            exigir(valor('categoriaId') !== '', 'categoriaId', 'Selecione a categoria.');
            exigir(valor('localId') !== '', 'localId', 'Selecione o local.');
            exigir(valor('titulo') !== '', 'titulo', 'Informe um título.');
            exigir(valor('descricao') !== '', 'descricao', 'Descreva o problema.');
            exigir(!!radioMarcado('urgencia'), 'urgencia', 'Escolha a urgência.');
            var erroAnexo = validarAnexos(arquivos);
            exigir(!erroAnexo, 'anexos', erroAnexo);
        }

        if (etapa === 3) {
            exigir(campo('consentimento').checked, 'consentimento',
                'Para enviar, marque que concorda com o uso dos seus dados.');
        }
        return ok;
    }

    // Limpa o erro do campo assim que a pessoa corrige
    ['nome', 'setorId', 'categoriaId', 'localId', 'titulo', 'descricao'].forEach(function (id) {
        var c = campo(id);
        c.addEventListener(c.tagName === 'SELECT' ? 'change' : 'input', function () {
            if (c.value.trim() !== '') limparErro(id);
        });
    });
    ['email', 'telefone'].forEach(function (id) {
        campo(id).addEventListener('input', function () {
            if (valor('email') !== '' || valor('telefone') !== '') {
                limparErro('email');
                campo('telefone').classList.remove('is-invalid');
            }
        });
    });
    campo('consentimento').addEventListener('change', function () {
        if (this.checked) limparErro('consentimento');
    });
    form.querySelectorAll('input[name="urgencia"]').forEach(function (r) {
        r.addEventListener('change', function () { limparErro('urgencia'); });
    });

    // ------------------------------------------------------------------
    // Categoria filtrada pela área
    // ------------------------------------------------------------------
    var selectCategoria = campo('categoriaId');
    var todasCategorias = Array.prototype.slice.call(selectCategoria.querySelectorAll('option[data-area]'))
        .map(function (op) { return { id: op.value, area: op.dataset.area, nome: op.text }; });

    function filtrarCategorias() {
        var area = radioMarcado('areaId');
        var selecionada = selectCategoria.value;
        selectCategoria.innerHTML = '';
        var primeira = document.createElement('option');
        primeira.value = '';
        primeira.textContent = area ? 'Selecione' : 'Escolha a área primeiro';
        selectCategoria.appendChild(primeira);
        selectCategoria.disabled = !area;
        if (!area) return;
        todasCategorias.forEach(function (c) {
            if (c.area !== area.value) return;
            var op = document.createElement('option');
            op.value = c.id;
            op.textContent = c.nome;
            if (c.id === selecionada) op.selected = true;
            selectCategoria.appendChild(op);
        });
    }

    form.querySelectorAll('input[name="areaId"]').forEach(function (r) {
        r.addEventListener('change', function () {
            limparErro('areaId');
            filtrarCategorias();
        });
    });
    filtrarCategorias();

    // ------------------------------------------------------------------
    // Contador da descrição
    // ------------------------------------------------------------------
    var descricao = campo('descricao');
    var contador = campo('contadorDescricao');
    function atualizarContador() {
        contador.textContent = descricao.value.length + '/' + MAX_DESCRICAO;
    }
    descricao.addEventListener('input', atualizarContador);
    atualizarContador();

    // ------------------------------------------------------------------
    // Anexos
    // ------------------------------------------------------------------
    var inputAnexos = campo('anexos');
    var listaAnexos = campo('listaAnexos');
    var arquivos = [];
    var podeMontarLista = typeof DataTransfer !== 'undefined';

    function extensao(nome) {
        var i = nome.lastIndexOf('.');
        return i < 0 ? '' : nome.substring(i + 1).toLowerCase();
    }

    function tamanhoLegivel(bytes) {
        if (bytes < 1024) return bytes + ' B';
        if (bytes < 1024 * 1024) return Math.round(bytes / 1024) + ' KB';
        return (bytes / 1024 / 1024).toFixed(1).replace('.', ',') + ' MB';
    }

    function validarAnexos(lista) {
        if (lista.length > MAX_ANEXOS) return 'Envie no máximo ' + MAX_ANEXOS + ' arquivos.';
        for (var i = 0; i < lista.length; i++) {
            var a = lista[i];
            if (EXTENSOES.indexOf(extensao(a.name)) < 0) {
                return 'O arquivo "' + a.name + '" não é de um tipo aceito. Use JPG, PNG, PDF, DOCX ou XLSX.';
            }
            if (a.size === 0) return 'O arquivo "' + a.name + '" está vazio.';
            if (a.size > MAX_BYTES) return 'O arquivo "' + a.name + '" passa de 10 MB.';
        }
        return '';
    }

    function sincronizarInput() {
        if (!podeMontarLista) return;
        var dt = new DataTransfer();
        arquivos.forEach(function (a) { dt.items.add(a); });
        inputAnexos.files = dt.files;
    }

    function desenharLista() {
        listaAnexos.innerHTML = '';
        arquivos.forEach(function (a, indice) {
            var li = document.createElement('li');
            var icone = document.createElement('i');
            icone.className = 'bi ' + (/^(jpg|jpeg|png)$/.test(extensao(a.name)) ? 'bi-file-earmark-image' : 'bi-file-earmark-text');
            icone.setAttribute('aria-hidden', 'true');
            var nome = document.createElement('span');
            nome.className = 'nome';
            nome.textContent = a.name;
            var tam = document.createElement('span');
            tam.className = 'tamanho';
            tam.textContent = tamanhoLegivel(a.size);
            li.appendChild(icone);
            li.appendChild(nome);
            li.appendChild(tam);
            if (podeMontarLista) {
                var remover = document.createElement('button');
                remover.type = 'button';
                remover.className = 'remover';
                remover.setAttribute('aria-label', 'Remover ' + a.name);
                remover.innerHTML = '<i class="bi bi-x-lg" aria-hidden="true"></i>';
                remover.addEventListener('click', function () {
                    arquivos.splice(indice, 1);
                    sincronizarInput();
                    desenharLista();
                    var erro = validarAnexos(arquivos);
                    if (erro) mostrarErro('anexos', erro); else limparErro('anexos');
                });
                li.appendChild(remover);
            }
            listaAnexos.appendChild(li);
        });
        listaAnexos.hidden = arquivos.length === 0;
    }

    inputAnexos.addEventListener('change', function () {
        var novos = Array.prototype.slice.call(inputAnexos.files);
        if (podeMontarLista) {
            // Soma aos já escolhidos (ignora o mesmo arquivo escolhido duas vezes)
            novos.forEach(function (n) {
                var repetido = arquivos.some(function (a) {
                    return a.name === n.name && a.size === n.size && a.lastModified === n.lastModified;
                });
                if (!repetido) arquivos.push(n);
            });
            sincronizarInput();
        } else {
            arquivos = novos;
        }
        desenharLista();
        var erro = validarAnexos(arquivos);
        if (erro) mostrarErro('anexos', erro); else limparErro('anexos');
    });
    desenharLista();

    // ------------------------------------------------------------------
    // Resumo da revisão (só com o que a pessoa preencheu)
    // ------------------------------------------------------------------
    function juntar(partes) {
        return partes.filter(function (p) { return p; }).join(' · ');
    }

    function preencher(chave, texto) {
        var alvo = form.querySelector('[data-resumo="' + chave + '"]');
        if (alvo) alvo.textContent = texto || '—';
    }

    function montarResumo() {
        preencher('solicitante', juntar([valor('nome'), textoSelecionado('setorId'),
            valor('matricula') ? 'matrícula ' + valor('matricula') : '']));
        preencher('contato', juntar([valor('email'), valor('telefone')]));
        var area = radioMarcado('areaId');
        preencher('area', area ? area.dataset.nome : '');
        var urg = radioMarcado('urgencia');
        preencher('urgencia', urg ? urg.dataset.nome : '');
        preencher('categoria', textoSelecionado('categoriaId'));
        preencher('local', textoSelecionado('localId'));
        preencher('titulo', valor('titulo'));
        preencher('descricao', valor('descricao'));
        var itemAnexos = form.querySelector('[data-resumo-item="anexos"]');
        itemAnexos.hidden = arquivos.length === 0;
        preencher('anexos', arquivos.map(function (a) { return a.name; }).join(', '));
    }

    // ------------------------------------------------------------------
    // Envio
    // ------------------------------------------------------------------
    var btnEnviar = campo('btnEnviar');
    var enviando = false;

    form.addEventListener('submit', function (evento) {
        if (enviando) { evento.preventDefault(); return; }
        for (var e = 1; e <= 3; e++) {
            if (!validarEtapa(e)) {
                evento.preventDefault();
                irPara(e, false);
                focarPrimeiroErro(form.querySelector('.painel-etapa[data-etapa="' + e + '"]'));
                return;
            }
        }
        enviando = true;
        btnEnviar.disabled = true;
        btnEnviar.querySelector('.spinner-border').classList.remove('d-none');
        btnEnviar.querySelector('.texto').textContent = 'Enviando...';
    });

    // Se a pessoa voltar pelo botão "voltar" do navegador, reabilita o botão
    window.addEventListener('pageshow', function () {
        enviando = false;
        btnEnviar.disabled = false;
        btnEnviar.querySelector('.spinner-border').classList.add('d-none');
        btnEnviar.querySelector('.texto').textContent = 'Enviar chamado';
    });

    // ------------------------------------------------------------------
    // Início: abre na etapa indicada pelo servidor (1, ou a do primeiro erro)
    // ------------------------------------------------------------------
    var inicial = Number(form.dataset.etapaInicial) || 1;
    irPara(inicial, false);
    if (inicial > 1 || form.querySelector('.is-invalid')) {
        focarPrimeiroErro(form.querySelector('.painel-etapa.ativa'));
    }
})();
