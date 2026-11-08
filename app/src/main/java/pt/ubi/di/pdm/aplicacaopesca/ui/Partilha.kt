package pt.ubi.di.pdm.aplicacaopesca.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import pt.ubi.di.pdm.aplicacaopesca.R
import pt.ubi.di.pdm.aplicacaopesca.data.Captura
import pt.ubi.di.pdm.aplicacaopesca.data.Catalogo
import pt.ubi.di.pdm.aplicacaopesca.data.Especie
import pt.ubi.di.pdm.aplicacaopesca.data.Repositorio
import pt.ubi.di.pdm.aplicacaopesca.util.kg
import pt.ubi.di.pdm.aplicacaopesca.util.pt
import java.io.File

/**
 * Partilha de capturas e espécies via WhatsApp ou SMS, com texto pré-formatado
 * e, se existir, a fotografia da captura (partilhada com FileProvider).
 */
object Partilha {

    /** Texto para partilhar uma captura. */
    fun textoCaptura(ctx: Context, c: Captura): String {
        val esp = Catalogo.especie(c.especie).nome
        val local = Repositorio.pesqueiro(c.pesqueiro)?.nome ?: c.pesqueiro
        val sb = StringBuilder()
        sb.append(ctx.getString(R.string.partilha_titulo_captura)).append('\n')
        sb.append(ctx.getString(R.string.partilha_especie, esp)).append('\n')
        sb.append(ctx.getString(R.string.partilha_peso, kg(c.pesoKg))).append('\n')
        sb.append(ctx.getString(R.string.partilha_local, local)).append('\n')
        sb.append(ctx.getString(R.string.partilha_data, c.data.pt())).append('\n')
        if (!c.isco.isNullOrBlank()) sb.append(ctx.getString(R.string.partilha_isco, Catalogo.iscoNome(c.isco))).append('\n')
        sb.append(ctx.getString(R.string.partilha_assinatura))
        return sb.toString()
    }

    fun textoEspecie(ctx: Context, e: Especie): String {
        val sb = StringBuilder()
        sb.append(ctx.getString(R.string.partilha_especie, e.nome)).append('\n')
        sb.append(e.latim).append("\n\n")
        sb.append(ctx.getString(R.string.partilha_tamanho, e.tamanho)).append('\n')
        sb.append(ctx.getString(R.string.partilha_epoca, e.epoca)).append('\n')
        sb.append(ctx.getString(R.string.partilha_iscos, e.iscos.joinToString(", "))).append("\n\n")
        sb.append(ctx.getString(R.string.partilha_assinatura_dex))
        return sb.toString()
    }

    fun porWhatsApp(ctx: Context, c: Captura) {
        enviar(ctx, textoCaptura(ctx, c), c.foto, pacote = "com.whatsapp",
            semApp = ctx.getString(R.string.cap_sem_whatsapp))
    }

    fun porSms(ctx: Context, c: Captura) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
        intent.putExtra("sms_body", textoCaptura(ctx, c))
        try {
            ctx.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, R.string.cap_sem_app_sms, Toast.LENGTH_SHORT).show()
        }
    }

    fun partilhar(ctx: Context, texto: String, titulo: String) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, texto)
        }
        ctx.startActivity(Intent.createChooser(i, titulo))
    }

    private fun enviar(ctx: Context, texto: String, caminhoFoto: String?, pacote: String?, semApp: String) {
        val i = Intent(Intent.ACTION_SEND)
        if (caminhoFoto != null && File(caminhoFoto).exists()) {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fotos", File(caminhoFoto))
            i.type = "image/*"
            i.putExtra(Intent.EXTRA_STREAM, uri)
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            i.type = "text/plain"
        }
        i.putExtra(Intent.EXTRA_TEXT, texto)
        if (pacote != null) i.setPackage(pacote)
        try {
            ctx.startActivity(i)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, semApp, Toast.LENGTH_SHORT).show()
        }
    }
}
