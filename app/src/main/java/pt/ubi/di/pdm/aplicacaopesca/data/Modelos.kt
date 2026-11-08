package pt.ubi.di.pdm.aplicacaopesca.data

import androidx.annotation.StringRes
import pt.ubi.di.pdm.aplicacaopesca.R
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class Agua(@StringRes val rotulo: Int) {
    DOCE(R.string.agua_tipo_doce), SALGADA(R.string.agua_tipo_salgada), SALOBRA(R.string.agua_tipo_salobra)
}

/** Grupo de uma espécie. O id é o que fica na base de dados; o texto vem dos recursos (traduzido). */
enum class Grupo(val id: String, @StringRes val rotulo: Int) {
    PEIXE("peixe", R.string.grupo_peixe), CRUSTACEO("crustaceo", R.string.grupo_crustaceo);
    companion object { fun de(id: String) = entries.firstOrNull { it.id == id } ?: PEIXE }
}

/** Tipo de local de pesca. O id fica na base de dados; o texto vem dos recursos (traduzido). */
enum class TipoLocal(val id: String, @StringRes val rotulo: Int) {
    RIO("rio", R.string.tipo_local_rio), BARRAGEM("barragem", R.string.tipo_local_barragem),
    ALBUFEIRA("albufeira", R.string.tipo_local_albufeira), PRAIA("praia", R.string.tipo_local_praia),
    FALESIA("falesia", R.string.tipo_local_falesia), ESTUARIO("estuario", R.string.tipo_local_estuario),
    LAGOA("lagoa", R.string.tipo_local_lagoa), MARGEM("margem", R.string.tipo_local_margem),
    OUTRO("outro", R.string.tipo_local_outro);
    companion object { fun de(id: String) = entries.firstOrNull { it.id == id } ?: OUTRO }
}

/** Espécie (peixe ou crustáceo). Todas as espécies fazem parte da Peixedex. Os textos estão no idioma atual. */
data class Especie(
    val id: String,
    val nome: String,
    val latim: String,
    val agua: Agua,
    val grupo: Grupo,
    val tipo: String,             // ex.: "Predador · Salmonídeo"
    val tecnica: String,
    val tamanho: String,
    val epoca: String,
    val habitat: String,
    val iscos: List<String>,
    val identificar: List<String>,
    /** 4 cores: dorso, flanco, ventre, barbatanas (ARGB) */
    val cores: IntArray,
    val nota: String? = null
) {
    /** Número na Peixedex (1, 2, 3…), definido pela ordem no catálogo. */
    var numero: Int = 0
        internal set
    val crustaceo get() = grupo == Grupo.CRUSTACEO
}

/** Tipo de licença de pesca (valores indicativos). */
data class TipoLicenca(
    val id: String,
    val nome: String,
    val entidade: String,
    val agua: Agua,
    val comoObter: String
)

/** Pesqueiro / local de pesca. [exige] são os tipos de licença necessários. */
data class Pesqueiro(
    val id: String,
    var nome: String,
    val curto: String,
    /** onde desenhar o nome no mapa: 't' cima, 'b' baixo, 'l' esquerda, 'r' direita */
    val etiqueta: Char,
    var regiao: String,           // "Distrito · Concelho"
    val tipo: TipoLocal,
    val lat: Double,
    val lon: Double,
    val especies: List<String>,
    val agua: Agua,
    val exige: List<String>,
    val bacia: String? = null,
    val referencia: String? = null,
    val aproximada: Boolean = false,
    /** true se foi criado pelo utilizador (fica guardado na base de dados) */
    val doUtilizador: Boolean = false
)

data class Licenca(
    val id: Long,
    val tipo: String,
    val numero: String,
    val inicio: LocalDate,
    val fim: LocalDate
)

data class Captura(
    val id: Long,
    val data: LocalDate,
    val pesqueiro: String,
    val especie: String,
    val pesoKg: Double,
    val isco: String?,            // id do isco (tabela iscos)
    val foto: String?             // caminho do ficheiro da fotografia (pode ser null)
)

enum class Estado { OK, AVISO, ERRO }

data class EstadoLicenca(val dias: Long, val total: Long, val estado: Estado)

/** Resultado da verificação de uma licença exigida num pesqueiro. */
data class Exigencia(
    val tipo: String,
    val estado: Estado,
    val licenca: Licenca?,
    val dias: Long?
) {
    val emFalta get() = licenca == null
}

data class EstadoPesqueiro(val itens: List<Exigencia>) {
    val emFalta get() = itens.count { it.estado == Estado.ERRO }
    val estado: Estado
        get() = when {
            itens.any { it.estado == Estado.ERRO } -> Estado.ERRO
            itens.any { it.estado == Estado.AVISO } -> Estado.AVISO
            else -> Estado.OK
        }
    val menorPrazo: Long? get() = itens.filter { it.estado == Estado.AVISO }.mapNotNull { it.dias }.minOrNull()
    val temLicencaEmFalta get() = itens.any { it.emFalta }
}

fun estadoDe(l: Licenca, hoje: LocalDate, diasAviso: Int): EstadoLicenca {
    val dias = ChronoUnit.DAYS.between(hoje, l.fim)
    val total = maxOf(1L, ChronoUnit.DAYS.between(l.inicio, l.fim))
    val e = when {
        dias < 0 -> Estado.ERRO
        dias <= diasAviso -> Estado.AVISO
        else -> Estado.OK
    }
    return EstadoLicenca(dias, total, e)
}
