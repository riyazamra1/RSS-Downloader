package com.riyaz.rssdownloader

import android.graphics.drawable.Drawable
import android.widget.EditText

/** Compatibility helpers for the lightweight native Android UI. */
private operator fun Drawable.invoke(): Drawable = this

private var EditText.hintTextColor: Int
    get() = currentHintTextColor
    set(value) = setHintTextColor(value)
