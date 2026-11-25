package com.mybalance.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/** Movimento selezionato nella cronologia (date = giorno della singola ripetizione, null = voce periodica). */
class DetailTarget(val tx: Tx, val date: Long?, val cancelled: Boolean)

private class DetailLine(val label: String, val value: String, val color: Color? = null)

/** Scheda a comparsa dal basso con tutti i dettagli del movimento, in elenco. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxDetailSheet(vm: MainViewModel, target: DetailTarget, onDismiss: () -> Unit) {
    val l = LocalL.current
    val p = LocalPalette.current
    val locale = LocalLocale.current
    val cur = vm.profile?.currency ?: "EUR"
    val tx = target.tx
    val today = LocalDate.now()
    val income = tx.type == TxType.INCOME
    val tone = if (income) p.positive else p.negative
    val recurring = tx.period != Period.ONCE

    val lines = ArrayList<DetailLine>()
    lines.add(DetailLine(l("Type", "Tipo"), if (income) l("Income", "Incasso") else l("Expense", "Spesa"), tone))
    lines.add(
        DetailLine(
            if (recurring) l("Amount each time", "Importo a volta") else l("Amount", "Importo"),
            money(tx.cents, cur), tone
        )
    )
    lines.add(DetailLine(l("Period", "Periodo"), scheduleSummary(tx, l, locale)))
    lines.add(DetailLine(l("Category", "Categoria"), tx.tag.label(l)))

    if (target.date != null) {
        lines.add(DetailLine(l("Date", "Data"), formatDay(target.date, locale)))
        if (recurring) {
            lines.add(
                DetailLine(
                    l("Status", "Stato"),
                    if (target.cancelled) l("Cancelled", "Annullata") else l("Done", "Effettuata"),
                    if (target.cancelled) p.negative else null
                )
            )
        }
    } else {
        lines.add(DetailLine(l("Started on", "Iniziata il"), formatDay(tx.startDay, locale)))
        lines.add(
            DetailLine(
                l("Status", "Stato"),
                if (isFinished(tx, today)) l("Ended", "Terminata") else l("Active", "Attiva")
            )
        )
        lines.add(DetailLine(l("Times so far", "Ripetizioni finora"), occurrencesUntil(tx, today).toString()))
        if (tx.skipped.isNotEmpty()) {
            lines.add(DetailLine(l("Cancelled", "Annullate"), tx.skipped.size.toString()))
        }
        lines.add(DetailLine(l("Total so far", "Totale finora"), money(txTotal(tx, today), cur), tone))
    }

    fun fundName(id: Long): String =
        vm.funds.firstOrNull { it.id == id }?.name ?: l("Deleted fund", "Fondo eliminato")
    val fundLabel = if (income) l("Added to", "Inserito in") else l("Taken from", "Prelevato da")
    if (tx.splits.isNotEmpty()) {
        for (s in tx.splits) lines.add(DetailLine(fundLabel, fundName(s.fundId) + "  " + money(s.cents, cur)))
    } else if (tx.fundId != null) {
        lines.add(DetailLine(fundLabel, fundName(tx.fundId)))
    } else {
        lines.add(DetailLine(l("Fund", "Fondo"), l("None (not allocated)", "Nessuno (non assegnato)")))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = p.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(tx.name, color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            lines.forEachIndexed { i, line ->
                if (i > 0) HorizontalDivider(color = p.outline)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(line.label, color = p.muted, fontSize = 15.sp)
                    Spacer(Modifier.padding(start = 16.dp))
                    Text(
                        line.value,
                        color = line.color ?: p.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
