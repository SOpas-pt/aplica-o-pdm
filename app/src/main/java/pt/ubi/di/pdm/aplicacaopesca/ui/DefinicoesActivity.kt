package pt.ubi.di.pdm.aplicacaopesca.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import pt.ubi.di.pdm.aplicacaopesca.App
import pt.ubi.di.pdm.aplicacaopesca.R
import pt.ubi.di.pdm.aplicacaopesca.data.Repositorio

/**
 * Definições.
 * Tema (escuro/claro), distritos no mapa, antecedência dos avisos de licença,
 * carregar dados de exemplo e começar do zero.
 */
class DefinicoesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_definicoes)
        Repositorio.iniciar(this)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.raiz)) { v, insets ->
            val s = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = s.top, bottom = s.bottom)
            insets
        }

        findViewById<View>(R.id.btnVoltar).setOnClickListener { finish() }

        val togEscuro = findViewById<MaterialSwitch>(R.id.togEscuro)
        togEscuro.isChecked = Repositorio.modoEscuro
        val aplicarModoEscuro: (Boolean) -> Unit = { marcado ->
            if (marcado != Repositorio.modoEscuro) {
                Repositorio.modoEscuro = marcado
                App.aplicarTema(marcado)
                recreate()
            }
        }
        togEscuro.setOnCheckedChangeListener { _, m -> aplicarModoEscuro(m) }
        findViewById<View>(R.id.swEscuro).setOnClickListener { togEscuro.toggle() }

        val togDist = findViewById<MaterialSwitch>(R.id.togDistritos)
        togDist.isChecked = Repositorio.mostrarDistritos
        togDist.setOnCheckedChangeListener { _, m -> Repositorio.mostrarDistritos = m }
        findViewById<View>(R.id.swDistritos).setOnClickListener { togDist.toggle() }

        // Idioma (lista vinda da tabela idiomas; «Automático» segue o sistema)
        val txtIdioma = findViewById<android.widget.TextView>(R.id.txtIdiomaAtual)
        val idiomas = Repositorio.idiomas()
        fun mostrarIdioma() {
            val escolhido = Repositorio.idiomaEscolhido
            txtIdioma.text = idiomas.firstOrNull { it.first == escolhido }?.second ?: getString(R.string.idioma_automatico)
        }
        mostrarIdioma()
        findViewById<View>(R.id.linhaIdioma).setOnClickListener {
            val codigos = listOf(Idiomas.AUTO) + idiomas.map { it.first }
            val nomes = listOf(getString(R.string.idioma_automatico)) + idiomas.map { it.second }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.def_idioma)
                .setSingleChoiceItems(nomes.toTypedArray(), codigos.indexOf(Repositorio.idiomaEscolhido).coerceAtLeast(0)) { d, i ->
                    d.dismiss()
                    if (codigos[i] != Repositorio.idiomaEscolhido) Idiomas.aplicar(codigos[i])
                    mostrarIdioma()
                }
                .setNegativeButton(R.string.cancelar, null)
                .show()
        }

        // Antecedência (7/15/30/60)
        val grupo = findViewById<MaterialButtonToggleGroup>(R.id.grupoAviso)
        grupo.check(when (Repositorio.diasAviso) {
            7 -> R.id.avi7
            15 -> R.id.avi15
            60 -> R.id.avi60
            else -> R.id.avi30
        })
        grupo.addOnButtonCheckedListener { _, id, marcado ->
            if (!marcado) return@addOnButtonCheckedListener
            Repositorio.diasAviso = when (id) {
                R.id.avi7 -> 7; R.id.avi15 -> 15; R.id.avi60 -> 60; else -> 30
            }
            Notificacoes.agendarProximas(this)
        }

        findViewById<View>(R.id.btnExemplo).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.def_carregar_exemplo)
                .setMessage(R.string.def_carregar_exemplo_sub)
                .setNegativeButton(R.string.cancelar, null)
                .setPositiveButton(R.string.guardar) { _, _ ->
                    Repositorio.carregarExemplo()
                    Notificacoes.agendarProximas(this)
                    Toast.makeText(this, R.string.def_feito_exemplo, Toast.LENGTH_SHORT).show()
                }
                .show()
        }
        findViewById<View>(R.id.btnZero).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.def_zero)
                .setMessage(R.string.def_zero_confirmar)
                .setNegativeButton(R.string.cancelar, null)
                .setPositiveButton(R.string.eliminar) { _, _ ->
                    Repositorio.comecarDoZero()
                    Notificacoes.cancelarTodas(this)
                    Toast.makeText(this, R.string.def_feito_zero, Toast.LENGTH_SHORT).show()
                }
                .show()
        }
    }
}
