package pt.ubi.di.pdm.aplicacaopesca.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import pt.ubi.di.pdm.aplicacaopesca.R
import pt.ubi.di.pdm.aplicacaopesca.data.Catalogo
import pt.ubi.di.pdm.aplicacaopesca.data.Estado
import pt.ubi.di.pdm.aplicacaopesca.data.Repositorio
import java.util.Locale

/**
 * Escolher o local no mapa.
 * O utilizador toca onde fica o pesqueiro, vê o pin laranja, e confirma.
 * As coordenadas são devolvidas via Activity result.
 */
class EscolherLocalActivity : AppCompatActivity() {

    companion object {
        const val RESULT_LAT = "lat"
        const val RESULT_LON = "lon"
        const val EXTRA_INICIAL_LAT = "ini_lat"
        const val EXTRA_INICIAL_LON = "ini_lon"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_escolher_local)
        Repositorio.iniciar(this)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.raiz)) { v, insets ->
            val s = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = s.top, bottom = s.bottom)
            insets
        }
        findViewById<android.view.View>(R.id.btnVoltar).setOnClickListener { finish() }

        val mapa = findViewById<MapaPortugalView>(R.id.mapa)
        val coords = findViewById<TextView>(R.id.txtCoords)
        val btn = findViewById<MaterialButton>(R.id.btnConfirmar)

        mapa.mostrarDistritos = Repositorio.mostrarDistritos
        mapa.mostrarNomes = false
        // mostrar os pesqueiros existentes como referência (sem nomes para não entupir)
        mapa.definir(Repositorio.pesqueiros().map { MapaPortugalView.Bola(it, Estado.OK) })
        mapa.modoSelecao = true
        mapa.aoSelecionarLocal = { lat, lon ->
            coords.text = String.format(Locale.US, "%.5f, %.5f", lat, lon)
            btn.isEnabled = true
        }

        // posição inicial (se já tinha uma marcada)
        val la = intent.getDoubleExtra(EXTRA_INICIAL_LAT, Double.NaN)
        val lo = intent.getDoubleExtra(EXTRA_INICIAL_LON, Double.NaN)
        if (!la.isNaN() && !lo.isNaN()) {
            mapa.pinSelecao = la to lo
            coords.text = String.format(Locale.US, "%.5f, %.5f", la, lo)
            btn.isEnabled = true
        }

        findViewById<ImageButton>(R.id.btnZoomMais).setOnClickListener { mapa.aproximar() }
        findViewById<ImageButton>(R.id.btnZoomMenos).setOnClickListener { mapa.afastar() }
        findViewById<ImageButton>(R.id.btnZoomRepor).setOnClickListener { mapa.repor(); mapa.pinSelecao = null; coords.setText(R.string.mapa_toque_marcar); btn.isEnabled = false }

        btn.setOnClickListener {
            val (lat, lon) = mapa.pinSelecao ?: return@setOnClickListener
            setResult(Activity.RESULT_OK, Intent()
                .putExtra(RESULT_LAT, lat)
                .putExtra(RESULT_LON, lon))
            finish()
        }
    }
}
