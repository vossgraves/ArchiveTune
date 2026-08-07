/*
 * ArchiveTune (2026)
 * Stub GatekeeperViewModel: the proprietary gatekeeper has been removed.
 * blockedMessages never emits — the gate is always open.
 */
package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject

@HiltViewModel
class GatekeeperViewModel
    @Inject
    constructor() : ViewModel() {
    val blockedMessages: SharedFlow<String> = MutableSharedFlow()
}
