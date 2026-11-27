package com.example.rideautoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import kotlin.random.Random

class RideAutoClickerService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var isClicking = false
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null

    private val targetPackages = setOf(
        "com.rapido.rider",
        "com.ubercab.driver",
        "com.olacabs.oladriver",
        "com.bharattaxi.driver"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        setupFloatingWidget()
    }

    private fun setupFloatingWidget() {
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            floatingView = LayoutInflater.from(this).inflate(R.layout.floating_widget, null)

            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 50
                y = 200
            }

            val btnToggle = floatingView?.findViewById<Button>(R.id.btnFloatingToggle)
            val prefs = getSharedPreferences("AutoClickerPrefs", Context.MODE_PRIVATE)
            updateFloatingButtonUI(btnToggle, prefs.getBoolean("is_enabled", false))

            btnToggle?.setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f
                private var isClick = false

                override fun onTouch(v: View?, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            isClick = true
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val diffX = (event.rawX - initialTouchX).toInt()
                            val diffY = (event.rawY - initialTouchY).toInt()
                            if (Math.abs(diffX) > 10 || Math.abs(diffY) > 10) {
                                isClick = false
                                params.x = initialX + diffX
                                params.y = initialY + diffY
                                windowManager?.updateViewLayout(floatingView, params)
                            }
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (isClick) {
                                val currentState = prefs.getBoolean("is_enabled", false)
                                val newState = !currentState
                                prefs.edit().putBoolean("is_enabled", newState).apply()
                                updateFloatingButtonUI(btnToggle, newState)
                            }
                            return true
                        }
                    }
                    return false
                }
            })

            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            Log.e("AutoClicker", "Floating widget error: ${e.message}")
        }
    }

    private fun updateFloatingButtonUI(button: Button?, isEnabled: Boolean) {
        if (isEnabled) {
            button?.text = "ON"
            button?.setBackgroundColor(Color.parseColor("#38A169"))
        } else {
            button?.text = "OFF"
            button?.setBackgroundColor(Color.parseColor("#E53E3E"))
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return

        val prefs = getSharedPreferences("AutoClickerPrefs", Context.MODE_PRIVATE)
        val isServiceEnabled = prefs.getBoolean("is_enabled", false)
        if (!isServiceEnabled || isClicking) return

        if (targetPackages.contains(packageName)) {
            val rootNode = rootInActiveWindow ?: return
            val minFare = prefs.getInt("min_fare", 40)
            val maxDistance = prefs.getFloat("max_distance", 5.0f)

            scanScreenAndAccept(rootNode, minFare, maxDistance)
        }
    }

    private fun scanScreenAndAccept(rootNode: AccessibilityNodeInfo, minFare: Int, maxDistance: Float) {
        var foundValidFare = false
        var foundValidDistance = false
        var distanceFoundOnScreen = false
        var acceptButtonNode: AccessibilityNodeInfo? = null

        val nodeQueue = ArrayDeque<AccessibilityNodeInfo>()
        nodeQueue.add(rootNode)

        while (nodeQueue.isNotEmpty()) {
            val currentNode = nodeQueue.removeFirst()
            val nodeText = currentNode.text?.toString() ?: ""

            if (nodeText.contains("₹") || nodeText.contains("Rs", ignoreCase = true)) {
                val extractedAmount = extractNumber(nodeText).toInt()
                if (extractedAmount >= minFare) {
                    foundValidFare = true
                }
            }

            if (nodeText.contains("km", ignoreCase = true)) {
                distanceFoundOnScreen = true
                val extractedKm = extractNumber(nodeText)
                if (extractedKm in 0.1..maxDistance.toDouble()) {
                    foundValidDistance = true
                }
            } else if (nodeText.endsWith(" m", ignoreCase = true) || nodeText.contains("meters", ignoreCase = true)) {
                distanceFoundOnScreen = true
                if (maxDistance >= 1.0f) {
                    foundValidDistance = true
                }
            }

            if (nodeText.equals("Accept", ignoreCase = true) ||
                nodeText.equals("Confirm", ignoreCase = true) ||
                nodeText.contains("Accept Ride", ignoreCase = true) ||
                nodeText.contains("સ્વીકારો")
            ) {
                acceptButtonNode = currentNode
            }

            for (i in 0 until currentNode.childCount) {
                currentNode.getChild(i)?.let { nodeQueue.add(it) }
            }
        }

        val isDistanceOk = if (distanceFoundOnScreen) foundValidDistance else true

        if (foundValidFare && isDistanceOk && acceptButtonNode != null) {
            triggerClick(acceptButtonNode)
        }
    }

    private fun extractNumber(text: String): Double {
        return try {
            val match = Regex("[0-9]+(\\.[0-9]+)?").find(text)
            match?.value?.toDouble() ?: 0.0
        } catch (e: Exception) {
            0.0
        }
    }

    private fun triggerClick(node: AccessibilityNodeInfo) {
        isClicking = true
        val delay = Random.nextLong(300, 750)

        handler.postDelayed({
            try {
                if (node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } else {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    performGestureClick(rect.centerX().toFloat(), rect.centerY().toFloat())
                }
            } catch (e: Exception) {
                Log.e("AutoClicker", "Click failed: ${e.message}")
            } finally {
                handler.postDelayed({ isClicking = false }, 2000)
            }
        }, delay)
    }

    private fun performGestureClick(x: Float, y: Float) {
        val clickPath = Path().apply {
            moveTo(x, y)
        }
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(clickPath, 0, 50))
        dispatchGesture(gestureBuilder.build(), null, null)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (floatingView != null) {
            windowManager?.removeView(floatingView)
        }
    }
}
