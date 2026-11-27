package com.isabgroup.pimsg.data

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

class PlayBilling(
    context: Context,
    private val onProducts: (Map<String, ProductDetails>) -> Unit,
    private val onMessage: (String) -> Unit,
    private val onVerified: () -> Unit,
) : PurchasesUpdatedListener {
    companion object {
        const val PLUS_MONTHLY = "pimsg_plus_monthly"
        const val PLUS_YEARLY = "pimsg_plus_yearly"
        const val WEEKLY = "pimsg_pro_weekly"
        const val MONTHLY = "pimsg_pro_monthly"
        const val PRO_YEARLY = "pimsg_pro_yearly"
        const val BUSINESS_MONTHLY = "pimsg_business_monthly"
        const val BUSINESS_YEARLY = "pimsg_business_yearly"
        val PRODUCT_IDS = listOf(PLUS_MONTHLY, PLUS_YEARLY, WEEKLY, MONTHLY, PRO_YEARLY, BUSINESS_MONTHLY, BUSINESS_YEARLY)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun connect() {
        if (client.isReady) {
            loadProducts()
            syncPurchases()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: com.android.billingclient.api.BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    loadProducts()
                    syncPurchases()
                } else {
                    onMessage("A Google Play não está disponível neste momento.")
                }
            }
            override fun onBillingServiceDisconnected() = Unit
        })
    }

    private fun loadProducts() {
        val products = PRODUCT_IDS.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        client.queryProductDetailsAsync(params) { result, detailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                onProducts(detailsResult.productDetailsList.associateBy { it.productId })
            } else {
                onMessage("Não foi possível carregar os preços da Google Play. Confirma se os produtos estão publicados.")
            }
        }
    }

    fun buy(activity: Activity, product: ProductDetails) {
        val offer = product.subscriptionOfferDetails?.firstOrNull()
        if (offer == null) {
            onMessage("Não foi possível abrir esta opção de compra. Tenta novamente mais tarde.")
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
            .setOfferToken(offer.offerToken)
            .build()
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK &&
            result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            onMessage(result.debugMessage.ifBlank { "Não foi possível iniciar a compra." })
        }
    }

    fun restorePurchases() {
        if (client.isReady) syncPurchases() else connect()
    }

    private fun syncPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach(::processPurchase)
            }
        }
    }

    override fun onPurchasesUpdated(
        result: com.android.billingclient.api.BillingResult,
        purchases: List<Purchase>?,
    ) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach(::processPurchase)
            BillingClient.BillingResponseCode.USER_CANCELED -> onMessage("Compra cancelada.")
            else -> onMessage(result.debugMessage.ifBlank { "A compra não foi concluída." })
        }
    }

    private fun processPurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            onMessage("A compra está pendente de confirmação da Google Play.")
            return
        }
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val productId = purchase.products.firstOrNull { it in PRODUCT_IDS } ?: return
        scope.launch {
            try {
                Api.post(
                    "/v1/billing/verify",
                    JSONObject()
                        .put("purchase_token", purchase.purchaseToken)
                        .put("product_id", productId),
                )
                onVerified()
                onMessage("Subscrição confirmada.")
            } catch (e: Exception) {
                onMessage("A Google Play confirmou a compra, mas o servidor ainda não conseguiu validá-la. Não repitas o pagamento; tenta restaurar a compra mais tarde.")
            }
        }
    }

    fun close() {
        if (client.isReady) client.endConnection()
    }
}
