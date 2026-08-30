package com.example.espcamapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.espcamapp.ui.theme.ESPCamAppTheme
import com.example.espcamapp.views.AppRoot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Navegação básica entre as abas e a tela de cadastro.
 *
 * Não depende de rede: sem dispositivos salvos nem servidor acessível, as telas
 * mostram os estados vazios/de erro, e é isso que o teste percorre.
 */
@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun launchApp() {
        composeRule.setContent {
            ESPCamAppTheme { AppRoot() }
        }
    }

    @Test
    fun abreNaTelaPrincipal() {
        launchApp()
        composeRule.onNodeWithText("ESPCamApp - aplicativo de monitoramento").assertIsDisplayed()
        composeRule.onNodeWithText("Escanear ambiente").assertIsDisplayed()
    }

    @Test
    fun navegaEntreAsAbas() {
        launchApp()

        composeRule.onNodeWithText("Dispositivos").performClick()
        composeRule.onNodeWithText("Adicionar Novo ESP32-CAM").assertIsDisplayed()

        composeRule.onNodeWithText("Pessoas").performClick()
        composeRule.onNodeWithText("+ Cadastrar Nova Pessoa").assertIsDisplayed()

        composeRule.onNodeWithText("Principal").performClick()
        composeRule.onNodeWithText("Rostos detectados recentes:").assertIsDisplayed()
    }

    @Test
    fun abreEFechaOCadastroDePessoa() {
        launchApp()

        composeRule.onNodeWithText("Pessoas").performClick()
        composeRule.onNodeWithText("+ Cadastrar Nova Pessoa").performClick()
        composeRule.onNodeWithText("Fotos para Treinamento Facial").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Voltar").performClick()
        composeRule.onNodeWithText("+ Cadastrar Nova Pessoa").assertIsDisplayed()
    }
}
