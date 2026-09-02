package com.example.espcamapp.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.espcamapp.data.model.Device
import com.example.espcamapp.data.model.Room
import com.example.espcamapp.ui.theme.DarkGrayCard
import com.example.espcamapp.ui.theme.DarkGrayInput
import com.example.espcamapp.ui.theme.OffWhite
import com.example.espcamapp.ui.theme.PurplePrimary
import com.example.espcamapp.ui.theme.SlateGray
import com.example.espcamapp.ui.theme.StatusAmberPending
import com.example.espcamapp.views.components.AppTextField
import com.example.espcamapp.views.components.HeaderBlock
import com.example.espcamapp.views.components.HeaderDivider
import com.example.espcamapp.views.components.OutlinePurpleButton
import com.example.espcamapp.views.components.PillStatus
import com.example.espcamapp.views.components.PrimaryButton
import com.example.espcamapp.views.components.ScreenHorizontalPadding
import com.example.espcamapp.views.components.SelectableChips
import com.example.espcamapp.views.components.SectionTitle
import com.example.espcamapp.views.components.StatusPill

@Composable
fun DevicesScreen(
    snackbarHostState: SnackbarHostState,
    viewModel: DevicesViewModel = viewModel(factory = DevicesViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var nome by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("") }
    var porta by remember { mutableStateOf("") }
    var portaStream by remember { mutableStateOf("") }
    var comodo by remember { mutableStateOf("") }
    var serverUrl by remember(state.faceServerUrl) { mutableStateOf(state.faceServerUrl) }
    var unlockTarget by remember { mutableStateOf<Device?>(null) }

    // Os cômodos são criados em Pessoas -> Cômodos da casa, por outro ViewModel.
    // Este sobrevive à troca de abas, então sem reler ao abrir a tela a lista
    // ficava congelada no que existia quando o app subiu.
    LaunchedEffect(Unit) { viewModel.loadRooms() }

    ShowMessageEffect(state.message, snackbarHostState, viewModel::consumeMessage)

    state.editing?.let { device ->
        EditDeviceDialog(
            device = device,
            rooms = state.rooms,
            roomsError = state.roomsError,
            saving = state.saving,
            onDismiss = viewModel::cancelEditing,
            onConfirm = { name, host, port, streamPort, rotation, room ->
                viewModel.saveEditing(name, host, port, streamPort, rotation, room)
            }
        )
    }

    // Liberar a trava é irreversível do lado físico: confirma antes de enviar.
    unlockTarget?.let { device ->
        AlertDialog(
            onDismissRequest = { unlockTarget = null },
            title = { Text("Liberar acesso") },
            text = { Text("Acionar o relé de ${device.name} por 3 segundos?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.unlock(device)
                    unlockTarget = null
                }) { Text("Liberar") }
            },
            dismissButton = {
                TextButton(onClick = { unlockTarget = null }) { Text("Cancelar") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = ScreenHorizontalPadding)
            .padding(top = 16.dp, bottom = 16.dp)
    ) {
        HeaderBlock(
            title = "ESPCamApp - Dispositivos",
            subtitle = "Gerencie e configure seus módulos ESP32-CAM"
        )
        HeaderDivider()
        Spacer(Modifier.height(18.dp))

        if (state.devices.isEmpty()) {
            Text(
                "Nenhum ESP32-CAM cadastrado ainda. Preencha o formulário abaixo com o IP " +
                    "mostrado no monitor serial da placa.",
                color = SlateGray,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(18.dp))
        }

        state.devices.forEachIndexed { index, device ->
            val card = state.cards[device.id]
            DeviceCard(
                device = device,
                online = card?.online == true,
                accent = index == 0,
                flashOn = card?.status?.flashOn == true,
                busy = card?.busy == true,
                detail = card?.status?.let { status ->
                    "FW ${status.firmware} · ${status.rssi} dBm · %.1f fps · %d cliente(s)"
                        .format(status.fps, status.streamClients)
                },
                onFlashChange = { viewModel.setFlash(device, it) },
                onRotationChange = { viewModel.setRotation(device, it) },
                onUnlock = { unlockTarget = device },
                onEdit = { viewModel.startEditing(device) },
                onRemove = { viewModel.removeDevice(device) }
            )
            Spacer(Modifier.height(14.dp))
        }

        Spacer(Modifier.height(8.dp))

        SectionTitle("Adicionar Novo ESP32-CAM")
        Spacer(Modifier.height(12.dp))

        AppTextField(nome, { nome = it }, "Nome do Dispositivo (Ex: ESP32 da Sala)")
        Spacer(Modifier.height(12.dp))
        AppTextField(ip, { ip = it }, "Endereço IP na Rede (Ex: 192.168.15.72)")
        Spacer(Modifier.height(12.dp))
        AppTextField(
            porta,
            { porta = it },
            "Porta da API (padrão 80)",
            keyboardType = KeyboardType.Number
        )
        Spacer(Modifier.height(12.dp))
        AppTextField(
            portaStream,
            { portaStream = it },
            "Porta do stream MJPEG (padrão 81)",
            keyboardType = KeyboardType.Number
        )

        // Cômodo já no cadastro: antes só a edição oferecia a escolha, e um
        // dispositivo novo nascia sem lugar até o usuário reabrir o formulário.
        Spacer(Modifier.height(16.dp))
        RoomSection(
            rooms = state.rooms,
            selected = comodo,
            roomsError = state.roomsError,
            onSelect = { comodo = it }
        )

        Spacer(Modifier.height(16.dp))

        PrimaryButton(
            text = if (state.saving) "Testando conexão..." else "Salvar e Conectar Dispositivo",
            onClick = {
                if (!state.saving) {
                    viewModel.addDevice(nome, ip, porta, portaStream, comodo)
                    nome = ""; ip = ""; porta = ""; portaStream = ""; comodo = ""
                }
            }
        )

        Spacer(Modifier.height(26.dp))

        SectionTitle("Servidor de Reconhecimento Facial")
        Spacer(Modifier.height(4.dp))
        Text(
            "Endereço do servidor Python na LAN — usado pelas telas Principal e Pessoas.",
            color = SlateGray,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        AppTextField(serverUrl, { serverUrl = it }, "Ex: http://192.168.15.10:8000")
        Spacer(Modifier.height(12.dp))
        OutlinePurpleButton(
            text = if (state.checkingServer) "Testando servidor..." else "Salvar e testar endereço",
            onClick = { if (!state.checkingServer) viewModel.setFaceServerUrl(serverUrl) }
        )
    }
}

/** Formulário de edição de um ESP32-CAM já cadastrado. */
@Composable
private fun EditDeviceDialog(
    device: Device,
    rooms: List<Room>,
    roomsError: String?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        host: String,
        port: String,
        streamPort: String,
        rotation: Int,
        room: String
    ) -> Unit
) {
    var nome by remember(device.id) { mutableStateOf(device.name) }
    var ip by remember(device.id) { mutableStateOf(device.host) }
    var porta by remember(device.id) { mutableStateOf(device.port.toString()) }
    var portaStream by remember(device.id) { mutableStateOf(device.streamPort.toString()) }
    var rotacao by remember(device.id) { mutableIntStateOf(device.rotationDegrees) }
    var comodo by remember(device.id) { mutableStateOf(device.room) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Editar dispositivo") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                AppTextField(nome, { nome = it }, "Nome do Dispositivo")
                Spacer(Modifier.height(10.dp))
                AppTextField(ip, { ip = it }, "Endereço IP na Rede")
                Spacer(Modifier.height(10.dp))
                AppTextField(porta, { porta = it }, "Porta da API", keyboardType = KeyboardType.Number)
                Spacer(Modifier.height(10.dp))
                AppTextField(
                    portaStream,
                    { portaStream = it },
                    "Porta do stream MJPEG",
                    keyboardType = KeyboardType.Number
                )

                Spacer(Modifier.height(16.dp))
                RoomSection(
                    rooms = rooms,
                    selected = comodo,
                    roomsError = roomsError,
                    onSelect = { comodo = it }
                )

                Spacer(Modifier.height(16.dp))
                SectionTitle("Orientação da imagem")
                Spacer(Modifier.height(4.dp))
                Text(
                    "Gira só a exibição no app — o ESP32 continua enviando o quadro original.",
                    color = SlateGray,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                RotationPicker(selected = rotacao, onSelect = { rotacao = it })
                Spacer(Modifier.height(6.dp))
                Text(
                    Device.rotationLabel(rotacao),
                    color = PurplePrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    "A conexão só é testada se o endereço mudar.",
                    color = SlateGray,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = { onConfirm(nome, ip, porta, portaStream, rotacao, comodo) }
            ) { Text(if (saving) "Salvando..." else "Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") }
        }
    )
}

/** Rótulo do chip que desmarca o cômodo — não é o nome de nenhum cômodo. */
private const val SEM_COMODO = "Sem cômodo"

/**
 * Escolha do cômodo vigiado, usada tanto no cadastro quanto na edição.
 *
 * As opções vêm do servidor (Pessoas -> Cômodos da casa) e são relidas ao abrir
 * a tela e ao abrir a edição, então um cômodo recém-criado já aparece aqui.
 */
@Composable
private fun RoomSection(
    rooms: List<Room>,
    selected: String,
    roomsError: String?,
    onSelect: (String) -> Unit
) {
    Column {
        SectionTitle("Cômodo vigiado")
        Spacer(Modifier.height(4.dp))
        Text(
            "O servidor usa este cômodo para conferir se a pessoa reconhecida " +
                "pode estar aqui, segundo os cômodos liberados no cadastro dela.",
            color = SlateGray,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))

        if (rooms.isEmpty()) {
            Text(
                if (roomsError != null) {
                    "Não foi possível ler os cômodos do servidor ($roomsError). " +
                        "Confira o endereço logo abaixo — enquanto isso, o nome " +
                        "digitado aqui é casado pelo texto."
                } else {
                    "Nenhum cômodo cadastrado ainda — crie em Pessoas -> Cômodos " +
                        "da casa. Enquanto isso, o nome digitado aqui é casado " +
                        "pelo texto."
                },
                color = if (roomsError != null) StatusAmberPending else SlateGray,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            AppTextField(selected, onSelect, "Ex: Quarto do João")
            return@Column
        }

        SelectableChips(
            // "Sem cômodo" primeiro: sem ele não havia como desmarcar uma
            // escolha depois de feita.
            options = listOf(SEM_COMODO) + rooms.map { it.name },
            selected = selected.ifBlank { SEM_COMODO },
            onSelect = { onSelect(if (it == SEM_COMODO) "" else it) }
        )

        // O cômodo é gravado pelo nome: se ele foi renomeado ou removido no
        // servidor, nenhum chip fica marcado e o motivo precisa ficar visível.
        if (selected.isNotBlank() && rooms.none { it.name == selected }) {
            Spacer(Modifier.height(6.dp))
            Text(
                "O cômodo \"$selected\" não está mais na lista — escolha um acima.",
                color = StatusAmberPending,
                fontSize = 11.sp
            )
        }
    }
}

/** Seletor de giro (0/90/180/270) usado no formulário e no card. */
@Composable
private fun RotationPicker(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Device.ROTATION_OPTIONS.forEach { degrees ->
            val isSelected = degrees == Device.normalizeRotation(selected)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) PurplePrimary else DarkGrayInput)
                    .clickable { onSelect(degrees) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$degrees°",
                    color = if (isSelected) Color.White else SlateGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: Device,
    online: Boolean,
    accent: Boolean,
    flashOn: Boolean,
    busy: Boolean,
    detail: String?,
    onFlashChange: (Boolean) -> Unit,
    onRotationChange: (Int) -> Unit,
    onUnlock: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(14.dp))
            .background(DarkGrayCard)
    ) {
        if (accent) {
            Box(Modifier.fillMaxHeight().width(4.dp).background(PurplePrimary))
        }
        Column(modifier = Modifier.weight(1f).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (accent) PurplePrimary.copy(alpha = 0.15f)
                            else SlateGray.copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.CameraAlt,
                        contentDescription = null,
                        tint = if (accent) PurplePrimary else SlateGray
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(device.name, color = OffWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(device.addressLabel, color = SlateGray, fontSize = 13.sp)
                }
                StatusPill(
                    text = if (online) "Online" else "Offline",
                    status = if (online) PillStatus.ONLINE else PillStatus.PENDING
                )
            }

            if (detail != null) {
                Spacer(Modifier.height(8.dp))
                Text(detail, color = SlateGray, fontSize = 12.sp)
            }

            if (device.room.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("Cômodo: ${device.room}", color = SlateGray, fontSize = 12.sp)
            }

            HorizontalDivider(
                color = SlateGray.copy(alpha = 0.15f),
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // Rótulo acima dos chips: em telas estreitas os quatro ângulos não
            // cabem na mesma linha que o texto.
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Orientação da imagem", color = OffWhite, fontSize = 14.sp)
                Text(
                    Device.rotationLabel(device.rotationDegrees),
                    color = SlateGray,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                RotationPicker(selected = device.rotationDegrees, onSelect = onRotationChange)
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Flash LED auxiliar", color = OffWhite, fontSize = 14.sp)
                Switch(
                    checked = flashOn,
                    enabled = online && !busy,
                    onCheckedChange = onFlashChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = PurplePrimary,
                        checkedBorderColor = PurplePrimary,
                        uncheckedThumbColor = SlateGray,
                        uncheckedTrackColor = DarkGrayCard,
                        uncheckedBorderColor = SlateGray
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onUnlock, enabled = online && !busy) {
                    Icon(
                        Icons.Outlined.LockOpen,
                        contentDescription = null,
                        tint = PurplePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Liberar acesso", color = PurplePrimary, fontSize = 13.sp)
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "Editar dispositivo",
                            tint = SlateGray
                        )
                    }
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Remover dispositivo",
                            tint = SlateGray
                        )
                    }
                }
            }
        }
    }
}
