package com.meapps.scadenzespese.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meapps.scadenzespese.data.EntryWithCategory
import com.meapps.scadenzespese.ui.theme.ActionBlue
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable fun CalendarScreen(entries:List<EntryWithCategory>,open:(EntryWithCategory)->Unit){
    var month by remember{mutableStateOf(YearMonth.now())};var selected by remember{mutableStateOf(LocalDate.now())}
    val first=month.atDay(1);val blanks=(first.dayOfWeek.value-1);val cells=List(blanks){null}+ (1..month.lengthOfMonth()).map{month.atDay(it)}
    val dayEntries=entries.filter{it.nextDate==selected.toEpochDay()}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=14.dp),contentPadding=PaddingValues(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
            IconButton({month=month.minusMonths(1);selected=month.atDay(1)}){Icon(Icons.Default.ChevronLeft,"Mese precedente")}
            Text("${month.month.getDisplayName(TextStyle.FULL,Locale.ITALY).replaceFirstChar(Char::uppercase)} ${month.year}",fontWeight=FontWeight.Bold)
            IconButton({month=month.plusMonths(1);selected=month.atDay(1)}){Icon(Icons.Default.ChevronRight,"Mese successivo")}
        }}
        item{Row(Modifier.fillMaxWidth()){listOf("L","M","M","G","V","S","D").forEach{Text(it,Modifier.weight(1f),fontWeight=FontWeight.Bold)}}}
        items(cells.chunked(7)){week->Row(Modifier.fillMaxWidth()){week.forEach{day->
            if(day==null) Spacer(Modifier.weight(1f).aspectRatio(1f)) else {
                val has=entries.any{it.nextDate==day.toEpochDay()};val active=day==selected
                Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp).background(if(active)ActionBlue else Color.Transparent,RoundedCornerShape(12.dp)).clickable{selected=day},contentAlignment=Alignment.Center){
                    Column(horizontalAlignment=Alignment.CenterHorizontally){Text(day.dayOfMonth.toString(),color=if(active)Color.White else MaterialTheme.colorScheme.onBackground);if(has)Text("•",color=if(active)Color.White else ActionBlue)}
                }
            }
        }}}
        item{SectionTitle("${selected.dayOfMonth} ${selected.month.getDisplayName(TextStyle.FULL,Locale.ITALY).uppercase()}")}
        items(dayEntries,key={it.id}){EntryRow(it){open(it)}}
        if(dayEntries.isEmpty())item{Text("Nessun evento in questo giorno.")}
        item{Spacer(Modifier.height(72.dp))}
    }
}
