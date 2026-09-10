package com.meapps.scadenzespese

import com.meapps.scadenzespese.data.*
import com.meapps.scadenzespese.util.occurrenceDates
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FormattingTest {
    @Test fun monthlyForecastIncludesEveryOccurrence() {
        val start=LocalDate.of(2026,1,15)
        val item=EntryEntity(name="Test",categoryId=1,type=EntryType.EXPENSE,amountCents=100,
            nextDate=start.toEpochDay(),frequencyCount=1,frequencyUnit=FrequencyUnit.MONTHS,renewalMode=RenewalMode.AUTOMATIC)
        assertEquals(4, occurrenceDates(item,start,start.plusMonths(3)).count())
    }
}
