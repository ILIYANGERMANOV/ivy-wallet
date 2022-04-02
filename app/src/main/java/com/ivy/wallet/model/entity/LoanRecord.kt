package com.ivy.wallet.model.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import java.util.*

@Entity(tableName = "loan_records")
data class LoanRecord(
    val loanId: UUID,
    val amount: Double,
    val note: String? = null,
    val dateTime: LocalDateTime,
    val interest:Boolean = false,
    val accountId : UUID? = null,
    //This is used store the converted amount for currencies which are different from the loan account currency
    val convertedAmount :Double? = null,

    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,

    @PrimaryKey
    val id: UUID = UUID.randomUUID()
)