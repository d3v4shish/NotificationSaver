package dev.d3v.notificationsaver

data class ConversationSelection(
    val conversationId: Long,
)

fun String?.trimmedOrNull(): String? = this?.trim()?.takeIf(String::isNotEmpty)

fun ConversationEntity.activityCount(): Int = if (messageCount > 0) messageCount else recordCount
