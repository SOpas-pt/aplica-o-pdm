package pt.ubi.di.pdm.aplicacaopesca

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import pt.ubi.di.pdm.aplicacaopesca.data.Catalogo
import pt.ubi.di.pdm.aplicacaopesca.data.Repositorio
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import pt.ubi.di.pdm.aplicacaopesca.ui.CapturasFragment
import pt.ubi.di.pdm.aplicacaopesca.ui.ConquistasActivity
import pt.ubi.di.pdm.aplicacaopesca.ui.DefinicoesActivity
import pt.ubi.di.pdm.aplicacaopesca.ui.EstatisticasFragment
import pt.ubi.di.pdm.aplicacaopesca.ui.GuiaActivity
import pt.ubi.di.pdm.aplicacaopesca.ui.Vistas
import pt.ubi.di.pdm.aplicacaopesca.ui.LicencasFragment
import pt.ubi.di.pdm.aplicacaopesca.ui.NovoPesqueiroActivity
import pt.ubi.di.pdm.aplicacaopesca.ui.PeixedexFragment
import pt.ubi.di.pdm.aplicacaopesca.ui.PerfilActivity
import pt.ubi.di.pdm.aplicacaopesca.ui.PesqueirosFragment

/**
 * Atividade principal: barra de topo, menu lateral (com perfil) e barra inferior com 5 separadores.
 * Cada separador é um Fragment mostrado no contentor R.id.fragmentos.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        /** Espécies desbloqueadas necessárias para cada título (mesma ordem de R.array.niveis_peixedex). */
        private val NIVEIS_PEIXEDEX = intArrayOf(0, 3, 6, 10, 15, 20)

        /** extra: id do pesqueiro a mostrar no mapa (usado pelo botão «Ver no mapa») */
        const val EXTRA_FOCAR = "focar_pesqueiro"
        /** extras: ir diretamente ao separador Capturas, Nova, com estes valores pré-preenchidos. */
        const val EXTRA_CAPTURA_PESQUEIRO = "captura_pesqueiro"
        const val EXTRA_CAPTURA_ESPECIE = "captura_especie"

        fun abrirNoMapa(ctx: Context, pesqueiroId: String) {
            ctx.startActivity(Intent(ctx, MainActivity::class.java)
                .putExtra(EXTRA_FOCAR, pesqueiroId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }

        /** Abre o separador «Capturas · Nova» com o pesqueiro e/ou espécie pré-escolhidos. */
        fun abrirCaptura(ctx: Context, pesqueiro: String? = null, especie: String? = null) {
            val i = Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (pesqueiro != null) i.putExtra(EXTRA_CAPTURA_PESQUEIRO, pesqueiro)
            if (especie != null) i.putExtra(EXTRA_CAPTURA_ESPECIE, especie)
            ctx.startActivity(i)
        }
    }

    private lateinit var gaveta: DrawerLayout
    private lateinit var navInferior: BottomNavigationView
    private lateinit var navLateral: NavigationView
    private lateinit var fabNovo: ExtendedFloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        Repositorio.iniciar(this)

        gaveta = findViewById(R.id.main)
        navInferior = findViewById(R.id.navInferior)
        navLateral = findViewById(R.id.navLateral)
        fabNovo = findViewById(R.id.fabNovoLocal)
        fabNovo.setOnClickListener { startActivity(Intent(this, NovoPesqueiroActivity::class.java)) }

        // margens do sistema (barra de estado em cima; a barra inferior trata da navegação)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.conteudo)) { v, insets ->
            val s = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = s.top)
            insets
        }

        findViewById<ImageButton>(R.id.btnMenu).setOnClickListener { gaveta.openDrawer(GravityCompat.START) }
        findViewById<TextView>(R.id.txtAvatar).setOnClickListener { gaveta.openDrawer(GravityCompat.START) }
        findViewById<ImageButton>(R.id.btnTema).apply {
            setImageResource(if (Repositorio.modoEscuro) R.drawable.ic_sol else R.drawable.ic_lua)
            setOnClickListener {
                Repositorio.modoEscuro = !Repositorio.modoEscuro
                App.aplicarTema(Repositorio.modoEscuro)   // a atividade é recriada com o novo tema
            }
        }

        navInferior.setOnItemSelectedListener { item ->
            mostrar(item.itemId)
            navLateral.setCheckedItem(lateralDe(item.itemId))
            fabNovo.visibility = if (item.itemId == R.id.nav_pesqueiros) android.view.View.VISIBLE else android.view.View.GONE
            true
        }
        navLateral.setNavigationItemSelectedListener { item ->
            gaveta.closeDrawer(GravityCompat.START)
            when (item.itemId) {
                R.id.lat_pesqueiros -> navInferior.selectedItemId = R.id.nav_pesqueiros
                R.id.lat_peixedex -> navInferior.selectedItemId = R.id.nav_peixedex
                R.id.lat_capturas -> navInferior.selectedItemId = R.id.nav_capturas
                R.id.lat_licencas -> navInferior.selectedItemId = R.id.nav_licencas
                R.id.lat_estatisticas -> navInferior.selectedItemId = R.id.nav_estatisticas
                R.id.lat_perfil -> startActivity(Intent(this, PerfilActivity::class.java))
                R.id.lat_definicoes -> startActivity(Intent(this, DefinicoesActivity::class.java))
                R.id.lat_guia -> startActivity(Intent(this, GuiaActivity::class.java))
                R.id.lat_conquistas -> startActivity(Intent(this, ConquistasActivity::class.java))
                else -> Toast.makeText(this, getString(R.string.menu_item_em_desenvolvimento, item.title), Toast.LENGTH_SHORT).show()
            }
            true
        }

        // «voltar» fecha primeiro o menu lateral (compatível com o gesto preditivo do Android)
        val fecharMenu = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() { gaveta.closeDrawer(GravityCompat.START) }
        }
        onBackPressedDispatcher.addCallback(this, fecharMenu)
        gaveta.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: android.view.View) { fecharMenu.isEnabled = true; atualizarPerfil() }
            override fun onDrawerClosed(drawerView: android.view.View) { fecharMenu.isEnabled = false }
        })

        if (savedInstanceState == null) {
            navInferior.selectedItemId = R.id.nav_pesqueiros
            tratarIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tratarIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        atualizarPerfil()
        pedirNotificacoes()
    }

    private val permissaoNotificacoes = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private var jaPediuNotificacoes = false

    /** Android 13+: pede autorização para os avisos de licença, mas só quando já há licenças e uma vez por sessão. */
    fun pedirNotificacoes() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || jaPediuNotificacoes) return
        if (Repositorio.licencas().isEmpty()) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        jaPediuNotificacoes = true
        permissaoNotificacoes.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun tratarIntent(i: Intent?) {
        i ?: return
        val idMapa = i.getStringExtra(EXTRA_FOCAR)
        if (idMapa != null) {
            i.removeExtra(EXTRA_FOCAR)
            if (navInferior.selectedItemId != R.id.nav_pesqueiros) navInferior.selectedItemId = R.id.nav_pesqueiros
            supportFragmentManager.executePendingTransactions()
            (supportFragmentManager.findFragmentById(R.id.fragmentos) as? PesqueirosFragment)?.focarNoMapa(idMapa)
            return
        }
        if (i.hasExtra(EXTRA_CAPTURA_PESQUEIRO) || i.hasExtra(EXTRA_CAPTURA_ESPECIE)) {
            val p = i.getStringExtra(EXTRA_CAPTURA_PESQUEIRO); val e = i.getStringExtra(EXTRA_CAPTURA_ESPECIE)
            i.removeExtra(EXTRA_CAPTURA_PESQUEIRO); i.removeExtra(EXTRA_CAPTURA_ESPECIE)
            mostrarComArgs(R.id.nav_capturas, CapturasFragment.novo(p, e))
            navInferior.menu.findItem(R.id.nav_capturas).isChecked = true
            navLateral.setCheckedItem(lateralDe(R.id.nav_capturas))
        }
    }

    private fun mostrar(itemId: Int) {
        val f: Fragment = when (itemId) {
            R.id.nav_pesqueiros -> PesqueirosFragment()
            R.id.nav_licencas -> LicencasFragment()
            R.id.nav_peixedex -> PeixedexFragment()
            R.id.nav_capturas -> CapturasFragment()
            else -> EstatisticasFragment()
        }
        supportFragmentManager.beginTransaction().replace(R.id.fragmentos, f).commit()
    }

    private fun mostrarComArgs(@Suppress("UNUSED_PARAMETER") itemId: Int, f: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.fragmentos, f).commit()
    }

    private fun lateralDe(itemId: Int) = when (itemId) {
        R.id.nav_peixedex -> R.id.lat_peixedex
        R.id.nav_capturas -> R.id.lat_capturas
        R.id.nav_licencas -> R.id.lat_licencas
        R.id.nav_estatisticas -> R.id.lat_estatisticas
        else -> R.id.lat_pesqueiros
    }

    fun atualizarPerfil() {
        val nome = Repositorio.nomePerfil
        val iniciais = nome.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
            .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
        val txtAvatar = findViewById<TextView>(R.id.txtAvatar)
        txtAvatar.text = iniciais
        val cab = navLateral.getHeaderView(0)
        val txtAvatarGrande = cab.findViewById<TextView>(R.id.txtAvatarGrande)
        txtAvatarGrande.text = iniciais
        val foto = Repositorio.fotoPerfil
        Vistas.mostrarAvatarPerfil(txtAvatar, findViewById(R.id.imgAvatar), foto)
        Vistas.mostrarAvatarPerfil(txtAvatarGrande, cab.findViewById(R.id.imgAvatarGrande), foto)
        cab.findViewById<TextView>(R.id.txtNome).text = nome
        val capturas = Repositorio.capturas().size
        // O título sobe com as espécies desbloqueadas na Peixedex (limiares em NIVEIS_PEIXEDEX)
        val desbloqueadas = Repositorio.desbloqueadas().size
        val nivel = NIVEIS_PEIXEDEX.indexOfLast { desbloqueadas >= it }.coerceAtLeast(0)
        cab.findViewById<TextView>(R.id.txtNivel).text = resources.getStringArray(R.array.niveis_peixedex)[nivel]
        cab.findViewById<TextView>(R.id.txtNumCapturas).text = contador(capturas.toString(), getString(R.string.contador_capturas))
        cab.findViewById<TextView>(R.id.txtNumPeixedex).text =
            contador("${Repositorio.desbloqueadas().size}/${Catalogo.especies.size}", getString(R.string.nav_peixedex))
        cab.findViewById<TextView>(R.id.txtNumLocais).text = contador(Repositorio.pesqueiros().size.toString(), getString(R.string.contador_locais))
    }

    private fun contador(numero: String, rotulo: String): CharSequence {
        val sb = SpannableStringBuilder(numero)
        sb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, numero.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(AbsoluteSizeSpan(15, true), 0, numero.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.append("\n").append(rotulo)
        return sb
    }

}
