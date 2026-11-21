package com.cashbk.app.notebook.filter

import java.io.Serializable

data class NotebookFilterCriteria(
    var sortBy: SortBy = SortBy.DATE_NEWEST,
    var balanceStatus: BalanceStatus = BalanceStatus.ALL,
    var timePeriod: TimePeriod = TimePeriod.ALL_TIME
) : Serializable {

    enum class SortBy {
        DATE_NEWEST,
        DATE_OLDEST,
        NAME_ASC,
        NAME_DESC,
        BALANCE_HIGH,
        BALANCE_LOW
    }

    enum class BalanceStatus {
        ALL,
        CREDIT,
        DEBIT,
        SETTLED
    }

    enum class TimePeriod {
        ALL_TIME,
        THIS_MONTH,
        THIS_YEAR
    }

    fun isDefault(): Boolean {
        return sortBy == SortBy.DATE_NEWEST &&
                balanceStatus == BalanceStatus.ALL &&
                timePeriod == TimePeriod.ALL_TIME
    }

    fun getActiveFilterCount(): Int {
        var count = 0
        if (sortBy != SortBy.DATE_NEWEST) count++
        if (balanceStatus != BalanceStatus.ALL) count++
        if (timePeriod != TimePeriod.ALL_TIME) count++
        return count
    }
}

