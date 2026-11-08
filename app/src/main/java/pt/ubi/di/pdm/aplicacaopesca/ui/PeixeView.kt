package pt.ubi.di.pdm.aplicacaopesca.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import pt.ubi.di.pdm.aplicacaopesca.data.Catalogo
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Desenha a ilustração de uma espécie.
 * Modos: NORMAL (peixe colorido em água), SILHUETA (preto com raios azuis — «Quem é este peixe?»),
 * ESCURO (água escura e peixe esbatido, para espécies com cadeado).
 * Coordenadas internas: 200 × 100.
 */
class PeixeView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    enum class Modo { NORMAL, SILHUETA, ESCURO }

    var especieId: String = "truta"
        set(v) { field = v; invalidate() }
    var modo: Modo = Modo.NORMAL
        set(v) { field = v; invalidate() }
    /** proporção altura/largura usada em onMeasure (ex.: 0.75 = 4:3) */
    var proporcao: Float = 0.75f
        set(v) { field = v; requestLayout() }
    /** cantos arredondados do fundo (px) */
    var raio: Float = 0f
        set(v) { field = v; invalidate() }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        val hMode = MeasureSpec.getMode(hSpec)
        val h = if (hMode == MeasureSpec.EXACTLY) MeasureSpec.getSize(hSpec) else (w * proporcao).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        c.save()
        if (raio > 0f) { clip.reset(); clip.addRoundRect(RectF(0f, 0f, w, h), raio, raio, Path.Direction.CW); c.clipPath(clip) }
        desenharFundo(c, w, h)
        // área do peixe: centrada, ~82% da largura
        val s = min(w * 0.82f / 200f, h * 0.74f / 100f)
        c.translate((w - 200f * s) / 2f, (h - 100f * s) / 2f)
        c.scale(s, s)
        if (especieId == "lagostim") desenharLagostim(c) else desenharPeixe(c)
        c.restore()
    }

    private fun desenharFundo(c: Canvas, w: Float, h: Float) {
        p.style = Paint.Style.FILL
        when (modo) {
            Modo.SILHUETA -> {
                // raios azuis alternados
                p.shader = null
                p.color = Color.parseColor("#1B43B4"); c.drawRect(0f, 0f, w, h, p)
                p.color = Color.parseColor("#2F66F0")
                val cx = w / 2f; val cy = h * 0.58f; val r = w + h
                val raios = 24
                for (i in 0 until raios step 2) {
                    val a0 = Math.toRadians(i * 360.0 / raios); val a1 = Math.toRadians((i + 1) * 360.0 / raios)
                    val t = Path()
                    t.moveTo(cx, cy)
                    t.lineTo(cx + r * cos(a0).toFloat(), cy + r * sin(a0).toFloat())
                    t.lineTo(cx + r * cos(a1).toFloat(), cy + r * sin(a1).toFloat())
                    t.close()
                    c.drawPath(t, p)
                }
                p.shader = RadialGradient(cx, cy, w * 0.6f, Color.argb(70, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, w, h, p)
            }
            Modo.NORMAL -> {
                p.shader = RadialGradient(w / 2f, 0f, maxOf(w, h) * 1.1f,
                    intArrayOf(Color.parseColor("#4BA3C8"), Color.parseColor("#1B6089"), Color.parseColor("#0A2C48")),
                    floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, w, h, p)
            }
            Modo.ESCURO -> {
                p.shader = RadialGradient(w / 2f, 0f, maxOf(w, h) * 1.1f,
                    intArrayOf(Color.parseColor("#24557A"), Color.parseColor("#0F3555"), Color.parseColor("#07192B")),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, w, h, p)
            }
        }
        p.shader = null
    }

    private fun corpo() = Path().apply {
        moveTo(10f, 52f); cubicTo(20f, 32f, 55f, 22f, 95f, 24f); cubicTo(125f, 26f, 146f, 38f, 160f, 45f)
        lineTo(160f, 57f); cubicTo(146f, 64f, 125f, 76f, 95f, 78f); cubicTo(55f, 80f, 22f, 72f, 10f, 52f); close()
    }

    private fun barbatanas(id: String): List<Path> {
        val l = mutableListOf<Path>()
        l += Path().apply { moveTo(155f, 51f); lineTo(196f, 22f); lineTo(187f, 51f); lineTo(196f, 80f); close() } // cauda
        when (id) {
            "achiga" -> {
                l += Path().apply { moveTo(62f, 25f); lineTo(66f, 11f); lineTo(74f, 16f); lineTo(82f, 9f); lineTo(90f, 15f); lineTo(98f, 11f); lineTo(104f, 25f); close() }
                l += Path().apply { moveTo(108f, 26f); cubicTo(114f, 10f, 132f, 12f, 142f, 36f); close() }
            }
            "carpa" -> l += Path().apply { moveTo(58f, 25f); cubicTo(72f, 8f, 130f, 8f, 150f, 42f); close() }
            else -> l += Path().apply { moveTo(68f, 25f); cubicTo(76f, 7f, 106f, 7f, 120f, 29f); close() }
        }
        l += Path().apply { moveTo(88f, 77f); cubicTo(92f, 91f, 108f, 93f, 112f, 77f); close() }   // pélvica
        l += Path().apply { moveTo(122f, 70f); cubicTo(128f, 83f, 140f, 81f, 146f, 62f); close() }  // anal
        l += Path().apply { moveTo(52f, 63f); cubicTo(60f, 71f, 70f, 73f, 77f, 66f); close() }     // peitoral
        return l
    }

    private fun desenharPeixe(c: Canvas) {
        val id = especieId
        val e = Catalogo.especie(id)
        val sil = modo == Modo.SILHUETA
        c.save()
        when (id) {
            "carpa", "dourada", "sargo", "percasol" -> { c.translate(0f, -9f); c.scale(1f, 1.18f) }
            "linguado" -> { c.translate(0f, -17f); c.scale(1f, 1.34f) }
        }
        val alfa = if (modo == Modo.ESCURO) 130 else 255
        val corpo = corpo()
        p.style = Paint.Style.FILL; p.shader = null
        p.color = if (sil) Color.BLACK else e.cores[3]; p.alpha = if (sil) 255 else (alfa * 0.92f).toInt()
        barbatanas(id).forEach { c.drawPath(it, p) }
        // corpo com gradiente dorso → ventre
        if (sil) { p.color = Color.BLACK; p.alpha = 255 }
        else { p.shader = LinearGradient(0f, 22f, 0f, 80f, intArrayOf(e.cores[0], e.cores[1], e.cores[2]), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP); p.alpha = alfa }
        c.drawPath(corpo, p)
        p.shader = null
        if (!sil) {
            c.save(); c.clipPath(corpo)
            padrao(c, id)
            c.restore()
            p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = Color.argb(70, 0, 0, 0)
            c.drawPath(Path().apply { moveTo(45f, 34f); cubicTo(53f, 46f, 53f, 60f, 45f, 70f) }, p)
            p.strokeWidth = 1.3f; p.color = Color.argb(115, 0, 0, 0); c.drawLine(10f, 52f, 21f, 54f, p)
            if (id == "barbo" || id == "carpa") {
                p.color = Color.parseColor("#2A2A1A"); p.strokeWidth = 1.6f; p.strokeCap = Paint.Cap.ROUND
                c.drawPath(Path().apply { moveTo(12f, 56f); cubicTo(8f, 61f, 5f, 62f, 2f, 60f) }, p)
            }
            p.style = Paint.Style.FILL; p.color = Color.parseColor("#0B0F0C"); c.drawCircle(29f, 46f, 4.6f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.4f; p.color = Color.parseColor("#E9DCA3"); c.drawCircle(29f, 46f, 4.6f, p)
            p.style = Paint.Style.FILL; p.color = Color.WHITE; c.drawCircle(30.3f, 44.7f, 1.2f, p)
        }
        p.alpha = 255
        c.restore()
    }

    /** Padrões simples por espécie (desenhados dentro do corpo). */
    private fun padrao(c: Canvas, id: String) {
        p.style = Paint.Style.FILL
        when (id) {
            "truta" -> {
                p.color = Color.parseColor("#D9362B")
                floatArrayOf(60f, 45f, 76f, 51f, 92f, 43f, 107f, 49f, 122f, 46f, 70f, 58f, 100f, 61f, 132f, 51f).let { a ->
                    for (i in a.indices step 2) c.drawCircle(a[i], a[i + 1], 2.7f, p)
                }
                p.color = Color.parseColor("#1A1A14")
                floatArrayOf(66f, 36f, 82f, 33f, 98f, 35f, 114f, 36f, 130f, 42f, 80f, 45f, 116f, 57f, 88f, 62f).let { a ->
                    for (i in a.indices step 2) c.drawCircle(a[i], a[i + 1], 1.6f, p)
                }
            }
            "achiga" -> { p.color = Color.argb(185, 14, 38, 18); c.drawRoundRect(34f, 47f, 150f, 56f, 5f, 5f, p) }
            "sargo" -> { p.color = Color.argb(97, 30, 35, 40); for (x in listOf(62f, 84f, 106f, 128f)) c.drawRect(x, 20f, x + 7f, 82f, p); p.color = Color.BLACK; c.drawCircle(150f, 51f, 6f, p) }
            "dourada" -> { p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.strokeCap = Paint.Cap.ROUND; p.color = Color.parseColor("#E7B93A")
                c.drawPath(Path().apply { moveTo(22f, 36f); cubicTo(28f, 31f, 38f, 31f, 44f, 35f) }, p) }
            "cavala" -> { p.style = Paint.Style.STROKE; p.strokeWidth = 2.4f; p.color = Color.argb(190, 10, 40, 50)
                for (k in 0 until 7) c.drawPath(Path().apply { val x = 50f + k * 14f; moveTo(x, 26f); quadTo(x + 4f, 34f, x, 42f) }, p) }
            "percasol" -> { p.color = Color.BLACK; c.drawCircle(44f, 44f, 5f, p); p.color = Color.parseColor("#E0442B"); c.drawCircle(48f, 44f, 2.4f, p) }
            "lucioperca" -> { p.color = Color.argb(64, 30, 40, 30); for (x in listOf(64f, 86f, 108f, 130f)) c.drawRect(x, 22f, x + 8f, 62f, p) }
            "linguado" -> { p.color = Color.argb(115, 60, 40, 20); floatArrayOf(60f, 44f, 80f, 56f, 96f, 40f, 112f, 58f, 128f, 46f).let { a ->
                for (i in a.indices step 2) c.drawCircle(a[i], a[i + 1], 3f, p) } }
            "tainha", "robalo", "boga", "escalo" -> { p.style = Paint.Style.STROKE; p.strokeWidth = 1.6f; p.color = Color.argb(110, 255, 255, 255)
                c.drawPath(Path().apply { moveTo(40f, 50f); cubicTo(80f, 47f, 120f, 49f, 158f, 51f) }, p) }
        }
        p.style = Paint.Style.FILL
    }

    private fun desenharLagostim(c: Canvas) {
        val e = Catalogo.especie("lagostim")
        val sil = modo == Modo.SILHUETA
        val corCorpo = if (sil) Color.BLACK else e.cores[1]
        p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = 2.2f; p.strokeCap = Paint.Cap.ROUND
        p.color = if (sil) Color.BLACK else e.cores[0]
        c.drawPath(Path().apply { moveTo(64f, 46f); cubicTo(40f, 30f, 22f, 22f, 4f, 20f); moveTo(64f, 50f); cubicTo(42f, 42f, 24f, 44f, 6f, 50f) }, p)
        for (x in listOf(84f, 96f, 108f, 118f)) { c.drawLine(x, 61f, x - 4f, 76f, p); c.drawLine(x, 39f, x - 4f, 26f, p) }
        p.style = Paint.Style.FILL; p.color = corCorpo
        c.drawPath(Path().apply { moveTo(70f, 44f); cubicTo(60f, 38f, 48f, 34f, 40f, 30f); lineTo(36f, 36f); cubicTo(46f, 40f, 58f, 46f, 68f, 50f); close() }, p)
        c.drawPath(Path().apply { moveTo(40f, 30f); cubicTo(28f, 18f, 12f, 16f, 6f, 26f); cubicTo(14f, 26f, 22f, 28f, 30f, 34f); cubicTo(20f, 34f, 12f, 40f, 10f, 46f); cubicTo(22f, 46f, 32f, 42f, 40f, 36f); close() }, p)
        c.drawPath(Path().apply { moveTo(70f, 56f); cubicTo(60f, 62f, 50f, 70f, 42f, 74f); lineTo(38f, 68f); cubicTo(48f, 64f, 58f, 58f, 68f, 52f); close() }, p)
        c.drawPath(Path().apply { moveTo(42f, 74f); cubicTo(30f, 86f, 14f, 88f, 8f, 78f); cubicTo(16f, 78f, 24f, 76f, 32f, 70f); cubicTo(22f, 70f, 14f, 64f, 12f, 58f); cubicTo(24f, 58f, 34f, 62f, 42f, 68f); close() }, p)
        if (!sil) p.shader = LinearGradient(0f, 36f, 0f, 64f, intArrayOf(e.cores[0], e.cores[1], e.cores[2]), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawOval(62f, 36f, 122f, 64f, p)
        c.drawOval(119f, 38f, 137f, 62f, p); c.drawOval(135f, 39f, 151f, 61f, p); c.drawOval(150f, 40f, 164f, 60f, p); c.drawOval(164f, 41f, 176f, 59f, p)
        c.drawPath(Path().apply { moveTo(174f, 50f); lineTo(196f, 34f); lineTo(198f, 50f); lineTo(196f, 66f); close() }, p)
        p.shader = null
        if (!sil) { p.color = Color.parseColor("#111111"); c.drawCircle(66f, 45f, 2.8f, p) }
    }
}
