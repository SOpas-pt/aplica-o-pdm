package pt.ubi.di.pdm.aplicacaopesca.data

/**
 * Cache em memória dos dados de referência, lidos da base de dados no arranque e sempre que se muda de idioma
 * (ver Repositorio.recarregarCatalogo): espécies, iscos, tipos de licença e distritos, já no idioma atual.
 * Aqui fica também a geografia usada para desenhar o mapa (costa, fronteira, rios).
 * Os dados iniciais estão em DadosIniciais.kt.
 */
object Catalogo {

    /** Isco que aparece como botão no formulário de captura. */
    data class Isco(val id: String, val nome: String)

    lateinit var especies: List<Especie>
        private set
    lateinit var iscosFormulario: List<Isco>
        private set
    lateinit var tiposLicenca: List<TipoLicenca>
        private set
    lateinit var distritos: List<Distrito>
        private set
    private var porId = emptyMap<String, Especie>()
    private var tipoPorId = emptyMap<String, TipoLicenca>()
    private var nomesIscos = emptyMap<String, String>()

    fun carregar(
        especies: List<Especie>, iscosFormulario: List<Isco>, nomesIscos: Map<String, String>,
        tipos: List<TipoLicenca>, distritos: List<Distrito>
    ) {
        this.especies = especies; this.iscosFormulario = iscosFormulario; this.nomesIscos = nomesIscos
        tiposLicenca = tipos; this.distritos = distritos
        porId = especies.associateBy { it.id }
        tipoPorId = tipos.associateBy { it.id }
    }

    /** Nome do isco no idioma atual (o id, se não for conhecido). */
    fun iscoNome(id: String?): String? = id?.let { nomesIscos[it] ?: it }

    fun especie(id: String): Especie = porId.getValue(id)
    fun tipoLicenca(id: String): TipoLicenca = tipoPorId[id] ?: tipoPorId.getValue("int")

    /** Uma licença de não residente serve tanto em águas interiores como no mar. */
    fun cobre(tipoDaLicenca: String, exigido: String): Boolean =
        tipoDaLicenca == exigido || tipoDaLicenca == "nao-res"

    /** nome, longitude, latitude (centro aproximado), região de licença (N/C/S), etiqueta curta */
    data class Distrito(val nome: String, val lon: Double, val lat: Double, val regiao: Char, val etiqueta: String)


    /** Distrito mais próximo de um ponto (aproximação). */
    fun distritoDe(lat: Double, lon: Double): Distrito =
        distritos.minBy { d -> val dx = (d.lon - lon) * 0.77; val dy = d.lat - lat; dx * dx + dy * dy }

    /** Contorno simplificado de Portugal continental: costa (N→S) e fronteira (S→N). [lon, lat] */
    val costa = listOf(
        -8.87 to 41.87, -8.85 to 41.5, -8.78 to 41.15, -8.7 to 40.65, -8.9 to 40.15, -9.0 to 39.75, -9.4 to 39.35,
        -9.5 to 38.78, -9.2 to 38.68, -9.22 to 38.42, -8.9 to 38.5, -8.78 to 38.15, -8.85 to 37.9, -8.8 to 37.5,
        -8.99 to 37.02, -8.6 to 37.1, -8.2 to 37.1, -7.9 to 37.0, -7.4 to 37.17
    )
    val fronteira = listOf(
        -7.45 to 37.5, -7.5 to 37.75, -7.35 to 38.2, -7.25 to 38.5, -7.1 to 38.85, -7.0 to 39.1, -7.3 to 39.45,
        -7.15 to 39.7, -6.95 to 40.0, -6.85 to 40.4, -6.9 to 40.75, -6.8 to 41.05, -6.3 to 41.5, -6.55 to 41.9,
        -7.0 to 41.95, -7.4 to 41.85, -7.5 to 42.0, -7.9 to 41.9, -8.2 to 42.15, -8.65 to 42.03
    )
    /** Rios principais (Douro, Tejo, Mondego, Guadiana) — só decorativos. */
    val rios = listOf(
        listOf(-6.8 to 41.12, -7.3 to 41.15, -7.9 to 41.15, -8.3 to 41.1, -8.68 to 41.14),
        listOf(-7.0 to 39.65, -7.5 to 39.6, -8.0 to 39.45, -8.6 to 39.0, -9.0 to 38.78, -9.2 to 38.68),
        listOf(-7.5 to 40.4, -8.2 to 40.2, -8.85 to 40.15),
        listOf(-7.4 to 38.0, -7.45 to 37.5, -7.4 to 37.17)
    )
}
