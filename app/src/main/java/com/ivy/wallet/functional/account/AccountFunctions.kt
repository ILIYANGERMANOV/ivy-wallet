package com.ivy.wallet.functional.account

import arrow.core.nonEmptyListOf
import com.ivy.wallet.functional.data.ClosedTimeRange
import com.ivy.wallet.functional.data.IncomeExpensePair
import com.ivy.wallet.persistence.dao.TransactionDao
import java.math.BigDecimal
import java.util.*


suspend fun calculateAccountBalance(
    transactionDao: TransactionDao,
    accountId: UUID,
    range: ClosedTimeRange = ClosedTimeRange.allTimeIvy()
): BigDecimal {
    return calculateAccountValues(
        transactionDao = transactionDao,
        accountId = accountId,
        range = range,
        valueFunctions = nonEmptyListOf(
            AccountValueFunctions::balance
        )
    ).head
}

data class AccountStats(
    val balance: BigDecimal,
    val income: BigDecimal,
    val expense: BigDecimal,
    val incomeCount: Int,
    val expenseCount: Int
)

suspend fun calculateAccountStats(
    transactionDao: TransactionDao,
    accountId: UUID,
    range: ClosedTimeRange = ClosedTimeRange.allTimeIvy()
): AccountStats {
    val values = calculateAccountValues(
        transactionDao = transactionDao,
        accountId = accountId,
        range = range,
        valueFunctions = nonEmptyListOf(
            AccountValueFunctions::balance,
            AccountValueFunctions::income,
            AccountValueFunctions::expense,
            AccountValueFunctions::incomeCount,
            AccountValueFunctions::expenseCount
        )
    )

    return AccountStats(
        balance = values[0],
        income = values[1],
        expense = values[2],
        incomeCount = values[3].toInt(),
        expenseCount = values[4].toInt()
    )
}

suspend fun calculateAccountIncomeExpense(
    transactionDao: TransactionDao,
    accountId: UUID,
    range: ClosedTimeRange = ClosedTimeRange.allTimeIvy()
): IncomeExpensePair {
    val values = calculateAccountValues(
        transactionDao = transactionDao,
        accountId = accountId,
        range = range,
        valueFunctions = nonEmptyListOf(
            AccountValueFunctions::income,
            AccountValueFunctions::expense,
        )
    )

    return IncomeExpensePair(
        income = values[0],
        expense = values[1],
    )
}


