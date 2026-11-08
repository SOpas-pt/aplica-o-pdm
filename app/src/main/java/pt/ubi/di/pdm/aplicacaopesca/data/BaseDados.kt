package pt.ubi.di.pdm.aplicacaopesca.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.content.contentValuesOf

/**
 * Base de dados local (SQLite), dividida em tabelas pequenas ligadas por chaves estrangeiras.
 *
 * Referência (vem com a app, preenchida por DadosIniciaisBD a partir de DadosIniciais.kt):
 *   idiomas, distritos, tipos_licenca (+ tipo_licenca_texto), iscos (+ isco_texto),
 *   especies (+ especie_texto, especie_isco, especie_sinal + especie_sinal_texto),
 *   pesqueiros (+ pesqueiro_texto, pesqueiro_especie, pesqueiro_licenca)
 * Do utilizador:
 *   licencas, capturas, desbloqueadas, e os pesqueiros com do_utilizador = 1
 *
 * Tudo o que é texto para mostrar está nas tabelas «*_texto», uma linha por idioma (pt é o idioma de recurso).
 * Para alterar um dado (ex.: o habitat de uma espécie) basta editar a linha; não é preciso mexer no código.
 */
class BaseDados(context: Context) : SQLiteOpenHelper(context, NOME, null, VERSAO) {

    companion object {
        const val NOME = "caderno_pescador.db"
        const val VERSAO = 3

        // tabelas, dos «filhos» para os «pais» (ordem segura para apagar com chaves estrangeiras ativas)
        private val TABELAS = listOf(
            "capturas", "desbloqueadas", "licencas", "pesqueiro_especie", "pesqueiro_licenca", "pesqueiro_texto",
            "pesqueiros", "especie_isco", "especie_sinal_texto", "especie_sinal", "especie_texto", "especies",
            "isco_texto", "iscos", "tipo_licenca_texto", "tipos_licenca", "distritos", "idiomas"
        )
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        criarEsquema(db)
        DadosIniciaisBD.inserir(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, antiga: Int, nova: Int) {
        if (antiga < 3) migrarParaV3(db, preservarDados = antiga == 2)
    }

    private fun criarEsquema(db: SQLiteDatabase) {
        listOf(
            "CREATE TABLE idiomas (codigo TEXT PRIMARY KEY, nome TEXT NOT NULL)",
            """CREATE TABLE distritos (
                id TEXT PRIMARY KEY,
                nome TEXT NOT NULL,
                lon REAL NOT NULL,
                lat REAL NOT NULL,
                regiao TEXT NOT NULL,
                etiqueta TEXT NOT NULL)""",
            """CREATE TABLE tipos_licenca (
                id TEXT PRIMARY KEY,
                entidade TEXT NOT NULL,
                agua TEXT NOT NULL)""",
            """CREATE TABLE tipo_licenca_texto (
                tipo_id TEXT NOT NULL REFERENCES tipos_licenca(id) ON DELETE CASCADE,
                lingua TEXT NOT NULL REFERENCES idiomas(codigo),
                nome TEXT NOT NULL,
                como_obter TEXT NOT NULL,
                PRIMARY KEY (tipo_id, lingua))""",
            """CREATE TABLE iscos (
                id TEXT PRIMARY KEY,
                formulario INTEGER NOT NULL DEFAULT 0,
                ordem INTEGER NOT NULL)""",
            """CREATE TABLE isco_texto (
                isco_id TEXT NOT NULL REFERENCES iscos(id) ON DELETE CASCADE,
                lingua TEXT NOT NULL REFERENCES idiomas(codigo),
                nome TEXT NOT NULL,
                PRIMARY KEY (isco_id, lingua))""",
            """CREATE TABLE especies (
                id TEXT PRIMARY KEY,
                numero INTEGER NOT NULL UNIQUE,
                latim TEXT NOT NULL,
                grupo TEXT NOT NULL,
                agua TEXT NOT NULL,
                cor_dorso INTEGER NOT NULL,
                cor_flanco INTEGER NOT NULL,
                cor_ventre INTEGER NOT NULL,
                cor_barbatanas INTEGER NOT NULL)""",
            """CREATE TABLE especie_texto (
                especie_id TEXT NOT NULL REFERENCES especies(id) ON DELETE CASCADE,
                lingua TEXT NOT NULL REFERENCES idiomas(codigo),
                nome TEXT NOT NULL,
                tipo TEXT NOT NULL,
                tecnica TEXT NOT NULL,
                tamanho TEXT NOT NULL,
                epoca TEXT NOT NULL,
                habitat TEXT NOT NULL,
                nota TEXT,
                PRIMARY KEY (especie_id, lingua))""",
            """CREATE TABLE especie_isco (
                especie_id TEXT NOT NULL REFERENCES especies(id) ON DELETE CASCADE,
                isco_id TEXT NOT NULL REFERENCES iscos(id),
                ordem INTEGER NOT NULL,
                PRIMARY KEY (especie_id, isco_id))""",
            """CREATE TABLE especie_sinal (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                especie_id TEXT NOT NULL REFERENCES especies(id) ON DELETE CASCADE,
                ordem INTEGER NOT NULL)""",
            """CREATE TABLE especie_sinal_texto (
                sinal_id INTEGER NOT NULL REFERENCES especie_sinal(id) ON DELETE CASCADE,
                lingua TEXT NOT NULL REFERENCES idiomas(codigo),
                texto TEXT NOT NULL,
                PRIMARY KEY (sinal_id, lingua))""",
            """CREATE TABLE pesqueiros (
                id TEXT PRIMARY KEY,
                curto TEXT NOT NULL,
                etiqueta TEXT NOT NULL DEFAULT 'b',
                tipo TEXT NOT NULL,
                distrito_id TEXT NOT NULL REFERENCES distritos(id),
                concelho TEXT NOT NULL,
                lat REAL NOT NULL,
                lon REAL NOT NULL,
                agua TEXT NOT NULL,
                aproximada INTEGER NOT NULL DEFAULT 0,
                do_utilizador INTEGER NOT NULL DEFAULT 0)""",
            """CREATE TABLE pesqueiro_texto (
                pesqueiro_id TEXT NOT NULL REFERENCES pesqueiros(id) ON DELETE CASCADE,
                lingua TEXT NOT NULL REFERENCES idiomas(codigo),
                nome TEXT NOT NULL,
                bacia TEXT,
                referencia TEXT,
                PRIMARY KEY (pesqueiro_id, lingua))""",
            """CREATE TABLE pesqueiro_especie (
                pesqueiro_id TEXT NOT NULL REFERENCES pesqueiros(id) ON DELETE CASCADE,
                especie_id TEXT NOT NULL REFERENCES especies(id),
                ordem INTEGER NOT NULL,
                PRIMARY KEY (pesqueiro_id, especie_id))""",
            """CREATE TABLE pesqueiro_licenca (
                pesqueiro_id TEXT NOT NULL REFERENCES pesqueiros(id) ON DELETE CASCADE,
                tipo_id TEXT NOT NULL REFERENCES tipos_licenca(id),
                ordem INTEGER NOT NULL,
                PRIMARY KEY (pesqueiro_id, tipo_id))""",
            """CREATE TABLE licencas (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                tipo TEXT NOT NULL REFERENCES tipos_licenca(id),
                numero TEXT NOT NULL DEFAULT '',
                inicio TEXT NOT NULL,
                fim TEXT NOT NULL)""",
            """CREATE TABLE capturas (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                data TEXT NOT NULL,
                pesqueiro TEXT NOT NULL REFERENCES pesqueiros(id),
                especie TEXT NOT NULL REFERENCES especies(id),
                peso REAL NOT NULL,
                isco TEXT REFERENCES iscos(id),
                foto TEXT)""",
            "CREATE TABLE desbloqueadas (especie TEXT PRIMARY KEY REFERENCES especies(id))",
            "CREATE INDEX idx_capturas_pesqueiro ON capturas(pesqueiro)",
            "CREATE INDEX idx_capturas_especie ON capturas(especie)"
        ).forEach { db.execSQL(it) }
    }

    /**
     * v2 → v3: o catálogo é partido em tabelas mais pequenas e os textos passam para tabelas por idioma.
     * Guarda em tabelas temporárias o que é do utilizador, refaz o esquema, volta a semear e repõe os dados.
     * Antes da v2 nunca houve versões distribuídas, por isso nesse caso recomeça-se limpo.
     */
    private fun migrarParaV3(db: SQLiteDatabase, preservarDados: Boolean) {
        if (preservarDados) {
            db.execSQL("CREATE TEMP TABLE t_licencas AS SELECT id,tipo,numero,inicio,fim FROM licencas")
            db.execSQL("CREATE TEMP TABLE t_capturas AS SELECT id,data,pesqueiro,especie,peso,isco,foto FROM capturas")
            db.execSQL("CREATE TEMP TABLE t_desbloqueadas AS SELECT especie FROM desbloqueadas")
            db.execSQL("""CREATE TEMP TABLE t_pesq AS SELECT id,nome,curto,etiqueta,regiao,tipo,lat,lon,agua,bacia,referencia,aproximada
                FROM pesqueiros WHERE do_utilizador = 1""")
            db.execSQL("CREATE TEMP TABLE t_pesq_esp AS SELECT pesqueiro_id,especie_id,ordem FROM pesqueiro_especie WHERE pesqueiro_id IN (SELECT id FROM t_pesq)")
            db.execSQL("CREATE TEMP TABLE t_pesq_lic AS SELECT pesqueiro_id,tipo_id,ordem FROM pesqueiro_licenca WHERE pesqueiro_id IN (SELECT id FROM t_pesq)")
        }
        // as tabelas antigas (v1 ou v2) saem dos «filhos» para os «pais»
        listOf("pesqueiros_utilizador", "pesqueiros_apagados", "pesqueiros_editados").forEach { db.execSQL("DROP TABLE IF EXISTS $it") }
        TABELAS.forEach { db.execSQL("DROP TABLE IF EXISTS $it") }
        criarEsquema(db)
        DadosIniciaisBD.inserir(db)
        if (!preservarDados) return

        db.execSQL("INSERT INTO licencas(id,tipo,numero,inicio,fim) SELECT id,tipo,numero,inicio,fim FROM t_licencas WHERE tipo IN (SELECT id FROM tipos_licenca)")
        db.execSQL("INSERT INTO desbloqueadas(especie) SELECT especie FROM t_desbloqueadas WHERE especie IN (SELECT id FROM especies)")

        db.rawQuery("SELECT id,nome,curto,etiqueta,regiao,tipo,lat,lon,agua,bacia,referencia,aproximada FROM t_pesq", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val partes = c.getString(4).split(" · ")
                val distritoId = DadosIniciaisBD.slug(partes[0]).takeIf { d -> DadosIniciaisBD.existeDistrito(db, d) } ?: DadosIniciaisBD.primeiroDistrito(db)
                db.insertOrThrow("pesqueiros", null, contentValuesOf(
                    "id" to id, "curto" to c.getString(2), "etiqueta" to c.getString(3), "tipo" to tipoLocalDe(c.getString(5)).id,
                    "distrito_id" to distritoId, "concelho" to partes.getOrElse(1) { partes[0] },
                    "lat" to c.getDouble(6), "lon" to c.getDouble(7), "agua" to c.getString(8),
                    "aproximada" to c.getInt(11), "do_utilizador" to 1))
                db.insertOrThrow("pesqueiro_texto", null, contentValuesOf(
                    "pesqueiro_id" to id, "lingua" to "pt", "nome" to c.getString(1), "bacia" to c.getString(9), "referencia" to c.getString(10)))
            }
        }
        db.execSQL("INSERT OR IGNORE INTO pesqueiro_especie(pesqueiro_id,especie_id,ordem) SELECT pesqueiro_id,especie_id,ordem FROM t_pesq_esp WHERE especie_id IN (SELECT id FROM especies)")
        db.execSQL("INSERT OR IGNORE INTO pesqueiro_licenca(pesqueiro_id,tipo_id,ordem) SELECT pesqueiro_id,tipo_id,ordem FROM t_pesq_lic WHERE tipo_id IN (SELECT id FROM tipos_licenca)")

        // o isco passa de texto («Minhoca») para o id do isco; se não houver correspondência fica vazio
        db.execSQL("""INSERT INTO capturas(id,data,pesqueiro,especie,peso,isco,foto)
            SELECT id,data,pesqueiro,especie,peso,
                (SELECT isco_id FROM isco_texto WHERE lingua = 'pt' AND nome = t_capturas.isco LIMIT 1), foto
            FROM t_capturas
            WHERE pesqueiro IN (SELECT id FROM pesqueiros) AND especie IN (SELECT id FROM especies)""")
        listOf("t_licencas", "t_capturas", "t_desbloqueadas", "t_pesq", "t_pesq_esp", "t_pesq_lic").forEach { db.execSQL("DROP TABLE $it") }
    }

    /** O tipo de local guardado até à v2 era o texto em português («Rio», «Rio - margem»…). */
    private fun tipoLocalDe(texto: String): TipoLocal = when (texto.lowercase()) {
        "rio" -> TipoLocal.RIO; "barragem" -> TipoLocal.BARRAGEM; "albufeira" -> TipoLocal.ALBUFEIRA
        "praia" -> TipoLocal.PRAIA; "falésia" -> TipoLocal.FALESIA; "estuário" -> TipoLocal.ESTUARIO
        "lagoa" -> TipoLocal.LAGOA; "rio - margem" -> TipoLocal.MARGEM; else -> TipoLocal.OUTRO
    }
}
