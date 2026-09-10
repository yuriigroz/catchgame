package com.example.catchgame

import android.app.Activity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Менеджер внутриигровых покупок (Google Play Billing).
 *
 * Продукты (ID должны совпадать с Play Console):
 *  - continue_token  — расходуемый товар (продолжение после проигрыша)
 *  - golden_basket   — одноразовая покупка (скин корзины)
 *  - double_score    — одноразовая покупка (удвоение очков)
 */
class BillingManager(
    private val activity: Activity,
    private val onEntitlement: (productId: String, granted: Boolean) -> Unit
) : PurchasesUpdatedListener {

    private val client: BillingClient = BillingClient.newBuilder(activity)
        .enablePendingPurchases()
        .setListener(this)
        .build()

    private val productIds = listOf("continue_token", "golden_basket", "double_score")
    private val consumables = setOf("continue_token")
    private var products: List<ProductDetails> = emptyList()

    fun start() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    loadProducts()
                    restorePurchases()   // восстановить уже купленное
                }
            }
            override fun onBillingServiceDisconnected() {
                // BillingClient автоматически пытается переподключиться
            }
        })
    }

    private fun loadProducts() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                productIds.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                }
            ).build()

        client.queryProductDetailsAsync(params, ProductDetailsResponseListener { result ->
            if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                products = result.productDetailsList
            }
        })
    }

    /** Список товаров для магазина: productId -> "Название — цена" */
    fun getProducts(): List<Pair<String, String>> =
        products.mapNotNull { p ->
            val price = p.oneTimePurchaseOfferDetails?.formattedPrice ?: return@mapNotNull null
            p.productId to "${p.name} — $price"
        }

    fun purchase(productId: String) {
        val details = products.find { it.productId == productId } ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            ).build()
        client.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { handlePurchase(it) }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        for (id in purchase.products) {
            if (consumables.contains(id)) {
                // Расходуемый товар: сначала потребляем, потом выдаём награду
                val params = ConsumeParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                client.consumeAsync(params) { _, _ -> onEntitlement(id, true) }
            } else {
                // Одноразовый товар: подтверждаем покупку (обязательно в течение 3 дней!)
                if (!purchase.isAcknowledged) {
                    val params = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    client.acknowledgePurchase(params) { onEntitlement(id, true) }
                } else {
                    onEntitlement(id, true)
                }
            }
        }
    }

    /** Восстановление покупок при запуске приложения */
    fun restorePurchases() {
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { _, purchases -> purchases.forEach { handlePurchase(it) } }
    }

    fun end() = client.endConnection()
}
