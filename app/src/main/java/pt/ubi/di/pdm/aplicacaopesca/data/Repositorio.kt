package pt.ubi.di.pdm.aplicacaopesca.data

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.contentValuesOf
import pt.ubi.di.pdm.aplicacaopesca.R
import java.time.LocalDate

/**
 * Ponto único de acesso aos dados da app.
 * Chamar Repositorio.iniciar(context) uma vez (é feito em MainActivity / App).
 */
object Repositorio {

    private lateinit var bd: BaseDados
    private lateinit var prefs: SharedPreferences
    private lateinit var appCtx: Context

    var lingua = "pt"
        private set

    fun iniciar(context: Context) {
        if (::bd.isInitialized) return
        appCtx = context.applicationContext
        bd = BaseDados(appCtx)
        prefs = appCtx.getSharedPreferences("definicoes", Context.MODE_PRIVATE)
        recarregarCatalogo()
    }

    /** «auto» (segue o sistema) ou o código de um idioma da tabela idiomas. */
    var idiomaEscolhido: String
        get() = prefs.getString("idioma", "auto") ?: "auto"
        set(v) = prefs.edit().putString("idioma", v).apply()

    fun idiomas(): List<Pair<String, String>> {
        val lista = mutableListOf<Pair<String, String>>()
        bd.readableDatabase.rawQuery("SELECT codigo, nome FROM idiomas ORDER BY rowid", null).use { c ->
            while (c.moveToNext()) lista += c.getString(0) to c.getString(1)
        }
        return lista
    }

    private fun suportado(codigo: String?): String? = codigo?.takeIf { s -> idiomas().any { it.first == s } }

    /** Idioma do sistema (não o da app), se existir tradução; senão «pt». */
    fun idiomaDoSistema(): String = suportado(Resources.getSystem().configuration.locales[0].language) ?: "pt"

    private fun idiomaEfetivo(): String =
        suportado(AppCompatDelegate.getApplicationLocales().get(0)?.language)
            ?: suportado(idiomaEscolhido.takeIf { it != "auto" })
            ?: idiomaDoSistema()

    /** Relê espécies, iscos, tipos de licença e distritos no idioma atual (ou em [idioma], ao mudar de idioma). */
    fun recarregarCatalogo(idioma: String? = null) {
        lingua = idioma ?: idiomaEfetivo()
        carregarCatalogo(bd.readableDatabase)
    }

    private fun carregarCatalogo(db: SQLiteDatabase) {
        val l = arrayOf(lingua)
        val iscos = agrupar(db, "SELECT ei.especie_id, COALESCE(t.nome, f.nome) FROM especie_isco ei " +
            "LEFT JOIN isco_texto t ON t.isco_id = ei.isco_id AND t.lingua = ? " +
            "LEFT JOIN isco_texto f ON f.isco_id = ei.isco_id AND f.lingua = 'pt' ORDER BY ei.especie_id, ei.ordem", l)
        val sinais = agrupar(db, "SELECT s.especie_id, COALESCE(t.texto, f.texto) FROM especie_sinal s " +
            "LEFT JOIN especie_sinal_texto t ON t.sinal_id = s.id AND t.lingua = ? " +
            "LEFT JOIN especie_sinal_texto f ON f.sinal_id = s.id AND f.lingua = 'pt' ORDER BY s.especie_id, s.ordem", l)
        val especies = mutableListOf<Especie>()
        db.rawQuery("SELECT e.id, e.numero, COALESCE(t.nome, f.nome), e.latim, e.agua, e.grupo, " +
            "COALESCE(t.tipo, f.tipo), COALESCE(t.tecnica, f.tecnica), COALESCE(t.tamanho, f.tamanho), " +
            "COALESCE(t.epoca, f.epoca), COALESCE(t.habitat, f.habitat), COALESCE(t.nota, f.nota), " +
            "e.cor_dorso, e.cor_flanco, e.cor_ventre, e.cor_barbatanas FROM especies e " +
            "LEFT JOIN especie_texto t ON t.especie_id = e.id AND t.lingua = ? " +
            "LEFT JOIN especie_texto f ON f.especie_id = e.id AND f.lingua = 'pt' ORDER BY e.numero", l).use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                especies += Especie(id, c.getString(2), c.getString(3), Agua.valueOf(c.getString(4)), Grupo.de(c.getString(5)),
                    c.getString(6), c.getString(7), c.getString(8), c.getString(9), c.getString(10),
                    iscos[id] ?: emptyList(), sinais[id] ?: emptyList(),
                    intArrayOf(c.getInt(12), c.getInt(13), c.getInt(14), c.getInt(15)), c.getString(11)
                ).also { it.numero = c.getInt(1) }
            }
        }
        val tipos = mutableListOf<TipoLicenca>()
        db.rawQuery("SELECT x.id, COALESCE(t.nome, f.nome), x.entidade, x.agua, COALESCE(t.como_obter, f.como_obter) " +
            "FROM tipos_licenca x " +
            "LEFT JOIN tipo_licenca_texto t ON t.tipo_id = x.id AND t.lingua = ? " +
            "LEFT JOIN tipo_licenca_texto f ON f.tipo_id = x.id AND f.lingua = 'pt' ORDER BY x.rowid", l).use { c ->
            while (c.moveToNext()) tipos += TipoLicenca(c.getString(0), c.getString(1), c.getString(2), Agua.valueOf(c.getString(3)), c.getString(4))
        }
        val iscosFormulario = mutableListOf<Catalogo.Isco>()
        val nomesIscos = mutableMapOf<String, String>()
        db.rawQuery("SELECT i.id, i.formulario, COALESCE(t.nome, f.nome) FROM iscos i " +
            "LEFT JOIN isco_texto t ON t.isco_id = i.id AND t.lingua = ? " +
            "LEFT JOIN isco_texto f ON f.isco_id = i.id AND f.lingua = 'pt' ORDER BY i.ordem", l).use { c ->
            while (c.moveToNext()) {
                nomesIscos[c.getString(0)] = c.getString(2)
                if (c.getInt(1) == 1) iscosFormulario += Catalogo.Isco(c.getString(0), c.getString(2))
            }
        }
        val distritos = mutableListOf<Catalogo.Distrito>()
        db.rawQuery("SELECT nome, lon, lat, regiao, etiqueta FROM distritos ORDER BY rowid", null).use { c ->
            while (c.moveToNext()) distritos += Catalogo.Distrito(c.getString(0), c.getDouble(1), c.getDouble(2), c.getString(3).first(), c.getString(4))
        }
        Catalogo.carregar(especies, iscosFormulario, nomesIscos, tipos, distritos)
    }

    /** Junta linhas (id, valor) numa lista de valores por id, pela ordem da consulta. */
    private fun agrupar(db: SQLiteDatabase, sql: String, args: Array<String>? = null): Map<String, List<String>> {
        val m = LinkedHashMap<String, MutableList<String>>()
        db.rawQuery(sql, args).use { c -> while (c.moveToNext()) m.getOrPut(c.getString(0)) { mutableListOf() }.add(c.getString(1)) }
        return m
    }

    val hoje: LocalDate get() = LocalDate.now()

    /** Dias antes do fim a partir dos quais uma licença fica «A expirar». */
    var diasAviso: Int
        get() = prefs.getInt("dias_aviso", 30)
        set(v) = prefs.edit().putInt("dias_aviso", v).apply()

    var modoEscuro: Boolean
        get() = prefs.getBoolean("modo_escuro", true)
        set(v) = prefs.edit().putBoolean("modo_escuro", v).apply()

    var nomePerfil: String
        get() = prefs.getString("nome_perfil", null) ?: appCtx.getString(R.string.perf_nome_padrao)
        set(v) = prefs.edit().putString("nome_perfil", v).apply()

    /** Caminho absoluto para a foto de perfil (null = sem foto, mostra iniciais). */
    var fotoPerfil: String?
        get() = prefs.getString("foto_perfil", null)
        set(v) = prefs.edit().putString("foto_perfil", v).apply()

    var mostrarDistritos: Boolean
        get() = prefs.getBoolean("distritos", true)
        set(v) = prefs.edit().putBoolean("distritos", v).apply()

    fun pesqueiros(): List<Pesqueiro> {
        val db = bd.readableDatabase
        val especies = agrupar(db, "SELECT pesqueiro_id, especie_id FROM pesqueiro_especie ORDER BY pesqueiro_id, ordem")
        val licencas = agrupar(db, "SELECT pesqueiro_id, tipo_id FROM pesqueiro_licenca ORDER BY pesqueiro_id, ordem")
        val lista = mutableListOf<Pesqueiro>()
        db.rawQuery("SELECT p.id, COALESCE(t.nome, f.nome), p.curto, p.etiqueta, d.nome || ' · ' || p.concelho, p.tipo, " +
            "p.lat, p.lon, p.agua, COALESCE(t.bacia, f.bacia), COALESCE(t.referencia, f.referencia), p.aproximada, p.do_utilizador " +
            "FROM pesqueiros p JOIN distritos d ON d.id = p.distrito_id " +
            "LEFT JOIN pesqueiro_texto t ON t.pesqueiro_id = p.id AND t.lingua = ? " +
            "LEFT JOIN pesqueiro_texto f ON f.pesqueiro_id = p.id AND f.lingua = 'pt' ORDER BY p.rowid", arrayOf(lingua)).use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                lista += Pesqueiro(
                    id = id, nome = c.getString(1), curto = c.getString(2), etiqueta = c.getString(3).first(),
                    regiao = c.getString(4), tipo = TipoLocal.de(c.getString(5)), lat = c.getDouble(6), lon = c.getDouble(7),
                    especies = especies[id] ?: emptyList(), agua = Agua.valueOf(c.getString(8)),
                    exige = licencas[id] ?: emptyList(), bacia = c.getString(9), referencia = c.getString(10),
                    aproximada = c.getInt(11) == 1, doUtilizador = c.getInt(12) == 1
                )
            }
        }
        return lista
    }

    fun pesqueiro(id: String): Pesqueiro? = pesqueiros().firstOrNull { it.id == id }

    /** Apaga um pesqueiro. Devolve false se ainda tem capturas registadas (a base de dados não deixa). */
    fun apagarPesqueiro(id: String): Boolean =
        try { bd.writableDatabase.delete("pesqueiros", "id=?", arrayOf(id)) > 0 }
        catch (_: SQLiteConstraintException) { false }

    /**
     * Guarda um pesqueiro. Novo → fica como do utilizador (texto em «pt», que serve para todos os idiomas).
     * Existente do utilizador → atualiza tudo. Existente do catálogo → só tipo, posição, água e o nome no idioma atual.
     */
    fun guardarPesqueiro(p: Pesqueiro) {
        val db = bd.writableDatabase
        db.beginTransaction()
        try {
            val partes = p.regiao.split(" · ")
            val distritoId = db.rawQuery("SELECT id FROM distritos WHERE nome = ?", arrayOf(partes[0])).use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: DadosIniciaisBD.primeiroDistrito(db)
            val concelho = partes.getOrElse(1) { partes[0] }
            val comuns = contentValuesOf(
                "tipo" to p.tipo.id, "lat" to p.lat, "lon" to p.lon, "agua" to p.agua.name,
                "aproximada" to if (p.aproximada) 1 else 0
            )
            val doUtilizador = db.rawQuery("SELECT do_utilizador FROM pesqueiros WHERE id = ?", arrayOf(p.id)).use {
                if (it.moveToFirst()) it.getInt(0) == 1 else null
            }
            when (doUtilizador) {
                null -> {
                    comuns.put("id", p.id); comuns.put("curto", p.curto); comuns.put("etiqueta", p.etiqueta.toString())
                    comuns.put("distrito_id", distritoId); comuns.put("concelho", concelho); comuns.put("do_utilizador", 1)
                    db.insertOrThrow("pesqueiros", null, comuns)
                    db.insertOrThrow("pesqueiro_texto", null, contentValuesOf(
                        "pesqueiro_id" to p.id, "lingua" to "pt", "nome" to p.nome, "referencia" to p.referencia))
                }
                true -> {
                    comuns.put("curto", p.curto); comuns.put("distrito_id", distritoId); comuns.put("concelho", concelho)
                    db.update("pesqueiros", comuns, "id=?", arrayOf(p.id))
                    db.update("pesqueiro_texto", contentValuesOf("nome" to p.nome, "referencia" to p.referencia),
                        "pesqueiro_id=? AND lingua='pt'", arrayOf(p.id))
                }
                false -> {
                    db.update("pesqueiros", comuns, "id=?", arrayOf(p.id))
                    if (db.update("pesqueiro_texto", contentValuesOf("nome" to p.nome), "pesqueiro_id=? AND lingua=?", arrayOf(p.id, lingua)) == 0) {
                        db.insertOrThrow("pesqueiro_texto", null, contentValuesOf("pesqueiro_id" to p.id, "lingua" to lingua, "nome" to p.nome))
                    }
                }
            }
            db.delete("pesqueiro_especie", "pesqueiro_id=?", arrayOf(p.id))
            db.delete("pesqueiro_licenca", "pesqueiro_id=?", arrayOf(p.id))
            p.especies.forEachIndexed { i, e ->
                db.insertOrThrow("pesqueiro_especie", null, contentValuesOf("pesqueiro_id" to p.id, "especie_id" to e, "ordem" to i))
            }
            p.exige.forEachIndexed { i, t ->
                db.insertOrThrow("pesqueiro_licenca", null, contentValuesOf("pesqueiro_id" to p.id, "tipo_id" to t, "ordem" to i))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun licencas(): List<Licenca> {
        val lista = mutableListOf<Licenca>()
        bd.readableDatabase.rawQuery("SELECT id,tipo,numero,inicio,fim FROM licencas ORDER BY fim", null).use { c ->
            while (c.moveToNext()) {
                lista += Licenca(c.getLong(0), c.getString(1), c.getString(2),
                    LocalDate.parse(c.getString(3)), LocalDate.parse(c.getString(4)))
            }
        }
        return lista
    }

    /** Guarda uma licença nova (id = 0) ou atualiza uma existente. */
    fun guardarLicenca(l: Licenca): Long {
        val v = ContentValues().apply {
            put("tipo", l.tipo); put("numero", l.numero)
            put("inicio", l.inicio.toString()); put("fim", l.fim.toString())
        }
        val db = bd.writableDatabase
        return if (l.id == 0L) db.insert("licencas", null, v)
        else { db.update("licencas", v, "id=?", arrayOf(l.id.toString())); l.id }
    }

    fun apagarLicenca(id: Long) {
        bd.writableDatabase.delete("licencas", "id=?", arrayOf(id.toString()))
    }

    /** A licença do utilizador que serve para o tipo exigido (a que acaba mais tarde). */
    fun melhorLicenca(exigido: String, licencas: List<Licenca> = licencas()): Licenca? =
        licencas.filter { Catalogo.cobre(it.tipo, exigido) }.maxByOrNull { it.fim }

    fun estadoPesqueiro(p: Pesqueiro, licencas: List<Licenca> = licencas()): EstadoPesqueiro =
        EstadoPesqueiro(p.exige.map { t ->
            val l = melhorLicenca(t, licencas)
            if (l == null) Exigencia(t, Estado.ERRO, null, null)
            else { val e = estadoDe(l, hoje, diasAviso); Exigencia(t, e.estado, l, e.dias) }
        })

    fun capturas(): List<Captura> {
        val lista = mutableListOf<Captura>()
        bd.readableDatabase.rawQuery(
            "SELECT id,data,pesqueiro,especie,peso,isco,foto FROM capturas ORDER BY data DESC, id DESC", null
        ).use { c ->
            while (c.moveToNext()) {
                lista += Captura(c.getLong(0), LocalDate.parse(c.getString(1)), c.getString(2), c.getString(3),
                    c.getDouble(4), if (c.isNull(5)) null else c.getString(5), if (c.isNull(6)) null else c.getString(6))
            }
        }
        return lista
    }

    private fun valoresCaptura(c: Captura) = ContentValues().apply {
        put("data", c.data.toString()); put("pesqueiro", c.pesqueiro); put("especie", c.especie)
        put("peso", c.pesoKg); put("isco", c.isco); put("foto", c.foto)
    }

    fun adicionarCaptura(c: Captura): Long = bd.writableDatabase.insert("capturas", null, valoresCaptura(c))

    fun atualizarCaptura(c: Captura) {
        bd.writableDatabase.update("capturas", valoresCaptura(c), "id=?", arrayOf(c.id.toString()))
    }

    fun apagarCaptura(id: Long) {
        bd.writableDatabase.delete("capturas", "id=?", arrayOf(id.toString()))
    }

    /** Captura com mais peso (recorde). Null se ainda não há capturas. */
    fun recorde(cs: List<Captura> = capturas()): Captura? = cs.maxByOrNull { it.pesoKg }

    /** Primeira foto que o utilizador tirou desta espécie, ou null se não há. */
    fun fotoCapturada(especieId: String): String? =
        capturas().firstOrNull { it.especie == especieId && !it.foto.isNullOrBlank() }?.foto

    fun zonasDe(especie: String): List<Pesqueiro> = pesqueiros().filter { especie in it.especies }

    fun desbloqueadas(): Set<String> {
        val s = mutableSetOf<String>()
        bd.readableDatabase.rawQuery("SELECT especie FROM desbloqueadas", null).use { c -> while (c.moveToNext()) s += c.getString(0) }
        return s
    }

    /** Desbloqueia a espécie em TODOS os locais. Devolve true se era nova. */
    fun desbloquear(especie: String): Boolean {
        val r = bd.writableDatabase.insertWithOnConflict("desbloqueadas", null,
            ContentValues().apply { put("especie", especie) }, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
        return r != -1L
    }

    /** Carrega licenças, capturas e Peixedex de exemplo (para demonstração). */
    fun carregarExemplo() {
        val h = hoje
        val db = bd.writableDatabase
        db.beginTransaction()
        try {
            listOf(
                Triple("int", "PT-INT-018342", -357L to 8L),
                Triple("mar", "PT-MAR-004412", -155L to 209L),
                Triple("nao-res", "PT-NR-002201", -30L to -5L)
            ).forEach { (t, n, d) ->
                guardarLicenca(Licenca(0, t, n, h.plusDays(d.first), h.plusDays(d.second)))
            }
            listOf(
                arrayOf("zezere", "truta", "0.42", "mosca-seca", "6"), arrayOf("zezere", "boga", "0.30", "minhoca", "13"),
                arrayOf("meimoa", "achiga", "1.85", "colher", "20"), arrayOf("fratel", "barbo", "1.20", "milho", "37"),
                arrayOf("alqueva", "achiga", "3.85", "isco-artificial", "46")
            ).forEach { a ->
                adicionarCaptura(Captura(0, h.minusDays(a[4].toLong()), a[0], a[1], a[2].toDouble(), a[3], null))
            }
            listOf("truta", "boga", "barbo", "achiga").forEach { desbloquear(it) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Apaga licenças, capturas, Peixedex e os locais criados pelo utilizador. */
    fun comecarDoZero() {
        val db = bd.writableDatabase
        listOf("capturas", "licencas", "desbloqueadas").forEach { db.delete(it, null, null) }
        db.delete("pesqueiros", "do_utilizador=1", null)
    }
}
