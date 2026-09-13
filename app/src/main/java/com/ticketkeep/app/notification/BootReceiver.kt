package com.ticketkeep.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ticketkeep.app.data.local.TicketDatabase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: WarrantyReminderScheduler
    @Inject lateinit var database: TicketDatabase

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tickets = database.ticketDao().getAllWithWarranty()
                scheduler.rescheduleAll(tickets)
            } finally {
                pending.finish()
            }
        }
    }
}
