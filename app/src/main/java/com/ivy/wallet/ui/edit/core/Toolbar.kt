package com.ivy.wallet.ui.edit.core

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivy.design.api.navigation
import com.ivy.wallet.R
import com.ivy.wallet.model.TransactionType
import com.ivy.wallet.ui.theme.components.CloseButton
import com.ivy.wallet.ui.theme.components.DeleteButton
import com.ivy.wallet.ui.theme.components.IvyOutlinedButton
import java.util.*

@Composable
fun Toolbar(
    type: TransactionType,
    initialTransactionId: UUID?,

    onDeleteTrnModal: () -> Unit,
    onChangeTransactionTypeModal: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(24.dp))

        val nav = navigation()
        CloseButton {
            nav.back()
        }

        Spacer(Modifier.weight(1f))

        when (type) {
            TransactionType.INCOME -> {
                IvyOutlinedButton(
                    text = "Income",
                    iconStart = R.drawable.ic_income
                ) {
                    onChangeTransactionTypeModal()
                }

                Spacer(Modifier.width(12.dp))
            }
            TransactionType.EXPENSE -> {
                IvyOutlinedButton(
                    text = "Expense",
                    iconStart = R.drawable.ic_expense
                ) {
                    onChangeTransactionTypeModal()
                }

                Spacer(Modifier.width(12.dp))
            }
            else -> {
                //show nothing
            }
        }

        if (initialTransactionId != null) {

            DeleteButton {
                onDeleteTrnModal()
            }

            Spacer(Modifier.width(24.dp))
        }
    }
}
