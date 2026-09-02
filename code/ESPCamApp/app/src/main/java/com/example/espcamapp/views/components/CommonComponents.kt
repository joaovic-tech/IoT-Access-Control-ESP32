package com.example.espcamapp.views.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.espcamapp.ui.theme.DarkGrayInput
import com.example.espcamapp.ui.theme.OffWhite
import com.example.espcamapp.ui.theme.PurplePrimary
import com.example.espcamapp.ui.theme.SlateGray
import com.example.espcamapp.ui.theme.StatusAmberPending
import com.example.espcamapp.ui.theme.StatusGreenOnline

val ScreenHorizontalPadding = 20.dp

/** Título de seção em destaque (ex.: "Rostos detectados recentes:"). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = OffWhite,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}

/** Cabeçalho de tela: título grande + subtítulo opcional. */
@Composable
fun HeaderBlock(title: String, subtitle: String? = null) {
    Text(title, color = OffWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    if (subtitle != null) {
        Text(
            subtitle,
            color = SlateGray,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

@Composable
fun HeaderDivider() {
    HorizontalDivider(
        color = SlateGray.copy(alpha = 0.15f),
        thickness = 1.dp,
        modifier = Modifier.padding(top = 14.dp)
    )
}

/** Botão primário roxo, largura total. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PurplePrimary,
            contentColor = Color.White
        )
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        }
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Botão secundário com contorno roxo. */
@Composable
fun OutlinePurpleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, PurplePrimary),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = DarkGrayInput.copy(alpha = 0.4f),
            contentColor = PurplePrimary
        )
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        }
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Campo de texto padrão do app (fundo escuro, sem borda visível, cantos arredondados). */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    singleLine: Boolean = true,
    minHeight: Int = 56,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = minHeight.dp),
        placeholder = { Text(placeholder, color = SlateGray, fontSize = 14.sp) },
        leadingIcon = leadingIcon?.let { icon -> { Icon(icon, contentDescription = null, tint = SlateGray) } },
        singleLine = singleLine,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = DarkGrayInput,
            unfocusedContainerColor = DarkGrayInput,
            disabledContainerColor = DarkGrayInput,
            focusedTextColor = OffWhite,
            unfocusedTextColor = OffWhite,
            cursorColor = PurplePrimary,
            focusedPlaceholderColor = SlateGray,
            unfocusedPlaceholderColor = SlateGray,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        )
    )
}

enum class PillStatus { ACTIVE, PENDING, ONLINE }

/** Etiqueta de status arredondada (Ativo / Pendente / Online). */
@Composable
fun StatusPill(text: String, status: PillStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        PillStatus.ACTIVE, PillStatus.ONLINE -> StatusGreenOnline
        PillStatus.PENDING -> StatusAmberPending
    }
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(50))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Grupo de chips selecionáveis (ex.: Morador / Visitante / Prestador, cômodos).
 *
 * Em [FlowRow] de propósito: com uma `Row` simples, a partir do terceiro ou
 * quarto cômodo os chips saíam da tela e não havia como escolhê-los.
 */
@Composable
fun SelectableChips(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) PurplePrimary else DarkGrayInput)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 18.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    option,
                    color = if (isSelected) Color.White else SlateGray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

fun listContentPadding(bottom: Int = 24): PaddingValues =
    PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = bottom.dp)
