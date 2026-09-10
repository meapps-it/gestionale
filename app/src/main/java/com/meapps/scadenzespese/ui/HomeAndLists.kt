package com.meapps.scadenzespese.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.meapps.scadenzespese.data.*
import com.meapps.scadenzespese.ui.theme.Positive
import com.meapps.scadenzespese.util.euro
import com.meapps.scadenzespese.util.occurrenceDates
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

private fun expenseIn(items: List<EntryWithCategory>, from: LocalDate, to: LocalDate): Long = items
    .filter { it.status==EntryStatus.ACTIVE && it.type!=EntryType.DEADLINE && it.amountCents!=null }
    .sumOf { item -> occurrenceDates(item.asEntity(),from,to).count() * (item.amountCents ?: 0) }

@Composable fun HomeScreen(entries: List<EntryWithCategory>, open: (EntryWithCategory)->Unit) {
    val today=LocalDate.now(); val month=YearMonth.now()
    val active=entries.filter { it.status==EntryStatus.ACTIVE }
    val next=active.filter { it.nextDate>=today.toEpochDay() }.minByOrNull { it.nextDate }
    val monthCost=expenseIn(entries,month.atDay(1),month.atEndOfMonth())
    val yearCost=expenseIn(entries,today,today.plusMonths(12).minusDays(1))
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=14.dp),contentPadding=PaddingValues(vertical=14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                MetricCard("Spese previste questo mese",euro(monthCost),Positive,Modifier.weight(1f))
                MetricCard("Costo annuale previsto",euro(yearCost),Positive,Modifier.weight(1f))
            }
        }
        item { MetricCard("Prossima scadenza",next?.let {
            val days=ChronoUnit.DAYS.between(today,LocalDate.ofEpochDay(it.nextDate)); "${it.name}\ntra $days giorni"
        } ?: "Nessuna",modifier=Modifier.fillMaxWidth()) }
        item { SectionTitle("Prossime scadenze") }
        if(active.isEmpty()) item { Text("Nessuna voce. Usa + per iniziare.",color=Color.Gray) }
        items(active.take(12),key={it.id}) { EntryRow(it){open(it)} }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable fun DeadlinesScreen(entries: List<EntryWithCategory>, open:(EntryWithCategory)->Unit) {
    var tab by remember { mutableIntStateOf(0) }; var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<Long?>(null) }; var amountFilter by remember { mutableIntStateOf(0) }
    val today=LocalDate.now().toEpochDay()
    val filtered=entries.filter { it.type!=EntryType.EXPENSE && it.name.contains(query,true) }
        .filter { category==null || it.categoryId==category }
        .filter { amountFilter==0 || (amountFilter==1)==(it.amountCents!=null) }
        .filter { when(tab){0->it.status==EntryStatus.ACTIVE&&it.nextDate>=today;1->it.status==EntryStatus.ACTIVE&&it.nextDate<today;else->it.status==EntryStatus.COMPLETED} }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=14.dp),contentPadding=PaddingValues(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("Cerca per nome")},singleLine=true) }
        item { TabRow(tab) { listOf("In arrivo","Scadute","Completate").forEachIndexed { i,s -> Tab(tab==i,{tab=i},text={Text(s)}) } } }
        item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(amountFilter==0,{amountFilter=0},{Text("Tutte")})
            FilterChip(amountFilter==1,{amountFilter=1},{Text("Con importo")})
            FilterChip(amountFilter==2,{amountFilter=2},{Text("Senza importo")})
        } }
        items(filtered,key={it.id}) { EntryRow(it){open(it)} }
        if(filtered.isEmpty()) item { Text("Nessuna voce con questi filtri.") }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable fun ExpensesScreen(entries: List<EntryWithCategory>) {
    val now=YearMonth.now(); val today=LocalDate.now()
    val month=expenseIn(entries,now.atDay(1),now.atEndOfMonth())
    val next=expenseIn(entries,now.plusMonths(1).atDay(1),now.plusMonths(1).atEndOfMonth())
    val year=expenseIn(entries,LocalDate.of(today.year,1,1),LocalDate.of(today.year,12,31))
    val twelve=expenseIn(entries,today,today.plusMonths(12).minusDays(1))
    val grouped=entries.filter { it.type!=EntryType.DEADLINE&&it.amountCents!=null }.groupBy { it.categoryName }
        .mapValues { (_,v)->expenseIn(v,today,today.plusMonths(12).minusDays(1)) }.toList().sortedByDescending { it.second }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=14.dp),contentPadding=PaddingValues(vertical=14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            MetricCard("Questo mese",euro(month),Positive,Modifier.weight(1f));MetricCard("Prossimo mese",euro(next),modifier=Modifier.weight(1f))
        } }
        item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            MetricCard("Anno corrente",euro(year),Positive,Modifier.weight(1f));MetricCard("Media mensile",euro(twelve/12),modifier=Modifier.weight(1f))
        } }
        item { MetricCard("Previsione prossimi 3 mesi",euro(expenseIn(entries,today,today.plusMonths(3))),modifier=Modifier.fillMaxWidth()) }
        item { SectionTitle("Previsione 12 mesi") }
        items(12) { offset ->
            val m=now.plusMonths(offset.toLong()); val value=expenseIn(entries,m.atDay(1),m.atEndOfMonth())
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(m.month.getDisplayName(java.time.format.TextStyle.FULL,java.util.Locale.ITALY).replaceFirstChar(Char::uppercase));Text(euro(value)) }
            LinearProgressIndicator(progress={if(twelve==0L)0f else (value.toFloat()/(twelve.coerceAtLeast(1)/4f)).coerceIn(0f,1f)},Modifier.fillMaxWidth())
        }
        item { SectionTitle("Costi per categoria") }
        items(grouped) { (name,value) -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(name);Text(euro(value),color=Positive)} }
        item { Spacer(Modifier.height(72.dp)) }
    }
}
