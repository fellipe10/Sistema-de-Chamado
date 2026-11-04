package br.com.goias.chamados.dominio.cadastro;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface FeriadoRepository extends JpaRepository<Feriado, Long> {

    /** Datas sem expediente da área: feriados de todas as áreas (area nula) + os só dela. */
    @Query("select distinct f.data from Feriado f where f.area is null or f.area.id = :areaId")
    List<LocalDate> datasDaArea(@Param("areaId") Long areaId);

    /** Anos que têm pelo menos um feriado cadastrado (aviso "próximo ano sem feriados", Etapa 5). */
    @Query("select distinct extract(year from f.data) from Feriado f")
    List<Integer> anosComFeriado();

    /** Feriados de um ano (tela de feriados), em ordem de data. */
    @Query("select f from Feriado f left join fetch f.area where f.data between :de and :ate order by f.data, f.descricao")
    List<Feriado> doPeriodo(@Param("de") LocalDate de, @Param("ate") LocalDate ate);

    /** Mesma data e mesmo alcance (todas as áreas ou a mesma área) — o banco não deixa repetir. */
    @Query("select count(f) from Feriado f where f.data = :data and f.id <> :ignorar"
            + " and ((:areaId is null and f.area is null) or f.area.id = :areaId)")
    long contarNaData(@Param("data") LocalDate data, @Param("areaId") Long areaId, @Param("ignorar") Long ignorarId);
}
