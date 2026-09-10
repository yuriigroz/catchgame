package com.example.catchgame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.random.Random

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    // ==== Состояния, которые выставляются через внутриигровые покупки ====
    var goldenBasket = false   // золотая корзина (одноразовая покупка)
    var doubleScore = false    // удвоение очков (одноразовая покупка)
    var continuesLeft = 0      // продолжения после проигрыша (расходуемый товар)

    // =====================================================================

    private var score = 0
    private var lives = 3
    private var gameOver = false
    private var basketX = 0f
    private var thread: GameThread? = null

    private val prefs = context.getSharedPreferences("game", Context.MODE_PRIVATE)
    private var highScore = prefs.getInt("high_score", 0)

    private val items = mutableListOf<Item>()
    private var frame = 0

    private data class Item(var x: Float, var y: Float, val vy: Float, val isBomb: Boolean)

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(h: SurfaceHolder) {
        basketX = width / 2f
        thread = GameThread().also { it.start() }
    }

    override fun surfaceChanged(h: SurfaceHolder, format: Int, w: Int, height: Int) {}

    override fun surfaceDestroyed(h: SurfaceHolder) {
        thread?.stopGame()
        thread = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (gameOver) restartOrContinue() else basketX = event.x
            }
            MotionEvent.ACTION_MOVE -> if (!gameOver) basketX = event.x
        }
        return true
    }

    private fun restartOrContinue() {
        if (continuesLeft > 0) {
            continuesLeft--      // тратим купленное продолжение
            lives = 3
        } else {
            score = 0
            lives = 3
            items.clear()
            frame = 0
        }
        gameOver = false
    }

    private fun update() {
        if (gameOver) return
        frame++

        // Чем дольше игра — тем быстрее и чаще падают предметы
        val spawnEvery = (28 - frame / 200).coerceAtLeast(10)
        val speed = 8f + frame / 400f
        if (frame % spawnEvery == 0) {
            items.add(
                Item(
                    x = Random.nextInt(60, (width - 60).coerceAtLeast(61)).toFloat(),
                    y = -40f,
                    vy = speed + Random.nextFloat() * 4f,
                    isBomb = Random.nextInt(100) < 18   // 18% бомб
                )
            )
        }

        val basketTop = height - 200f
        val basketHalf = 130f
        val iterator = items.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            item.y += item.vy
            val caught = item.y in basketTop..(basketTop + 60f) &&
                    item.x > basketX - basketHalf && item.x < basketX + basketHalf
            if (caught) {
                if (item.isBomb) lives-- else score += if (doubleScore) 2 else 1
                iterator.remove()
            } else if (item.y > height + 40f) {
                iterator.remove()
            }
        }

        if (lives <= 0) {
            gameOver = true
            if (score > highScore) {
                highScore = score
                prefs.edit().putInt("high_score", highScore).apply()
            }
        }
    }

    private fun draw() {
        val canvas: Canvas = holder.lockCanvas() ?: return
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Фон
        canvas.drawColor(Color.rgb(15, 23, 42))

        // Корзина
        val basketColor = if (goldenBasket) Color.rgb(255, 193, 7) else Color.rgb(45, 212, 191)
        p.color = basketColor
        canvas.drawRoundRect(
            RectF(basketX - 130f, height - 200f, basketX + 130f, height - 140f),
            24f, 24f, p
        )

        // Падающие предметы
        for (item in items) {
            if (item.isBomb) {
                p.color = Color.rgb(71, 85, 105)
                canvas.drawCircle(item.x, item.y, 24f, p)
                p.color = Color.rgb(239, 68, 68)
                canvas.drawCircle(item.x + 10f, item.y - 24f, 6f, p) // искра фитиля
            } else {
                p.color = Color.rgb(250, 204, 21)  // монета
                canvas.drawCircle(item.x, item.y, 22f, p)
                p.color = Color.rgb(254, 249, 195)
                canvas.drawCircle(item.x - 6f, item.y - 6f, 7f, p)
            }
        }

        // Текст
        p.color = Color.WHITE
        p.textSize = 52f
        p.textAlign = Paint.Align.LEFT
        canvas.drawText("Очки: $score", 32f, 90f, p)
        p.textSize = 40f
        canvas.drawText("Рекорд: $highScore", 32f, 145f, p)

        p.textAlign = Paint.Align.RIGHT
        canvas.drawText("Жизни: " + "❤".repeat(lives.coerceAtLeast(0)), width - 32f, 90f, p)
        if (continuesLeft > 0) {
            canvas.drawText("Продолжений: $continuesLeft", width - 32f, 145f, p)
        }

        if (gameOver) {
            canvas.drawColor(Color.argb(160, 0, 0, 0))
            p.textAlign = Paint.Align.CENTER
            p.textSize = 80f
            canvas.drawText("Игра окончена", width / 2f, height / 2f - 120f, p)
            p.textSize = 56f
            canvas.drawText("Счёт: $score", width / 2f, height / 2f - 30f, p)
            p.textSize = 44f
            val hint = if (continuesLeft > 0)
                "Коснись — использовать продолжение"
            else
                "Коснись, чтобы начать заново"
            canvas.drawText(hint, width / 2f, height / 2f + 60f, p)
        }

        holder.unlockCanvasAndPost(canvas)
    }

    private inner class GameThread : Thread() {
        @Volatile private var running = true

        fun stopGame() { running = false }

        override fun run() {
            while (running) {
                update()
                draw()
                SystemClock.sleep(16)   // ~60 FPS
            }
        }
    }
}
