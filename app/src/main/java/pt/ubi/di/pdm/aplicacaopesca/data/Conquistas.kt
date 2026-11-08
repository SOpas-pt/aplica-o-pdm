package pt.ubi.di.pdm.aplicacaopesca.data

import androidx.annotation.StringRes
import pt.ubi.di.pdm.aplicacaopesca.R

/** O que as conquistas medem: capturas, espécies desbloqueadas e o total de espécies do catálogo. */
data class DadosConquistas(val capturas: List<Captura>, val desbloqueadas: Int, val totalEspecies: Int)

data class Progresso(val conquista: Conquista, val atual: Int, val meta: Int) {
    val feita get() = atual >= meta
}

/**
 * Conquistas calculadas sempre a partir das capturas e da Peixedex (não há tabela própria):
 * por isso ficam certas ao editar, apagar ou importar capturas.
 */
enum class Conquista(
    @StringRes val titulo: Int,
    @StringRes val descricao: Int,
    private val meta: (DadosConquistas) -> Int,
    private val medida: (DadosConquistas) -> Int
) {
    PRIMEIRA(R.string.conq_primeira_t, R.string.conq_primeira_d, { 1 }, { it.capturas.size }),
    DEZ(R.string.conq_dez_t, R.string.conq_dez_d, { 10 }, { it.capturas.size }),
    ASSIDUO(R.string.conq_assiduo_t, R.string.conq_assiduo_d, { 25 }, { it.capturas.size }),
    ESPECIES5(R.string.conq_esp5_t, R.string.conq_esp5_d, { 5 }, { it.desbloqueadas }),
    ESPECIES10(R.string.conq_esp10_t, R.string.conq_esp10_d, { 10 }, { it.desbloqueadas }),
    PEIXEDEX(R.string.conq_todas_t, R.string.conq_todas_d, { maxOf(1, it.totalEspecies) }, { it.desbloqueadas }),
    GRAUDO(R.string.conq_graudo_t, R.string.conq_graudo_d, { 3 }, { d -> (d.capturas.maxOfOrNull { it.pesoKg } ?: 0.0).toInt() }),
    GIGANTE(R.string.conq_gigante_t, R.string.conq_gigante_d, { 10 }, { d -> (d.capturas.maxOfOrNull { it.pesoKg } ?: 0.0).toInt() }),
    EXPLORADOR(R.string.conq_explorador_t, R.string.conq_explorador_d, { 3 }, { d -> d.capturas.map { it.pesqueiro }.toSet().size }),
    FOTOGRAFO(R.string.conq_fotografo_t, R.string.conq_fotografo_d, { 5 }, { d -> d.capturas.count { !it.foto.isNullOrBlank() } });

    fun avaliar(d: DadosConquistas) = Progresso(this, minOf(medida(d), meta(d)), meta(d))

    companion object {
        fun todas(d: DadosConquistas): List<Progresso> = entries.map { it.avaliar(d) }

        /** Conquistas que passaram a estar feitas entre dois momentos (para avisar o utilizador). */
        fun novas(antes: DadosConquistas, depois: DadosConquistas): List<Conquista> =
            entries.filter { !it.avaliar(antes).feita && it.avaliar(depois).feita }
    }
}
