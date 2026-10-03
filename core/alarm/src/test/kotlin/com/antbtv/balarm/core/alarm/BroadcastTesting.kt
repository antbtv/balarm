package com.antbtv.balarm.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import java.util.concurrent.TimeUnit
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBroadcastPendingResult
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/*
 * Доставка broadcast «как от системы» — с PendingResult, чтобы работал goAsync().
 * Опирается на внутренности Robolectric 4.17 (package-private ShadowBroadcastPendingResult.create,
 * скрытые BroadcastReceiver.setPendingResult / mPendingResult). При обновлении Robolectric сверить API:
 * `javap -cp shadows-framework-<ver>.jar org.robolectric.shadows.ShadowBroadcastPendingResult`.
 */

fun <R : BroadcastReceiver> R.withPendingResult(): R = apply {
    val pending = ReflectionHelpers.callStaticMethod<BroadcastReceiver.PendingResult>(
        ShadowBroadcastPendingResult::class.java,
        "create",
        ClassParameter.from(Int::class.javaPrimitiveType, 0),
        ClassParameter.from(String::class.java, null),
        ClassParameter.from(Bundle::class.java, null),
        ClassParameter.from(Boolean::class.javaPrimitiveType, false),
    )
    ReflectionHelpers.callInstanceMethod<Unit>(
        this,
        "setPendingResult",
        ClassParameter.from(BroadcastReceiver.PendingResult::class.java, pending),
    )
}

/** Вызывает `onReceive` и ждёт `PendingResult.finish()` (в т.ч. после `goAsync`). */
fun BroadcastReceiver.deliverAndAwaitFinish(context: Context, intent: Intent, timeoutSeconds: Long) {
    withPendingResult()
    val pending = ReflectionHelpers.getField<BroadcastReceiver.PendingResult>(this, "mPendingResult")
    onReceive(context, intent)
    Shadow.extract<ShadowBroadcastPendingResult>(pending).future.get(timeoutSeconds, TimeUnit.SECONDS)
}
