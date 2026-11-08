package pt.ubi.di.pdm.aplicacaopesca.ui

import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import pt.ubi.di.pdm.aplicacaopesca.data.CopiaSeguranca
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import pt.ubi.di.pdm.aplicacaopesca.R
import pt.ubi.di.pdm.aplicacaopesca.data.Captura
import pt.ubi.di.pdm.aplicacaopesca.data.Catalogo
import pt.ubi.di.pdm.aplicacaopesca.data.Repositorio
import pt.ubi.di.pdm.aplicacaopesca.util.dpi
import pt.ubi.di.pdm.aplicacaopesca.util.kg
import java.time.LocalDate

/**
 * Estatísticas.
 * Totais, pesqueiro mais visitado, % da Peixedex, capturas por mês (6 meses)
 * e espécies mais capturadas. Tudo calculado no telemóvel a partir das capturas.
 */
class EstatisticasFragment : Fragment(R.layout.fragment_estatisticas) {

    private val criarFicheiro = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        val ok = try {
            requireContext().contentResolver.openOutputStream(uri)?.use { it.write(CopiaSeguranca.exportar().toByteArray()) } != null
        } catch (_: Exception) { false }
        aviso(if (ok) R.string.copia_exportada else R.string.copia_erro_exportar)
    }

    private val abrirFicheiro = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val texto = requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: throw IllegalArgumentException()
            val r = CopiaSeguranca.importar(texto)
            Toast.makeText(requireContext(), getString(R.string.copia_importada, r.capturas, r.licencas, r.ignorados), Toast.LENGTH_LONG).show()
            atualizar()
        } catch (_: Exception) {
            aviso(R.string.copia_erro_importar)
        }
    }

    private fun aviso(texto: Int) = Toast.makeText(requireContext(), texto, Toast.LENGTH_LONG).show()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.btnExportar).setOnClickListener {
            criarFicheiro.launch("caderno-pescador-${LocalDate.now()}.json")
        }
        view.findViewById<View>(R.id.btnImportar).setOnClickListener {
            abrirFicheiro.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
        }
    }

    override fun onResume() {
        super.onResume()
        if (view == null) return
        atualizar()
    }

    private fun atualizar() {
        val v = view ?: return
        val cs = Repositorio.capturas()
        v.findViewById<View>(R.id.avisoSemDados).visibility = if (cs.isEmpty()) View.VISIBLE else View.GONE

        v.findViewById<TextView>(R.id.txtTotal).text = cs.size.toString()
        v.findViewById<TextView>(R.id.txtTotalSub).text =
            getString(if (cs.isEmpty()) R.string.est_sem_registos else R.string.est_registadas)

        val rec = Repositorio.recorde(cs)
        v.findViewById<TextView>(R.id.txtRecorde).text = if (rec != null) getString(R.string.kg_valor, kg(rec.pesoKg)) else "—"
        v.findViewById<TextView>(R.id.txtRecordeSub).text = rec?.let {
            Catalogo.especie(it.especie).nome + " · " + (Repositorio.pesqueiro(it.pesqueiro)?.curto ?: "")
        } ?: ""

        // Pesqueiro mais visitado (dias diferentes com captura nesse local)
        val visitas = cs.groupBy { it.pesqueiro }
            .mapValues { (_, l) -> l.map { it.data }.toSet().size }
        val top = visitas.maxByOrNull { it.value }
        v.findViewById<TextView>(R.id.txtTopLocal).text = top?.let { Repositorio.pesqueiro(it.key)?.nome ?: it.key } ?: "—"
        v.findViewById<TextView>(R.id.txtTopLocalSub).text = top?.let {
            resources.getQuantityString(R.plurals.n_visitas, it.value, it.value)
        } ?: ""

        val desb = Repositorio.desbloqueadas().size
        val tot = Catalogo.especies.size
        val pct = if (tot == 0) 0 else desb * 100 / tot
        v.findViewById<CircularProgressIndicator>(R.id.anel).progress = pct
        v.findViewById<TextView>(R.id.txtPct).text = "$pct%"
        v.findViewById<TextView>(R.id.txtPeixedex).text = getString(R.string.est_x_de_y, desb, tot)

        // Gráfico mensal (6 meses até hoje, incluindo o atual)
        val meses = resources.getStringArray(R.array.meses_abrev)
        val hoje = LocalDate.now()
        val barras = (5 downTo 0).map { k ->
            val mes = hoje.minusMonths(k.toLong())
            val n = cs.count { it.data.year == mes.year && it.data.monthValue == mes.monthValue }
            meses[mes.monthValue - 1] to n
        }
        v.findViewById<GraficoBarrasView>(R.id.grafico).apply {
            definir(barras)
            contentDescription = getString(R.string.est_por_mes) + ": " + barras.joinToString(", ") { "${it.first} ${it.second}" }
        }

        val lista = v.findViewById<LinearLayout>(R.id.listaEspecies)
        lista.removeAllViews()
        val porEspecie = cs.groupingBy { it.especie }.eachCount().toList().sortedByDescending { it.second }
        v.findViewById<View>(R.id.txtSemEspecies).visibility = if (porEspecie.isEmpty()) View.VISIBLE else View.GONE
        val maximo = porEspecie.maxOfOrNull { it.second } ?: 1
        porEspecie.take(8).forEach { (idEsp, n) ->
            lista.addView(construirBarraHorizontal(Catalogo.especie(idEsp).nome, n, maximo))
        }
    }

    private fun construirBarraHorizontal(nome: String, valor: Int, maximo: Int): View {
        val ctx = requireContext()
        val linha = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = ctx.dpi(4); bottomMargin = ctx.dpi(4) }
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val txtNome = TextView(ctx).apply {
            text = nome
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(resources.getColor(R.color.cp_texto, null))
            layoutParams = LinearLayout.LayoutParams(ctx.dpi(90), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        linha.addView(txtNome)

        val barra = LinearProgressIndicator(ctx).apply {
            max = 100
            progress = (valor * 100 / maximo).coerceIn(4, 100)
            trackCornerRadius = ctx.dpi(5)
            trackThickness = ctx.dpi(9)
            setIndicatorColor(resources.getColor(R.color.cp_primaria, null))
            trackColor = resources.getColor(R.color.cp_cartao2, null)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = ctx.dpi(8); marginEnd = ctx.dpi(8)
            }
        }
        linha.addView(barra)

        val txtVal = TextView(ctx).apply {
            text = valor.toString()
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(resources.getColor(R.color.cp_texto_suave, null))
            layoutParams = LinearLayout.LayoutParams(ctx.dpi(28), ViewGroup.LayoutParams.WRAP_CONTENT)
            gravity = android.view.Gravity.END
        }
        linha.addView(txtVal)
        return linha
    }
}
