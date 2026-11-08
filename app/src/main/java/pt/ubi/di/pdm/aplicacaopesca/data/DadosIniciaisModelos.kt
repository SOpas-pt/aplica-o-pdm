package pt.ubi.di.pdm.aplicacaopesca.data

/** Texto nos idiomas suportados. O português é o idioma de recurso (usado quando falta uma tradução). */
class Texto(val pt: String, val en: String, val it: String) {
    fun em(lingua: String): String = when (lingua) { "en" -> en; "it" -> it; else -> pt }
}

/** Isco. [formulario] = aparece como botão no formulário de captura. O id é um «slug» do nome em português. */
class SIsco(val id: String, val nome: Texto, val formulario: Boolean = false)

class STipoLicenca(val id: String, val entidade: String, val agua: Agua, val nome: Texto, val comoObter: Texto)

/** [iscos] são ids de [SIsco]; [sinais] são os pontos de «como identificar». */
class SEspecie(
    val id: String, val latim: String, val grupo: Grupo, val agua: Agua, val cores: IntArray,
    val nome: Texto, val tipo: Texto, val tecnica: Texto, val tamanho: Texto, val epoca: Texto, val habitat: Texto,
    val iscos: List<String>, val sinais: List<Texto>, val nota: Texto? = null
)

/** [distrito] é o NOME do distrito (como em DadosIniciais.distritos); [especies] e [licencas] são ids. */
class SPesqueiro(
    val id: String, val curto: String, val etiqueta: Char, val tipo: TipoLocal, val distrito: String, val concelho: String,
    val lat: Double, val lon: Double, val agua: Agua, val nome: Texto, val bacia: Texto, val referencia: Texto,
    val especies: List<String>, val licencas: List<String>, val aproximada: Boolean = false
)
