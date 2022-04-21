package com.ivy.wallet.domain.action.account

import com.ivy.wallet.domain.action.FPAction
import com.ivy.wallet.domain.data.entity.Account
import com.ivy.wallet.io.persistence.dao.AccountDao
import javax.inject.Inject

class AccountsAct @Inject constructor(
    private val accountDao: AccountDao
) : FPAction<Unit, List<Account>>() {
    override suspend fun Unit.recipe(): suspend () -> List<Account> = suspend {
        io { accountDao.findAll() }
    }
}