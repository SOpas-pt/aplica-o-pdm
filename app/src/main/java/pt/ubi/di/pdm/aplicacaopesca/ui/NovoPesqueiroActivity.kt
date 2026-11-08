package pt.ubi.di.pdm.aplicacaopesca.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import pt.ubi.di.pdm.aplicacaopesca.R
import pt.ubi.di.pdm.aplicacaopesca.data.Agua
import pt.ubi.di.pdm.aplicacaopesca.data.Catalogo
import pt.ubi.di.pdm.aplicacaopesca.data.Pesqueiro
import pt.ubi.di.pdm.aplicacaopesca.data.Repositorio
import pt.ubi.di.pdm.aplicacaopesca.data.TipoLocal

/**
 * Novo pesqueiro (ou editar um pesqueiro do utilizador).
 * Usa o centro do distrito escolhido como coordenadas aproximadas,
 * já que a app não usa GPS (ainda).
 */
class NovoPesqueiroActivity : AppCompatActivity() {

    companion object { const val EXTRA_ID = "pesqueiro_id" }

    private val tipos = TipoLocal.entries

    private var editandoId: String? = null
    private var agua: Agua = Agua.DOCE
    private var distritoSel: Catalogo.Distrito? = null
    private var tipoSel: TipoLocal = tipos.first()
    /** Coordenadas escolhidas pelo utilizador no mapa (nulo = usar o centro do distrito). */
    private var latEscolhida: Double? = null
    private var lonEscolhida: Double? = null
    private lateinit var lanchMapa: ActivityResultLauncher<Intent>
    private lateinit var txtMarcado: TextView

    private lateinit var edtNome: TextInputEditText
    private lateinit var caixaNome: TextInputLayout
    private lateinit var edtDistrito: AutoCompleteTextView
    private lateinit var edtTipo: AutoCompleteTextView
    private lateinit var grupoAgua: MaterialButtonToggleGroup
    private lateinit var chipsLic: ChipGroup
    private lateinit var chipsEsp: ChipGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_novo_pesqueiro)
        Repositorio.iniciar(this)

        lanchMapa = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == Activity.RESULT_OK) {
                val lat = res.data?.getDoubleExtra(EscolherLocalActivity.RESULT_LAT, Double.NaN) ?: Double.NaN
                val lon = res.data?.getDoubleExtra(EscolherLocalActivity.RESULT_LON, Double.NaN) ?: Double.NaN
                if (!lat.isNaN() && !lon.isNaN()) {
                    latEscolhida = lat; lonEscolhida = lon
                    atualizarRotuloMarcado()
                    // ajustar o distrito para o mais próximo das coordenadas
                    distritoSel = Catalogo.distritoDe(lat, lon)
                    findViewById<AutoCompleteTextView>(R.id.edtDistrito).setText(distritoSel!!.nome, false)
                }
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.raiz)) { v, insets ->
            val s = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = s.top, bottom = s.bottom)
            insets
        }

        findViewById<View>(R.id.btnVoltar).setOnClickListener { finish() }
        caixaNome = findViewById(R.id.caixaNome)
        edtNome = findViewById(R.id.edtNome)
        edtDistrito = findViewById(R.id.edtDistrito)
        edtTipo = findViewById(R.id.edtTipo)
        grupoAgua = findViewById(R.id.grupoAgua)
        chipsLic = findViewById(R.id.chipsLicencas)
        chipsEsp = findViewById(R.id.chipsEspecies)

        editandoId = intent.getStringExtra(EXTRA_ID)
        val existente: Pesqueiro? = editandoId?.let { Repositorio.pesqueiro(it) }
        val tituloTxt = findViewById<android.widget.TextView>(R.id.txtTitulo)
        tituloTxt.setText(if (existente == null) R.string.novo_pesqueiro else R.string.editar_pesqueiro)

        val distritos = Catalogo.distritos
        edtDistrito.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, distritos.map { it.nome }))
        distritoSel = existente?.let { Catalogo.distritoDe(it.lat, it.lon) } ?: distritos.first()
        edtDistrito.setText(distritoSel!!.nome, false)
        edtDistrito.setOnItemClickListener { _, _, pos, _ -> distritoSel = distritos[pos] }

        edtTipo.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, tipos.map { getString(it.rotulo) }))
        tipoSel = existente?.tipo ?: tipos.first()
        edtTipo.setText(getString(tipoSel.rotulo), false)
        edtTipo.setOnItemClickListener { _, _, pos, _ -> tipoSel = tipos[pos] }

        agua = existente?.agua ?: Agua.DOCE
        grupoAgua.check(when (agua) {
            Agua.DOCE -> R.id.aguaDoce
            Agua.SALGADA -> R.id.aguaSalgada
            Agua.SALOBRA -> R.id.aguaSalobra
        })
        grupoAgua.addOnButtonCheckedListener { _, id, marcado ->
            if (!marcado) return@addOnButtonCheckedListener
            agua = when (id) {
                R.id.aguaSalgada -> Agua.SALGADA
                R.id.aguaSalobra -> Agua.SALOBRA
                else -> Agua.DOCE
            }
            desenharChipsLicencas(existente?.exige?.toSet() ?: emptySet())
        }

        edtNome.setText(existente?.nome ?: "")

        txtMarcado = findViewById(R.id.txtMarcado)
        if (existente != null) {
            latEscolhida = existente.lat; lonEscolhida = existente.lon
        }
        atualizarRotuloMarcado()
        findViewById<MaterialButton>(R.id.btnMarcar).setOnClickListener {
            val i = Intent(this, EscolherLocalActivity::class.java)
            latEscolhida?.let { i.putExtra(EscolherLocalActivity.EXTRA_INICIAL_LAT, it) }
            lonEscolhida?.let { i.putExtra(EscolherLocalActivity.EXTRA_INICIAL_LON, it) }
            lanchMapa.launch(i)
        }

        desenharChipsLicencas(existente?.exige?.toSet() ?: emptySet())
        desenharChipsEspecies(existente?.especies?.toSet() ?: emptySet())

        findViewById<MaterialButton>(R.id.btnGuardar).setOnClickListener { guardar() }
        findViewById<MaterialButton>(R.id.btnApagar).apply {
            if (existente != null && existente.doUtilizador) {
                visibility = View.VISIBLE
                setOnClickListener { confirmarApagar(existente) }
            }
        }
    }

    private fun desenharChipsLicencas(marcadas: Set<String>) {
        chipsLic.removeAllViews()
        val licAqui = Catalogo.tiposLicenca.filter { it.agua == agua }
        licAqui.forEach { t ->
            chipsLic.addView(Chip(this).apply {
                text = t.nome
                isCheckable = true
                isChecked = t.id in marcadas
                tag = t.id
            })
        }
    }

    private fun desenharChipsEspecies(marcadas: Set<String>) {
        chipsEsp.removeAllViews()
        Catalogo.especies.forEach { e ->
            chipsEsp.addView(Chip(this).apply {
                text = e.nome
                isCheckable = true
                isChecked = e.id in marcadas
                tag = e.id
            })
        }
    }

    private fun guardar() {
        val nome = edtNome.text?.toString()?.trim().orEmpty()
        if (nome.isEmpty()) {
            caixaNome.error = getString(R.string.novo_erro_nome)
            return
        }
        caixaNome.error = null
        val distrito = distritoSel ?: Catalogo.distritos.first()
        val licencas = (0 until chipsLic.childCount).mapNotNull { i ->
            (chipsLic.getChildAt(i) as? Chip)?.takeIf { it.isChecked }?.tag as? String
        }
        val especies = (0 until chipsEsp.childCount).mapNotNull { i ->
            (chipsEsp.getChildAt(i) as? Chip)?.takeIf { it.isChecked }?.tag as? String
        }
        if (licencas.isEmpty()) {
            Toast.makeText(this, R.string.novo_erro_licenca, Toast.LENGTH_SHORT).show()
            return
        }
        val id = editandoId ?: "u${System.currentTimeMillis()}"
        val curto = nome.split(" ").take(2).joinToString(" ")
        val temExata = latEscolhida != null && lonEscolhida != null
        val lat = latEscolhida ?: distrito.lat
        val lon = lonEscolhida ?: distrito.lon
        val p = Pesqueiro(
            id = id, nome = nome, curto = curto, etiqueta = 'b',
            regiao = "${distrito.nome} · ${distrito.nome}",
            tipo = tipoSel, lat = lat, lon = lon,
            especies = especies, agua = agua, exige = licencas,
            bacia = null,
            referencia = getString(if (temExata) R.string.novo_ref_marcado else R.string.novo_ref_aproximada),
            aproximada = !temExata, doUtilizador = true
        )
        Repositorio.guardarPesqueiro(p)
        Toast.makeText(this, if (editandoId == null) R.string.novo_guardado else R.string.novo_atualizado, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun atualizarRotuloMarcado() {
        val lat = latEscolhida; val lon = lonEscolhida
        txtMarcado.text = if (lat != null && lon != null) {
            getString(R.string.novo_marcado, String.format(java.util.Locale.US, "%.4f, %.4f", lat, lon))
        } else {
            getString(R.string.novo_centro_distrito)
        }
    }

    private fun confirmarApagar(p: Pesqueiro) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.novo_apagar)
            .setMessage(R.string.confirmar_eliminar)
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.eliminar) { _, _ ->
                if (Repositorio.apagarPesqueiro(p.id)) {
                    Toast.makeText(this, getString(R.string.novo_eliminado, p.nome), Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, R.string.novo_erro_apagar_capturas, Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }
}
