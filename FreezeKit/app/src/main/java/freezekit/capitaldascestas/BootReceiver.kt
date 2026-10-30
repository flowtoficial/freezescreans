package freezekit.capitaldascestas

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import freezekit.capitaldascestas.log.EventLog
import freezekit.capitaldascestas.log.EventType

/** No boot, apenas registra o evento; reaplicação de políticas pertence à Etapa 6. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        EventLog.record(context, EventType.DEVICE_BOOTED)
    }
}
