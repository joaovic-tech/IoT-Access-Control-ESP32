package com.example.espcamapp.views

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import com.example.espcamapp.views.components.BottomNavBar
import com.example.espcamapp.views.components.Tab

/** Ponto de entrada da UI: controla a aba atual e a navegação para telas de detalhe. */
@Composable
fun AppRoot() {
    var currentTab by remember { mutableStateOf(Tab.PRINCIPAL) }
    var showRegisterPerson by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!showRegisterPerson) {
                BottomNavBar(current = currentTab, onSelect = { currentTab = it })
            }
        }
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val contentPadding = PaddingValues(
            start = innerPadding.calculateStartPadding(layoutDirection),
            end = innerPadding.calculateEndPadding(layoutDirection),
            top = innerPadding.calculateTopPadding(),
            bottom = innerPadding.calculateBottomPadding()
        )
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            when {
                showRegisterPerson -> RegisterPersonScreen(
                    snackbarHostState = snackbarHostState,
                    onBack = { showRegisterPerson = false }
                )

                currentTab == Tab.PRINCIPAL -> HomeScreen(snackbarHostState)
                currentTab == Tab.DISPOSITIVOS -> DevicesScreen(snackbarHostState)
                currentTab == Tab.PESSOAS -> PeopleScreen(
                    snackbarHostState = snackbarHostState,
                    onAddPerson = { showRegisterPerson = true }
                )
            }
        }
    }
}

/**
 * Mostra [message] no snackbar e avisa o `ViewModel` para limpá-la, evitando
 * que a mesma mensagem reapareça a cada recomposição.
 */
@Composable
fun ShowMessageEffect(
    message: String?,
    snackbarHostState: SnackbarHostState,
    onConsumed: () -> Unit
) {
    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            onConsumed()
        }
    }
}
