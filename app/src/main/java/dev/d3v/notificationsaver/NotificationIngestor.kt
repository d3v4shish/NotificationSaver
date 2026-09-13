package dev.d3v.notificationsaver

import android.service.notification.StatusBarNotification
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

sealed interface CaptureEvent {
    val capturedAt: Long

    data class Posted(
        val notification: StatusBarNotification,
        val rankingImportance: Int?,
        val recovered: Boolean,
        override val capturedAt: Long = System.currentTimeMillis(),
    ) : CaptureEvent

    data class Removed(
        val notification: StatusBarNotification,
        val reason: Int,
        val rankingImportance: Int?,
        override val capturedAt: Long = System.currentTimeMillis(),
    ) : CaptureEvent

    data class Reconcile(
        val activeNotifications: List<Posted>,
        override val capturedAt: Long = System.currentTimeMillis(),
    ) : CaptureEvent
}

enum class EnqueueResult {
    Accepted,
    Closed,
}

class NotificationIngestor(
    scope: CoroutineScope,
    private val repository: NotificationRepository,
    private val operationalMetrics: OperationalMetricsStore,
    private val logger: AppLogger,
) {
    private val channel = Channel<CaptureEvent>(capacity = Channel.UNLIMITED)
    private val pendingCount = AtomicInteger(0)

    init {
        scope.launch {
            for (first in channel) {
                val batch = ArrayList<CaptureEvent>(MAX_BATCH_SIZE)
                batch += first
                pendingCount.decrementAndGet()
                while (batch.size < MAX_BATCH_SIZE) {
                    val next = channel.tryReceive().getOrNull() ?: break
                    batch += next
                    pendingCount.decrementAndGet()
                }
                runCatching {
                    repository.recordCaptureEvents(batch)
                }.onFailure { error ->
                    operationalMetrics.recordStoreFailure()
                    logger.error("NotificationIngestor", "Failed to persist a capture batch", error)
                }
            }
        }
    }

    fun submit(event: CaptureEvent): EnqueueResult {
        // Increment before publishing so the consumer cannot decrement an event it has not
        // yet been counted for.
        val depth = pendingCount.incrementAndGet()
        val result = channel.trySend(event)
        if (result.isFailure) {
            pendingCount.decrementAndGet()
            operationalMetrics.recordQueueDrop()
            logger.error("NotificationIngestor", "Capture channel is closed")
            return EnqueueResult.Closed
        }
        operationalMetrics.recordQueueDepth(depth)
        return EnqueueResult.Accepted
    }

    companion object {
        private const val MAX_BATCH_SIZE = 100
    }
}
