package com.ivy.wallet.ui.edit

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.design.navigation.Navigation
import com.ivy.wallet.base.*
import com.ivy.wallet.event.AccountsUpdatedEvent
import com.ivy.wallet.logic.*
import com.ivy.wallet.logic.currency.ExchangeRatesLogic
import com.ivy.wallet.logic.loantrasactions.LoanTransactionsLogic
import com.ivy.wallet.logic.model.CreateAccountData
import com.ivy.wallet.logic.model.CreateCategoryData
import com.ivy.wallet.model.CustomExchangeRateState
import com.ivy.wallet.model.TransactionType
import com.ivy.wallet.model.entity.Account
import com.ivy.wallet.model.entity.Category
import com.ivy.wallet.model.entity.Transaction
import com.ivy.wallet.persistence.SharedPrefs
import com.ivy.wallet.persistence.dao.*
import com.ivy.wallet.sync.uploader.TransactionUploader
import com.ivy.wallet.ui.EditTransaction
import com.ivy.wallet.ui.IvyWalletCtx
import com.ivy.wallet.ui.Main
import com.ivy.wallet.ui.loan.data.EditTransactionDisplayLoan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import java.time.LocalDateTime
import java.util.*
import javax.inject.Inject

@HiltViewModel
class EditTransactionViewModel @Inject constructor(
    private val loanDao: LoanDao,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val settingsDao: SettingsDao,
    private val ivyContext: IvyWalletCtx,
    private val nav: Navigation,
    private val transactionUploader: TransactionUploader,
    private val sharedPrefs: SharedPrefs,
    private val exchangeRatesLogic: ExchangeRatesLogic,
    private val categoryCreator: CategoryCreator,
    private val accountCreator: AccountCreator,
    private val paywallLogic: PaywallLogic,
    private val plannedPaymentsLogic: PlannedPaymentsLogic,
    private val smartTitleSuggestionsLogic: SmartTitleSuggestionsLogic,
    private val loanTransactionsLogic: LoanTransactionsLogic
) : ViewModel() {

    private val _transactionType = MutableLiveData<TransactionType>()
    val transactionType = _transactionType

    private val _initialTitle = MutableLiveData<String?>()
    val initialTitle = _initialTitle.asLiveData()

    private val _titleSuggestions = MutableStateFlow(emptySet<String>())
    val titleSuggestions = _titleSuggestions.asStateFlow()

    private val _currency = MutableLiveData<String>()
    val currency = _currency.asLiveData()

    private val _description = MutableLiveData<String?>()
    val description = _description.asLiveData()

    private val _dateTime = MutableLiveData<LocalDateTime?>()
    val dateTime = _dateTime.asLiveData()

    private val _dueDate = MutableLiveData<LocalDateTime?>()
    val dueDate = _dueDate.asLiveData()

    private val _accounts = MutableLiveData<List<Account>>()
    val accounts = _accounts.asLiveData()

    private val _categories = MutableLiveData<List<Category>>()
    val categories = _categories.asLiveData()

    private val _account = MutableLiveData<Account>()
    val account = _account.asLiveData()

    private val _toAccount = MutableLiveData<Account?>()
    val toAccount = _toAccount.asLiveData()

    private val _category = MutableLiveData<Category?>()
    val category = _category.asLiveData()

    private val _amount = MutableLiveData(0.0)
    val amount = _amount.asLiveData()

    private val _hasChanges = MutableLiveData(false)
    val hasChanges = _hasChanges.asLiveData()

    private val _displayLoanHelper: MutableStateFlow<EditTransactionDisplayLoan> =
        MutableStateFlow(EditTransactionDisplayLoan())
    val displayLoanHelper = _displayLoanHelper.asStateFlow()

    //This is used to when the transaction is associated with a loan/loan record,
    // used to indicate the background updating of loan/loanRecord data
    private val _backgroundProcessingStarted = MutableStateFlow(false)
    val backgroundProcessingStarted = _backgroundProcessingStarted.asStateFlow()

    private val _customExchangeRateState = MutableStateFlow(CustomExchangeRateState())
    val customExchangeRateState = _customExchangeRateState.asStateFlow()

    private var loadedTransaction: Transaction? = null
    private var editMode = false

    //Used for optimising in updating all loan/loanRecords
    private var accountsChanged = false

    var title: String? = null
    private lateinit var baseUserCurrency: String

    fun start(screen: EditTransaction) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            editMode = screen.initialTransactionId != null

            baseUserCurrency = baseCurrency()

            val accounts = ioThread { accountDao.findAll() }!!
            if (accounts.isEmpty()) {
                closeScreen()
                return@launch
            }
            _accounts.value = accounts

            _categories.value = ioThread { categoryDao.findAll() }!!

            reset()

            loadedTransaction = screen.initialTransactionId?.let {
                ioThread { transactionDao.findById(it)!! }
            } ?: Transaction(
                accountId = defaultAccountId(
                    screen = screen,
                    accounts = accounts
                ),
                categoryId = screen.categoryId,
                type = screen.type,
                amount = 0.0,
            )

            display(loadedTransaction!!)

            TestIdlingResource.decrement()
        }
    }

    private suspend fun getDisplayLoanHelper(trans: Transaction): EditTransactionDisplayLoan {
        if (trans.loanId == null)
            return EditTransactionDisplayLoan()

        val loan =
            ioThread { loanDao.findById(trans.loanId) } ?: return EditTransactionDisplayLoan()
        val isLoanRecord = trans.loanRecordId != null

        val loanWarningDescription = if (isLoanRecord)
            "Note: This transaction is associated with a Loan Record of Loan : ${loan.name}\n" +
                    "You are trying to change the account associated with the loan record to an account of different currency" +
                    "\n The Loan Record will be re-calculated based on today's currency exchanges rates"
        else {
            "Note: You are trying to change the account associated with the loan: ${loan.name} with an account " +
                    "of different currency, " +
                    "\nAll the loan records will be re-calculated based on today's currency exchanges rates "
        }

        val loanCaption =
            if (isLoanRecord) "* This transaction is associated with a Loan Record of Loan : ${loan.name}"
            else "* This transaction is associated with Loan : ${loan.name}"

        return EditTransactionDisplayLoan(
            isLoan = true,
            isLoanRecord = isLoanRecord,
            loanCaption = loanCaption,
            loanWarningDescription = loanWarningDescription
        )
    }

    private suspend fun defaultAccountId(
        screen: EditTransaction,
        accounts: List<Account>,
    ): UUID {
        if (screen.accountId != null) {
            return screen.accountId
        }

        val lastSelectedId = sharedPrefs.getString(SharedPrefs.LAST_SELECTED_ACCOUNT_ID, null)
            ?.let { UUID.fromString(it) }
        if (lastSelectedId != null && ioThread { accounts.find { it.id == lastSelectedId } } != null) {
            //use last selected account
            return lastSelectedId
        }

        return accounts.first().id
    }

    private suspend fun display(transaction: Transaction) {
        this.title = transaction.title

        _transactionType.value = transaction.type
        _initialTitle.value = transaction.title
        _dateTime.value = transaction.dateTime
        _description.value = transaction.description
        _dueDate.value = transaction.dueDate
        val selectedAccount = ioThread { accountDao.findById(transaction.accountId)!! }
        _account.value = selectedAccount
        _toAccount.value = transaction.toAccountId?.let {
            ioThread { accountDao.findById(it) }
        }
        _category.value = transaction.smartCategoryId()?.let {
            ioThread { categoryDao.findById(it) }
        }
        _amount.value = transaction.amount

        updateCurrency(account = selectedAccount)

        transaction.toAmount?.let {
            val exchangeRate = it / transaction.amount
            val toAccountCurrency =
                _accounts.value?.find { acc -> acc.id == transaction.toAccountId }?.currency
            _customExchangeRateState.value =
                _customExchangeRateState.value.copy(
                    showCard = toAccountCurrency != account.value?.currency,
                    exchangeRate = exchangeRate,
                    convertedAmount = it,
                    toCurrencyCode = toAccountCurrency,
                    fromCurrencyCode = currency.value
                )
        } ?: let {
            _customExchangeRateState.value = CustomExchangeRateState()
        }

        _displayLoanHelper.value = getDisplayLoanHelper(trans = transaction)
    }

    private suspend fun updateCurrency(account: Account) {
        _currency.value = account.currency ?: baseCurrency()
    }

    private suspend fun baseCurrency(): String = ioThread { settingsDao.findFirst().currency }

    fun onAmountChanged(newAmount: Double) {
        viewModelScope.launch {
            loadedTransaction = loadedTransaction().copy(
                amount = newAmount
            )
            _amount.value = newAmount
            updateCustomExchangeRateState(amt = newAmount)

            saveIfEditMode()
        }
    }

    fun onTitleChanged(newTitle: String?) {
        loadedTransaction = loadedTransaction().copy(
            title = newTitle
        )
        this.title = newTitle

        saveIfEditMode()

        updateTitleSuggestions(newTitle)
    }

    private fun updateTitleSuggestions(title: String? = loadedTransaction().title) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            _titleSuggestions.value = ioThread {
                smartTitleSuggestionsLogic.suggest(
                    title = title,
                    categoryId = category.value?.id,
                    accountId = account.value?.id
                )
            }

            TestIdlingResource.decrement()
        }
    }

    fun onDescriptionChanged(newDescription: String?) {
        loadedTransaction = loadedTransaction().copy(
            description = newDescription
        )
        _description.value = newDescription

        saveIfEditMode()
    }

    fun onCategoryChanged(newCategory: Category?) {
        loadedTransaction = loadedTransaction().copy(
            categoryId = newCategory?.id
        )
        _category.value = newCategory

        saveIfEditMode()

        updateTitleSuggestions()
    }

    fun onAccountChanged(newAccount: Account) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            loadedTransaction = loadedTransaction().copy(
                accountId = newAccount.id
            )
            _account.value = newAccount

            updateCustomExchangeRateState(fromAccount = newAccount)

            viewModelScope.launch {
                updateCurrency(account = newAccount)
            }

            accountsChanged = true

            //update last selected account
            sharedPrefs.putString(SharedPrefs.LAST_SELECTED_ACCOUNT_ID, newAccount.id.toString())

            saveIfEditMode()

            updateTitleSuggestions()

            TestIdlingResource.decrement()
        }
    }

    fun onToAccountChanged(newAccount: Account) {
        viewModelScope.launch {
            loadedTransaction = loadedTransaction().copy(
                toAccountId = newAccount.id
            )
            _toAccount.value = newAccount
            updateCustomExchangeRateState(toAccount = newAccount)

            saveIfEditMode()
        }
    }

    fun onDueDateChanged(newDueDate: LocalDateTime?) {
        loadedTransaction = loadedTransaction().copy(
            dueDate = newDueDate
        )
        _dueDate.value = newDueDate

        saveIfEditMode()
    }

    fun onSetDateTime(newDateTime: LocalDateTime) {
        loadedTransaction = loadedTransaction().copy(
            dateTime = newDateTime
        )
        _dateTime.value = newDateTime

        saveIfEditMode()
    }

    fun onSetTransactionType(newTransactionType: TransactionType) {
        loadedTransaction = loadedTransaction().copy(
            type = newTransactionType
        )
        _transactionType.value = newTransactionType

        saveIfEditMode()
    }


    fun onPayPlannedPayment() {
        viewModelScope.launch {
            TestIdlingResource.increment()

            plannedPaymentsLogic.payOrGet(
                transaction = loadedTransaction(),
                syncTransaction = false
            ) { paidTransaction ->
                loadedTransaction = paidTransaction
                _dueDate.value = paidTransaction.dueDate
                _dateTime.value = paidTransaction.dateTime

                saveIfEditMode(
                    closeScreen = true
                )
            }

            TestIdlingResource.decrement()
        }
    }


    fun delete() {
        viewModelScope.launch {
            TestIdlingResource.increment()

            ioThread {
                loadedTransaction?.let {
                    transactionDao.flagDeleted(it.id)
                }
                closeScreen()

                loadedTransaction?.let {
                    transactionUploader.delete(it.id)
                }
            }

            TestIdlingResource.decrement()
        }
    }

    fun createCategory(data: CreateCategoryData) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            categoryCreator.createCategory(data) {
                _categories.value = ioThread { categoryDao.findAll() }!!

                //Select the newly created category
                onCategoryChanged(it)
            }

            TestIdlingResource.decrement()
        }
    }

    fun editCategory(updatedCategory: Category) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            categoryCreator.editCategory(updatedCategory) {
                _categories.value = ioThread { categoryDao.findAll() }!!
            }

            TestIdlingResource.decrement()
        }
    }

    fun createAccount(data: CreateAccountData) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            accountCreator.createAccount(data) {
                EventBus.getDefault().post(AccountsUpdatedEvent())
                _accounts.value = ioThread { accountDao.findAll() }!!
            }

            TestIdlingResource.decrement()
        }
    }

    private fun saveIfEditMode(closeScreen: Boolean = false) {
        if (editMode) {
            _hasChanges.value = true

            save(closeScreen)
        }
    }

    fun save(closeScreen: Boolean = true) {
        if (!validateTransaction()) {
            return
        }

        viewModelScope.launch {
            TestIdlingResource.increment()

            paywallLogic.protectQuotaExceededWithPaywall(
                onPaywallHit = {
                    nav.back()
                }
            ) {
                saveInternal(closeScreen = closeScreen)
            }

            TestIdlingResource.decrement()
        }
    }

    private suspend fun saveInternal(closeScreen: Boolean) {
        try {
            ioThread {
                val amount = amount.value ?: error("no amount")

                loadedTransaction = loadedTransaction().copy(
                    accountId = account.value?.id ?: error("no accountId"),
                    toAccountId = toAccount.value?.id,
                    toAmount = _customExchangeRateState.value.convertedAmount,
                    title = title?.trim(),
                    description = description.value?.trim(),
                    amount = amount,
                    type = transactionType.value ?: error("no transaction type"),
                    dueDate = dueDate.value,
                    dateTime = when {
                        loadedTransaction().dateTime == null &&
                                dueDate.value == null -> {
                            timeNowUTC()
                        }
                        else -> loadedTransaction().dateTime
                    },
                    categoryId = category.value?.id,
                    isSynced = false
                )

                if (loadedTransaction?.loanId != null) {
                    loanTransactionsLogic.updateAssociatedLoanData(
                        loadedTransaction!!.copy(),
                        onBackgroundProcessingStart = {
                            _backgroundProcessingStarted.value = true
                        },
                        onBackgroundProcessingEnd = {
                            _backgroundProcessingStarted.value = false
                        },
                        accountsChanged = accountsChanged
                    )

                    //Reset Counter
                    accountsChanged = false
                }

                transactionDao.save(loadedTransaction())
            }

            if (closeScreen) {
                closeScreen()

                ioThread {
                    transactionUploader.sync(loadedTransaction())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setHasChanges(hasChanges: Boolean) {
        _hasChanges.value = hasChanges
    }

    private suspend fun transferToAmount(
        amount: Double
    ): Double? {
        if (transactionType.value != TransactionType.TRANSFER) return null
        val toCurrency = toAccount.value?.currency ?: baseCurrency()
        val fromCurrency = account.value?.currency ?: baseCurrency()

        return exchangeRatesLogic.convertAmount(
            baseCurrency = baseCurrency(),
            amount = amount,
            fromCurrency = fromCurrency,
            toCurrency = toCurrency
        )
    }

    private fun closeScreen() {
        if (nav.backStackEmpty()) {
            nav.resetBackStack()
            nav.navigateTo(Main)
        } else {
            nav.back()
        }
    }

    private fun validateTransaction(): Boolean {
        if (transactionType.value == TransactionType.TRANSFER && toAccount.value == null) {
            return false
        }

        if (amount.value == 0.0) {
            return false
        }

        return true
    }

    private fun reset() {
        loadedTransaction = null

        _initialTitle.value = null
        _description.value = null
        _dueDate.value = null
        _category.value = null
        _hasChanges.value = false
    }

    private fun loadedTransaction() = loadedTransaction ?: error("Loaded transaction is null")

    private suspend fun updateCustomExchangeRateState(
        toAccount: Account? = null,
        fromAccount: Account? = null,
        amt: Double? = null,
        exchangeRate: Double? = null
    ) {
        computationThread {

            val toAcc = toAccount ?: _toAccount.value
            val fromAcc = fromAccount ?: _account.value

            val toAccCurrencyCode = toAcc?.currency ?: baseUserCurrency
            val fromAccCurrencyCode = fromAcc?.currency ?: baseUserCurrency

            if (toAcc == null || fromAcc == null || (toAccCurrencyCode == fromAccCurrencyCode)) {
                _customExchangeRateState.value = CustomExchangeRateState()
                return@computationThread
            }

            val exRate = exchangeRate
                ?: if (customExchangeRateState.value.showCard && toAccCurrencyCode == customExchangeRateState.value.toCurrencyCode
                    && fromAccCurrencyCode == customExchangeRateState.value.fromCurrencyCode
                )
                    customExchangeRateState.value.exchangeRate
                else
                    exchangeRatesLogic.convertAmount(
                        baseCurrency = baseUserCurrency,
                        amount = 1.0,
                        fromCurrency = fromAccCurrencyCode,
                        toCurrency = toAccCurrencyCode
                    )


            val amount = amt ?: _amount.value ?: 0.0

            val customTransferExchangeRateState = CustomExchangeRateState(
                showCard = true,
                toCurrencyCode = toAccCurrencyCode,
                fromCurrencyCode = fromAccCurrencyCode,
                exchangeRate = exRate,
                convertedAmount = exRate * amount
            )

            _customExchangeRateState.value = customTransferExchangeRateState
            uiThread {
                saveIfEditMode()
            }
        }
    }

    fun updateExchangeRate(exRate: Double) {
        viewModelScope.launch {
            updateCustomExchangeRateState(exchangeRate = exRate)
        }
    }
}