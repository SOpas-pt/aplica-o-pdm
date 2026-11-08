package pt.ubi.di.pdm.aplicacaopesca

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.ubi.di.pdm.aplicacaopesca.data.Captura
import pt.ubi.di.pdm.aplicacaopesca.data.Catalogo
import pt.ubi.di.pdm.aplicacaopesca.data.Conquista
import pt.ubi.di.pdm.aplicacaopesca.data.CopiaSeguranca
import pt.ubi.di.pdm.aplicacaopesca.data.DadosConquistas
import pt.ubi.di.pdm.aplicacaopesca.data.Estado
import pt.ubi.di.pdm.aplicacaopesca.data.Licenca
import pt.ubi.di.pdm.aplicacaopesca.data.estadoDe
import java.time.LocalDate

/** Testes que correm no computador (sem emulador): regras de licenças e validação da cópia de segurança. */
class ExampleUnitTest {
    private val hoje = LocalDate.of(2026, 6, 1)
    private fun lic(tipo: String = "int", numero: String = "A1", inicio: LocalDate = hoje.minusDays(10), fim: LocalDate = hoje.plusDays(100)) =
        Licenca(0, tipo, numero, inicio, fim)
    private fun cap(peso: Double = 1.0, pesqueiro: String = "zezere", especie: String = "truta", data: LocalDate = hoje) =
        Captura(0, data, pesqueiro, especie, peso, null, null)

    private val pesqueiroExiste = { id: String -> id == "zezere" }
    private val especieExiste = { id: String -> id == "truta" || id == "boga" }

    // ---- estado das licenças
    @Test fun licencaLongeDoFimEstaValida() =
        assertEquals(Estado.OK, estadoDe(lic(fim = hoje.plusDays(100)), hoje, 30).estado)

    @Test fun licencaNoPrazoDeAvisoEstaAExpirar() =
        assertEquals(Estado.AVISO, estadoDe(lic(fim = hoje.plusDays(30)), hoje, 30).estado)

    @Test fun licencaQueTerminouEstaExpirada() =
        assertEquals(Estado.ERRO, estadoDe(lic(fim = hoje.minusDays(1)), hoje, 30).estado)

    @Test fun licencaQueTerminaHojeAindaEstaAExpirar() =
        assertEquals(Estado.AVISO, estadoDe(lic(fim = hoje), hoje, 30).estado)

    // ---- licença nacional cobre as regionais
    @Test fun licencaNacionalCobreRegionais() {
        assertTrue(Catalogo.cobre("nao-res", "int"))
        assertTrue(Catalogo.cobre("int", "int"))
        assertFalse(Catalogo.cobre("int", "mar"))
    }

    // ---- importação: capturas
    @Test fun capturaValidaEntra() =
        assertEquals(1, CopiaSeguranca.capturasNovas(listOf(cap()), emptyList(), pesqueiroExiste, especieExiste).size)

    @Test fun capturaComPesoNegativoOuZeroEIgnorada() {
        val r = CopiaSeguranca.capturasNovas(listOf(cap(peso = 0.0), cap(peso = -2.0)), emptyList(), pesqueiroExiste, especieExiste)
        assertTrue(r.isEmpty())
    }

    @Test fun capturaDeLocalOuEspecieDesconhecidosEIgnorada() {
        val r = CopiaSeguranca.capturasNovas(listOf(cap(pesqueiro = "xpto"), cap(especie = "xpto")), emptyList(), pesqueiroExiste, especieExiste)
        assertTrue(r.isEmpty())
    }

    @Test fun capturaJaExistenteNaoEntraDeNovo() {
        val r = CopiaSeguranca.capturasNovas(listOf(cap()), listOf(cap()), pesqueiroExiste, especieExiste)
        assertTrue(r.isEmpty())
    }

    @Test fun repetidasNoProprioFicheiroEntramUmaVez() {
        val r = CopiaSeguranca.capturasNovas(listOf(cap(), cap(), cap(especie = "boga")), emptyList(), pesqueiroExiste, especieExiste)
        assertEquals(2, r.size)
    }

    // ---- conquistas
    private fun dados(capturas: List<Captura>, desbloqueadas: Int = 0, total: Int = 20) = DadosConquistas(capturas, desbloqueadas, total)
    private fun feita(c: Conquista, d: DadosConquistas) = c.avaliar(d).feita

    @Test fun semCapturasNaoHaConquistas() =
        assertTrue(Conquista.todas(dados(emptyList())).none { it.feita })

    @Test fun primeiraCapturaDesbloqueiaPrimeira() {
        assertTrue(feita(Conquista.PRIMEIRA, dados(listOf(cap()))))
        assertFalse(feita(Conquista.DEZ, dados(listOf(cap()))))
    }

    @Test fun pesoDecideOsPeixesGraudos() {
        assertFalse(feita(Conquista.GRAUDO, dados(listOf(cap(peso = 2.99)))))
        assertTrue(feita(Conquista.GRAUDO, dados(listOf(cap(peso = 3.0)))))
        assertFalse(feita(Conquista.GIGANTE, dados(listOf(cap(peso = 9.5)))))
    }

    @Test fun exploradorContaLocaisDiferentes() {
        val tres = listOf(cap(pesqueiro = "a"), cap(pesqueiro = "b"), cap(pesqueiro = "a"))
        assertFalse(feita(Conquista.EXPLORADOR, dados(tres)))
        assertTrue(feita(Conquista.EXPLORADOR, dados(tres + cap(pesqueiro = "c"))))
    }

    @Test fun peixedexCompletaPrecisaDeTodasAsEspecies() {
        assertFalse(feita(Conquista.PEIXEDEX, dados(emptyList(), desbloqueadas = 19)))
        assertTrue(feita(Conquista.PEIXEDEX, dados(emptyList(), desbloqueadas = 20)))
    }

    @Test fun novasSoDevolveAsQueMudaram() {
        val antes = dados(emptyList())
        val depois = dados(listOf(cap()), desbloqueadas = 1)
        assertEquals(listOf(Conquista.PRIMEIRA), Conquista.novas(antes, depois))
        assertTrue(Conquista.novas(depois, depois).isEmpty())
    }

    // ---- importação: licenças
    @Test fun licencaValidaEntra() =
        assertEquals(1, CopiaSeguranca.licencasNovas(listOf(lic()), emptyList()) { it == "int" }.size)

    @Test fun licencaComFimAntesDoInicioEIgnorada() {
        val r = CopiaSeguranca.licencasNovas(listOf(lic(inicio = hoje, fim = hoje.minusDays(5))), emptyList()) { true }
        assertTrue(r.isEmpty())
    }

    @Test fun licencaSemNumeroOuDeTipoDesconhecidoEIgnorada() {
        val r = CopiaSeguranca.licencasNovas(listOf(lic(numero = "  "), lic(tipo = "xpto")), emptyList()) { it == "int" }
        assertTrue(r.isEmpty())
    }

    @Test fun licencaJaExistenteNaoEntraDeNovo() =
        assertTrue(CopiaSeguranca.licencasNovas(listOf(lic()), listOf(lic())) { true }.isEmpty())
}
