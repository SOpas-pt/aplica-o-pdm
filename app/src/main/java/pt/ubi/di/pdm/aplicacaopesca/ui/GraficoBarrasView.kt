package pt.ubi.di.pdm.aplicacaopesca.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import pt.ubi.di.pdm.aplicacaopesca.R
import pt.ubi.di.pdm.aplicacaopesca.util.cor
import pt.ubi.di.pdm.aplicacaopesca.util.dp
import kotlin.math.max

/**
 * Gráfico de barras simples (capturas por mês), desenhado à mão com Canvas.
 * Define os valores com [definir]: pares ("Jan", 3), ("Fev", 5)…
 */
class GraficoBarrasView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    private var dados: List<Pair<String, Int>> = emptyList()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    fun definir(pares: List<Pair<String, Int>>) { dados = pares; invalidate() }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        val hMode = MeasureSpec.getMode(hSpec)
        val h = if (hMode == MeasureSpec.EXACTLY) MeasureSpec.getSize(hSpec) else context.dp(130f).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(c: Canvas) {
        if (dados.isEmpty()) return
        val w = width.toFloat(); val h = height.toFloat()
        val padBaixo = context.dp(22f)  // espaço para rótulos
        val padTopo = context.dp(14f)   // espaço para valores
        val zonaBarras = h - padBaixo - padTopo
        val n = dados.size
        val larguraCel = w / n
        val larguraBarra = larguraCel * 0.52f
        val maximo = max(1, dados.maxOf { it.second })

        val corBase = context.cor(R.color.cp_primaria)
        val corClara = context.cor(R.color.cp_destaque)
        val corTexto = context.cor(R.color.cp_texto)
        val corSuave = context.cor(R.color.cp_texto_suave)

        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD

        dados.forEachIndexed { i, (rot, v) ->
            val cx = larguraCel * i + larguraCel / 2f
            val altura = (v.toFloat() / maximo) * zonaBarras
            val top = padTopo + (zonaBarras - altura)
            rect.set(cx - larguraBarra / 2f, top, cx + larguraBarra / 2f, h - padBaixo)
            p.shader = LinearGradient(0f, top, 0f, h - padBaixo,
                corClara, corBase, Shader.TileMode.CLAMP)
            val raio = context.dp(6f)
            c.drawRoundRect(rect, raio, raio, p)
            p.shader = null

            p.color = corTexto
            p.textSize = context.dp(11f)
            c.drawText(v.toString(), cx, top - context.dp(4f), p)

            p.color = corSuave
            p.textSize = context.dp(10.5f)
            c.drawText(rot, cx, h - context.dp(6f), p)
        }
    }
}
