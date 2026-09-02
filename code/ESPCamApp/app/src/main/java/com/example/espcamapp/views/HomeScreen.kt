package com.example.espcamapp.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.espcamapp.data.model.Detection
import com.example.espcamapp.data.model.DetectionStatus
import com.example.espcamapp.stream.MjpegPlayer
import com.example.espcamapp.stream.StreamState
import com.example.espcamapp.ui.theme.DarkGrayCard
import com.example.espcamapp.ui.theme.DarkGrayInput
import com.example.espcamapp.ui.theme.OffWhite
import com.example.espcamapp.ui.theme.SlateGray
import com.example.espcamapp.ui.theme.StatusAmberPending
import com.example.espcamapp.ui.theme.StatusGreenOnline
import com.example.espcamapp.ui.theme.StatusRedLive
import com.example.espcamapp.views.components.HeaderDivider
import com.example.espcamapp.views.components.PrimaryButton
import com.example.espcamapp.views.components.ScreenHorizontalPadding
import com.example.espcamapp.views.components.SectionTitle

@Composable
fun HomeScreen(
    snackbarHostState: SnackbarHostState,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var streamState by remember { mutableStateOf<StreamState>(StreamState.Idle) }
    var deviceMenuOpen by remember { mutableStateOf(false) }

    ShowMessageEffect(state.message, snackbarHostState, viewModel::consumeMessage)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = ScreenHorizontalPadding)
            .padding(top = 12.dp, bottom = 16.dp)
    ) {
        Text(
            "ESPCamApp - aplicativo de monitoramento",
            color = OffWhite,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(14.dp))

        // Seletor de câmera: dispositivos salvos no SettingsStore.
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkGrayInput)
                    .clickable(enabled = state.hasDevices) { deviceMenuOpen = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    state.selectedDevice?.name ?: "Nenhum ESP32-CAM cadastrado",
                    color = if (state.hasDevices) OffWhite else SlateGray,
                    fontSize = 15.sp
                )
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = SlateGray)
            }
            DropdownMenu(expanded = deviceMenuOpen, onDismissRequest = { deviceMenuOpen = false }) {
                state.devices.forEach { device ->
                    DropdownMenuItem(
                        text = { Text("${device.name} — ${device.host}") },
                        onClick = {
                            viewModel.selectDevice(device)
                            deviceMenuOpen = false
                        }
                    )
                }
            }
        }

        HeaderDivider()
        Spacer(Modifier.height(18.dp))

        // Vídeo ao vivo direto do ESP32-CAM (MJPEG na porta 81).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF1B2436), Color(0xFF0B1120))))
        ) {
            MjpegPlayer(
                streamUrl = state.selectedDevice?.streamUrl,
                modifier = Modifier.fillMaxSize(),
                rotationDegrees = state.selectedDevice?.rotationDegrees ?: 0,
                onStateChange = { streamState = it },
                placeholder = {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.size(120.dp).align(Alignment.Center)
                    )
                }
            )

            StreamBadge(streamState, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))

            state.selectedDevice?.let { device ->
                Text(
                    "${device.name} - ${device.host}:${device.streamPort}",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                )
            }
        }

        if (streamState is StreamState.Offline) {
            Spacer(Modifier.height(8.dp))
            Text(
                (streamState as StreamState.Offline).message + " — tentando reconectar...",
                color = StatusAmberPending,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(14.dp))

        PrimaryButton(
            text = if (state.scanning) "Escaneando..." else "Escanear ambiente",
            onClick = { if (!state.scanning) viewModel.scanEnvironment() }
        )

        Spacer(Modifier.height(22.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SectionTitle("Rostos detectados recentes:")
            if (state.loadingDetections) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = SlateGray
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        when {
            // Mostrar o endereço tentado é o que revela um IP desatualizado:
            // sem ele o usuário só vê "não foi possível conectar".
            state.detectionsError != null -> InfoBox(
                "Sem contato com o servidor de reconhecimento em " +
                    "${state.faceServerUrl}\n${state.detectionsError}\n\n" +
                    "Confira o endereço na aba Dispositivos — ele precisa ser o IP " +
                    "do PC que roda o servidor Python (veja com ipconfig)."
            )

            state.detections.isEmpty() -> InfoBox(
                "Nenhuma detecção ainda. Use \"Escanear ambiente\" ou passe na frente do sensor."
            )

            else -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkGrayCard.copy(alpha = 0.5f))
                    .border(1.dp, SlateGray.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.detections.take(3).forEach { detection ->
                    FaceThumb(detection = detection, modifier = Modifier.weight(1f))
                }
                // Mantém o alinhamento das colunas quando há menos de 3 detecções.
                repeat(3 - state.detections.take(3).size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StreamBadge(state: StreamState, modifier: Modifier = Modifier) {
    val (label, color) = when (state) {
        StreamState.Live -> "Ao vivo" to StatusRedLive
        StreamState.Connecting -> "Conectando" to StatusAmberPending
        StreamState.Idle -> "Sem câmera" to SlateGray
        is StreamState.Offline -> "Sem sinal" to SlateGray
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
    }
}

@Composable
private fun InfoBox(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkGrayCard.copy(alpha = 0.5f))
            .border(1.dp, SlateGray.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Text(text, color = SlateGray, fontSize = 13.sp)
    }
}

/**
 * Miniatura de uma detecção (design `design-app-mobile/carrosel-rostos.png`).
 *
 * A moldura e a faixa codificam o veredito sem precisar ler o texto: verde
 * "Autorizado" quando a pessoa é reconhecida, autorizada **e** está num cômodo
 * liberado; vermelha "Não autorizado" / "Sem cadastro"; âmbar "Não identificado"
 * quando há alguém no quadro mas o rosto não pôde ser extraído (G2); e cinza
 * quando não havia ninguém.
 */
@Composable
private fun FaceThumb(detection: Detection, modifier: Modifier = Modifier) {
    val accent = when {
        detection.status == DetectionStatus.NO_FACE -> SlateGray
        // Presença sem identificação: é um alerta, mas não uma pessoa negada.
        detection.status == DetectionStatus.PERSON_NO_FACE -> StatusAmberPending
        detection.allowedHere -> StatusGreenOnline
        else -> StatusRedLive
    }
    val shape = RoundedCornerShape(10.dp)

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(Brush.linearGradient(listOf(Color(0xFF243049), Color(0xFF141C2C))))
                .border(2.dp, accent, shape),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = detection.imageUrl,
                contentDescription = detection.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Faixa com o veredito sobre a foto — a mesma cor da moldura.
            Text(
                detection.accessLabel,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(accent.copy(alpha = 0.85f))
                    .padding(vertical = 3.dp, horizontal = 4.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            detection.displayName,
            color = accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // Só aparece quando a regra de cômodo é que barrou: o nome sozinho não
        // explicaria por que uma pessoa autorizada aparece como não autorizada.
        detection.wrongRoomWarning?.let { warning ->
            Text(
                warning,
                color = StatusRedLive,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
        Text(detection.timeLabel, color = SlateGray, fontSize = 10.sp)
    }
}
