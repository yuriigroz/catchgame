package com.example.catchgame

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var gameView: GameView
    private lateinit var billing: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        gameView = GameView(this)

        val shopButton = Button(this).apply {
            text = "Магазин"
            setOnClickListener { showShop() }
        }

        val layout = FrameLayout(this).apply {
            addView(gameView)
            addView(shopButton, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(24, 72, 0, 0) })
        }
        setContentView(layout)

        // Колбэк: выдаём покупку в игру
        billing = BillingManager(this) { productId, granted ->
            if (!granted) return@BillingManager
            when (productId) {
                "golden_basket" -> {
                    gameView.goldenBasket = true
                    toast("Золотая корзина активирована!")
                }
                "double_score" -> {
                    gameView.doubleScore = true
                    toast("Очки будут удваиваться!")
                }
                "continue_token" -> {
                    gameView.continuesLeft++
                    toast("Продолжение добавлено! Всего: ${gameView.continuesLeft}")
                }
            }
        }
        billing.start()
    }

    private fun showShop() {
        val products = billing.getProducts()
        if (products.isEmpty()) {
            toast("Товары ещё загружаются… проверь интернет")
            return
        }
        val names = products.map { it.second }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Магазин")
            .setItems(names) { _, which -> billing.purchase(products[which].first) }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        billing.end()
        super.onDestroy()
    }
}
