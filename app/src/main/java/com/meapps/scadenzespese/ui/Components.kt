package com.meapps.scadenzespese.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meapps.scadenzespese.data.EntryStatus
import com.meapps.scadenzespese.data.EntryWithCategory
import com.meapps.scadenzespese.ui.theme.*
import com.meapps.scadenzespese.util.date
import com.meapps.scadenzespese.util.euro
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable fun AppHeader(onMenu: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Navy).statusBarsPadding().padding(horizontal=16.dp, vertical=12.dp),
        verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Scadenze e Spese", color=Color.White, fontSize=20.sp, fontWeight=FontWeight.Bold)
            Text(LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", java.util.Locale.ITALY)),
                color=Color(0xFFD7DEEB), fontSize=12.sp)
        }
        FilledTonalIconButton(onClick=onMenu, colors=IconButtonDefaults.filledTonalIconButtonColors(
            containerColor=Color(0xFF202B44), contentColor=Color.White)) {
            Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
        }
    }
}

@Composable fun MetricCard(label: String, value: String, accent: Color=Navy, modifier: Modifier=Modifier) {
    Card(modifier.heightIn(min=96.dp), shape=RoundedCornerShape(18.dp), border=androidx.compose.foundation.BorderStroke(1.dp,Line),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface), elevation=CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(label.uppercase(), fontSize=10.sp, fontWeight=FontWeight.Bold, color=Color(0xFF64748B))
            Text(value, fontSize=22.sp, fontWeight=FontWeight.Bold, color=accent, maxLines=2, overflow=TextOverflow.Ellipsis)
        }
    }
}

fun urgency(item: EntryWithCategory): Pair<Color,String> {
    if (item.status == EntryStatus.COMPLETED) return Positive to "Completata"
    val days = ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.ofEpochDay(item.nextDate))
    return when { days < 0 -> Danger to "Scaduta"; days <= 7 -> Warning to "Imminente"; else -> ActionBlue to "In arrivo" }
}

@Composable fun EntryRow(item: EntryWithCategory, onClick: () -> Unit) {
    val (color,status) = urgency(item)
    Card(Modifier.fillMaxWidth().clickable(onClick=onClick), shape=RoundedCornerShape(16.dp),
        border=androidx.compose.foundation.BorderStroke(1.dp,Line), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(12.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Box(Modifier.width(4.dp).height(48.dp).background(color,RoundedCornerShape(9.dp)))
            Column(Modifier.weight(1f)) {
                Text(date(item.nextDate), fontSize=11.sp, color=Color(0xFF64748B), fontWeight=FontWeight.SemiBold)
                Text(item.name, fontSize=15.sp, fontWeight=FontWeight.Bold, maxLines=1, overflow=TextOverflow.Ellipsis)
                Text("${item.categoryIcon} ${item.categoryName}", fontSize=11.sp, color=Color(0xFF64748B))
            }
            Column(horizontalAlignment=Alignment.End) {
                item.amountCents?.let { Text(euro(it), fontWeight=FontWeight.Bold, color=if(it>=0) Positive else Danger) }
                Text(status, fontSize=10.sp, fontWeight=FontWeight.Bold, color=color)
            }
        }
    }
}

@Composable fun SectionTitle(text: String) = Text(text, fontSize=18.sp, fontWeight=FontWeight.Bold,
    modifier=Modifier.padding(top=6.dp,bottom=2.dp))
