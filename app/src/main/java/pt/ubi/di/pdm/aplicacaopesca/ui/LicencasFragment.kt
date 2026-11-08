package pt.ubi.di.pdm.aplicacaopesca.ui

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import pt.ubi.di.pdm.aplicacaopesca.R
import pt.ubi.di.pdm.aplicacaopesca.data.Catalogo
import pt.ubi.di.pdm.aplicacaopesca.data.Estado
import pt.ubi.di.pdm.aplicacaopesca.data.Exigencia
import pt.ubi.di.pdm.aplicacaopesca.data.Repositorio
import pt.ubi.di.pdm.aplicacaopesca.data.estadoDe
import pt.ubi.di.pdm.aplicacaopesca.util.comoEtiqueta
import pt.ubi.di.pdm.aplicacaopesca.util.cor
import pt.ubi.di.pdm.aplicacaopesca.util.corForte
import pt.ubi.di.pdm.aplicacaopesca.util.corFundo
import pt.ubi.di.pdm.aplicacaopesca.util.corTexto
import pt.ubi.di.pdm.aplicacaopesca.util.fundoArredondado
import pt.ubi.di.pdm.aplicacaopesca.util.pt

/**
 * As minhas licenças: contadores (Válidas / A expirar / Expiradas), lista com barras de progresso,
 * botão «Adicionar licença» e lista das licenças exigidas nos pesqueiros que o utilizador ainda não tem.
 */
class LicencasFragment : Fragment(R.layout.fragment_licencas) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<MaterialButton>(R.id.btnAdicionar).setOnClickListener {
            LicencaDialogo.mostrar(requireContext()) { atualizar() }
        }
    }

    override fun onResume() {
        super.onResume()
        atualizar()
    }

    private fun atualizar() {
        val v = view ?: return
        val ctx = requireContext()
        val hoje = Repositorio.hoje
        val aviso = Repositorio.diasAviso
        val licencas = Repositorio.licencas().sortedBy { it.fim }
        if (licencas.isNotEmpty()) (activity as? pt.ubi.di.pdm.aplicacaopesca.MainActivity)?.pedirNotificacoes()
        val estados = licencas.map { estadoDe(it, hoje, aviso) }

        fun contador(id: Int, n: Int, rotulo: String, e: Estado) {
            v.findViewById<TextView>(id).apply {
                val sb = SpannableStringBuilder(n.toString())
                sb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(AbsoluteSizeSpan(28, true), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.append("\n").append(rotulo)
                text = sb
                setTextColor(ctx.cor(e.corTexto()))
                fundoArredondado(e.corFundo(), 16f)
            }
        }
        contador(R.id.cntValidas, estados.count { it.estado == Estado.OK }, getString(R.string.validas), Estado.OK)
        contador(R.id.cntAExpirar, estados.count { it.estado == Estado.AVISO }, getString(R.string.a_expirar), Estado.AVISO)
        contador(R.id.cntExpiradas, estados.count { it.estado == Estado.ERRO }, getString(R.string.expiradas), Estado.ERRO)

        val lista = v.findViewById<LinearLayout>(R.id.lista)
        lista.removeAllViews()
        v.findViewById<View>(R.id.vazio).visibility = if (licencas.isEmpty()) View.VISIBLE else View.GONE
        val pesqueiros = Repositorio.pesqueiros()
        licencas.forEachIndexed { i, l ->
            val e = estados[i]
            val tipo = Catalogo.tipoLicenca(l.tipo)
            val item = layoutInflater.inflate(R.layout.item_licenca, lista, false)
            item.findViewById<TextView>(R.id.txtNome).text = tipo.nome
            item.findViewById<TextView>(R.id.txtEstado).comoEtiqueta(e.estado, when (e.estado) {
                Estado.OK -> getString(R.string.estado_valida)
                Estado.AVISO -> if (e.dias == 0L) getString(R.string.expira_hoje)
                    else getString(R.string.a_expirar_em, resources.getQuantityString(R.plurals.n_dias, e.dias.toInt(), e.dias))
                Estado.ERRO -> getString(R.string.estado_expirada)
            })
            item.findViewById<TextView>(R.id.txtDetalhe).text =
                tipo.entidade + (if (l.numero.isNotBlank()) " · ${getString(R.string.numero_abrev, l.numero)}" else "") +
                    " · ${l.inicio.pt()} → ${l.fim.pt()}"
            val pct = if (e.estado == Estado.ERRO) 100 else (e.dias * 100 / e.total).toInt().coerceIn(4, 100)
            item.findViewById<LinearProgressIndicator>(R.id.barra).apply {
                setIndicatorColor(ctx.cor(e.estado.corForte()))
                progress = pct
            }
            item.findViewById<TextView>(R.id.txtDias).apply {
                setTextColor(ctx.cor(e.estado.corTexto()))
                text = when {
                    e.estado == Estado.ERRO -> getString(R.string.expirada_ha,
                        resources.getQuantityString(R.plurals.n_dias, (-e.dias).toInt(), -e.dias))
                    e.dias == 0L -> getString(R.string.expira_hoje)
                    else -> resources.getQuantityString(R.plurals.n_dias_restantes, e.dias.toInt(), e.dias)
                }
            }
            val usos = pesqueiros.count { p -> p.exige.any { Catalogo.cobre(l.tipo, it) } }
            item.findViewById<TextView>(R.id.txtUso).text = resources.getQuantityString(R.plurals.n_pesqueiros, usos, usos)
            item.setOnClickListener { LicencaDialogo.mostrar(ctx, existente = l) { atualizar() } }
            lista.addView(item)
        }

        val falta = Catalogo.tiposLicenca.filter { t ->
            licencas.none { Catalogo.cobre(it.tipo, t.id) } && pesqueiros.any { t.id in it.exige }
        }
        v.findViewById<View>(R.id.tituloFalta).visibility = if (falta.isEmpty()) View.GONE else View.VISIBLE
        val listaFalta = v.findViewById<LinearLayout>(R.id.listaFalta)
        listaFalta.removeAllViews()
        falta.forEach { t ->
            val n = pesqueiros.count { t.id in it.exige }
            val item = Vistas.exigencia(layoutInflater, listaFalta, Exigencia(t.id, Estado.ERRO, null, null),
                aoAdicionar = { tipo -> LicencaDialogo.mostrar(ctx, tipoInicial = tipo) { atualizar() } },
                aoEditar = { })
            item.findViewById<TextView>(R.id.txtEstado).text =
                getString(R.string.exigida_em, resources.getQuantityString(R.plurals.n_pesqueiros, n, n))
            item.findViewById<View>(R.id.txtComo).visibility = View.GONE
        }
    }
}
