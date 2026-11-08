package pt.ubi.di.pdm.aplicacaopesca.ui

import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import pt.ubi.di.pdm.aplicacaopesca.R
import pt.ubi.di.pdm.aplicacaopesca.data.Agua
import pt.ubi.di.pdm.aplicacaopesca.data.Captura
import pt.ubi.di.pdm.aplicacaopesca.data.Catalogo
import pt.ubi.di.pdm.aplicacaopesca.data.Conquista
import pt.ubi.di.pdm.aplicacaopesca.data.DadosConquistas
import pt.ubi.di.pdm.aplicacaopesca.data.Pesqueiro
import pt.ubi.di.pdm.aplicacaopesca.data.Repositorio
import pt.ubi.di.pdm.aplicacaopesca.util.kg
import pt.ubi.di.pdm.aplicacaopesca.util.pt
import java.io.File
import java.text.NumberFormat
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

/**
 * Capturas (separador «Nova» = formulário / «Histórico» = lista).
 *
 * A fotografia é tirada com a câmara do telemóvel (via FileProvider) ou escolhida
 * da galeria com `PickVisualMedia`. Fica guardada em `filesDir/fotos/`.
 * Qualquer captura desbloqueia a espécie na Peixedex.
 */
class CapturasFragment : Fragment(R.layout.fragment_capturas) {

    companion object {
        const val ARG_PESQUEIRO = "pesqueiro"
        const val ARG_ESPECIE = "especie"
        private const val EST_TAB = "tab"
        private const val EST_PESQUEIRO = "f_pesq"
        private const val EST_ESPECIE = "f_esp"
        private const val EST_PESO = "f_peso"
        private const val EST_DATA = "f_data"
        private const val EST_ISCO = "f_isco"
        private const val EST_FOTO = "f_foto"
        private const val EST_FOTO_PEND = "f_foto_pend"
        private const val EST_EDITANDO = "f_editando"
        private const val EST_FOTO_ORIG = "f_foto_orig"

        fun novo(pesqueiro: String? = null, especie: String? = null): CapturasFragment =
            CapturasFragment().apply {
                arguments = Bundle().apply {
                    if (pesqueiro != null) putString(ARG_PESQUEIRO, pesqueiro)
                    if (especie != null) putString(ARG_ESPECIE, especie)
                }
            }
    }

    private var histAtivo = false
    private var fPesqueiro: String = ""
    private var fEspecie: String = ""
    private var fPeso: String = ""
    private var fData: LocalDate = LocalDate.now()
    private var fIsco: String? = null
    private var fFoto: String? = null              // caminho para o ficheiro guardado
    private var fotoPendenteCamara: String? = null // Uri do ficheiro a receber a foto da câmara
    private var editandoId: Long? = null           // id da captura em edição (null = nova)
    private var fotoOriginal: String? = null       // foto da captura em edição; só se apaga ao guardar

    private lateinit var grupoTab: MaterialButtonToggleGroup
    private lateinit var zonaNova: View
    private lateinit var zonaHist: View
    private lateinit var edtPesqueiro: AutoCompleteTextView
    private lateinit var edtEspecie: AutoCompleteTextView
    private lateinit var caixaPeso: TextInputLayout
    private lateinit var edtPeso: TextInputEditText
    private lateinit var edtData: TextInputEditText
    private lateinit var chipsIsco: ChipGroup
    private lateinit var alertaErro: View
    private lateinit var txtAlertaTit: TextView
    private lateinit var dropFoto: View
    private lateinit var zonaComFoto: View
    private lateinit var imgFoto: ImageView
    private lateinit var txtFotoDesbloqueia: TextView
    private lateinit var lista: LinearLayout
    private lateinit var vazio: View
    private lateinit var edtBuscaHistorico: TextInputEditText
    private lateinit var btnGuardar: MaterialButton
    private lateinit var btnCancelarEdicao: MaterialButton

    private lateinit var lanchCamara: ActivityResultLauncher<Uri>
    private lateinit var lanchGaleria: ActivityResultLauncher<PickVisualMediaRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { s ->
            histAtivo = s.getBoolean(EST_TAB, false)
            fPesqueiro = s.getString(EST_PESQUEIRO, "")
            fEspecie = s.getString(EST_ESPECIE, "")
            fPeso = s.getString(EST_PESO, "")
            fData = LocalDate.parse(s.getString(EST_DATA) ?: LocalDate.now().toString())
            fIsco = s.getString(EST_ISCO)
            fFoto = s.getString(EST_FOTO)
            fotoPendenteCamara = s.getString(EST_FOTO_PEND)
            editandoId = if (s.containsKey(EST_EDITANDO)) s.getLong(EST_EDITANDO) else null
            fotoOriginal = s.getString(EST_FOTO_ORIG)
        } ?: run {
            arguments?.getString(ARG_PESQUEIRO)?.let { fPesqueiro = it }
            arguments?.getString(ARG_ESPECIE)?.let { fEspecie = it }
        }

        // câmara: regista o resultado antes do onViewCreated
        lanchCamara = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            val caminho = fotoPendenteCamara
            fotoPendenteCamara = null
            if (ok && caminho != null) {
                descartarFotoNova()
                fFoto = caminho
                desenharFoto()
            }
        }
        lanchGaleria = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                val destino = copiarParaFotos(uri)
                if (destino != null) {
                    descartarFotoNova()
                    fFoto = destino.absolutePath
                    desenharFoto()
                }
            }
        }
    }

    /** Apaga o ficheiro da foto escolhida no formulário, menos a foto original da captura em edição. */
    private fun descartarFotoNova() {
        fFoto?.takeIf { it != fotoOriginal }?.let { File(it).delete() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        recolherFormulario()
        outState.putBoolean(EST_TAB, histAtivo)
        outState.putString(EST_PESQUEIRO, fPesqueiro)
        outState.putString(EST_ESPECIE, fEspecie)
        outState.putString(EST_PESO, fPeso)
        outState.putString(EST_DATA, fData.toString())
        outState.putString(EST_ISCO, fIsco)
        outState.putString(EST_FOTO, fFoto)
        outState.putString(EST_FOTO_PEND, fotoPendenteCamara)
        editandoId?.let { outState.putLong(EST_EDITANDO, it) }
        outState.putString(EST_FOTO_ORIG, fotoOriginal)
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        grupoTab = v.findViewById(R.id.grupoTab)
        zonaNova = v.findViewById(R.id.zonaNova)
        zonaHist = v.findViewById(R.id.zonaHistorico)
        edtPesqueiro = v.findViewById(R.id.edtPesqueiro)
        edtEspecie = v.findViewById(R.id.edtEspecie)
        caixaPeso = v.findViewById(R.id.caixaPeso)
        edtPeso = v.findViewById(R.id.edtPeso)
        edtData = v.findViewById(R.id.edtData)
        chipsIsco = v.findViewById(R.id.chipsIsco)
        alertaErro = v.findViewById(R.id.alertaErro)
        txtAlertaTit = v.findViewById(R.id.txtAlertaTit)
        dropFoto = v.findViewById(R.id.dropFoto)
        zonaComFoto = v.findViewById(R.id.zonaComFoto)
        imgFoto = v.findViewById(R.id.imgFoto)
        txtFotoDesbloqueia = v.findViewById(R.id.txtFotoDesbloqueia)
        lista = v.findViewById(R.id.listaCapturas)
        vazio = v.findViewById(R.id.vazio)
        edtBuscaHistorico = v.findViewById(R.id.edtBuscaHistorico)
        edtBuscaHistorico.doAfterTextChanged { atualizarHistorico() }

        preencherListas()
        prepararDatePicker()
        prepararChipsIsco()
        prepararFoto(v)

        btnGuardar = v.findViewById(R.id.btnGuardar)
        btnCancelarEdicao = v.findViewById(R.id.btnCancelarEdicao)
        btnGuardar.setOnClickListener { guardar() }
        btnCancelarEdicao.setOnClickListener { cancelarEdicao() }
        aplicarModoEdicao()

        grupoTab.check(if (histAtivo) R.id.tabHistorico else R.id.tabNova)
        grupoTab.addOnButtonCheckedListener { _, id, marcado ->
            if (marcado) { histAtivo = id == R.id.tabHistorico; aplicarTab() }
        }
        aplicarTab()
        mostrarFormulario()
    }

    override fun onResume() {
        super.onResume()
        if (view == null) return
        // os pesqueiros podem ter mudado (p. ex. novo local do utilizador)
        preencherListaPesqueiros()
        preencherListaEspecies()
        if (histAtivo) atualizarHistorico()
    }

    private fun aplicarTab() {
        zonaNova.visibility = if (histAtivo) View.GONE else View.VISIBLE
        zonaHist.visibility = if (histAtivo) View.VISIBLE else View.GONE
        if (histAtivo) atualizarHistorico()
    }

    private fun preencherListas() {
        preencherListaPesqueiros()
        preencherListaEspecies()
    }

    private fun preencherListaPesqueiros() {
        val lista = Repositorio.pesqueiros().sortedBy { it.nome.lowercase() }
        if (fPesqueiro.isBlank() && lista.isNotEmpty()) fPesqueiro = lista.first().id
        edtPesqueiro.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, lista.map { it.nome }))
        edtPesqueiro.setText(lista.firstOrNull { it.id == fPesqueiro }?.nome ?: "", false)
        edtPesqueiro.setOnItemClickListener { _, _, pos, _ ->
            fPesqueiro = lista[pos].id
            preencherListaEspecies()
            atualizarFotoLegenda()
        }
    }

    /** Só as espécies que existem no pesqueiro escolhido (todas, se o local ainda não tiver nenhuma associada). */
    private fun preencherListaEspecies() {
        val doLocal = Repositorio.pesqueiro(fPesqueiro)?.especies.orEmpty()
        val especies = Catalogo.especies.filter { it.id in doLocal }.ifEmpty { Catalogo.especies }
        if (especies.none { it.id == fEspecie }) fEspecie = especies.first().id
        val rotulos = especies.map { "${it.nome} · ${getString(it.agua.rotulo)}" }
        edtEspecie.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, rotulos))
        edtEspecie.setText(especies.firstOrNull { it.id == fEspecie }?.let { "${it.nome} · ${getString(it.agua.rotulo)}" } ?: "", false)
        edtEspecie.setOnItemClickListener { _, _, pos, _ -> fEspecie = especies[pos].id; atualizarFotoLegenda() }
    }

    private fun prepararDatePicker() {
        edtData.setText(fData.pt())
        edtData.setOnClickListener {
            DatePickerDialog(requireContext(), { _, a, m, d ->
                val nova = LocalDate.of(a, m + 1, d)
                if (!nova.isAfter(LocalDate.now())) { fData = nova; edtData.setText(fData.pt()) }
            }, fData.year, fData.monthValue - 1, fData.dayOfMonth).apply {
                datePicker.maxDate = System.currentTimeMillis()
            }.show()
        }
    }

    private fun prepararChipsIsco() {
        chipsIsco.removeAllViews()
        Catalogo.iscosFormulario.forEach { isco ->
            val chip = Chip(requireContext()).apply {
                text = isco.nome
                isCheckable = true
                isChecked = isco.id == fIsco
                setOnCheckedChangeListener { _, marcado -> if (marcado) fIsco = isco.id else if (fIsco == isco.id) fIsco = null }
            }
            chipsIsco.addView(chip)
        }
    }

    private fun prepararFoto(raiz: View) {
        dropFoto.setOnClickListener { escolherOrigemFoto() }
        imgFoto.setOnClickListener { escolherOrigemFoto() }
        raiz.findViewById<ImageButton>(R.id.btnRemoverFoto).setOnClickListener {
            descartarFotoNova()
            fFoto = null
            desenharFoto()
        }
        atualizarFotoLegenda()
        desenharFoto()
    }

    private fun escolherOrigemFoto() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.cap_foto)
            .setItems(arrayOf(getString(R.string.cap_foto_tirar), getString(R.string.cap_foto_galeria))) { _, qual ->
                if (qual == 0) tirarFoto() else lanchGaleria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            .show()
    }

    private fun tirarFoto() {
        val pasta = File(requireContext().filesDir, "fotos").apply { mkdirs() }
        val ficheiro = File(pasta, "captura_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fotos", ficheiro)
        fotoPendenteCamara = ficheiro.absolutePath
        try {
            lanchCamara.launch(uri)
        } catch (_: Exception) {
            fotoPendenteCamara = null
            Toast.makeText(requireContext(), R.string.cap_sem_camara, Toast.LENGTH_SHORT).show()
        }
    }

    private fun atualizarFotoLegenda() {
        val desbloqueada = fEspecie in Repositorio.desbloqueadas()
        txtFotoDesbloqueia.setText(if (desbloqueada) R.string.cap_foto_ja else R.string.cap_foto_desbloqueia)
    }

    private fun desenharFoto() {
        val tem = fFoto != null && File(fFoto!!).exists()
        dropFoto.visibility = if (tem) View.GONE else View.VISIBLE
        zonaComFoto.visibility = if (tem) View.VISIBLE else View.GONE
        if (tem) {
            val bmp = BitmapFactory.decodeFile(fFoto)
            imgFoto.setImageBitmap(bmp)
        }
    }

    private fun mostrarFormulario() {
        edtPeso.setText(fPeso)
        edtPeso.doAfterTextChanged {
            fPeso = it?.toString() ?: ""
            caixaPeso.error = null
            if (alertaErro.visibility == View.VISIBLE && fPeso.trim().isNotEmpty()) alertaErro.visibility = View.GONE
        }
    }

    private fun recolherFormulario() {
        fPeso = edtPeso.text?.toString() ?: ""
    }

    private fun guardar() {
        recolherFormulario()
        val texto = fPeso.trim().replace(',', '.')
        val valor = texto.toDoubleOrNull()
        val erro: String? = when {
            texto.isEmpty() -> getString(R.string.cap_erro_peso_vazio)
            valor == null || valor < 0.01 || valor > 99.0 -> getString(R.string.cap_erro_peso_invalido)
            else -> null
        }
        if (erro != null) {
            caixaPeso.error = " "    // marca a caixa a vermelho
            txtAlertaTit.text = erro
            alertaErro.visibility = View.VISIBLE
            alertaErro.setBackgroundResource(R.drawable.bg_caixa_info)
            edtPeso.requestFocus()
            return
        }

        // Só deixa registar se a espécie existir naquele pesqueiro.
        val local = Repositorio.pesqueiro(fPesqueiro)
        if (local != null && fEspecie !in local.especies) {
            val esp = Catalogo.especie(fEspecie)
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.cap_lugar_errado)
                .setMessage(getString(R.string.cap_lugar_errado_sub, esp.nome, local.nome))
                .setPositiveButton(R.string.btn_fechar, null)
                .show()
            return
        }

        val id = editandoId
        val antes = dadosConquistas()
        val captura = Captura(
            id = id ?: 0L,
            data = fData,
            pesqueiro = fPesqueiro,
            especie = fEspecie,
            pesoKg = valor!!,
            isco = fIsco,
            foto = fFoto
        )
        if (id == null) {
            Repositorio.adicionarCaptura(captura)
        } else {
            Repositorio.atualizarCaptura(captura)
            // a foto antiga só se apaga depois de a alteração ficar guardada
            fotoOriginal?.takeIf { it != fFoto }?.let { File(it).delete() }
        }

        // Qualquer captura desbloqueia a espécie (a foto só decide se aparece a foto do utilizador).
        val eraNova = Repositorio.desbloquear(fEspecie)

        Toast.makeText(requireContext(), if (id == null) R.string.cap_guardada else R.string.cap_atualizada, Toast.LENGTH_SHORT).show()
        Conquista.novas(antes, dadosConquistas()).forEach {
            Toast.makeText(requireContext(), getString(R.string.conq_nova, getString(it.titulo)), Toast.LENGTH_LONG).show()
        }

        if (eraNova) {
            val esp = Catalogo.especie(fEspecie)
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.cap_desbloqueou)
                .setMessage(getString(R.string.cap_desbloqueou_sub, esp.nome))
                .setPositiveButton(R.string.btn_fechar, null)
                .show()
        }

        sairDaEdicao()
        grupoTab.check(R.id.tabHistorico)

        (activity as? pt.ubi.di.pdm.aplicacaopesca.MainActivity)?.atualizarPerfil()
    }

    private fun sairDaEdicao() {
        editandoId = null; fotoOriginal = null
        fPeso = ""; edtPeso.setText("")
        fIsco = null; prepararChipsIsco()
        fFoto = null; desenharFoto()
        fData = LocalDate.now(); edtData.setText(fData.pt())
        alertaErro.visibility = View.GONE
        caixaPeso.error = null
        atualizarFotoLegenda()
        aplicarModoEdicao()
    }

    private fun aplicarModoEdicao() {
        val editando = editandoId != null
        btnGuardar.setText(if (editando) R.string.cap_guardar_alteracoes else R.string.cap_guardar)
        btnCancelarEdicao.visibility = if (editando) View.VISIBLE else View.GONE
    }

    private fun cancelarEdicao() {
        descartarFotoNova()
        sairDaEdicao()
    }

    private fun iniciarEdicao(c: Captura) {
        descartarFotoNova()
        editandoId = c.id
        fotoOriginal = c.foto
        fPesqueiro = c.pesqueiro; fEspecie = c.especie
        fPeso = kg(c.pesoKg); fData = c.data; fIsco = c.isco; fFoto = c.foto
        edtPeso.setText(fPeso)
        edtData.setText(fData.pt())
        alertaErro.visibility = View.GONE
        caixaPeso.error = null
        preencherListaPesqueiros(); preencherListaEspecies(); prepararChipsIsco()
        atualizarFotoLegenda(); desenharFoto(); aplicarModoEdicao()
        grupoTab.check(R.id.tabNova)
    }

    private fun dadosConquistas() =
        DadosConquistas(Repositorio.capturas(), Repositorio.desbloqueadas().size, Catalogo.especies.size)

    /** Minúsculas e sem acentos, para a pesquisa não depender deles («achiga» encontra «achigã»). */
    private fun simples(s: String) =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

    private fun atualizarHistorico() {
        val cs = Repositorio.capturas()
        val rec = Repositorio.recorde(cs)
        val busca = simples(edtBuscaHistorico.text?.toString().orEmpty().trim())
        // pesquisa por espécie, local ou data (como aparece no cartão)
        val visiveis = if (busca.isEmpty()) cs else cs.filter { c ->
            val local = Repositorio.pesqueiro(c.pesqueiro)?.nome ?: c.pesqueiro
            simples("${Catalogo.especie(c.especie).nome} $local ${c.data.pt()}").contains(busca)
        }
        lista.removeAllViews()
        vazio.visibility = if (cs.isEmpty()) View.VISIBLE else View.GONE
        val inf = layoutInflater
        visiveis.forEach { c -> lista.addView(cartao(inf, c, rec?.id == c.id)) }
        if (cs.isNotEmpty() && visiveis.isEmpty()) {
            lista.addView(TextView(requireContext()).apply {
                setText(R.string.hist_sem_resultados)
                setTextColor(resources.getColor(R.color.cp_texto_suave, null))
            })
        }
    }

    private fun cartao(inf: LayoutInflater, c: Captura, recorde: Boolean): View {
        val v = inf.inflate(R.layout.item_captura, lista, false)
        val peixe = v.findViewById<PeixeView>(R.id.peixeMini)
        val img = v.findViewById<ImageView>(R.id.imgMini)
        peixe.especieId = c.especie
        peixe.raio = requireContext().resources.displayMetrics.density * 10f
        if (c.foto != null && File(c.foto).exists()) {
            peixe.visibility = View.GONE
            img.visibility = View.VISIBLE
            img.setImageBitmap(BitmapFactory.decodeFile(c.foto))
        }
        v.findViewById<TextView>(R.id.txtNome).text =
            getString(R.string.cap_nome_peso, Catalogo.especie(c.especie).nome, kg(c.pesoKg))
        val local = Repositorio.pesqueiro(c.pesqueiro)?.nome ?: c.pesqueiro
        val extra = if (!c.isco.isNullOrBlank()) " · ${Catalogo.iscoNome(c.isco)}" else ""
        v.findViewById<TextView>(R.id.txtDetalhes).text = "$local\n${c.data.pt()}$extra"
        v.findViewById<View>(R.id.txtRecorde).visibility = if (recorde) View.VISIBLE else View.GONE
        v.findViewById<ImageButton>(R.id.btnPartilhar).setOnClickListener { partilhar(c) }
        v.setOnClickListener { mostrarDetalhe(c) }
        return v
    }

    private fun mostrarDetalhe(c: Captura) {
        val v = layoutInflater.inflate(R.layout.dialogo_captura, null, false)
        c.foto?.takeIf { File(it).exists() }?.let {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            v.findViewById<ImageView>(R.id.imgDetalhe).apply {
                setImageBitmap(BitmapFactory.decodeFile(it, opts)); visibility = View.VISIBLE
            }
        }
        val local = Repositorio.pesqueiro(c.pesqueiro)?.nome ?: c.pesqueiro
        val isco = if (c.isco.isNullOrBlank()) "" else "\n" + getString(R.string.cap_detalhe_isco, Catalogo.iscoNome(c.isco))
        v.findViewById<TextView>(R.id.txtDetalhe).text = "$local\n${c.data.pt()}$isco"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.cap_nome_peso, Catalogo.especie(c.especie).nome, kg(c.pesoKg)))
            .setView(v)
            .setPositiveButton(R.string.editar) { _, _ -> iniciarEdicao(c) }
            .setNeutralButton(R.string.eliminar) { _, _ -> confirmarApagar(c) }
            .setNegativeButton(R.string.btn_fechar, null)
            .show()
    }

    private fun partilhar(c: Captura) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.partilhar_captura)
            .setItems(arrayOf(getString(R.string.btn_whatsapp), getString(R.string.btn_sms))) { _, qual ->
                if (qual == 0) Partilha.porWhatsApp(requireContext(), c) else Partilha.porSms(requireContext(), c)
            }
            .setNegativeButton(R.string.btn_fechar, null)
            .show()
    }

    private fun confirmarApagar(c: Captura) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.cap_apagar)
            .setMessage(R.string.confirmar_eliminar)
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.eliminar) { _, _ ->
                c.foto?.let { File(it).delete() }
                Repositorio.apagarCaptura(c.id)
                atualizarHistorico()
                (activity as? pt.ubi.di.pdm.aplicacaopesca.MainActivity)?.atualizarPerfil()
            }
            .show()
    }

    private fun copiarParaFotos(origem: Uri): File? {
        return try {
            val pasta = File(requireContext().filesDir, "fotos").apply { mkdirs() }
            val destino = File(pasta, "captura_${System.currentTimeMillis()}.jpg")
            requireContext().contentResolver.openInputStream(origem)?.use { entrada ->
                destino.outputStream().use { saida -> entrada.copyTo(saida) }
            }
            destino
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.cap_erro_copiar_foto, Toast.LENGTH_SHORT).show()
            null
        }
    }
}
