package com.example.espcamapp.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.espcamapp.data.model.Room
import com.example.espcamapp.ui.theme.DarkGrayCard
import com.example.espcamapp.ui.theme.OffWhite
import com.example.espcamapp.ui.theme.PurplePrimary
import com.example.espcamapp.ui.theme.SlateGray
import com.example.espcamapp.views.components.AppTextField
import com.example.espcamapp.views.components.HeaderBlock
import com.example.espcamapp.views.components.HeaderDivider
import com.example.espcamapp.views.components.PrimaryButton
import com.example.espcamapp.views.components.ScreenHorizontalPadding
import com.example.espcamapp.views.components.SectionTitle

/**
 * Cômodos da casa.
 *
 * É aqui que o administrador declara os lugares que o sistema conhece; quem
 * entra em cada um é marcado na ficha de cada pessoa. A decisão no servidor lê
 * exatamente essa configuração — nada é inferido de texto livre.
 */
@Composable
fun AccessScreen(
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    viewModel: AccessViewModel = viewModel(factory = AccessViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var novoComodo by remember { mutableStateOf("") }
    var comodoEmEdicao by remember { mutableStateOf<Room?>(null) }
    var comodoParaRemover by remember { mutableStateOf<Room?>(null) }

    ShowMessageEffect(state.message, snackbarHostState, viewModel::consumeMessage)

    comodoEmEdicao?.let { room ->
        RenameRoomDialog(
            room = room,
            onDismiss = { comodoEmEdicao = null },
            onConfirm = { nome ->
                viewModel.renameRoom(room, nome)
                comodoEmEdicao = null
            }
        )
    }

    comodoParaRemover?.let { room ->
        ConfirmDialog(
            titulo = "Remover ${room.name}?",
            texto = "O cômodo sai do cadastro de todas as pessoas que o tinham liberado.",
            onDismiss = { comodoParaRemover = null },
            onConfirm = {
                viewModel.deleteRoom(room)
                comodoParaRemover = null
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = ScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = OffWhite)
                    }
                    Spacer(Modifier.width(4.dp))
                    HeaderBlock(
                        title = "Cômodos da casa",
                        subtitle = "Os lugares que o controle de acesso conhece"
                    )
                }
                HeaderDivider()
            }
        }

        state.error?.let { erro ->
            item {
                Text(
                    "Não foi possível falar com o servidor: $erro",
                    color = SlateGray,
                    fontSize = 13.sp
                )
            }
        }

        // ---------------- Cômodos ----------------
        item {
            Column {
                Spacer(Modifier.height(6.dp))
                SectionTitle("Cômodos")
                Spacer(Modifier.height(8.dp))
                AppTextField(novoComodo, { novoComodo = it }, "Ex: Quarto do João")
                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    text = "+ Adicionar cômodo",
                    enabled = novoComodo.isNotBlank() && !state.loading,
                    onClick = {
                        viewModel.createRoom(novoComodo)
                        novoComodo = ""
                    }
                )
            }
        }

        if (state.rooms.isEmpty()) {
            item {
                Text(
                    "Nenhum cômodo cadastrado. Sem cômodos, o acesso é decidido " +
                        "apenas pela autorização geral de cada pessoa.",
                    color = SlateGray,
                    fontSize = 12.sp
                )
            }
        }

        items(state.rooms, key = { "room-${it.id}" }) { room ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkGrayCard)
                    .padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(room.name, color = OffWhite, fontSize = 14.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { comodoEmEdicao = room }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Renomear", tint = SlateGray)
                }
                IconButton(onClick = { comodoParaRemover = room }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remover", tint = SlateGray)
                }
            }
        }

        // ---------------- Assistente (G3) ----------------
        item {
            Column {
                Spacer(Modifier.height(18.dp))
                SectionTitle("Assistente")
                Spacer(Modifier.height(4.dp))
                Text(
                    "Pergunte sobre o histórico ou peça uma mudança. O assistente " +
                        "nunca libera acesso sozinho: mudanças passam pela sua confirmação.",
                    color = SlateGray,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                AppTextField(
                    state.question,
                    viewModel::setQuestion,
                    "Ex: quem entrou na cozinha ontem?"
                )
                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    text = if (state.asking) "Consultando..." else "Perguntar",
                    enabled = state.question.isNotBlank() && !state.asking,
                    onClick = viewModel::ask
                )

                state.answer?.let { resposta ->
                    Spacer(Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkGrayCard)
                            .padding(14.dp)
                    ) {
                        Text(resposta, color = OffWhite, fontSize = 13.sp)

                        // Escrita só acontece com o OK explícito do administrador.
                        if (state.pendingProposal != null) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Confirma esta alteração?",
                                color = SlateGray,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = viewModel::confirmProposal) {
                                    Text("Confirmar")
                                }
                                TextButton(onClick = viewModel::discardProposal) {
                                    Text("Cancelar")
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun RenameRoomDialog(room: Room, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var nome by remember(room.id) { mutableStateOf(room.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renomear cômodo") },
        text = { AppTextField(nome, { nome = it }, "Nome do cômodo") },
        confirmButton = {
            TextButton(enabled = nome.isNotBlank(), onClick = { onConfirm(nome) }) {
                Text("Salvar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun ConfirmDialog(
    titulo: String,
    texto: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titulo) },
        text = { Text(texto) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Remover") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
