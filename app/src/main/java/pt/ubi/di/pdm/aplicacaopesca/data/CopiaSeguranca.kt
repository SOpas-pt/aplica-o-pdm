package pt.ubi.di.pdm.aplicacaopesca.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate

/**
 * Cópia de segurança em JSON (org.json já vem no Android): licenças, capturas e espécies desbloqueadas.
 * As fotografias não vão no ficheiro (são caminhos deste telemóvel).
 */
object CopiaSeguranca {
    private const val FORMATO = "caderno-do-pescador"
    private const val VERSAO = 1

    /** Resultado da importação: quantos registos entraram e quantos foram ignorados (repetidos ou inválidos). */
    data class Resultado(val capturas: Int, val licencas: Int, val ignorados: Int)

    fun exportar(): String {
        val licencas = JSONArray()
        Repositorio.licencas().forEach {
            licencas.put(JSONObject().put("tipo", it.tipo).put("numero", it.numero)
                .put("inicio", it.inicio.toString()).put("fim", it.fim.toString()))
        }
        val capturas = JSONArray()
        Repositorio.capturas().forEach {
            capturas.put(JSONObject().put("data", it.data.toString()).put("pesqueiro", it.pesqueiro)
                .put("especie", it.especie).put("pesoKg", it.pesoKg).put("isco", it.isco ?: JSONObject.NULL))
        }
        return JSONObject().put("formato", FORMATO).put("versao", VERSAO)
            .put("licencas", licencas).put("capturas", capturas)
            .put("desbloqueadas", JSONArray(Repositorio.desbloqueadas().toList()))
            .toString(2)
    }

    /**
     * Junta ao que já existe os registos válidos do ficheiro, sem duplicar.
     * Lança [IllegalArgumentException] se o ficheiro não for uma cópia desta app (nada é alterado).
     */
    fun importar(texto: String): Resultado {
        val json = try {
            JSONObject(texto).also { require(it.optString("formato") == FORMATO) }
        } catch (_: JSONException) {
            throw IllegalArgumentException("ficheiro inválido")
        }
        var ilegiveis = 0
        val iscosValidos = Catalogo.iscosFormulario.map { it.id }.toSet()

        val licencas = mutableListOf<Licenca>()
        val ls = json.optJSONArray("licencas") ?: JSONArray()
        for (i in 0 until ls.length()) {
            try {
                val o = ls.getJSONObject(i)
                licencas += Licenca(0, o.getString("tipo"), o.getString("numero"),
                    LocalDate.parse(o.getString("inicio")), LocalDate.parse(o.getString("fim")))
            } catch (_: Exception) { ilegiveis++ }
        }
        val capturas = mutableListOf<Captura>()
        val cs = json.optJSONArray("capturas") ?: JSONArray()
        for (i in 0 until cs.length()) {
            try {
                val o = cs.getJSONObject(i)
                capturas += Captura(0, LocalDate.parse(o.getString("data")), o.getString("pesqueiro"), o.getString("especie"),
                    o.getDouble("pesoKg"), o.optString("isco").takeIf { it in iscosValidos }, null)
            } catch (_: Exception) { ilegiveis++ }
        }

        val novasLicencas = licencasNovas(licencas, Repositorio.licencas()) { t -> Catalogo.tiposLicenca.any { it.id == t } }
        val novasCapturas = capturasNovas(capturas, Repositorio.capturas(),
            { Repositorio.pesqueiro(it) != null }, { id -> Catalogo.especies.any { it.id == id } })
        novasLicencas.forEach { Repositorio.guardarLicenca(it) }
        novasCapturas.forEach { Repositorio.adicionarCaptura(it); Repositorio.desbloquear(it.especie) }

        val ds = json.optJSONArray("desbloqueadas") ?: JSONArray()
        for (i in 0 until ds.length()) {
            val id = ds.optString(i)
            if (Catalogo.especies.any { it.id == id }) Repositorio.desbloquear(id)
        }
        val ignorados = ilegiveis + (licencas.size - novasLicencas.size) + (capturas.size - novasCapturas.size)
        return Resultado(novasCapturas.size, novasLicencas.size, ignorados)
    }

    /** Capturas do ficheiro que podem entrar: válidas e que ainda não existem (nem repetidas no próprio ficheiro). */
    fun capturasNovas(
        candidatas: List<Captura>, existentes: List<Captura>,
        pesqueiroExiste: (String) -> Boolean, especieExiste: (String) -> Boolean
    ): List<Captura> {
        val vistas = existentes.map { listOf(it.data, it.pesqueiro, it.especie, it.pesoKg) }.toMutableSet()
        return candidatas.filter { c ->
            c.pesoKg > 0 && pesqueiroExiste(c.pesqueiro) && especieExiste(c.especie) &&
                vistas.add(listOf(c.data, c.pesqueiro, c.especie, c.pesoKg))
        }
    }

    /** Licenças do ficheiro que podem entrar: tipo conhecido, número preenchido, datas certas e ainda não existentes. */
    fun licencasNovas(
        candidatas: List<Licenca>, existentes: List<Licenca>, tipoExiste: (String) -> Boolean
    ): List<Licenca> {
        val vistas = existentes.map { listOf(it.tipo, it.numero, it.inicio, it.fim) }.toMutableSet()
        return candidatas.map { it.copy(numero = it.numero.trim()) }.filter { l ->
            tipoExiste(l.tipo) && l.numero.isNotEmpty() && !l.fim.isBefore(l.inicio) &&
                vistas.add(listOf(l.tipo, l.numero, l.inicio, l.fim))
        }
    }
}
