/*
 * Painel do gestor e relatórios.
 * - Gráfico "Abertos x resolvidos" com Chart.js (servido localmente em /vendor/chartjs).
 *   Os dados vêm prontos do servidor num <script type="application/json" id="dadosGrafico">:
 *   { tipo: 'bar'|'line', rotulos: [...], mostrarRotulo: [...], dicas: [...], abertos: [...], resolvidos: [...] }
 * - Seletor de área do admin: troca sozinho ao escolher.
 * - Período personalizado: fecha a caixa ao clicar fora ou apertar Esc.
 * Sem JavaScript a página continua funcionando (filtros são links/formulários; o gráfico vira um texto-resumo).
 */
(function () {
    'use strict';

    // Seletor de área (admin)
    document.querySelectorAll('[data-enviar-ao-mudar]').forEach(function (campo) {
        campo.addEventListener('change', function () {
            if (campo.form) {
                campo.form.submit();
            }
        });
    });

    // Caixa "Período": fecha ao clicar fora / Esc
    var caixa = document.querySelector('.periodo-personalizado');
    if (caixa) {
        document.addEventListener('click', function (e) {
            if (caixa.open && !caixa.contains(e.target)) {
                caixa.open = false;
            }
        });
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape' && caixa.open) {
                caixa.open = false;
                caixa.querySelector('summary').focus();
            }
        });
    }

    // Gráfico
    var fonte = document.getElementById('dadosGrafico');
    var canvas = document.getElementById('graficoPainel');
    if (!fonte || !canvas || typeof Chart === 'undefined') {
        return;
    }
    var d;
    try {
        d = JSON.parse(fonte.textContent);
    } catch (e) {
        return;
    }

    var VERDE_MEDIO = '#7CC4A0';   // Abertos
    var VERDE_GOIAS = '#00491E';   // Resolvidos
    var linha = d.tipo === 'line';

    Chart.defaults.font.family = "Inter, system-ui, -apple-system, 'Segoe UI', sans-serif";
    Chart.defaults.font.size = 12;
    Chart.defaults.color = '#6B7280';

    function serie(rotulo, dados, cor) {
        var s = { label: rotulo, data: dados, backgroundColor: cor, borderColor: cor };
        if (linha) {
            s.borderWidth = 3;
            s.pointRadius = 0;
            s.pointHoverRadius = 5;
            s.pointHoverBackgroundColor = cor;
            s.cubicInterpolationMode = 'monotone'; // suave, sem passar abaixo de zero
            s.fill = false;
            s.borderCapStyle = 'round';
            s.borderJoinStyle = 'round';
        } else {
            s.borderRadius = { topLeft: 4, topRight: 4 };
            s.borderSkipped = 'bottom';
            s.maxBarThickness = 18;
            s.categoryPercentage = 0.7;
            s.barPercentage = 0.9;
        }
        return s;
    }

    new Chart(canvas, {
        type: d.tipo,
        data: {
            labels: d.rotulos,
            datasets: [serie('Abertos', d.abertos, VERDE_MEDIO), serie('Resolvidos', d.resolvidos, VERDE_GOIAS)]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: { mode: 'index', intersect: false },
            plugins: {
                legend: { display: false }, // a legenda fica no HTML, igual à prévia
                tooltip: {
                    backgroundColor: '#1F2937',
                    padding: 10,
                    cornerRadius: 8,
                    boxPadding: 4,
                    usePointStyle: true,
                    callbacks: {
                        title: function (itens) {
                            return itens.length ? d.dicas[itens[0].dataIndex] : '';
                        }
                    }
                }
            },
            scales: {
                x: {
                    grid: { display: false },
                    border: { color: '#E5E7EB' },
                    ticks: {
                        autoSkip: !linha,
                        maxRotation: 0,
                        // Na linha de 30 dias, um rótulo a cada 5 dias (o servidor já marcou quais)
                        callback: function (valor) {
                            return d.mostrarRotulo[valor] ? d.rotulos[valor] : '';
                        }
                    }
                },
                y: {
                    beginAtZero: true,
                    grid: { color: '#EEF2F0' },
                    border: { display: false },
                    ticks: { precision: 0, maxTicksLimit: 5 }
                }
            }
        }
    });
})();
