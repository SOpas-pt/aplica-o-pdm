package pt.ubi.di.pdm.aplicacaopesca.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import pt.ubi.di.pdm.aplicacaopesca.MainActivity
import pt.ubi.di.pdm.aplicacaopesca.R
import pt.ubi.di.pdm.aplicacaopesca.data.Catalogo
import pt.ubi.di.pdm.aplicacaopesca.data.Conquista
import pt.ubi.di.pdm.aplicacaopesca.data.DadosConquistas
import pt.ubi.di.pdm.aplicacaopesca.data.Especie
import pt.ubi.di.pdm.aplicacaopesca.data.Pesqueiro
import pt.ubi.di.pdm.aplicacaopesca.data.Repositorio

/**
 * Peixedex: coleção de todas as espécies (ou só as de um local).
 * - Desbloquear uma espécie (fotografar e registar) vale em TODOS os locais.
 * - Botão «Fotografar para desbloquear» (no cartão de próximo desafio) abre
 *   o separador de capturas já com a espécie pré-escolhida.
 */
class PeixedexFragment : Fragment(R.layout.fragment_peixedex) {

    private var localId: String? = null  // null = todas as zonas

    private lateinit var edtLocal: AutoCompleteTextView
    private lateinit var txtProgresso: TextView
    private lateinit var txtPercent: TextView
    private lateinit var barra: LinearProgressIndicator
    private lateinit var txtDica: TextView
    private lateinit var btnVerLocal: MaterialButton
    private lateinit var grelha: LinearLayout
    private lateinit var cartaoProximo: View
    private lateinit var txtProximoSub: TextView
    private lateinit var btnProximo: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        localId = savedInstanceState?.getString("local")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("local", localId)
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        edtLocal = v.findViewById(R.id.edtLocal)
        txtProgresso = v.findViewById(R.id.txtProgresso)
        txtPercent = v.findViewById(R.id.txtPercent)
        barra = v.findViewById(R.id.barra)
        txtDica = v.findViewById(R.id.txtDica)
        btnVerLocal = v.findViewById(R.id.btnVerLocal)
        grelha = v.findViewById(R.id.grelha)
        cartaoProximo = v.findViewById(R.id.cartaoProximo)
        txtProximoSub = v.findViewById(R.id.txtProximoSub)
        btnProximo = v.findViewById(R.id.btnProximo)
    }

    override fun onResume() {
        super.onResume()
        if (view == null) return
        preencherSeletor()
        atualizar()
    }

    private fun preencherSeletor() {
        val pesqueiros = Repositorio.pesqueiros().sortedBy { it.nome.lowercase() }
        val rotulos = mutableListOf(getString(R.string.peixedex_todas_zonas))
        rotulos.addAll(pesqueiros.map { it.nome })
        val ids = mutableListOf<String?>(null)
        ids.addAll(pesqueiros.map { it.id })
        edtLocal.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, rotulos))
        val pos = ids.indexOf(localId).coerceAtLeast(0)
        edtLocal.setText(rotulos[pos], false)
        edtLocal.setOnItemClickListener { _, _, p, _ -> localId = ids[p]; atualizar() }
    }

    private fun atualizar() {
        val zona: Pesqueiro? = localId?.let { Repositorio.pesqueiro(it) }
        val lista: List<Especie> = if (zona != null) zona.especies.map { Catalogo.especie(it) } else Catalogo.especies
        // Peixedex = coleção pessoal: silhueta até o utilizador registar uma captura.
        val desbloqueadas = Repositorio.desbloqueadas()
        val n = lista.count { it.id in desbloqueadas }
        val tot = lista.size
        val pct = if (tot == 0) 0 else n * 100 / tot

        txtProgresso.text = getString(if (zona != null) R.string.peixedex_progresso_local else R.string.peixedex_progresso, n, tot)
        txtPercent.text = "$pct%"
        barra.progress = pct
        val restam = tot - n
        txtDica.text = when {
            restam == 0 && zona != null -> getString(R.string.peixedex_completa_local)
            restam == 0 -> getString(R.string.peixedex_completa)
            else -> resources.getQuantityString(
                if (zona != null) R.plurals.peixedex_faltam_local else R.plurals.peixedex_faltam, restam, restam)
        }
        btnVerLocal.visibility = if (zona != null) View.VISIBLE else View.GONE
        if (zona != null) btnVerLocal.setOnClickListener { abrirLocal(zona.id) }

        // Peixedex: silhueta quando bloqueada; quando desbloqueada mostra a foto da PRIMEIRA
        // captura do utilizador (foto real da Wiki é fallback).
        Vistas.grelhaEspecies(layoutInflater, grelha, lista, desbloqueadas,
            peixedex = true, comFotoReal = true,
            fotoCapturaDe = { Repositorio.fotoCapturada(it) }) { e -> abrirFicha(e) }

        // Próximo desafio: primeira espécie ainda não desbloqueada da lista
        val proxima = lista.firstOrNull { it.id !in desbloqueadas }
        if (proxima != null) {
            cartaoProximo.visibility = View.VISIBLE
            txtProximoSub.text = getString(R.string.peixedex_proximo_txt, proxima.nome)
            btnProximo.setOnClickListener { abrirFicha(proxima) }
        } else {
            cartaoProximo.visibility = View.GONE
        }
        desenharConquistas()
    }

    /** Conquistas globais (não dependem do local escolhido no seletor). */
    private fun desenharConquistas() {
        val v = view ?: return
        val progressos = Conquista.todas(
            DadosConquistas(Repositorio.capturas(), Repositorio.desbloqueadas().size, Catalogo.especies.size))
        v.findViewById<TextView>(R.id.txtConquistas).text =
            getString(R.string.conq_progresso, progressos.count { it.feita }, progressos.size)
        v.findViewById<LinearProgressIndicator>(R.id.barraConquistas).progress =
            progressos.count { it.feita } * 100 / progressos.size
        v.findViewById<View>(R.id.btnConquistas).setOnClickListener {
            startActivity(Intent(requireContext(), ConquistasActivity::class.java))
        }
    }

    private fun abrirFicha(e: Especie) {
        startActivity(
            Intent(requireContext(), FichaEspecieActivity::class.java)
                .putExtra(FichaEspecieActivity.EXTRA_ID, e.id)
                .putExtra(FichaEspecieActivity.EXTRA_PEIXEDEX, true)
        )
    }

    private fun abrirLocal(id: String) {
        startActivity(
            Intent(requireContext(), LocalActivity::class.java)
                .putExtra(LocalActivity.EXTRA_ID, id)
        )
    }
}
