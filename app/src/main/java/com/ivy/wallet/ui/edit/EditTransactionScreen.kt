package com.ivy.wallet.ui.edit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding
import com.ivy.design.api.navigation
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.wallet.R
import com.ivy.wallet.base.convertUTCtoLocal
import com.ivy.wallet.base.getTrueDate
import com.ivy.wallet.base.onScreenStart
import com.ivy.wallet.base.timeNowLocal
import com.ivy.wallet.logic.model.CreateAccountData
import com.ivy.wallet.logic.model.CreateCategoryData
import com.ivy.wallet.model.CustomExchangeRateState
import com.ivy.wallet.model.TransactionType
import com.ivy.wallet.model.entity.Account
import com.ivy.wallet.model.entity.Category
import com.ivy.wallet.ui.EditPlanned
import com.ivy.wallet.ui.EditTransaction
import com.ivy.wallet.ui.IvyWalletPreview
import com.ivy.wallet.ui.edit.core.*
import com.ivy.wallet.ui.ivyWalletCtx
import com.ivy.wallet.ui.loan.data.EditTransactionDisplayLoan
import com.ivy.wallet.ui.theme.components.AddPrimaryAttributeButton
import com.ivy.wallet.ui.theme.components.ChangeTransactionTypeModal
import com.ivy.wallet.ui.theme.components.CustomExchangeRateCard
import com.ivy.wallet.ui.theme.modal.*
import com.ivy.wallet.ui.theme.modal.edit.*
import java.time.LocalDateTime
import java.util.*
import kotlin.math.roundToInt

@ExperimentalFoundationApi
@Composable
fun BoxWithConstraintsScope.EditTransactionScreen(screen: EditTransaction) {
    val viewModel: EditTransactionViewModel = viewModel()

    val transactionType by viewModel.transactionType.observeAsState(screen.type)
    val initialTitle by viewModel.initialTitle.observeAsState()
    val titleSuggestions by viewModel.titleSuggestions.collectAsState()
    val currency by viewModel.currency.observeAsState("")
    val description by viewModel.description.observeAsState()
    val dateTime by viewModel.dateTime.observeAsState()
    val category by viewModel.category.observeAsState()
    val account by viewModel.account.observeAsState()
    val toAccount by viewModel.toAccount.observeAsState()
    val dueDate by viewModel.dueDate.observeAsState()
    val amount by viewModel.amount.observeAsState(0.0)
    val loanData by viewModel.displayLoanHelper.collectAsState()
    val backgroundProcessing by viewModel.backgroundProcessingStarted.collectAsState()
    val customExchangeRateState by viewModel.customExchangeRateState.collectAsState()

    val categories by viewModel.categories.observeAsState(emptyList())
    val accounts by viewModel.accounts.observeAsState(emptyList())

    val hasChanges by viewModel.hasChanges.observeAsState(false)

    onScreenStart {
        viewModel.start(screen)
    }

    UI(
        screen = screen,
        transactionType = transactionType,
        baseCurrency = currency,
        initialTitle = initialTitle,
        titleSuggestions = titleSuggestions,
        description = description,
        dateTime = dateTime,
        category = category,
        account = account,
        toAccount = toAccount,
        dueDate = dueDate,
        amount = amount,
        loanData = loanData,
        backgroundProcessing = backgroundProcessing,
        customExchangeRateState = customExchangeRateState,

        categories = categories,
        accounts = accounts,

        hasChanges = hasChanges,

        onTitleChanged = viewModel::onTitleChanged,
        onDescriptionChanged = viewModel::onDescriptionChanged,
        onAmountChanged = viewModel::onAmountChanged,
        onCategoryChanged = viewModel::onCategoryChanged,
        onAccountChanged = viewModel::onAccountChanged,
        onToAccountChanged = viewModel::onToAccountChanged,
        onDueDateChanged = viewModel::onDueDateChanged,
        onSetDateTime = viewModel::onSetDateTime,
        onSetTransactionType = viewModel::onSetTransactionType,

        onCreateCategory = viewModel::createCategory,
        onEditCategory = viewModel::editCategory,
        onPayPlannedPayment = viewModel::onPayPlannedPayment,
        onSave = viewModel::save,
        onSetHasChanges = viewModel::setHasChanges,
        onDelete = viewModel::delete,
        onCreateAccount = viewModel::createAccount,
        onExchangeRateChanged = {
            viewModel.updateExchangeRate(exRate = it)
        }
    )
}

@ExperimentalFoundationApi
@Composable
private fun BoxWithConstraintsScope.UI(
    screen: EditTransaction,
    transactionType: TransactionType,
    baseCurrency: String,
    initialTitle: String?,
    titleSuggestions: Set<String>,
    description: String?,
    category: Category?,
    dateTime: LocalDateTime?,
    account: Account?,
    toAccount: Account?,
    dueDate: LocalDateTime?,
    amount: Double,
    loanData: EditTransactionDisplayLoan = EditTransactionDisplayLoan(),
    backgroundProcessing: Boolean = false,
    customExchangeRateState: CustomExchangeRateState,

    categories: List<Category>,
    accounts: List<Account>,

    hasChanges: Boolean = false,

    onTitleChanged: (String?) -> Unit,
    onDescriptionChanged: (String?) -> Unit,
    onAmountChanged: (Double) -> Unit,
    onCategoryChanged: (Category?) -> Unit,
    onAccountChanged: (Account) -> Unit,
    onToAccountChanged: (Account) -> Unit,
    onDueDateChanged: (LocalDateTime?) -> Unit,
    onSetDateTime: (LocalDateTime) -> Unit,
    onSetTransactionType: (TransactionType) -> Unit,

    onCreateCategory: (CreateCategoryData) -> Unit,
    onEditCategory: (Category) -> Unit,
    onPayPlannedPayment: () -> Unit,
    onSave: (closeScreen: Boolean) -> Unit,
    onSetHasChanges: (hasChanges: Boolean) -> Unit,
    onDelete: () -> Unit,
    onCreateAccount: (CreateAccountData) -> Unit,
    onExchangeRateChanged: (Double) -> Unit = { }
) {
    var chooseCategoryModalVisible by remember { mutableStateOf(false) }
    var categoryModalData: CategoryModalData? by remember { mutableStateOf(null) }
    var accountModalData: AccountModalData? by remember { mutableStateOf(null) }
    var descriptionModalVisible by remember { mutableStateOf(false) }
    var deleteTrnModalVisible by remember { mutableStateOf(false) }
    var changeTransactionTypeModalVisible by remember { mutableStateOf(false) }
    var amountModalShown by remember { mutableStateOf(false) }
    var exchangeRateAmountModalShown by remember { mutableStateOf(false) }
    var accountChangeModal by remember { mutableStateOf(false) }
    val waitModalVisible by remember(backgroundProcessing) {
        mutableStateOf(backgroundProcessing)
    }
    var selectedAcc by remember(account) {
        mutableStateOf(account)
    }

    var titleTextFieldValue by remember(initialTitle) {
        mutableStateOf(
            TextFieldValue(
                initialTitle ?: ""
            )
        )
    }
    val titleFocus = FocusRequester()
    val scrollState = rememberScrollState()

    //This is to scroll the column to the customExchangeCard composable when it is shown
    var customExchangeRatePosition by remember { mutableStateOf(0F) }
    LaunchedEffect(key1 = customExchangeRateState.showCard) {
        val scrollInt =
            if (customExchangeRateState.showCard) customExchangeRatePosition.roundToInt() else 0
        scrollState.animateScrollTo(scrollInt)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
    ) {
        Spacer(Modifier.height(16.dp))

        Toolbar(
            //Setting the transaction type to TransactionType.TRANSFER for transactions associated
            // with loan record to hide the ChangeTransactionType Button
            type = if (loanData.isLoanRecord) TransactionType.TRANSFER else transactionType,
            initialTransactionId = screen.initialTransactionId,
            onDeleteTrnModal = {
                deleteTrnModalVisible = true
            },
            onChangeTransactionTypeModal = {
                changeTransactionTypeModalVisible = true
            }
        )

        Spacer(Modifier.height(32.dp))

        Title(
            type = transactionType,
            titleFocus = titleFocus,
            initialTransactionId = screen.initialTransactionId,

            titleTextFieldValue = titleTextFieldValue,
            setTitleTextFieldValue = {
                titleTextFieldValue = it
            },
            suggestions = titleSuggestions,
            scrollState = scrollState,

            onTitleChanged = onTitleChanged,
            onNext = {
                when {
                    shouldFocusAmount(amount = amount) -> {
                        amountModalShown = true
                    }
                    else -> {
                        onSave(true)
                    }
                }
            }
        )

        if (loanData.loanCaption != null) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                modifier = Modifier.padding(horizontal = 24.dp),
                text = loanData.loanCaption,
                style = UI.typo.nB2.style(
                    color = UI.colors.mediumInverse,
                    fontWeight = FontWeight.Normal
                )
            )
        }

        if (transactionType != TransactionType.TRANSFER) {
            Spacer(Modifier.height(32.dp))

            Category(
                category = category,
                onChooseCategory = {
                    chooseCategoryModalVisible = true
                }
            )
        }

        Spacer(Modifier.height(32.dp))

        val ivyContext = ivyWalletCtx()

        if (dueDate != null) {
            DueDate(dueDate = dueDate) {
                ivyContext.datePicker(
                    initialDate = dueDate.toLocalDate()
                ) {
                    onDueDateChanged(it.atTime(12, 0))
                }
            }

            Spacer(Modifier.height(12.dp))
        }

        Description(
            description = description,
            onAddDescription = { descriptionModalVisible = true },
            onEditDescription = { descriptionModalVisible = true }
        )

        TransactionDateTime(
            dateTime = dateTime,
            dueDateTime = dueDate,
        ) {
            ivyContext.datePicker(
                initialDate = dateTime?.convertUTCtoLocal()?.toLocalDate(),
            ) { date ->
                ivyContext.timePicker { time ->
                    onSetDateTime(getTrueDate(date, time))
                }
            }
        }

        if (transactionType == TransactionType.TRANSFER && customExchangeRateState.showCard) {
            Spacer(Modifier.height(12.dp))
            CustomExchangeRateCard(
                fromCurrencyCode = baseCurrency,
                toCurrencyCode = customExchangeRateState.toCurrencyCode ?: baseCurrency,
                exchangeRate = customExchangeRateState.exchangeRate,
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    customExchangeRatePosition = coordinates.positionInParent().y * 0.3f
                }
            ) {
                exchangeRateAmountModalShown = true
            }
        }

        if (dueDate == null && transactionType != TransactionType.TRANSFER && dateTime == null) {
            Spacer(Modifier.height(12.dp))

            val nav = navigation()
            AddPrimaryAttributeButton(
                icon = R.drawable.ic_planned_payments,
                text = "Add planned date of payment",
                onClick = {
                    nav.back()
                    nav.navigateTo(
                        EditPlanned(
                            plannedPaymentRuleId = null,
                            type = transactionType,
                            amount = amount,
                            accountId = account?.id,
                            categoryId = category?.id,
                            title = titleTextFieldValue.text,
                            description = description,
                        )
                    )
                }
            )
        }

        Spacer(Modifier.height(600.dp)) //scroll hack
    }

    onScreenStart {
        if (screen.initialTransactionId == null) {
            amountModalShown = true
        }
    }

    EditBottomSheet(
        initialTransactionId = screen.initialTransactionId,
        type = transactionType,
        accounts = accounts,
        selectedAccount = account,
        toAccount = toAccount,
        amount = amount,
        currency = baseCurrency,
        convertedAmount = customExchangeRateState.convertedAmount,
        convertedAmountCurrencyCode = customExchangeRateState.toCurrencyCode,

        ActionButton = {
            if (screen.initialTransactionId != null) {
                //Edit mode
                if (dueDate != null) {
                    //due date stuff
                    if (hasChanges) {
                        //has changes
                        ModalSave {
                            onSave(false)
                            onSetHasChanges(false)
                        }
                    } else {
                        //no changes, pay
                        ModalCheck(label = if (transactionType == TransactionType.EXPENSE) "Pay" else "Get") {
                            onPayPlannedPayment()
                        }
                    }
                } else {
                    //normal transaction
                    ModalSave {
                        onSave(true)
                    }
                }
            } else {
                //create new mode
                ModalAdd {
                    onSave(true)
                }
            }
        },

        amountModalShown = amountModalShown,
        setAmountModalShown = {
            amountModalShown = it
        },

        onAmountChanged = {
            onAmountChanged(it)
            if (shouldFocusCategory(category, transactionType)) {
                chooseCategoryModalVisible = true
            } else if (shouldFocusTitle(titleTextFieldValue, transactionType)) {
                titleFocus.requestFocus()
            }
        },
        onSelectedAccountChanged = {
            if (loanData.isLoan && account?.currency != it.currency) {
                selectedAcc = it
                accountChangeModal = true
            } else
                onAccountChanged(it)
        },
        onToAccountChanged = onToAccountChanged,
        onAddNewAccount = {
            accountModalData = AccountModalData(
                account = null,
                baseCurrency = baseCurrency,
                balance = 0.0
            )
        }
    )

    //Modals
    ChooseCategoryModal(
        visible = chooseCategoryModalVisible,
        initialCategory = category,
        categories = categories,
        showCategoryModal = { categoryModalData = CategoryModalData(it) },
        onCategoryChanged = {
            onCategoryChanged(it)
            if (shouldFocusTitle(titleTextFieldValue, transactionType)) {
                titleFocus.requestFocus()
            } else if (shouldFocusAmount(amount = amount)) {
                amountModalShown = true
            }
        },
        dismiss = {
            chooseCategoryModalVisible = false
        }
    )

    CategoryModal(
        modal = categoryModalData,
        onCreateCategory = { createData ->
            onCreateCategory(createData)
            chooseCategoryModalVisible = false
        },
        onEditCategory = onEditCategory,
        dismiss = {
            categoryModalData = null
        }
    )

    AccountModal(
        modal = accountModalData,
        onCreateAccount = onCreateAccount,
        onEditAccount = { _, _ -> },
        dismiss = {
            accountModalData = null
        }
    )

    DescriptionModal(
        visible = descriptionModalVisible,
        description = description,
        onDescriptionChanged = onDescriptionChanged,
        dismiss = {
            descriptionModalVisible = false
        }
    )

    DeleteModal(
        visible = deleteTrnModalVisible,
        title = "Confirm deletion",
        description = "Deleting this transaction will remove it from the transaction history and update the balance accordingly.",
        dismiss = { deleteTrnModalVisible = false }
    ) {
        onDelete()
    }

    ChangeTransactionTypeModal(
        visible = changeTransactionTypeModalVisible,
        includeTransferType = true,
        initialType = transactionType,
        dismiss = {
            changeTransactionTypeModalVisible = false
        }
    ) {
        onSetTransactionType(it)
    }

    DeleteModal(
        visible = accountChangeModal,
        title = "Confirm Account Change",
        description = "Note: You are trying to change the account associated with the loan with an account of different currency, " +
                "\nAll the loan records will be re-calculated based on today's exchanges rates ",
        buttonText = "Confirm",
        iconStart = R.drawable.ic_agreed,
        dismiss = {
            accountChangeModal = false
        }
    ) {
        selectedAcc?.let { onAccountChanged(it) }
        accountChangeModal = false
    }

    ProgressModal(
        title = "Confirm Account Change",
        description = "Please wait, re-calculating all loan records",
        visible = waitModalVisible
    )

    AmountModal(
        id = UUID.randomUUID(),
        visible = exchangeRateAmountModalShown,
        currency = "",
        initialAmount = customExchangeRateState.exchangeRate,
        dismiss = { exchangeRateAmountModalShown = false },
        decimalCountMax = 4,
        onAmountChanged = {
            onExchangeRateChanged(it)
        }
    )

}

private fun shouldFocusCategory(
    category: Category?,
    type: TransactionType
): Boolean = category == null && type != TransactionType.TRANSFER

private fun shouldFocusTitle(
    titleTextFieldValue: TextFieldValue,
    type: TransactionType
): Boolean = titleTextFieldValue.text.isBlank() && type != TransactionType.TRANSFER

private fun shouldFocusAmount(amount: Double) = amount == 0.0

@ExperimentalFoundationApi
@Preview
@Composable
private fun Preview() {
    IvyWalletPreview {
        UI(
            screen = EditTransaction(null, TransactionType.EXPENSE),
            initialTitle = "",
            titleSuggestions = emptySet(),
            baseCurrency = "BGN",
            dateTime = timeNowLocal(),
            description = null,
            category = null,
            account = Account(name = "phyre"),
            toAccount = null,
            amount = 0.0,
            dueDate = null,
            transactionType = TransactionType.INCOME,
            customExchangeRateState = CustomExchangeRateState(),

            categories = emptyList(),
            accounts = emptyList(),

            onDueDateChanged = {},
            onCategoryChanged = {},
            onAccountChanged = {},
            onToAccountChanged = {},
            onDescriptionChanged = {},
            onTitleChanged = {},
            onAmountChanged = {},

            onCreateCategory = { },
            onEditCategory = {},
            onPayPlannedPayment = {},
            onSave = {},
            onSetHasChanges = {},
            onDelete = {},
            onCreateAccount = { },
            onSetDateTime = {},
            onSetTransactionType = {}
        )
    }
}