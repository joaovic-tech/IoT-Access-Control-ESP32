package com.example.espcamapp.views

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.espcamapp.data.repo.SampleImage
import com.example.espcamapp.ui.theme.DarkGrayInput
import com.example.espcamapp.ui.theme.DarkSlateBg
import com.example.espcamapp.ui.theme.OffWhite
import com.example.espcamapp.ui.theme.PurplePrimary
import com.example.espcamapp.ui.theme.SlateGray
import com.example.espcamapp.ui.theme.StatusAmberPending
import com.example.espcamapp.ui.theme.StatusRedLive
import com.example.espcamapp.views.components.AppTextField
import com.example.espcamapp.views.components.OutlinePurpleButton
import com.example.espcamapp.views.components.PrimaryButton
import com.example.espcamapp.views.components.ScreenHorizontalPadding
import com.example.espcamapp.views.components.SectionTitle
import com.example.espcamapp.views.components.SelectableChips
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Quantos slots a grade mostra por linha (design `cadastrar-nova-pessoa.png`). */
private const val SLOTS_PER_ROW = 4

/**
 * Cadastro e edição de pessoa.
 *
 * `personId` nulo abre o formulário em branco; preenchido carrega o cadastro
 * para edição. Os campos são os do design: amostras, nome, categoria/vínculo e
 * os cômodos que a pessoa pode acessar.
 */
@Composable
fun RegisterPersonScreen(
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    personId: String? = null,
    viewModel: RegisterPersonViewModel = viewModel(factory = RegisterPersonViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(personId) { viewModel.open(personId) }

    ShowMessageEffect(state.message, snackbarHostState, viewModel::consumeMessage)

    // Volta para a lista assim que o servidor confirma o cadastro/edição.
    LaunchedEffect(state.savedPersonName) {
        if (state.savedPersonName != null) {
            viewModel.reset()
            onBack()
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(RegisterPersonUiState.MAX_SAMPLES)
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        // Leitura fora da main thread: as fotos da galeria podem ser grandes.
        scope.launch {
            val images = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            SampleImage(uri.lastPathSegment ?: "amostra.jpg", input.readBytes())
                        }
                    }.getOrNull()
                }
            }
            viewModel.addSamples(images)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(DarkSlateBg)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = ScreenHorizontalPadding)
                .padding(top = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(DarkGrayInput)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = OffWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        if (state.editing) "Editar Pessoa" else "Cadastrar Pessoa",
                        color = OffWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Insira os dados e amostras para o reconhecimento facial",
                        color = SlateGray,
                        fontSize = 12.sp
                    )
                }
            }

            HorizontalDivider(
                color = SlateGray.copy(alpha = 0.15f),
                modifier = Modifier.padding(top = 14.dp)
            )
            Spacer(Modifier.height(16.dp))

            SectionTitle("Fotos para Treinamento Facial")
            Spacer(Modifier.height(4.dp))
            Text(state.sampleHint, color = SlateGray, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))

            SampleGrid(
                state = state,
                onAdd = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onRemove = viewModel::removeSample
            )

            Spacer(Modifier.height(14.dp))
            OutlinePurpleButton(
                text = if (state.capturing) "Capturando..." else "Capturar direto pelo ESP32-CAM",
                onClick = { if (!state.capturing) viewModel.captureFromDevice() },
                leadingIcon = Icons.Outlined.PhotoCamera
            )
            if (state.selectedDevice == null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Nenhum ESP32-CAM selecionado — cadastre um na aba Dispositivos.",
                    color = StatusAmberPending,
                    fontSize = 11.sp
                )
            }

            if (state.warnings.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                state.warnings.forEach { warning ->
                    Text("• $warning", color = StatusAmberPending, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle("Nome Completo")
            Spacer(Modifier.height(8.dp))
            AppTextField(state.name, viewModel::setName, "Ex: João Victor")

            Spacer(Modifier.height(16.dp))
            SectionTitle("Categoria / Vínculo")
            Spacer(Modifier.height(10.dp))
            SelectableChips(
                options = state.categories,
                selected = state.category,
                onSelect = viewModel::setCategory
            )

            Spacer(Modifier.height(16.dp))
            SectionTitle("Cômodos")
            Spacer(Modifier.height(14.dp))
            if (state.rooms.isEmpty()) {
                Text(
                    "Nenhum cômodo cadastrado — crie os cômodos da casa em " +
                        "Pessoas → Cômodos da casa.",
                    color = SlateGray,
                    fontSize = 12.sp
                )
            } else {
                state.rooms.forEach { room ->
                    RoomCheckRow(
                        name = room.name,
                        checked = room.id in state.selectedRoomIds,
                        onToggle = { viewModel.toggleRoom(room.id) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        HorizontalDivider(color = SlateGray.copy(alpha = 0.15f))
        Column(
            modifier = Modifier
                .background(DarkSlateBg)
                .navigationBarsPadding()
                .padding(horizontal = ScreenHorizontalPadding, vertical = 12.dp)
        ) {
            PrimaryButton(
                text = when {
                    state.saving -> "Enviando amostras..."
                    state.editing -> "Salvar Alterações"
                    else -> "Salvar e Iniciar Treinamento"
                },
                onClick = { viewModel.save() }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "O modelo será retreinado automaticamente",
                color = SlateGray,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Grade de amostras: as já cadastradas primeiro, depois as escolhidas agora, o
 * slot tracejado para adicionar e os vazios completando a linha.
 */
@Composable
private fun SampleGrid(
    state: RegisterPersonUiState,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit
) {
    // Uma linha de 4 como no design; a 5ª amostra abre uma segunda linha só
    // quando as quatro primeiras já estão preenchidas.
    val visiveis = (state.totalSamples + 1)
        .coerceIn(SLOTS_PER_ROW, RegisterPersonUiState.MAX_SAMPLES)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        (0 until visiveis).chunked(SLOTS_PER_ROW).forEach { linha ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                linha.forEach { index ->
                    val existentes = state.existingSampleUrls.size
                    when {
                        index < existentes -> RemoteSampleSlot(
                            url = state.existingSampleUrls[index],
                            modifier = Modifier.weight(1f)
                        )

                        index < state.totalSamples -> FilledSampleSlot(
                            sample = state.samples[index - existentes],
                            onRemove = { onRemove(index - existentes) },
                            modifier = Modifier.weight(1f)
                        )

                        index == state.totalSamples -> AddSampleSlot(
                            onClick = onAdd,
                            modifier = Modifier.weight(1f)
                        )

                        else -> EmptySampleSlot(modifier = Modifier.weight(1f))
                    }
                }
                // Mantém a largura dos slots igual quando a linha não enche.
                repeat(SLOTS_PER_ROW - linha.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Linha de cômodo com a caixa de marcação do design. */
@Composable
private fun RoomCheckRow(name: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (checked) PurplePrimary else Color.Transparent)
                .border(
                    width = 2.dp,
                    color = if (checked) PurplePrimary else OffWhite,
                    shape = RoundedCornerShape(6.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(name, color = OffWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** Amostra que já está no servidor (edição). Não tem botão de remover: o
 * servidor não expõe rota para apagar uma amostra isolada. */
@Composable
private fun RemoteSampleSlot(url: String, modifier: Modifier = Modifier) {
    SampleSlot(modifier = modifier) {
        AsyncImage(
            model = url,
            contentDescription = "Amostra cadastrada",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun FilledSampleSlot(
    sample: SampleImage,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Miniatura reduzida: as fotos da galeria são grandes demais para o slot.
    val bitmap = remember(sample) {
        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
        BitmapFactory.decodeByteArray(sample.bytes, 0, sample.bytes.size, options)
    }
    SampleSlot(modifier = modifier) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = sample.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        // Selo vermelho de remover, como no design.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(StatusRedLive)
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remover amostra",
                tint = Color.White,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun AddSampleSlot(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(0.92f)
            .clip(RoundedCornerShape(12.dp))
            .background(PurplePrimary.copy(alpha = 0.12f))
            .dashedBorder(1.dp, PurplePrimary, 12.dp)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = PurplePrimary)
            Spacer(Modifier.height(4.dp))
            Text("+ Amostra", color = PurplePrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun EmptySampleSlot(modifier: Modifier = Modifier) {
    SampleSlot(modifier = modifier) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = SlateGray,
            modifier = Modifier.size(28.dp).align(Alignment.Center)
        )
    }
}

@Composable
private fun SampleSlot(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .aspectRatio(0.92f)
            .clip(RoundedCornerShape(12.dp))
            .background(DarkGrayInput),
        content = content
    )
}

/** Borda tracejada desenhada manualmente (Compose não tem API nativa). */
private fun Modifier.dashedBorder(width: Dp, color: Color, cornerRadius: Dp): Modifier =
    this.drawBehind {
        val stroke = Stroke(
            width = width.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
        )
        val r = cornerRadius.toPx()
        drawRoundRect(
            color = color,
            style = stroke,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
        )
    }
