package com.example.espcamapp.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.espcamapp.data.model.Person
import com.example.espcamapp.data.model.PersonStatus
import com.example.espcamapp.ui.theme.DarkGrayCard
import com.example.espcamapp.ui.theme.OffWhite
import com.example.espcamapp.ui.theme.SlateGray
import com.example.espcamapp.views.components.AppTextField
import com.example.espcamapp.views.components.HeaderBlock
import com.example.espcamapp.views.components.HeaderDivider
import com.example.espcamapp.views.components.PillStatus
import com.example.espcamapp.views.components.PrimaryButton
import com.example.espcamapp.views.components.ScreenHorizontalPadding
import com.example.espcamapp.views.components.StatusPill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PeopleScreen(
    snackbarHostState: SnackbarHostState,
    onAddPerson: () -> Unit,
    viewModel: PeopleViewModel = viewModel(factory = PeopleViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingRemoval by remember { mutableStateOf<Person?>(null) }

    // O ViewModel vive no escopo da Activity e sobrevive à troca de abas, então
    // o refresh do `init` roda uma única vez. Sem isto a lista continuava vazia
    // depois de cadastrar alguém (o cadastro acontece em outro ViewModel).
    LaunchedEffect(Unit) { viewModel.refresh() }

    ShowMessageEffect(state.message, snackbarHostState, viewModel::consumeMessage)

    pendingRemoval?.let { person ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remover ${person.name}?") },
            text = { Text("As amostras faciais desta pessoa serão apagadas do servidor.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(person)
                    pendingRemoval = null
                }) { Text("Remover") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text("Cancelar") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = ScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Spacer(Modifier.height(16.dp))
                HeaderBlock(
                    title = "ESPCamApp - Pessoas",
                    subtitle = "Rostos cadastrados para reconhecimento"
                )
                HeaderDivider()
                Spacer(Modifier.height(16.dp))
                AppTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = "Buscar pessoa cadastrada...",
                    leadingIcon = Icons.Filled.Search
                )
                Spacer(Modifier.height(14.dp))
                PrimaryButton(text = "+ Cadastrar Nova Pessoa", onClick = onAddPerson)
                Spacer(Modifier.height(4.dp))
            }
        }

        state.error?.let { error ->
            item {
                Text(
                    "Não foi possível falar com o servidor de reconhecimento: $error\n" +
                        "Confira o endereço na aba Dispositivos.",
                    color = SlateGray,
                    fontSize = 13.sp
                )
            }
        }

        if (state.error == null && state.visiblePeople.isEmpty() && !state.loading) {
            item {
                Text(
                    if (state.people.isEmpty()) {
                        "Nenhuma pessoa cadastrada ainda."
                    } else {
                        "Nenhum resultado para \"${state.query}\"."
                    },
                    color = SlateGray,
                    fontSize = 13.sp
                )
            }
        }

        items(state.visiblePeople, key = { it.id }) { person ->
            PersonCard(
                person = person,
                onToggleAuthorized = { viewModel.setAuthorized(person, it) },
                onCaptureSample = { viewModel.addSampleFromCamera(person) },
                onRemove = { pendingRemoval = person }
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

@Composable
private fun PersonCard(
    person: Person,
    onToggleAuthorized: (Boolean) -> Unit,
    onCaptureSample: () -> Unit,
    onRemove: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkGrayCard)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF2A3550), Color(0xFF161E2E)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.25f),
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(person.name, color = OffWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(
                "${person.category} · cadastrado em ${person.formattedDate()}",
                color = SlateGray,
                fontSize = 12.sp
            )
            if (!person.authorized) {
                Text("Sem autorização de acesso", color = SlateGray, fontSize = 11.sp)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            StatusPill(
                text = person.status.label,
                status = if (person.status == PersonStatus.ATIVO) PillStatus.ACTIVE else PillStatus.PENDING
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (person.samples == 1) "1 amostra" else "${person.samples} amostras",
                color = SlateGray,
                fontSize = 12.sp
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Ações", tint = SlateGray)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = {
                        Text(if (person.authorized) "Revogar autorização" else "Autorizar acesso")
                    },
                    onClick = {
                        onToggleAuthorized(!person.authorized)
                        menuOpen = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Capturar amostra pelo ESP32-CAM") },
                    onClick = {
                        onCaptureSample()
                        menuOpen = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Remover") },
                    onClick = {
                        onRemove()
                        menuOpen = false
                    }
                )
            }
        }
    }
}

private fun Person.formattedDate(): String =
    if (createdAtEpochSeconds <= 0) "-"
    else dateFormat.format(Date((createdAtEpochSeconds * 1000).toLong()))
