package pt.ubi.di.pdm.aplicacaopesca.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import pt.ubi.di.pdm.aplicacaopesca.data.Repositorio

/**
 * Mudança de idioma da app com a API de idiomas por aplicação do AppCompat
 * (AppCompatDelegate.setApplicationLocales): funciona sem Internet e as Activities abertas recriam-se sozinhas.
 * Os textos da interface vêm de values-xx/strings.xml; os do catálogo vêm das tabelas «*_texto» (Repositorio.lingua).
 */
object Idiomas {

    const val AUTO = "auto"

    /** Guarda a escolha, relê o catálogo no novo idioma e muda o idioma da app. */
    fun aplicar(codigo: String) {
        Repositorio.idiomaEscolhido = codigo
        Repositorio.recarregarCatalogo(if (codigo == AUTO) Repositorio.idiomaDoSistema() else codigo)
        AppCompatDelegate.setApplicationLocales(listaDe(codigo))
    }

    /** No arranque da app: volta a aplicar o idioma escolhido (em «automático» não há nada a fazer). */
    fun restaurar() {
        val codigo = Repositorio.idiomaEscolhido
        if (codigo != AUTO) AppCompatDelegate.setApplicationLocales(listaDe(codigo))
    }

    private fun listaDe(codigo: String) =
        if (codigo == AUTO) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(codigo)
}
