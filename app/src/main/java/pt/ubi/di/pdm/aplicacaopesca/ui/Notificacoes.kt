package pt.ubi.di.pdm.aplicacaopesca.ui

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import pt.ubi.di.pdm.aplicacaopesca.MainActivity
import pt.ubi.di.pdm.aplicacaopesca.R
import pt.ubi.di.pdm.aplicacaopesca.data.Catalogo
import pt.ubi.di.pdm.aplicacaopesca.data.Licenca
import pt.ubi.di.pdm.aplicacaopesca.data.Repositorio
import pt.ubi.di.pdm.aplicacaopesca.util.pt
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Notificações locais de licenças a expirar.
 * Agendadas com AlarmManager, disparam [NotificacaoReceiver] no dia em que
 * a licença passa a estar «A expirar» (ou seja, [Repositorio.diasAviso] antes
 * do fim). Não há servidor nem rede.
 */
object Notificacoes {
    private const val CANAL = "licencas"
    private const val EXTRA_ID = "lic_id"

    /** Cria o canal (necessário em Android 8+). Chamar no arranque da app. */
    fun criarCanal(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CANAL) != null) return
        val canal = NotificationChannel(
            CANAL, ctx.getString(R.string.nav_licencas), NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = ctx.getString(R.string.notif_canal_descricao)
        }
        nm.createNotificationChannel(canal)
    }

    /** Agenda uma notificação para cada licença válida que ainda não foi avisada. */
    fun agendarProximas(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val hoje = Repositorio.hoje
        val aviso = Repositorio.diasAviso
        Repositorio.licencas().forEach { l ->
            val dia = l.fim.minusDays(aviso.toLong())  // data em que entra «a expirar»
            if (!dia.isAfter(hoje)) return@forEach    // já deve ter sido mostrada
            val quando = ZonedDateTime.of(dia, java.time.LocalTime.of(9, 0), ZoneId.systemDefault())
            val pi = criarPendente(ctx, l)
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC, quando.toInstant().toEpochMilli(), pi)
            } catch (_: SecurityException) {
                // em alguns dispositivos a partir do Android 12 pode ser preciso permissão;
                // o aviso dentro da app continua a funcionar
            }
        }
    }

    fun cancelarTodas(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        Repositorio.licencas().forEach { l -> am.cancel(criarPendente(ctx, l)) }
    }

    private fun criarPendente(ctx: Context, l: Licenca): PendingIntent {
        val i = Intent(ctx, NotificacaoReceiver::class.java).apply {
            action = "pescador.lic.${l.id}"
            putExtra(EXTRA_ID, l.id.toInt())
        }
        return PendingIntent.getBroadcast(ctx, l.id.toInt(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun mostrarAgora(ctx: Context, id: Int, titulo: String, texto: String) {
        criarCanal(ctx)
        val abrir = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(ctx, 0, abrir,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_licenca_pequeno)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        val nm = ContextCompat.getSystemService(ctx, NotificationManager::class.java) ?: return
        nm.notify(id, n)
    }
}

/** Recebe o alarme programado e envia a notificação, com o texto no idioma da app. */
class NotificacaoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("lic_id", 0)
        val licenca = Repositorio.licencas().firstOrNull { it.id.toInt() == id } ?: return
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(Repositorio.lingua)) }
        val ctx = context.createConfigurationContext(config)
        val dias = Repositorio.diasAviso
        val titulo = ctx.getString(R.string.notif_titulo, ctx.resources.getQuantityString(R.plurals.n_dias, dias, dias))
        val texto = ctx.getString(R.string.notif_texto, Catalogo.tipoLicenca(licenca.tipo).nome, licenca.fim.pt())
        Notificacoes.mostrarAgora(ctx, id, titulo, texto)
    }
}
