package pt.ubi.di.pdm.aplicacaopesca.data

import android.database.sqlite.SQLiteDatabase
import androidx.core.content.contentValuesOf
import java.text.Normalizer

/** Escreve nas tabelas os dados de [DadosIniciais]. Chamado dentro da transação de onCreate/onUpgrade. */
object DadosIniciaisBD {

    /** «Viana do Castelo» → «viana-do-castelo» (id estável de um distrito). */
    fun slug(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
            .lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    fun existeDistrito(db: SQLiteDatabase, id: String): Boolean =
        db.rawQuery("SELECT 1 FROM distritos WHERE id = ?", arrayOf(id)).use { it.moveToFirst() }

    fun primeiroDistrito(db: SQLiteDatabase): String =
        db.rawQuery("SELECT id FROM distritos ORDER BY rowid LIMIT 1", null).use { it.moveToFirst(); it.getString(0) }

    fun inserir(db: SQLiteDatabase) {
        val linguas = DadosIniciais.idiomas.map { it.first }
        DadosIniciais.idiomas.forEach { (codigo, nome) ->
            db.insertOrThrow("idiomas", null, contentValuesOf("codigo" to codigo, "nome" to nome))
        }
        DadosIniciais.distritos.forEach { d ->
            db.insertOrThrow("distritos", null, contentValuesOf(
                "id" to slug(d.nome), "nome" to d.nome, "lon" to d.lon, "lat" to d.lat,
                "regiao" to d.regiao.toString(), "etiqueta" to d.etiqueta))
        }
        DadosIniciais.tiposLicenca.forEach { t ->
            db.insertOrThrow("tipos_licenca", null, contentValuesOf("id" to t.id, "entidade" to t.entidade, "agua" to t.agua.name))
            linguas.forEach { l ->
                db.insertOrThrow("tipo_licenca_texto", null, contentValuesOf(
                    "tipo_id" to t.id, "lingua" to l, "nome" to t.nome.em(l), "como_obter" to t.comoObter.em(l)))
            }
        }
        DadosIniciais.iscos.forEachIndexed { i, s ->
            db.insertOrThrow("iscos", null, contentValuesOf("id" to s.id, "formulario" to if (s.formulario) 1 else 0, "ordem" to i))
            linguas.forEach { l ->
                db.insertOrThrow("isco_texto", null, contentValuesOf("isco_id" to s.id, "lingua" to l, "nome" to s.nome.em(l)))
            }
        }
        DadosIniciais.especies.forEachIndexed { i, e ->
            db.insertOrThrow("especies", null, contentValuesOf(
                "id" to e.id, "numero" to i + 1, "latim" to e.latim, "grupo" to e.grupo.id, "agua" to e.agua.name,
                "cor_dorso" to e.cores[0], "cor_flanco" to e.cores[1], "cor_ventre" to e.cores[2], "cor_barbatanas" to e.cores[3]))
            linguas.forEach { l ->
                db.insertOrThrow("especie_texto", null, contentValuesOf(
                    "especie_id" to e.id, "lingua" to l, "nome" to e.nome.em(l), "tipo" to e.tipo.em(l),
                    "tecnica" to e.tecnica.em(l), "tamanho" to e.tamanho.em(l), "epoca" to e.epoca.em(l),
                    "habitat" to e.habitat.em(l), "nota" to e.nota?.em(l)))
            }
            e.iscos.forEachIndexed { j, isco ->
                db.insertOrThrow("especie_isco", null, contentValuesOf("especie_id" to e.id, "isco_id" to isco, "ordem" to j))
            }
            e.sinais.forEachIndexed { j, sinal ->
                val sinalId = db.insertOrThrow("especie_sinal", null, contentValuesOf("especie_id" to e.id, "ordem" to j))
                linguas.forEach { l ->
                    db.insertOrThrow("especie_sinal_texto", null, contentValuesOf("sinal_id" to sinalId, "lingua" to l, "texto" to sinal.em(l)))
                }
            }
        }
        DadosIniciais.pesqueiros.forEach { p ->
            db.insertOrThrow("pesqueiros", null, contentValuesOf(
                "id" to p.id, "curto" to p.curto, "etiqueta" to p.etiqueta.toString(), "tipo" to p.tipo.id,
                "distrito_id" to slug(p.distrito), "concelho" to p.concelho, "lat" to p.lat, "lon" to p.lon,
                "agua" to p.agua.name, "aproximada" to if (p.aproximada) 1 else 0, "do_utilizador" to 0))
            linguas.forEach { l ->
                db.insertOrThrow("pesqueiro_texto", null, contentValuesOf(
                    "pesqueiro_id" to p.id, "lingua" to l, "nome" to p.nome.em(l), "bacia" to p.bacia.em(l), "referencia" to p.referencia.em(l)))
            }
            p.especies.forEachIndexed { j, id ->
                db.insertOrThrow("pesqueiro_especie", null, contentValuesOf("pesqueiro_id" to p.id, "especie_id" to id, "ordem" to j))
            }
            p.licencas.forEachIndexed { j, id ->
                db.insertOrThrow("pesqueiro_licenca", null, contentValuesOf("pesqueiro_id" to p.id, "tipo_id" to id, "ordem" to j))
            }
        }
    }
}
