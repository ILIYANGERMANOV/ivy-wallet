package com.ivy.wallet.ui.paywall

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.Purchase
import com.ivy.wallet.analytics.IvyAnalytics
import com.ivy.wallet.base.asLiveData
import com.ivy.wallet.base.ioThread
import com.ivy.wallet.base.sendToCrashlytics
import com.ivy.wallet.billing.IvyBilling
import com.ivy.wallet.billing.Plan
import com.ivy.wallet.billing.PlanType
import com.ivy.wallet.logic.PaywallLogic
import com.ivy.wallet.model.analytics.AnalyticsEvent
import com.ivy.wallet.model.entity.Account
import com.ivy.wallet.model.entity.Budget
import com.ivy.wallet.model.entity.Category
import com.ivy.wallet.model.entity.Loan
import com.ivy.wallet.persistence.dao.AccountDao
import com.ivy.wallet.persistence.dao.BudgetDao
import com.ivy.wallet.persistence.dao.CategoryDao
import com.ivy.wallet.persistence.dao.LoanDao
import com.ivy.wallet.ui.IvyActivity
import com.ivy.wallet.ui.Paywall
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val ivyBilling: IvyBilling,
    private val paywallLogic: PaywallLogic,
    private val ivyAnalytics: IvyAnalytics,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
    private val budgetDao: BudgetDao,
    private val loanDao: LoanDao
) : ViewModel() {

    private val _plans = MutableLiveData<List<Plan>>()
    val plans = _plans.asLiveData()

    private val _accounts = MutableLiveData<List<Account>>()
    val accounts = _accounts.asLiveData()

    private val _categories = MutableLiveData<List<Category>>()
    val categories = _categories.asLiveData()

    private val _budgets = MutableLiveData<List<Budget>>()
    val budgets = _budgets.asLiveData()

    private val _loans = MutableLiveData<List<Loan>>()
    val loans = _loans.asLiveData()

    private val _purchasedSkus = MutableLiveData<List<String>>(emptyList())
    val purchasedSkus = _purchasedSkus.asLiveData()

    private val _paywallReason = MutableLiveData<PaywallReason?>()
    val paywallReason = _paywallReason.asLiveData()

    private val activePurchases = mutableListOf<Purchase>()

    fun start(
        screen: Paywall,
        activity: IvyActivity
    ) {
        _paywallReason.value = screen.paywallReason

        ivyBilling.init(
            activity = activity,
            onReady = {
                viewModelScope.launch {
                    _plans.value = ivyBilling
                        .fetchPlans()
                        .filter { it.type != PlanType.SIX_MONTH }
                    processPurchases(ivyBilling.queryPurchases())
                }
            },
            onPurchases = { purchases ->
                viewModelScope.launch {
                    processPurchases(purchases)
                }
            },
            onError = { code, msg ->
                sendToCrashlytics("Paywall Billing error: code=$code: $msg")
                Timber.e("Billing error code=$code: $msg")
            }
        )

        viewModelScope.launch {
            _categories.value = ioThread { categoryDao.findAll() }!!
            _accounts.value = ioThread { accountDao.findAll() }!!
            _budgets.value = ioThread { budgetDao.findAll() }!!
            _loans.value = ioThread { loanDao.findAll() }!!

            ivyAnalytics.logEvent(
                when (screen.paywallReason) {
                    PaywallReason.CATEGORIES -> AnalyticsEvent.PAYWALL_CATEGORIES
                    PaywallReason.ACCOUNTS -> AnalyticsEvent.PAYWALL_ACCOUNTS
                    PaywallReason.EXPORT_CSV -> AnalyticsEvent.PAYWALL_EXPORT_CSV
                    PaywallReason.PREMIUM_COLOR -> AnalyticsEvent.PAYWALL_PREMIUM_COLOR
                    PaywallReason.BUDGETS -> AnalyticsEvent.PAYWALL_BUDGETS
                    PaywallReason.LOANS -> AnalyticsEvent.PAYWALL_LOANS
                    null -> AnalyticsEvent.PAYWALL_NO_REASON
                }
            )
        }
    }

    private suspend fun processPurchases(purchases: List<Purchase>) {
        _purchasedSkus.value = emptyList()
        activePurchases.clear()

        paywallLogic.processPurchases(
            purchases = purchases,
            onActivePurchase = {
                _purchasedSkus.value = purchasedSkus.value.orEmpty().plus(it.skus)
                activePurchases.add(it)

                viewModelScope.launch {
                    ivyAnalytics.logEvent(AnalyticsEvent.PAYWALL_ACTIVE_PREMIUM)
                }
            }
        )
    }

    fun onPlanSelected(plan: Plan?) {
        if (plan != null) {
            viewModelScope.launch {
                val chooseSpecificPlanEvent = when (plan.type) {
                    PlanType.MONTHLY -> AnalyticsEvent.PAYWALL_CHOOSE_PLAN_MONTHLY
                    PlanType.SIX_MONTH -> AnalyticsEvent.PAYWALL_CHOOSE_PLAN_6MONTH
                    PlanType.YEARLY -> AnalyticsEvent.PAYWALL_CHOOSE_PLAN_YEARLY
                    PlanType.LIFETIME -> AnalyticsEvent.PAYWALL_CHOOSE_PLAN_LIFETIME
                }

                ivyAnalytics.logEvent(chooseSpecificPlanEvent)
                ivyAnalytics.logEvent(AnalyticsEvent.PAYWALL_CHOOSE_PLAN)
            }
        }
    }

    fun buy(activity: IvyActivity, plan: Plan) {
        ivyBilling.buy(
            activity = activity,
            skuToBuy = plan.skuDetails,
            oldSubscriptionPurchaseToken = activePurchases
                .firstOrNull { !it.originalJson.contains("lifetime") }
                ?.purchaseToken
        )

        viewModelScope.launch {
            val buySpecificPlanEvent = when (plan.type) {
                PlanType.MONTHLY -> AnalyticsEvent.PAYWALL_START_BUY_MONTHLY
                PlanType.SIX_MONTH -> AnalyticsEvent.PAYWALL_START_BUY_6MONTH
                PlanType.YEARLY -> AnalyticsEvent.PAYWALL_START_BUY_YEARLY
                PlanType.LIFETIME -> AnalyticsEvent.PAYWALL_START_BUY_LIFETIME
            }

            ivyAnalytics.logEvent(buySpecificPlanEvent)
            ivyAnalytics.logEvent(AnalyticsEvent.PAYWALL_START_BUY)
        }
    }
}