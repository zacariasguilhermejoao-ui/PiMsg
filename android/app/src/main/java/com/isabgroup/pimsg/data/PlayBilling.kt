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
import com.android.billingclient.api.UnfetchedProduct
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
                    onProducts(emptyMap())
                    onMessage("A Google Play não está disponível neste momento (código ${result.responseCode}).")
                }
            }
            override fun onBillingServiceDisconnected() {
                onProducts(emptyMap())
                onMessage("A ligação à Google Play foi interrompida. Tenta carregar os planos novamente.")
            }
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
            if (result.responseCode == BillingClient.BillingResponseCode.OK || result.responseCode == BillingClient.BillingResponseCode.ITEM_UNAVAILABLE) {
                val fetched = detailsResult.productDetailsList
                onProducts(fetched.associateBy { it.productId })
                val missing = detailsResult.unfetchedProductList
                if (fetched.isEmpty()) {
                    val reasons = missing.groupBy { it.statusCode }.entries.joinToString("; ") { (code, items) ->
                        val reason = when (code) {
                            UnfetchedProduct.StatusCode.INVALID_PRODUCT_ID_FORMAT -> "ID inválido"
                            UnfetchedProduct.StatusCode.PRODUCT_NOT_FOUND -> "produto não encontrado ou ainda não activo"
                            UnfetchedProduct.StatusCode.NO_ELIGIBLE_OFFER -> "produto sem plano-base/oferta activa"
                            else -> "motivo não identificado"
                        }
                        "$reason: ${items.joinToString { it.productId }}"
                    }
                    onMessage(
                        if (reasons.isNotBlank()) "A Google Play não devolveu os planos do PiMsg ($reasons). Confirma os IDs e activa os planos-base na Play Console; instala a app por uma faixa de teste da Google Play."
                        else "A Google Play não devolveu nenhum plano. Confirma que os produtos estão activos e que esta instalação veio de uma faixa de teste da Google Play."
                    )
                } else if (missing.isNotEmpty()) {
                    onMessage("${missing.size} opção(ões) de subscrição não foram encontradas na Google Play. Os planos disponíveis continuam visíveis.")
                }
            } else {
                onProducts(emptyMap())
                onMessage("Não foi possível carregar os preços da Google Play (código ${result.responseCode}: ${result.debugMessage}).")
            }
        }
    }

    fun buy(activity: Activity, product: ProductDetails) {
        if (!client.isReady) {
            onMessage("A ligação à Google Play ainda não está pronta. Tenta novamente.")
            connect()
            return
        }
        val query = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        client.queryPurchasesAsync(query) { queryResult, purchases ->
            if (queryResult.responseCode != BillingClient.BillingResponseCode.OK) {
                onMessage("Não foi possível confirmar a subscrição actual na Google Play.")
                return@queryPurchasesAsync
            }
            val active = purchases.filter { purchase ->
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    purchase.products.any { it in PRODUCT_IDS }
            }
            when {
                active.size > 1 -> onMessage("Encontrámos mais de uma subscrição activa. Gere-as na Google Play antes de iniciar outra.")
                active.size == 1 -> {
                    val oldPurchase = active.first()
                    val oldProductId = oldPurchase.products.firstOrNull { it in PRODUCT_IDS }
                    if (oldProductId == null) {
                        onMessage("Não foi possível identificar o plano actual. Gere a subscrição na Google Play.")
                        return@queryPurchasesAsync
                    }
                    if (oldProductId == product.productId) {
                        onMessage("Este plano já está activo na tua conta.")
                        return@queryPurchasesAsync
                    }
                    launchPurchase(activity, product, oldPurchase, oldProductId)
                }
                else -> launchPurchase(activity, product, null, null)
            }
        }
    }

    private fun tierRank(productId: String): Int = when {
        productId.startsWith("pimsg_business_") -> 3
        productId.startsWith("pimsg_pro_") -> 2
        productId.startsWith("pimsg_plus_") -> 1
        else -> 0
    }

    private fun launchPurchase(
        activity: Activity,
        product: ProductDetails,
        oldPurchase: Purchase?,
        oldProductId: String?,
    ) {
        val offer = product.subscriptionOfferDetails?.firstOrNull()
        if (offer == null) {
            onMessage("Não foi possível abrir esta opção de compra. Tenta novamente mais tarde.")
            return
        }
        val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
            .setOfferToken(offer.offerToken)

        val productParams = productParamsBuilder.build()

        val flowBuilder = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
        if (oldPurchase != null) {
            val replacementMode =
                if (tierRank(product.productId) > tierRank(oldProductId.orEmpty())) {
                    BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.CHARGE_PRORATED_PRICE
                } else {
                    BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.DEFERRED
                }
            flowBuilder.setSubscriptionUpdateParams(
                BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                    .setOldPurchaseToken(oldPurchase.purchaseToken)
                    .setSubscriptionReplacementMode(replacementMode)
                    .build()
            )
        }

        val result = client.launchBillingFlow(activity, flowBuilder.build())
        if (result.responseCode != BillingClient.BillingResponseCode.OK &&
            result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            onMessage(result.debugMessage.ifBlank { "Não foi possível iniciar a compra." })
        }
    }

    fun refreshProducts() {
        if (client.isReady) loadProducts() else connect()
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
