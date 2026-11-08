package pt.ubi.di.pdm.aplicacaopesca.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import pt.ubi.di.pdm.aplicacaopesca.R
import pt.ubi.di.pdm.aplicacaopesca.data.Repositorio
import java.io.File

/**
 * Editar perfil.
 * Permite mudar o nome e a fotografia (vinda da galeria do telefone).
 */
class PerfilActivity : AppCompatActivity() {

    private lateinit var ini: TextView
    private lateinit var img: ImageView

    private val escolherFoto = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val destino = File(filesDir, "perfil.jpg")
        runCatching {
            contentResolver.openInputStream(uri)?.use { ent ->
                destino.outputStream().use { saida -> ent.copyTo(saida) }
            }
            Repositorio.fotoPerfil = destino.absolutePath
            atualizarAvatar()
        }.onFailure {
            Toast.makeText(this, R.string.perf_erro_foto, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_perfil)
        Repositorio.iniciar(this)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.raiz)) { v, insets ->
            val s = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = s.top, bottom = s.bottom)
            insets
        }

        findViewById<View>(R.id.btnVoltar).setOnClickListener { finish() }
        val edt = findViewById<TextInputEditText>(R.id.edtNome)
        ini = findViewById(R.id.txtIniciais)
        img = findViewById(R.id.imgFotoPerfil)
        edt.setText(Repositorio.nomePerfil)
        atualizarIniciais(edt.text?.toString())
        edt.doAfterTextChanged { atualizarIniciais(it?.toString()); atualizarAvatar() }

        findViewById<View>(R.id.caixaAvatar).setOnClickListener { abrirPicker() }
        findViewById<MaterialButton>(R.id.btnTrocarFoto).setOnClickListener {
            if (Repositorio.fotoPerfil.isNullOrBlank()) abrirPicker() else menuFoto()
        }

        findViewById<MaterialButton>(R.id.btnGuardar).setOnClickListener {
            val n = edt.text?.toString()?.trim().orEmpty()
            Repositorio.nomePerfil = if (n.isEmpty()) getString(R.string.perf_nome_padrao) else n
            Toast.makeText(this, R.string.cap_guardada, Toast.LENGTH_SHORT).show()
            finish()
        }

        atualizarAvatar()
    }

    private fun abrirPicker() {
        escolherFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun menuFoto() {
        MaterialAlertDialogBuilder(this)
            .setItems(arrayOf(getString(R.string.perf_trocar_foto), getString(R.string.perf_remover_foto))) { _, i ->
                if (i == 0) abrirPicker() else removerFoto()
            }
            .show()
    }

    private fun removerFoto() {
        Repositorio.fotoPerfil?.let { File(it).delete() }
        Repositorio.fotoPerfil = null
        atualizarAvatar()
    }

    private fun atualizarIniciais(nome: String?) {
        ini.text = (nome ?: "").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    }

    private fun atualizarAvatar() {
        Vistas.mostrarAvatarPerfil(ini, img, Repositorio.fotoPerfil)
    }
}
