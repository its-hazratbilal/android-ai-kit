package com.hazratbilal.aikit.core

/**
 * Marks API that is public only because it must cross Gradle module
 * boundaries within AiKit itself (e.g. from `core` to `chat`). Not intended
 * for use by consumers of the library — behavior may change without notice.
 */
@RequiresOptIn(
    message = "This is internal AiKit API not intended for use outside the library's own modules.",
    level = RequiresOptIn.Level.ERROR
)
@Retention(AnnotationRetention.BINARY)
annotation class InternalAiKitApi