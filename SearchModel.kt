package com.egci428.u5781070.guesswhere

import ir.mirrajabi.searchdialog.core.Searchable

class KeySelectModel (private val key: String?): Searchable {
    override fun getTitle(): String {
        return key!!
    }
}
