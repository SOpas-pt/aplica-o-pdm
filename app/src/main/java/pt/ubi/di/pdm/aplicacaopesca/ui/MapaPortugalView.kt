package pt.ubi.di.pdm.aplicacaopesca.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import pt.ubi.di.pdm.aplicacaopesca.R
import pt.ubi.di.pdm.aplicacaopesca.data.Catalogo
import pt.ubi.di.pdm.aplicacaopesca.data.Estado
import pt.ubi.di.pdm.aplicacaopesca.data.Pesqueiro
import pt.ubi.di.pdm.aplicacaopesca.util.cor
import pt.ubi.di.pdm.aplicacaopesca.util.corForte
import pt.ubi.di.pdm.aplicacaopesca.util.dp
import kotlin.math.hypot

/**
 * Mapa estilizado de Portugal continental, desenhado com Canvas (funciona sem internet).
 * - Distritos: células de Voronoi dos centros de distrito, recortadas pelo contorno do país.
 * - Bolas dos pesqueiros com o estado das licenças (pinta verde / laranja / vermelha).
 * - Zoom com pinça, duplo toque ou botões; arrastar para mover.
 * Coordenadas virtuais: 358 × 440.
 */
class MapaPortugalView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    companion object {
        const val MW = 358f; const val MH = 440f
        private const val K = 80f; private const val X0 = 40f
        private const val LON0 = -9.7; private const val LAT0 = 42.43
        const val ZOOM_MAX = 6f

        /** lon/lat → coordenadas virtuais do mapa */
        fun proj(lon: Double, lat: Double): Pair<Float, Float> =
            ((lon - LON0) * 0.77 * K + X0).toFloat() to ((LAT0 - lat) * K).toFloat()

        /** coordenadas virtuais → lon/lat */
        fun desproj(x: Float, y: Float): Pair<Double, Double> =
            ((x - X0) / (0.77 * K) + LON0) to (LAT0 - y / K)
    }

    data class Bola(val p: Pesqueiro, val estado: Estado)

    var aoTocarPesqueiro: ((Pesqueiro) -> Unit)? = null
    var mostrarDistritos = true
        set(v) { field = v; invalidate() }
    var mostrarNomes = true
        set(v) { field = v; invalidate() }
    var selecionado: String? = null
        set(v) { field = v; invalidate() }

    /** Quando true, tocar no mapa marca um ponto (lat/lon) em vez de escolher pesqueiros. */
    var modoSelecao: Boolean = false
        set(v) { field = v; invalidate() }
    /** Pin do local escolhido pelo utilizador em modo seleção (lat, lon). */
    var pinSelecao: Pair<Double, Double>? = null
        set(v) { field = v; invalidate() }
    /** Chamada quando o utilizador toca no mapa estando em modo seleção. */
    var aoSelecionarLocal: ((lat: Double, lon: Double) -> Unit)? = null

    private var bolas: List<Bola> = emptyList()
    fun definir(lista: List<Bola>) { bolas = lista; invalidate() }

    // transformação: ecrã = virtual * base * zoom + desvio
    private var zoom = 1f
    private var dx = 0f
    private var dy = 0f
    private val base get() = width / MW

    // geometria pré-calculada (virtual)
    private val contorno: Path
    private val oceano: Path
    private val celulas: List<Path>
    private val rios: List<Path>
    private val pontosPais: List<Pair<Float, Float>>

    init {
        pontosPais = (Catalogo.costa + Catalogo.fronteira).map { (lo, la) -> proj(lo, la) }
        contorno = caminho(pontosPais)
        val (ox1, _) = proj(-9.0, 42.35)
        val (ox2, oy2) = proj(-8.9, 42.2)
        val (_, ySul) = proj(-7.4, 37.17)
        oceano = caminho(listOf(0f to 0f, ox1 to 0f, ox2 to oy2) + Catalogo.costa.map { (lo, la) -> proj(lo, la) } +
            listOf(MW to ySul, MW to MH, 0f to MH))
        val sitios = Catalogo.distritos.map { proj(it.lon, it.lat) }
        celulas = sitios.mapIndexed { i, a ->
            var pg = pontosPais
            sitios.forEachIndexed { j, b -> if (j != i && pg.isNotEmpty()) pg = recortar(pg, a, b) }
            caminho(pg)
        }
        rios = Catalogo.rios.map { r ->
            Path().apply { r.forEachIndexed { k, (lo, la) -> val (x, y) = proj(lo, la); if (k == 0) moveTo(x, y) else lineTo(x, y) } }
        }
    }

    private fun caminho(pts: List<Pair<Float, Float>>) = Path().apply {
        pts.forEachIndexed { k, (x, y) -> if (k == 0) moveTo(x, y) else lineTo(x, y) }
        close()
    }

    /** Sutherland–Hodgman: guarda a parte do polígono mais perto de [a] do que de [b]. */
    private fun recortar(pg: List<Pair<Float, Float>>, a: Pair<Float, Float>, b: Pair<Float, Float>): List<Pair<Float, Float>> {
        val mx = (a.first + b.first) / 2; val my = (a.second + b.second) / 2
        val nx = b.first - a.first; val ny = b.second - a.second
        fun f(p: Pair<Float, Float>) = (p.first - mx) * nx + (p.second - my) * ny
        val out = mutableListOf<Pair<Float, Float>>()
        for (i in pg.indices) {
            val p = pg[i]; val q = pg[(i + 1) % pg.size]
            val fp = f(p); val fq = f(q)
            if (fp <= 0) out += p
            if (fp * fq < 0) { val t = fp / (fp - fq); out += (p.first + (q.first - p.first) * t) to (p.second + (q.second - p.second) * t) }
        }
        return out
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        setMeasuredDimension(w, (w * MH / MW).toInt())
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val iconePeixe: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_peixe)?.mutate()?.also {
        DrawableCompat.setTint(it, android.graphics.Color.WHITE)
    }

    override fun onDraw(c: Canvas) {
        val ctx = context
        c.save()
        c.translate(dx, dy)
        c.scale(base * zoom, base * zoom)
        p.style = Paint.Style.FILL
        p.color = ctx.cor(R.color.mapa_espanha); c.drawRect(0f, 0f, MW, MH, p)
        p.color = ctx.cor(R.color.mapa_oceano); c.drawPath(oceano, p)
        p.color = ctx.cor(R.color.mapa_pt); c.drawPath(contorno, p)
        if (mostrarDistritos) {
            celulas.forEachIndexed { i, cel ->
                p.style = Paint.Style.FILL
                p.color = ctx.cor(if (i % 2 == 0) R.color.mapa_pt else R.color.mapa_pt2); c.drawPath(cel, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 0.8f; p.color = ctx.cor(R.color.mapa_contorno); p.alpha = 130
                c.drawPath(cel, p); p.alpha = 255
            }
        }
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f; p.strokeCap = Paint.Cap.ROUND
        p.color = ctx.cor(R.color.mapa_rio); p.alpha = 165
        rios.forEach { c.drawPath(it, p) }
        p.alpha = 255
        p.strokeWidth = 1.6f; p.color = ctx.cor(R.color.mapa_contorno); c.drawPath(contorno, p)

        txt.color = ctx.cor(R.color.mapa_texto)
        if (mostrarDistritos) {
            txt.textSize = 6.6f
            Catalogo.distritos.forEach { d -> val (x, y) = proj(d.lon, d.lat); c.drawText(d.etiqueta, x, y + 2f, txt) }
        }
        txt.textSize = 9f
        c.drawText(ctx.getString(R.string.mapa_espanha), 312f, 215f, txt)
        c.save(); c.rotate(-90f, 20f, 250f); c.drawText(ctx.getString(R.string.mapa_oceano), 20f, 250f, txt); c.restore()
        c.restore()

        // bolas (tamanho fixo no ecrã)
        val r = ctx.dp(17f)
        bolas.forEach { b -> desenharBola(c, b, r) }

        // pin de seleção (modo "escolher no mapa")
        pinSelecao?.let { (lat, lon) ->
            val (vx, vy) = proj(lon, lat)
            val sx = vx * base * zoom + dx
            val sy = vy * base * zoom + dy
            val rr = ctx.dp(14f)
            p.style = Paint.Style.FILL; p.color = ctx.cor(R.color.cp_aviso); p.alpha = 90
            c.drawCircle(sx, sy, rr * 2f, p); p.alpha = 255
            p.color = ctx.cor(R.color.cp_aviso)
            c.drawCircle(sx, sy, rr, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = ctx.dp(3f); p.color = android.graphics.Color.WHITE
            c.drawCircle(sx, sy, rr, p)
        }
    }

    private fun ecra(p: Pesqueiro): Pair<Float, Float> {
        val (x, y) = proj(p.lon, p.lat)
        return x * base * zoom + dx to y * base * zoom + dy
    }

    private fun desenharBola(c: Canvas, b: Bola, r: Float) {
        val ctx = context
        val (x, y) = ecra(b.p)
        if (x < -r * 4 || y < -r * 4 || x > width + r * 4 || y > height + r * 4) return
        if (b.p.id == selecionado) { p.style = Paint.Style.FILL; p.color = ctx.cor(R.color.cp_primaria); p.alpha = 90; c.drawCircle(x, y, r * 1.55f, p); p.alpha = 255 }
        p.style = Paint.Style.FILL
        p.color = ctx.cor(if (b.estado == Estado.ERRO) R.color.cp_cinza else R.color.cp_primaria)
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = ctx.dp(2.5f); p.color = android.graphics.Color.WHITE
        c.drawCircle(x, y, r, p)
        iconePeixe?.let { d -> val s = (r * 0.62f).toInt(); d.setBounds((x - s).toInt(), (y - s).toInt(), (x + s).toInt(), (y + s).toInt()); d.draw(c) }
        val pr = r * 0.36f; val px = x + r * 0.72f; val py = y - r * 0.72f
        p.style = Paint.Style.FILL; p.color = ctx.cor(b.estado.corForte()); c.drawCircle(px, py, pr, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = ctx.dp(1.8f); p.color = ctx.cor(R.color.cp_cartao); c.drawCircle(px, py, pr, p)
        if (mostrarNomes) {
            txt.textSize = ctx.dp(10f); txt.color = ctx.cor(R.color.cp_texto)
            val tw = txt.measureText(b.p.curto); val th = ctx.dp(14f); val g = ctx.dp(4f)
            val (lx, ly) = when (b.p.etiqueta) {
                't' -> x to y - r - g - th / 2
                'l' -> x - r - g - tw / 2 - ctx.dp(5f) to y
                'r' -> x + r + g + tw / 2 + ctx.dp(5f) to y
                else -> x to y + r + g + th / 2
            }
            p.style = Paint.Style.FILL; p.color = ctx.cor(R.color.mapa_etiqueta_fundo)
            c.drawRoundRect(RectF(lx - tw / 2 - ctx.dp(5f), ly - th / 2, lx + tw / 2 + ctx.dp(5f), ly + th / 2), th / 2, th / 2, p)
            c.drawText(b.p.curto, lx, ly + txt.textSize * 0.35f, txt)
        }
    }

    private fun limitar() {
        zoom = zoom.coerceIn(1f, ZOOM_MAX)
        dx = dx.coerceIn(width - width * zoom, 0f)
        dy = dy.coerceIn(height - height * zoom, 0f)
    }

    private fun zoomEm(fx: Float, fy: Float, novo: Float) {
        val z2 = novo.coerceIn(1f, ZOOM_MAX)
        dx = fx - (fx - dx) * (z2 / zoom); dy = fy - (fy - dy) * (z2 / zoom); zoom = z2
        limitar(); invalidate()
    }

    fun aproximar() = zoomEm(width / 2f, height / 2f, zoom * 1.6f)
    fun afastar() = zoomEm(width / 2f, height / 2f, zoom / 1.6f)
    fun repor() { zoom = 1f; dx = 0f; dy = 0f; invalidate() }

    fun focar(p: Pesqueiro, z: Float = 2.4f) {
        post {
            val (x, y) = proj(p.lon, p.lat)
            zoom = z; dx = width / 2f - x * base * zoom; dy = height * 0.42f - y * base * zoom
            selecionado = p.id; limitar(); invalidate()
        }
    }

    private val escala = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean { zoomEm(d.focusX, d.focusY, zoom * d.scaleFactor); return true }
    })

    private val gestos = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distX: Float, distY: Float): Boolean {
            dx -= distX; dy -= distY; limitar(); invalidate(); return true
        }
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (modoSelecao) {
                // ecrã → virtual → lon/lat
                val vx = (e.x - dx) / (base * zoom)
                val vy = (e.y - dy) / (base * zoom)
                val (lon, lat) = desproj(vx, vy)
                pinSelecao = lat to lon
                aoSelecionarLocal?.invoke(lat, lon)
                return true
            }
            val r = context.dp(22f)
            val alvo = bolas.minByOrNull { b -> val (x, y) = ecra(b.p); hypot(x - e.x, y - e.y) }
            if (alvo != null) {
                val (x, y) = ecra(alvo.p)
                if (hypot(x - e.x, y - e.y) <= r) { aoTocarPesqueiro?.invoke(alvo.p); return true }
            }
            return false
        }
        override fun onDoubleTap(e: MotionEvent): Boolean { zoomEm(e.x, e.y, if (zoom > 3.5f) 1f else zoom * 2f); return true }
    })

    override fun onTouchEvent(e: MotionEvent): Boolean {
        // impede a página (scroll) de roubar o gesto enquanto se mexe no mapa
        if (e.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
        escala.onTouchEvent(e)
        if (!escala.isInProgress) gestos.onTouchEvent(e)
        if (e.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

}
