package com.ivarna.fluxlinux.ui.terminal

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager

fun hideIme(view: View) {
    val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.hideSoftInputFromWindow(view.windowToken, 0)
    view.clearFocus()
}
