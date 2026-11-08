package pt.ubi.di.pdm.aplicacaopesca

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import pt.ubi.di.pdm.aplicacaopesca.data.Repositorio
import pt.ubi.di.pdm.aplicacaopesca.ui.Idiomas
import pt.ubi.di.pdm.aplicacaopesca.ui.Notificacoes

/** Arranque da app: abre a base de dados, aplica o tema e prepara notificações. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Repositorio.iniciar(this)
        Idiomas.restaurar()
        aplicarTema(Repositorio.modoEscuro)
        Notificacoes.criarCanal(this)
        Notificacoes.agendarProximas(this)
    }

    companion object {
        fun aplicarTema(escuro: Boolean) {
            AppCompatDelegate.setDefaultNightMode(
                if (escuro) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }
    }
}
