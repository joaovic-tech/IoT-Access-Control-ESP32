package com.example.espcamapp.views.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.espcamapp.ui.theme.DarkGrayCard
import com.example.espcamapp.ui.theme.DarkSlateBg
import com.example.espcamapp.ui.theme.PurplePrimary
import com.example.espcamapp.ui.theme.SlateGray

enum class Tab(val label: String) {
    PRINCIPAL("Principal"),
    DISPOSITIVOS("Dispositivos"),
    PESSOAS("Pessoas")
}

@Composable
fun BottomNavBar(current: Tab, onSelect: (Tab) -> Unit) {
    Column(modifier = Modifier.background(DarkSlateBg)) {
        HorizontalDivider(color = SlateGray.copy(alpha = 0.15f), thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(Tab.PRINCIPAL, current, Icons.Filled.Home, Icons.Outlined.Home, onSelect)
            NavItem(Tab.DISPOSITIVOS, current, Icons.Outlined.CameraAlt, Icons.Outlined.CameraAlt, onSelect)
            NavItem(Tab.PESSOAS, current, Icons.Outlined.GroupAdd, Icons.Outlined.GroupAdd, onSelect)
        }
    }
}

@Composable
private fun NavItem(
    tab: Tab,
    current: Tab,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    onSelect: (Tab) -> Unit
) {
    val isSelected = tab == current
    val tint = if (isSelected) PurplePrimary else SlateGray
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) DarkGrayCard else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onSelect(tab) }
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Icon(
            if (isSelected) selectedIcon else unselectedIcon,
            contentDescription = tab.label,
            tint = tint
        )
        Text(
            tab.label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}
