package com.meapps.gestionale.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SettingsScreen(vm:AppViewModel,theme:String,onThemeChange:(String)->Unit,onBack:()->Unit){
    val categories by vm.categories.collectAsState();val context=LocalContext.current;val scope=rememberCoroutineScope()
    var name by remember{mutableStateOf("")};var confirmImport by remember{mutableStateOf<android.net.Uri?>(null)}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->uri?.let{scope.launch{runCatching{vm.backup.exportBackup(it)}.onSuccess{Toast.makeText(context,"Backup esportato",Toast.LENGTH_SHORT).show()}.onFailure{Toast.makeText(context,it.message,Toast.LENGTH_LONG).show()}}}}
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)confirmImport=uri}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=14.dp),contentPadding=PaddingValues(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{TextButton(onBack){Text("‹ Indietro")};SectionTitle("Impostazioni")}
        item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Aspetto",fontWeight=FontWeight.Bold);Text("Tema")
            SingleChoiceSegmentedButtonRow { listOf("Sistema","Chiaro","Scuro").forEachIndexed { index,value ->
                SegmentedButton(selected=theme==value,onClick={onThemeChange(value)},shape=SegmentedButtonDefaults.itemShape(index,3)){Text(value)}
            } };Text("Valuta: EUR");Text("Primo giorno della settimana: lunedì")
            Text("Il font segue quello di sistema del dispositivo.",style=MaterialTheme.typography.bodySmall)
        }}}
        item{SectionTitle("Categorie")}
        items(categories,key={it.id}){Text("${it.icon}  ${it.name}",Modifier.padding(vertical=5.dp))}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(name,{name=it},Modifier.weight(1f),label={Text("Nuova categoria")});Button({vm.addCategory(name);name=""}){Text("Aggiungi")}}}
        item{SectionTitle("Backup ed esportazione")}
        item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Button({export.launch("scadenze-spese-backup.json")},Modifier.fillMaxWidth()){Text("Esporta backup")}
            OutlinedButton({import.launch(arrayOf("application/json","text/plain"))},Modifier.fillMaxWidth()){Text("Importa backup")}
            Text("Il ripristino sostituisce i dati presenti solo dopo conferma.",style=MaterialTheme.typography.bodySmall)
        }}}
        item{SectionTitle("Notifiche");Text("I promemoria sono locali e continuano a funzionare senza Internet. I permessi si gestiscono anche dalle impostazioni Android.")}
        item{SectionTitle("Privacy");Text("Tutti i dati restano sul dispositivo. L'app non usa account, pubblicità, tracciamento o servizi cloud.")}
        item{SectionTitle("Informazioni");Text("Scadenze e Spese 1.0.0\nPackage: com.meapps.gestionale")}
    }
    confirmImport?.let{uri->AlertDialog(onDismissRequest={confirmImport=null},title={Text("Importare il backup?")},text={Text("I dati attuali verranno sostituiti. Questa operazione non può essere annullata.")},
        confirmButton={Button({scope.launch{runCatching{vm.backup.importBackup(uri)}.onSuccess{Toast.makeText(context,"Backup importato",Toast.LENGTH_SHORT).show()}.onFailure{Toast.makeText(context,it.message ?: "File non valido",Toast.LENGTH_LONG).show()}};confirmImport=null}){Text("Importa")}},
        dismissButton={TextButton({confirmImport=null}){Text("Annulla")}})}
}
