package pt.ubi.di.pdm.aplicacaopesca.util

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import pt.ubi.di.pdm.aplicacaopesca.R
import pt.ubi.di.pdm.aplicacaopesca.data.Estado
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

fun Context.dp(v: Float): Float = v * resources.displayMetrics.density
fun Context.dpi(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.cor(@ColorRes id: Int): Int = ContextCompat.getColor(this, id)

private val FMT_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-PT"))
fun LocalDate.pt(): String = format(FMT_DATA)

/** 1.8 → "1,80" */
fun kg(v: Double): String = String.format(Locale.getDefault(), "%.2f", v)


/** Cor forte, cor do texto e cor de fundo de cada estado (verde / laranja / vermelho). */
@ColorRes fun Estado.corForte() = when (this) { Estado.OK -> R.color.cp_ok; Estado.AVISO -> R.color.cp_aviso; Estado.ERRO -> R.color.cp_erro }
@ColorRes fun Estado.corTexto() = when (this) { Estado.OK -> R.color.cp_ok_texto; Estado.AVISO -> R.color.cp_aviso_texto; Estado.ERRO -> R.color.cp_erro_texto }
@ColorRes fun Estado.corFundo() = when (this) { Estado.OK -> R.color.cp_ok_fundo; Estado.AVISO -> R.color.cp_aviso_fundo; Estado.ERRO -> R.color.cp_erro_fundo }

fun View.fundoArredondado(@ColorRes fundo: Int, raioDp: Float = 14f, @ColorRes contorno: Int? = null, larguraDp: Float = 1.5f) {
    val c = context
    background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = c.dp(raioDp)
        setColor(c.cor(fundo))
        if (contorno != null) setStroke(c.dp(larguraDp).toInt(), c.cor(contorno))
    }
}

fun TextView.comoEtiqueta(estado: Estado, texto: String) {
    text = texto
    setTextColor(context.cor(estado.corTexto()))
    textSize = 12f
    setTypeface(typeface, android.graphics.Typeface.BOLD)
    val h = context.dpi(10); val v = context.dpi(4)
    setPadding(h, v, h, v)
    fundoArredondado(estado.corFundo(), 13f)
}

/** Círculo de sinal (verde ✓, laranja !, vermelho ✕). */
fun View.comoSinal(estado: Estado) {
    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(context.cor(estado.corForte())) }
}

fun Estado.icone(): Int = when (this) { Estado.OK -> R.drawable.ic_visto; Estado.AVISO -> R.drawable.ic_alerta; Estado.ERRO -> R.drawable.ic_fechar }

fun Context.csl(@ColorRes id: Int): ColorStateList = ColorStateList.valueOf(cor(id))
