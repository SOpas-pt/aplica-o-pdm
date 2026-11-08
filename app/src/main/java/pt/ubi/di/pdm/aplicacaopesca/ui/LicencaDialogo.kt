package pt.ubi.di.pdm.aplicacaopesca.ui

import android.app.DatePickerDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import pt.ubi.di.pdm.aplicacaopesca.R
import pt.ubi.di.pdm.aplicacaopesca.data.Catalogo
import pt.ubi.di.pdm.aplicacaopesca.data.Licenca
import pt.ubi.di.pdm.aplicacaopesca.data.Repositorio
import pt.ubi.di.pdm.aplicacaopesca.util.pt
import java.time.LocalDate

/**
 * Janela para adicionar ou editar uma licença.
 * - [existente] = null → nova licença (pode vir com [tipoInicial] pré-preenchido)
 * - [aoMudar] é chamado depois de guardar ou apagar, para o ecrã se atualizar.
 */
object LicencaDialogo {

    fun mostrar(ctx: Context, existente: Licenca? = null, tipoInicial: String? = null, aoMudar: () -> Unit) {
        val v = LayoutInflater.from(ctx).inflate(R.layout.dialogo_licenca, null, false)
        val tipos = Catalogo.tiposLicenca
        var tipo = existente?.tipo ?: tipoInicial ?: "int"
        var inicio: LocalDate = existente?.inicio ?: Repositorio.hoje
        var fim: LocalDate? = existente?.fim ?: Repositorio.hoje.plusDays(364)

        val edtTipo = v.findViewById<AutoCompleteTextView>(R.id.edtTipo)
        val edtNumero = v.findViewById<TextInputEditText>(R.id.edtNumero)
        val edtInicio = v.findViewById<TextInputEditText>(R.id.edtInicio)
        val edtFim = v.findViewById<TextInputEditText>(R.id.edtFim)
        val caixaFim = v.findViewById<TextInputLayout>(R.id.caixaFim)
        val txtServe = v.findViewById<TextView>(R.id.txtServe)

        fun atualizarServe() {
            val locais = Repositorio.pesqueiros().filter { p -> p.exige.any { Catalogo.cobre(tipo, it) } }.map { it.curto }
            txtServe.text = ctx.getString(
                if (tipo == "nao-res") R.string.licenca_serve_nao_res else R.string.licenca_serve,
                if (locais.isEmpty()) ctx.getString(R.string.licenca_serve_nenhum) else locais.joinToString(", "))
        }
        fun mostrarDatas() { edtInicio.setText(inicio.pt()); edtFim.setText(fim?.pt() ?: "") }

        edtTipo.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_list_item_1, tipos.map { "${it.nome} (${it.entidade})" }))
        edtTipo.setText(Catalogo.tipoLicenca(tipo).let { "${it.nome} (${it.entidade})" }, false)
        edtTipo.setOnItemClickListener { _, _, pos, _ -> tipo = tipos[pos].id; atualizarServe() }
        edtNumero.setText(existente?.numero ?: "")
        mostrarDatas(); atualizarServe()

        fun escolherData(atual: LocalDate, min: LocalDate, max: LocalDate, aoEscolher: (LocalDate) -> Unit) {
            val dlg = DatePickerDialog(ctx, { _, a, m, d -> aoEscolher(LocalDate.of(a, m + 1, d)) },
                atual.year, atual.monthValue - 1, atual.dayOfMonth)
            // licença dura no máximo 1 ano, por isso os picker ficam amarrados a essa janela
            dlg.datePicker.minDate = min.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            dlg.datePicker.maxDate = max.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            dlg.show()
        }
        val hoje = Repositorio.hoje
        edtInicio.setOnClickListener { escolherData(inicio, hoje.minusDays(365), hoje) { inicio = it; mostrarDatas() } }
        edtFim.setOnClickListener { escolherData(fim ?: inicio, inicio, inicio.plusDays(365)) { fim = it; caixaFim.error = null; mostrarDatas() } }

        // atalhos de duração (a partir da data de início)
        val chips = v.findViewById<ChipGroup>(R.id.chipsDuracao)
        listOf(R.string.duracao_1_dia to 1L, R.string.duracao_1_mes to 30L, R.string.duracao_3_meses to 91L, R.string.duracao_1_ano to 365L).forEach { (rot, dias) ->
            chips.addView(Chip(ctx).apply {
                setText(rot)
                setOnClickListener { fim = inicio.plusDays(dias - 1); caixaFim.error = null; mostrarDatas() }
            })
        }

        val b = MaterialAlertDialogBuilder(ctx)
            .setTitle(if (existente == null) R.string.adicionar_licenca else R.string.editar_licenca)
            .setView(v)
            .setPositiveButton(R.string.guardar, null)      // validação feita abaixo (não fecha se houver erro)
            .setNegativeButton(R.string.cancelar, null)
        if (existente != null) {
            b.setNeutralButton(R.string.apagar_licenca) { _, _ -> confirmarApagar(ctx, existente, aoMudar) }
        }
        val dlg = b.create()
        dlg.setOnShowListener {
            dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val f = fim
                when {
                    f == null -> caixaFim.error = ctx.getString(R.string.licenca_erro_sem_fim)
                    f.isBefore(inicio) -> caixaFim.error = ctx.getString(R.string.licenca_erro_fim_antes)
                    f.isAfter(inicio.plusDays(365)) -> caixaFim.error = ctx.getString(R.string.licenca_erro_mais_1_ano)
                    else -> {
                        Repositorio.guardarLicenca(Licenca(existente?.id ?: 0L, tipo, edtNumero.text?.toString()?.trim() ?: "", inicio, f))
                        Toast.makeText(ctx, if (existente == null) R.string.licenca_adicionada else R.string.licenca_atualizada, Toast.LENGTH_SHORT).show()
                        dlg.dismiss()
                        aoMudar()
                    }
                }
            }
        }
        dlg.show()
    }

    private fun confirmarApagar(ctx: Context, l: Licenca, aoMudar: () -> Unit) {
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.apagar_licenca)
            .setMessage(ctx.getString(R.string.confirmar_eliminar) + "\n\n" +
                ctx.getString(R.string.licenca_apagar_aviso, Catalogo.tipoLicenca(l.tipo).nome))
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.eliminar) { _, _ ->
                Repositorio.apagarLicenca(l.id)
                Toast.makeText(ctx, R.string.licenca_apagada, Toast.LENGTH_SHORT).show()
                aoMudar()
            }
            .show()
    }
}
