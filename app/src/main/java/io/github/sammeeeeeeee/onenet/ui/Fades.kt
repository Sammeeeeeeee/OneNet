package io.github.sammeeeeeeee.onenet.ui

import android.text.TextUtils
import android.widget.ImageView
import android.widget.TextView
import io.github.sammeeeeeeee.onenet.R

private const val FADE_OUT_MS = 90L
private const val FADE_IN_MS = 160L

/**
 * Cross-fades to [value]. Null keeps the old text, for a view about to be hidden to fade out
 * with; a hidden view changes at once.
 */
fun TextView.fadeTo(value: CharSequence?) {
    val wanted = value ?: ""
    if (TextUtils.equals(getTag(R.id.pending_text) as CharSequence? ?: text, wanted)) return
    setTag(R.id.pending_text, wanted)
    animate().cancel()
    when {
        value.isNullOrEmpty() -> alpha = 1f
        !isShown || text.isNullOrEmpty() -> {
            alpha = 1f
            text = value
        }
        else -> animate().alpha(0f).setDuration(FADE_OUT_MS).withEndAction {
            text = value
            animate().alpha(1f).setDuration(FADE_IN_MS)
        }
    }
}

/** Cross-fades to the drawable [resId]; a hidden view changes at once. */
fun ImageView.fadeTo(resId: Int) {
    val current = getTag(R.id.pending_image) as Int?
    if (current == resId) return
    setTag(R.id.pending_image, resId)
    animate().cancel()
    if (current == null || !isShown) {
        alpha = 1f
        setImageResource(resId)
        return
    }
    animate().alpha(0f).setDuration(FADE_OUT_MS).withEndAction {
        setImageResource(resId)
        animate().alpha(1f).setDuration(FADE_IN_MS)
    }
}
