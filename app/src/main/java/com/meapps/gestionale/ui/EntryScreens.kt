package com.meapps.gestionale.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meapps.gestionale.data.*
import com.meapps.gestionale.ui.theme.Danger
import com.meapps.gestionale.ui.theme.Positive
import com.meapps.gestionale.util.date
import com.meapps.gestionale.util.euro
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt

@Composable fun EntryFormScreen(categories: List<CategoryEntity>, existing: EntryWithCategory?,
    onCancel:()->Unit, onSave:(EntryDraft)->Unit) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var category by remember { mutableStateOf(existing?.categoryId ?: categories.firstOrNull()?.id ?: 0L) }
    var type by remember { mutableStateOf(existing?.type ?: EntryType.BOTH) }
    var amount by remember { mutableStateOf(existing?.amountCents?.let { "%.2f".format(java.util.Locale.US,it/100.0) }.orEmpty()) }
    var day by remember { mutableStateOf(existing?.nextDate?.let { LocalDate.ofEpochDay(it).toString() } ?: LocalDate.now().plusDays(7).toString()) }
    var count by remember { mutableStateOf((existing?.frequencyCount ?: 1).toString()) }
    var unit by remember { mutableStateOf(existing?.frequencyUnit ?: FrequencyUnit.MONTHS) }
    var renewal by remember { mutableStateOf(existing?.renewalMode ?: RenewalMode.MANUAL) }
    var reminders by remember { mutableStateOf(setOf(30,15,7,1,0)) }
    var customReminder by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        SectionTitle(if(existing==null) "Nuova voce" else "Modifica voce")
        OutlinedTextField(name,{name=it},Modifier.fillMaxWidth(),label={Text("Nome")},singleLine=true)
        categories.firstOrNull { it.id==category }?.let { selected ->
            ChoiceField("Categoria",categories,selected,{category=it.id}){"${it.icon} ${it.name}"}
        }
        ChoiceField("Tipo",EntryType.entries,type,{type=it}){when(it){EntryType.DEADLINE->"Solo scadenza";EntryType.EXPENSE->"Solo spesa ricorrente";EntryType.BOTH->"Scadenza + spesa"}}
        if(type!=EntryType.DEADLINE) OutlinedTextField(amount,{amount=it},Modifier.fillMaxWidth(),label={Text("Importo facoltativo (€)")},singleLine=true)
        OutlinedTextField(day,{day=it},Modifier.fillMaxWidth(),label={Text("Prossima data (AAAA-MM-GG)")},singleLine=true)
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(count,{count=it.filter(Char::isDigit)},Modifier.weight(.45f),label={Text("Ogni")},singleLine=true)
            ChoiceField("Intervallo",FrequencyUnit.entries,unit,{unit=it},Modifier.weight(1f)){when(it){FrequencyUnit.DAYS->"giorni";FrequencyUnit.WEEKS->"settimane";FrequencyUnit.MONTHS->"mesi";FrequencyUnit.YEARS->"anni"}}
        }
        ChoiceField("Rinnovo",RenewalMode.entries,renewal,{renewal=it}){if(it==RenewalMode.AUTOMATIC)"Automatico" else "Manuale"}
        Text("Promemoria",fontWeight=FontWeight.Bold)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            listOf(30,15,7,1,0).forEach { d -> FilterChip(d in reminders,{reminders=if(d in reminders)reminders-d else reminders+d},{Text(if(d==0)"Oggi" else "${d}g")}) }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(customReminder,{customReminder=it.filter(Char::isDigit)},Modifier.weight(1f),label={Text("Giorni personalizzati")},singleLine=true)
            Button(onClick={customReminder.toIntOrNull()?.let { reminders=reminders+it;customReminder="" }}){Text("Aggiungi")}
        }
        if(reminders.any { it !in listOf(30,15,7,1,0) }) Text("Personalizzati: ${reminders.filter { it !in listOf(30,15,7,1,0) }.sortedDescending().joinToString()} giorni")
        error?.let { Text(it,color=Danger) }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onCancel,Modifier.weight(1f)){Text("Annulla")}
            Button(onClick={
                try {
                    val date=LocalDate.parse(day).toEpochDay(); val n=count.toIntOrNull() ?: 0
                    val cents=amount.replace(',','.').toDoubleOrNull()?.times(100)?.roundToInt()?.toLong()
                    require(name.isNotBlank()){ "Inserisci il nome" };require(category!=0L){"Scegli la categoria"};require(n>0){"Intervallo non valido"}
                    onSave(EntryDraft(existing?.id ?: 0,name,category,type,cents,date,n,unit,renewal,reminders.toList()));error=null
                } catch(e:Exception){error=if(e is DateTimeParseException)"Data non valida. Usa AAAA-MM-GG" else e.message}
            },Modifier.weight(1f)){Text("Salva")}
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable private fun <T> ChoiceField(label:String, choices:List<T>, selected:T,onSelect:(T)->Unit,
    modifier:Modifier=Modifier,labeler:(T)->String) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton({open=true},Modifier.fillMaxWidth().height(56.dp)){Text("$label: ${labeler(selected)}",Modifier.weight(1f))}
        DropdownMenu(open,{open=false}) { choices.forEach { value -> DropdownMenuItem(text={Text(labeler(value))},onClick={onSelect(value);open=false}) } }
    }
}

@Composable fun DetailScreen(item:EntryWithCategory,vm:AppViewModel,onBack:()->Unit,onEdit:()->Unit,onDeleted:()->Unit) {
    val history by vm.history(item.id).collectAsStateWithLifecycle(initialValue=emptyList())
    val reminders by vm.reminders(item.id).collectAsStateWithLifecycle(initialValue=emptyList())
    var renew by remember { mutableStateOf(false) };var deleteConfirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        TextButton(onBack){Text("‹ Indietro")};SectionTitle(item.name)
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            LabelValue("Categoria","${item.categoryIcon} ${item.categoryName}");LabelValue("Prossima scadenza",date(item.nextDate));LabelValue("Importo",item.amountCents?.let(::euro) ?: "—")
            LabelValue("Frequenza","Ogni ${item.frequencyCount} ${item.frequencyUnit.name.lowercase()}")
            LabelValue("Promemoria",reminders.joinToString { if(it.daysBefore==0)"giorno stesso" else "${it.daysBefore} giorni" })
        } }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Button({renew=true}){Text("Rinnova")};Button({vm.complete(item)}){Text("Completa")};OutlinedButton(onEdit){Text("Modifica")} }
        TextButton({deleteConfirm=true},colors=ButtonDefaults.textButtonColors(contentColor=Danger)){Text("Elimina voce")}
        SectionTitle("Storico")
        if(history.isEmpty()) Text("Nessun rinnovo o pagamento registrato.")
        history.forEachIndexed { index,h ->
            val previous=history.getOrNull(index+1)?.amountCents
            Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp).fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(LocalDate.ofEpochDay(h.dueDate).year.toString(),fontWeight=FontWeight.Bold)
                Column { Text(h.amountCents?.let(::euro) ?: "Completata",color=Positive)
                    if(previous!=null&&h.amountCents!=null&&previous!=0L) Text("%+.1f%%".format((h.amountCents-previous)*100.0/previous),style=MaterialTheme.typography.labelSmall) }
            } }
        }
        if(history.isNotEmpty()) Text("Totale pagato: ${euro(history.sumOf { it.amountCents ?: 0 })}",fontWeight=FontWeight.Bold)
    }
    if(renew) RenewDialog(item,onDismiss={renew=false}) { d,a->vm.renew(item,d,a);renew=false }
    if(deleteConfirm) AlertDialog(onDismissRequest={deleteConfirm=false},title={Text("Eliminare la voce?")},text={Text("Verranno eliminati anche storico e promemoria.")},
        confirmButton={TextButton({vm.delete(item,onDeleted)}){Text("Elimina")}},dismissButton={TextButton({deleteConfirm=false}){Text("Annulla")}})
}

@Composable private fun LabelValue(label:String,value:String){Column{Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(value,fontWeight=FontWeight.SemiBold)}}
@Composable private fun RenewDialog(item:EntryWithCategory,onDismiss:()->Unit,onRenew:(Long,Long?)->Unit){
    var day by remember{mutableStateOf(LocalDate.ofEpochDay(item.nextDate).plusYears(1).toString())};var amount by remember{mutableStateOf(item.amountCents?.let{"%.2f".format(java.util.Locale.US,it/100.0)}.orEmpty())}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Rinnova ${item.name}")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(day,{day=it},label={Text("Nuova data")});OutlinedTextField(amount,{amount=it},label={Text("Nuovo importo")})}},
        confirmButton={Button({runCatching{onRenew(LocalDate.parse(day).toEpochDay(),amount.replace(',','.').toDoubleOrNull()?.times(100)?.roundToInt()?.toLong())}}){Text("Rinnova")}},dismissButton={TextButton(onDismiss){Text("Annulla")}})
}
