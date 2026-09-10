package com.meapps.gestionale.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meapps.gestionale.data.EntryWithCategory

enum class MainSection(val label: String) { HOME("Home"), DEADLINES("Scadenze"), EXPENSES("Spese"), CALENDAR("Calendario") }
sealed interface Page { data object Main:Page; data object Form:Page; data class Detail(val id:Long):Page; data object Settings:Page }

@Composable fun GestionaleApp(
    theme: String,
    onThemeChange: (String) -> Unit,
    vm: AppViewModel = viewModel()
) {
    val entries by vm.entries.collectAsState()
    val categories by vm.categories.collectAsState()
    var section by remember { mutableStateOf(MainSection.HOME) }
    var page: Page by remember { mutableStateOf(Page.Main) }
    var edit by remember { mutableStateOf<EntryWithCategory?>(null) }
    var menu by remember { mutableStateOf(false) }
    BackHandler(page !is Page.Main) { page=Page.Main }

    Scaffold(
        topBar = { AppHeader(onMenu = { menu = true }) },
        bottomBar={ if(page is Page.Main) NavigationBar {
            listOf(MainSection.HOME to Icons.Default.Home, MainSection.DEADLINES to Icons.Default.Event,
                MainSection.EXPENSES to Icons.Default.Euro, MainSection.CALENDAR to Icons.Default.CalendarMonth).forEach { (item,icon) ->
                NavigationBarItem(selected=section==item,onClick={section=item},icon={Icon(icon,item.label)},label={Text(item.label)})
            }
        } },
        floatingActionButton={ if(page is Page.Main) FloatingActionButton(onClick={edit=null;page=Page.Form}){Icon(Icons.Default.Add,"Nuova voce")} }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when(val p=page) {
                Page.Main -> when(section) {
                    MainSection.HOME -> HomeScreen(entries) { page=Page.Detail(it.id) }
                    MainSection.DEADLINES -> DeadlinesScreen(entries) { page=Page.Detail(it.id) }
                    MainSection.EXPENSES -> ExpensesScreen(entries)
                    MainSection.CALENDAR -> CalendarScreen(entries) { page=Page.Detail(it.id) }
                }
                Page.Form -> EntryFormScreen(
                    categories = categories,
                    existing = edit,
                    onCancel = { page = Page.Main },
                    onSave = { draft -> vm.save(draft) { page = Page.Main } }
                )
                is Page.Detail -> entries.firstOrNull { it.id==p.id }?.let { item -> DetailScreen(item,vm,
                    onBack={page=Page.Main},onEdit={edit=item;page=Page.Form},onDeleted={page=Page.Main}) }
                Page.Settings -> SettingsScreen(
                    vm = vm,
                    theme = theme,
                    onThemeChange = onThemeChange,
                    onBack = { page = Page.Main }
                )
            }
        }
    }
    DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
        DropdownMenuItem(text={Text("Categorie e impostazioni")},leadingIcon={Icon(Icons.Default.Settings,null)},onClick={menu=false;page=Page.Settings})
        DropdownMenuItem(text={Text("Privacy")},leadingIcon={Icon(Icons.Default.PrivacyTip,null)},onClick={menu=false;page=Page.Settings})
        DropdownMenuItem(text={Text("Informazioni")},leadingIcon={Icon(Icons.Default.Info,null)},onClick={menu=false;page=Page.Settings})
    }
}
