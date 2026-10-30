package freezekit.capitaldascestas

import android.os.Bundle
import android.widget.TextView
import freezekit.capitaldascestas.log.EventLog
import freezekit.capitaldascestas.security.AdminSessionActivity

/** Tela de consulta interna; a sessão é validada e encerrada pela classe-base. */
class EventLogActivity : AdminSessionActivity() {
    private lateinit var eventsText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!adminSessionValidAtCreate) return

        val page = UiSupport.page(
            this,
            "Registro de eventos",
            "Somente leitura. Os registros mais recentes aparecem primeiro."
        )
        UiSupport.addButton(page.body, this, "Atualizar") { refreshEvents() }
        eventsText = UiSupport.label(this, "Carregando…", 13f)
        page.body.addView(eventsText, UiSupport.matchWrap(this, 12))
        setContentView(page.scroll)
        refreshEvents()
    }

    override fun onResume() {
        super.onResume()
        if (::eventsText.isInitialized && !isFinishing) refreshEvents()
    }

    private fun refreshEvents() {
        if (!::eventsText.isInitialized) return
        val rows = EventLog.readNewestFirst(this)
        eventsText.text = if (rows.isEmpty()) {
            "Nenhum evento registrado ainda."
        } else {
            rows.joinToString(separator = "\n") { it.replace("\t", " | ") }
        }
    }
}
