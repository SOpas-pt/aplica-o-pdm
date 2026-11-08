package pt.ubi.di.pdm.aplicacaopesca.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import pt.ubi.di.pdm.aplicacaopesca.R
import pt.ubi.di.pdm.aplicacaopesca.data.Catalogo
import pt.ubi.di.pdm.aplicacaopesca.data.Especie
import pt.ubi.di.pdm.aplicacaopesca.data.Estado
import pt.ubi.di.pdm.aplicacaopesca.data.Exigencia
import pt.ubi.di.pdm.aplicacaopesca.util.comoSinal
import pt.ubi.di.pdm.aplicacaopesca.util.cor
import pt.ubi.di.pdm.aplicacaopesca.util.corForte
import pt.ubi.di.pdm.aplicacaopesca.util.corTexto
import pt.ubi.di.pdm.aplicacaopesca.util.dp
import pt.ubi.di.pdm.aplicacaopesca.util.dpi
import pt.ubi.di.pdm.aplicacaopesca.util.fundoArredondado
import pt.ubi.di.pdm.aplicacaopesca.util.icone
import pt.ubi.di.pdm.aplicacaopesca.util.pt

/** Funções que constroem pedaços de interface usados em vários ecrãs. */
object Vistas {

    fun etiqueta(ctx: Context, texto: String): Chip = Chip(ctx).apply {
        text = texto
        isClickable = false
        isCheckable = false
        setEnsureMinTouchTargetSize(false)
        chipMinHeight = ctx.dp(24f)
        textSize = 11.5f
        setTextColor(ctx.cor(R.color.cp_texto_suave))
        chipBackgroundColor = android.content.res.ColorStateList.valueOf(ctx.cor(R.color.cp_cartao2))
        chipStrokeColor = android.content.res.ColorStateList.valueOf(ctx.cor(R.color.cp_borda))
        chipStrokeWidth = ctx.dp(1f)
        textStartPadding = ctx.dp(2f); textEndPadding = ctx.dp(2f)
    }

    fun linhaInfo(inf: LayoutInflater, pai: ViewGroup, icone: Int, rotulo: String, valor: CharSequence): View {
        val v = inf.inflate(R.layout.linha_info, pai, false)
        v.findViewById<ImageView>(R.id.ico).setImageResource(icone)
        v.findViewById<TextView>(R.id.txtRotulo).text = rotulo
        v.findViewById<TextView>(R.id.txtValor).text = valor
        pai.addView(v)
        return v
    }

    /** Devolve o id do drawable foto_<id> se existir no APK; 0 caso contrário. */
    fun fotoRealDe(ctx: Context, especieId: String): Int {
        return ctx.resources.getIdentifier("foto_$especieId", "drawable", ctx.packageName)
    }

    /**
     * Grelha de espécies (3 colunas no telemóvel, mais em ecrãs largos).
     * @param peixedex true = modo coleção (bloqueadas em silhueta «Quem é este peixe?»)
     *                 false = mostra sempre a ilustração (com um cadeado pequeno se ainda não desbloqueada)
     * @param comFotoReal se true, usa as fotos reais (drawable foto_<id>) quando existirem
     * @param fotoCapturaDe se definido e devolver um caminho, mostra a foto da captura do utilizador
     *                      para essa espécie (tem prioridade sobre [comFotoReal])
     */
    fun grelhaEspecies(
        inf: LayoutInflater, pai: LinearLayout, especies: List<Especie>, desbloqueadas: Set<String>,
        peixedex: Boolean, comFotoReal: Boolean = false,
        fotoCapturaDe: ((String) -> String?)? = null,
        aoTocar: (Especie) -> Unit
    ) {
        pai.removeAllViews()
        val ctx = pai.context
        val colunas = ctx.resources.getInteger(R.integer.colunas_especies)
        especies.chunked(colunas).forEach { grupo ->
            val linha = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { bottomMargin = ctx.dpi(8) }
            }
            for (i in 0 until colunas) {
                val e = grupo.getOrNull(i)
                val cel = inf.inflate(R.layout.item_especie, linha, false)
                (cel.layoutParams as LinearLayout.LayoutParams).apply { if (i < colunas - 1) marginEnd = ctx.dpi(8) }
                if (e == null) { cel.visibility = View.INVISIBLE; linha.addView(cel); continue }
                val tem = e.id in desbloqueadas
                val pv = cel.findViewById<PeixeView>(R.id.peixe)
                pv.especieId = e.id
                pv.proporcao = if (peixedex) 1f else 0.75f
                val nome = cel.findViewById<TextView>(R.id.txtNome)
                val grupoTxt = cel.findViewById<TextView>(R.id.txtGrupo)
                val numero = cel.findViewById<TextView>(R.id.txtNumero)
                if (peixedex) {
                    numero.visibility = View.VISIBLE
                    numero.text = ctx.getString(R.string.numero_dex, e.numero)
                    if (tem) {
                        pv.modo = PeixeView.Modo.NORMAL
                        nome.text = e.nome; grupoTxt.text = e.latim
                        cel.findViewById<View>(R.id.visto).apply { visibility = View.VISIBLE; comoSinal(if (e.crustaceo) Estado.AVISO else Estado.OK) }
                        if (!aplicarFotoCaptura(cel, pv, fotoCapturaDe?.invoke(e.id)) && comFotoReal)
                            aplicarFotoReal(cel, pv, e.id)
                    } else {
                        pv.modo = PeixeView.Modo.SILHUETA
                        nome.text = ctx.getString(R.string.dex_desconhecido); grupoTxt.text = ctx.getString(R.string.dex_por_descobrir)
                        cel.findViewById<View>(R.id.txtQuem).visibility = View.VISIBLE
                    }
                } else {
                    pv.modo = PeixeView.Modo.NORMAL
                    nome.text = e.nome
                    grupoTxt.text = ctx.getString(e.grupo.rotulo)
                    if (e.crustaceo) grupoTxt.setTextColor(ctx.cor(R.color.cp_erro_texto))
                    if (!tem) cel.findViewById<View>(R.id.cadeado).visibility = View.VISIBLE
                    if (comFotoReal) aplicarFotoReal(cel, pv, e.id)
                }
                cel.setOnClickListener { aoTocar(e) }
                linha.addView(cel)
            }
            pai.addView(linha)
        }
    }

    /**
     * Cartão de uma licença exigida: sinal verde/laranja/vermelho, estado e ação.
     * @param aoAdicionar chamado quando falta a licença («Já tenho · adicionar»)
     * @param aoEditar chamado para atualizar datas (renovação)
     */
    fun exigencia(
        inf: LayoutInflater, pai: ViewGroup, ex: Exigencia,
        aoAdicionar: (String) -> Unit, aoEditar: (Long) -> Unit
    ): View {
        val ctx = pai.context
        val v = inf.inflate(R.layout.item_exigencia, pai, false)
        val tipo = Catalogo.tipoLicenca(ex.tipo)
        v.fundoArredondado(R.color.cp_cartao, 18f, ex.estado.corForte(), 1.5f)
        v.findViewById<View>(R.id.sinal).comoSinal(ex.estado)
        v.findViewById<ImageView>(R.id.icoSinal).setImageResource(ex.estado.icone())
        v.findViewById<TextView>(R.id.txtNome).text = tipo.nome
        val l = ex.licenca
        var ent = ctx.getString(R.string.exig_emitida_por, tipo.entidade)
        if (l != null && l.numero.isNotBlank()) ent += " · " + ctx.getString(R.string.numero_abrev, l.numero)
        if (l != null && l.tipo != ex.tipo) ent += " · " + ctx.getString(R.string.exig_coberta_nacional)
        v.findViewById<TextView>(R.id.txtEntidade).text = ent
        val dias = ex.dias ?: 0
        v.findViewById<TextView>(R.id.txtEstado).apply {
            setTextColor(ctx.cor(ex.estado.corTexto()))
            text = when {
                l == null -> ctx.getString(R.string.exig_nao_tem)
                ex.estado == Estado.ERRO -> ctx.getString(R.string.exig_expirada_a, l.fim.pt(),
                    ctx.resources.getQuantityString(R.plurals.n_dias, (-dias).toInt(), -dias))
                ex.estado == Estado.AVISO -> ctx.getString(R.string.exig_valida_expira, l.fim.pt(),
                    ctx.resources.getQuantityString(R.plurals.n_dias, dias.toInt(), dias))
                else -> ctx.getString(R.string.exig_valida_ate, l.fim.pt())
            }
        }
        val como = v.findViewById<TextView>(R.id.txtComo)
        val btn = v.findViewById<MaterialButton>(R.id.btnAcao)
        if (ex.estado == Estado.OK) {
            como.visibility = View.GONE; btn.visibility = View.GONE
        } else {
            como.text = tipo.comoObter
            if (l == null) {
                btn.text = ctx.getString(R.string.ja_tenho_adicionar)
                btn.setIconResource(R.drawable.ic_mais)
                btn.setOnClickListener { aoAdicionar(ex.tipo) }
            } else {
                btn.text = ctx.getString(R.string.exig_renovei)
                btn.setIconResource(R.drawable.ic_editar)
                btn.setOnClickListener { aoEditar(l.id) }
            }
        }
        pai.addView(v)
        return v
    }

    private fun aplicarFotoReal(cel: View, pv: PeixeView, especieId: String) {
        val foto = fotoRealDe(cel.context, especieId)
        if (foto == 0) return
        pv.visibility = View.GONE
        cel.findViewById<ImageView>(R.id.imgFoto).apply {
            visibility = View.VISIBLE
            setImageResource(foto)
        }
    }

    /** Decodifica a foto na thread da interface: aceitável para ~16 miniaturas; com mais, usar Glide ou Coil. */
    private fun aplicarFotoCaptura(cel: View, pv: PeixeView, caminho: String?): Boolean {
        if (caminho.isNullOrBlank() || !java.io.File(caminho).exists()) return false
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
        val bmp = android.graphics.BitmapFactory.decodeFile(caminho, opts) ?: return false
        pv.visibility = View.GONE
        cel.findViewById<ImageView>(R.id.imgFoto).apply {
            visibility = View.VISIBLE
            setImageBitmap(bmp)
        }
        return true
    }

    fun circulo(v: View, corRes: Int) {
        v.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(v.context.cor(corRes)) }
    }

    /**
     * Preenche um par (iniciais, foto) com a foto de perfil se existir, ou mostra as iniciais.
     * [img] pode ser qualquer ImageView redondo (usar o bg_avatar + clipToOutline).
     */
    fun mostrarAvatarPerfil(ini: TextView, img: ImageView, caminho: String?) {
        val bmp = caminho?.takeIf { it.isNotBlank() && java.io.File(it).exists() }?.let {
            val o = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
            android.graphics.BitmapFactory.decodeFile(it, o)
        }
        if (bmp != null) {
            img.setImageBitmap(bmp); img.visibility = View.VISIBLE; ini.visibility = View.GONE
        } else {
            img.visibility = View.GONE; ini.visibility = View.VISIBLE
        }
    }
}
