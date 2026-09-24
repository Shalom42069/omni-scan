package com.omniscan.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SectionCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = MaterialTheme.cardBackground
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.mutedText
                )
            }
            Spacer(Modifier.width(4.dp))
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.mutedText
        )
        Text(
            value,
            style = MaterialTheme.typography.caption.copy(
                fontFamily = FontFamily.Monospace
            ),
            fontWeight = FontWeight.Medium
        )
    }
}

/** Farbige Signal-Badge, grün (stark) → rot (schwach). */
@Composable
fun SignalBadge(dbm: Int?) {
    val txt = dbm?.let { "$it dBm" } ?: "—"
    val color = when {
        dbm == null -> Color(0xFF888888)
        dbm >= -55 -> Color(0xFF2ECC71)
        dbm >= -67 -> Color(0xFF7CF5C4)
        dbm >= -75 -> Color(0xFFF1C40F)
        dbm >= -85 -> Color(0xFFE67E22)
        else -> Color(0xFFE74C3C)
    }
    Surface(
        color = color.copy(alpha = 0.18f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            txt,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.body2,
        color = MaterialTheme.mutedText,
        modifier = Modifier.padding(16.dp)
    )
}

@Composable
fun CountPill(n: Int) {
    Surface(
        color = MaterialTheme.colors.primary.copy(alpha = 0.20f),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            "$n",
            color = MaterialTheme.colors.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
        )
    }
}
