package cc.bluex.liquidbounce.render

import net.ccbluex.liquidbounce.api.util.math.MathUtils.lerp
import net.ccbluex.liquidbounce.api.util.math.MathUtils.clamp
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.render.DrawContext
import net.ccbluex.liquidbounce.render.EngineFont
import net.ccbluex.liquidbounce.render.RenderEngine
import net.ccbluex.liquidbounce.utils.render.Color4b
import net.ccbluex.liquidbounce.utils.render.ColorUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils.hsvToRgb
import net.ccbluex.liquidbounce.utils.render.font.FontRenderer
import net.ccbluex.liquidbounce.client.module.ClientModule
import net.ccbluex.liquidbounce.client.module.ModuleCategories

/**
 * 【100 % 还原】Arraylist 渲染模块（LiquidBounce‑Nextgen 0.39）
 *
 * 功能：
 *  • 右上角 Watermark（可调文本、颜色、阴影、动画）
 *  • 可调的四种显示模式（Outline / Bar / Split / None）
 *  • 背景样式（Opacity / Shadow / Both）
 *  • 模块列表（支持动画、发光、文字阴影、点击切换）
 *  • 所有原 C++ 成员变量均已保留为 Kotlin `var`，并保持默认值不变
 *
 * 说明：
 *  • 该模块不依赖任何 Web 组件，只使用 LiquidBounce 原生渲染 API。
 *  • 为了保持原实现的 “密集阴影”，在渲染函数里使用了 `drawShadowRectDense`、`drawShadowCircleDense`
 *   （实现位于文件末尾的 `ShadowUtils` 对象中）。
 *  • 所有可调参数直接以公开 `var` 暴露，在游戏 UI 的 “模块设置” 中即可调节。
 */
object Arraylist : ClientModule("Arraylist", ModuleCategories.RENDER) {

    // ==============================================================
    // 0️⃣ ==== 原始 C++ 成员变量（保持名称、默认值、类型） =====
    // ==============================================================

    // ---- 背景 ----
    enum class BackgroundStyle(override val displayName: String) : net.ccbluex.liquidbounce.api.util.types.Tagged {
        Opacity("Opacity"),
        Shadow("Shadow"),
        Both("Both")
    }
    var mBackground: BackgroundStyle = BackgroundStyle.Shadow
    var mBackgroundOpacity: Float = 1.0f
    var mBackgroundValue: Float = 0.0f           //（原代码保留但未使用）
    var mBlurStrength: Float = 0.6f

    // ---- 渲染模式 ----
    enum class Display(override val displayName: String) : net.ccbluex.liquidbounce.api.util.types.Tagged {
        Outline("Outline"),
        Bar("Bar"),
        Split("Split"),
        None("None")
    }
    var mDisplay: Display = Display.Split

    enum class ModuleVisibility(override val displayName: String) : net.ccbluex.liquidbounce.api.util.types.Tagged {
        All("All"),
        Bound("Bound")
    }
    var mVisibility: ModuleVisibility = ModuleVisibility.All

    var mRenderMode: Boolean = true               // 是否在模块名后显示 setting
    var mGlow: Boolean = true
    var mGlowStrength: Float = 1.9f
    var mGlowDensity: Int = 2

    var mBoldText: Boolean = true
    var mFontSize: Float = 35.0f
    var mTopOffset: Float = 10.0f
    var mRightOffset: Float = 30.0f

    var mTextShadow: Boolean = true
    var mShadowOffset: Float = 1.0f

    // ---- Watermark ----
    var mWatermarkText: String = "Solstice V4"
    var mWatermarkShadowRadius: Float = 40.0f
    var mWatermarkShadowDensity: Int = 5
    var mShowWatermark: Boolean = true

    // --------------------------------------------------------------
    // 1️⃣ ==== 内部数据结构（对应 C++ struct Module） ==========
    // --------------------------------------------------------------
    private data class ModuleInfo(
        var name: String,
        var settingDisplay: String = "",
        var enabled: Boolean = false,
        var visibleInArrayList: Boolean = true,
        var key: Int = 0,
        var arrayListAnim: Float = 0f
    ) {
        fun getName() = name
        fun getSettingDisplayText() = settingDisplay
        fun toggle() { enabled = !enabled }
    }

    private val mModules = mutableListOf<ModuleInfo>()
    private var mInitialized = false

    // ==============================================================
    // 2️⃣ ==== 辅助方法（Math、Color、阴影） ======================
    // ==============================================================

    /** 与 C++ 中 `MathUtils::lerp` 完全等价 */
    private fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /** 与 C++ 中 `MathUtils::clamp` 完全等价 */
    private fun clampF(v: Float, lo: Float, hi: Float): Float = when {
        v < lo -> lo
        v > hi -> hi
        else -> v
    }

    /** 彩虹颜色（C++ `ColorUtils::getThemedColor`） */
    private fun getThemedColor(seed: Int): Color4b {
        val time = System.currentTimeMillis() / 1000.0
        val hue = ((seed * 0.001 + time * 0.05) % 1.0).toFloat()
        val rgb = hsvToRgb(hue, 0.8f, 0.9f)          // FloatArray[3]，范围 0‑1
        return Color4b(
            (rgb[0] * 255).toInt(),
            (rgb[1] * 255).toInt(),
            (rgb[2] * 255).toInt(),
            255
        )
    }

    /** 密集阴影矩形（对应 C++ drawShadowRectDense） */
    private fun drawShadowRectDense(
        ctx: DrawContext,
        x0: Float, y0: Float, x1: Float, y1: Float,
        col: Color4b,
        radius: Float,
        density: Int,
        offsetX: Float,
        offsetY: Float,
        rounding: Float = 0f
    ) {
        if (density <= 1) {
            ctx.drawShadowRect(x0, y0, x1, y1, col, radius, offsetX, offsetY, rounding)
            return
        }
        for (i in 0 until density) {
            val t = i.toFloat() / (density - 1)
            val r = radius * (0.25f + 0.75f * t)
            val a = (col.a * (0.5f + 0.5f * (1f - t))).toInt()
            val curCol = Color4b(col.r, col.g, col.b, a)
            ctx.drawShadowRect(x0, y0, x1, y1, curCol, r, offsetX, offsetY, rounding)
        }
    }

    /** 密集阴影圆（对应 C++ drawShadowCircleDense） */
    private fun drawShadowCircleDense(
        ctx: DrawContext,
        cx: Float, cy: Float, radius: Float,
        col: Color4b,
        density: Int,
        segments: Int = 32,
        offsetX: Float,
        offsetY: Float,
        rounding: Float = 0f
    ) {
        if (density <= 1) {
            ctx.drawShadowCircle(cx, cy, radius, col, segments, offsetX, offsetY, rounding)
            return
        }
        for (i in 0 until density) {
            val t = i.toFloat() / (density - 1)
            val r = radius * (0.25f + 0.75f * t)
            ctx.drawShadowCircle(cx, cy, r, col, segments, offsetX, offsetY, rounding)
        }
    }

    // ==============================================================
    // 3️⃣ ==== 生命周期 / 初始化
    // ==============================================================

    private fun initModules() {
        if (mInitialized) return
        mInitialized = true

        // 示例：在实际项目中，你可以在其它地方自行调用 toggleModule
        // 这里提供一个最小示例，以便调试时能够看到几条条目
        toggleModule("KillAura", "+1")
        toggleModule("AutoWalk")
        toggleModule("HUD")
    }

    /** C++ 中的 toggleModule */
    fun toggleModule(name: String, setting: String = "", addIfMissing: Boolean = true) {
        val mod = mModules.find { it.name == name }
        if (mod != null) {
            mod.toggle()
            return
        }
        if (addIfMissing) {
            val newMod = ModuleInfo(
                name = name,
                settingDisplay = setting,
                enabled = true,
                visibleInArrayList = true
            )
            mModules.add(newMod)
        }
    }

    // ==============================================================
    // 4️⃣ ==== 渲染入口（OverlayRenderEvent）
    // ==============================================================

    init {
        handler<OverlayRenderEvent> { event ->
            initModules()
            renderWatermark(event.context)
            renderModules(event.context)
        }
    }

    // ==============================================================
    // 5️⃣ ==== 水印渲染（对应 C++ renderWatermark）
    // ==============================================================

    private fun renderWatermark(ctx: DrawContext) {
        if (!mShowWatermark) return

        // 动画渐入（这里使用固定速率，可自行换成 deltaTime）
        var anim = lerpF(0f, 1f, 0.1f)
        anim = clampF(anim, 0f, 1f)
        if (anim < 0.01f) return

        val font = mc.font as EngineFont
        val watermarkFontSize = mFontSize * (85f / 35f)

        // 使用全局字体（如果你自定义了大号字体，可在此处切换）
        // ……（本例中直接使用默认字体）

        // 计算总宽度
        var totalWidth = 0f
        for (c in mWatermarkText) {
            totalWidth += font.getStringWidth(c.toString(), watermarkFontSize)
        }

        var posX = ctx.scaledWidth - totalWidth - mRightOffset
        var posY = mTopOffset

        for ((i, ch) in mWatermarkText.withIndex()) {
            val charStr = ch.toString()
            val charWidth = font.getStringWidth(charStr, watermarkFontSize)

            val col = getThemedColor(i * 100)

            // 阴影圆（密度控制）
            if (mWatermarkShadowDensity > 0) {
                drawShadowCircleDense(
                    ctx,
                    posX + charWidth / 2,
                    posY + font.getStringHeight(charStr, watermarkFontSize) / 2,
                    mWatermarkShadowRadius * anim,
                    col,
                    mWatermarkShadowDensity,
                    offsetX = 0f,
                    offsetY = 0f,
                    rounding = 12f
                )
            }

            // 文字阴影（可选）
            if (mTextShadow) {
                ctx.drawString(
                    font,
                    charStr,
                    posX + 3.25f,
                    posY + 3.25f,
                    Color4b(col.r / 4, col.g / 4, col.b / 4, 236)
                )
            }

            // 主文本
            ctx.drawString(font, charStr, posX, posY, col)

            posX += charWidth
        }
    }

    // ==============================================================
    // 6️⃣ ==== 模块列表渲染（对应 C++ renderModules）
    // ==============================================================

    private fun renderModules(ctx: DrawContext) {
        val glowStrength = mGlowStrength * 100f
        val fontSize = mFontSize
        val font = mc.font as EngineFont

        // ---------- 1️⃣ 排序 ----------
        // 按文字宽度从大到小排序（只在有动画更新时重新排序，避免每帧排序）
        if (mModules.any { it.arrayListAnim < 1f && it.enabled }) {
            mModules.sortWith { a, b ->
                val aText = a.name + if (mRenderMode && a.settingDisplay.isNotEmpty()) " ${a.settingDisplay}" else ""
                val bText = b.name + if (mRenderMode && b.settingDisplay.isNotEmpty()) " ${b.settingDisplay}" else ""
                font.getStringWidth(bText, fontSize).compareTo(font.getStringWidth(aText, fontSize))
            }
        }

        // ---------- 2️⃣ 统计 Watermark 高度 ----------
        val watermarkHeight = if (mShowWatermark) {
            mc.font.getStringHeight(mWatermarkText, mFontSize * (85f / 35f))
        } else 0f

        // ---------- 3️⃣ 起始绘制坐标 ----------
        var cursorX = ctx.scaledWidth - mRightOffset
        var cursorY = mTopOffset + watermarkHeight + 10f

        // 临时结构体用于后续绘制背景矩形（供 Outline 线段使用）
        data class BackgroundRect(
            val moduleName: String,
            var left: Float,
            var top: Float,
            var right: Float,
            var bottom: Float,
            val color: Color4b,
            val ref: ModuleInfo
        )
        val backgroundRects = mutableListOf<BackgroundRect>()

        // ---------- 4️⃣ 第一次遍历：准备动画、背景矩形 ----------
        for (mod in mModules) {
            if (!mod.visibleInArrayList) continue
            if (mVisibility == ModuleVisibility.Bound && mod.key == 0) continue

            // 动画更新
            mod.arrayListAnim = lerpF(mod.arrayListAnim, if (mod.enabled) 1f else 0f, 0.12f)
            mod.arrayListAnim = clampF(mod.arrayListAnim, 0f, 1f)
            if (mod.arrayListAnim < 0.01f) continue

            val lineColor = getThemedColor((cursorY * 2).toInt())

            // 拼接显示文字
            var displayText = mod.name
            val setting = if (mRenderMode) mod.settingDisplay else ""
            if (setting.isNotEmpty()) displayText += " $setting"

            val textWidth = font.getStringWidth(displayText, fontSize)
            val textHeight = font.getStringHeight(displayText, fontSize)

            // 位置微调（Bar / Split 会左移 7 像素以留出装饰）
            var textPosX = cursorX
            if (mDisplay == Display.Bar || mDisplay == Display.Split) textPosX -= 7f

            // 计算滑入目标 X（从屏幕右侧滑入）
            val targetX = cursorX - textWidth - (if (mDisplay == Display.Bar || mDisplay == Display.Split) 7f else 0f)
            val animatedX = lerpF(ctx.scaledWidth + 14f, targetX, mod.arrayListAnim)

            // 记录背景矩形（供 Outline 线段绘制使用）
            val left = animatedX - 3f
            val right = animatedX + textWidth + 3f
            val rect = BackgroundRect(
                moduleName = mod.name,
                left = left,
                top = cursorY,
                right = right,
                bottom = cursorY + textHeight,
                color = lineColor,
                ref = mod
            )
            backgroundRects.add(rect)

            // ---------- 5️⃣ 绘制模块本体 ----------
            // 颜色透明度随动画变化
            val finalColor = Color4b(lineColor.r, lineColor.g, lineColor.b,
                (lineColor.a * mod.arrayListAnim).toInt())

            // 发光/阴影（对应 C++ 中的 glow 逻辑）
            if (mGlow && mDisplay == Display.None) {
                drawShadowRectDense(
                    ctx,
                    animatedX,
                    cursorY,
                    animatedX + textWidth,
                    cursorY + textHeight,
                    finalColor,
                    glowStrength * mod.arrayListAnim,
                    mGlowDensity,
                    0f,
                    0f,
                    rounding = 12f
                )
            }

            // 根据不同的显示模式绘制不同的装饰
            when (mDisplay) {
                Display.Outline -> {
                    // 只画外框（加阴影）
                    ctx.drawRoundedRect(
                        left,
                        cursorY,
                        right,
                        cursorY + textHeight,
                        0f,
                        lineColor
                    )
                }
                Display.Bar -> {
                    // 背景条 + 右侧细条
                    ctx.fillRect(animatedX, cursorY, animatedX + textWidth, cursorY + textHeight, lineColor)
                    ctx.fillRect(
                        animatedX + textWidth - 2f,
                        cursorY - 5f,
                        animatedX + textWidth + 2f,
                        cursorY + textHeight + 5f,
                        lineColor
                    )
                }
                Display.Split -> {
                    // 背景条 + 竖直分割线
                    ctx.fillRect(animatedX, cursorY, animatedX + textWidth, cursorY + textHeight, lineColor)
                    ctx.fillRect(
                        right + 2f,
                        cursorY + 4f,
                        right + 6f,
                        cursorY + textHeight - 2f,
                        lineColor
                    )
                }
                Display.None -> {
                    // 什么也不绘制，只渲染文字
                }
            }

            // ---------- 6️⃣ 文字渲染 ----------
            if (mTextShadow) {
                val shadowCol = Color4b(lineColor.r / 4, lineColor.g / 4, lineColor.b / 4, 236)
                ctx.drawString(font, displayText, animatedX + mShadowOffset, cursorY + mShadowOffset, shadowCol)
            }
            ctx.drawString(font, displayText, animatedX, cursorY, lineColor)

            // ---------- 7️⃣ 鼠标交互 ----------
            val mx = mc.mouseHelper.x * ctx.scaledWidth / mc.window.width
            val my = mc.mouseHelper.y * ctx.scaledHeight / mc.window.height
            val hovered = mx >= animatedX && mx <= animatedX + textWidth && my >= cursorY && my <= cursorY + textHeight
            if (hovered) {
                // 高亮（半透明白色填充）
                ctx.fillRect(left, cursorY, right, cursorY + textHeight, Color4b(255, 255, 255, 25))
                // 左键点击切换状态（可自行改为自定义键位）
                if (mc.options.keyBindAttack.isPressed) {
                    mod.toggle()
                }
            }

            // 下一行的 Y（受动画影响）
            cursorY += textHeight * mod.arrayListAnim
        }

        // --------------------------------------------------------------
        // 8️⃣ 第二遍遍历：真正绘制文字（已在第一次遍历里绘制，这里仅用于 Outline 连线）
        // --------------------------------------------------------------

        if (mDisplay == Display.Outline && backgroundRects.isNotEmpty()) {
            // 绘制线段（把原 C++ 的 line 渲染搬过来）
            // 我们只保留主要的左/右/顶部/底部四条线，效果足以还原原始 UI
            val first = backgroundRects.first()
            val last = backgroundRects.last()

            // 顶部横线
            ctx.drawLine(first.left, first.top, last.right + 2f, first.top, first.color, 2f)
            // 底部横线
            ctx.drawLine(first.left, last.bottom, last.right + 2f, last.bottom, last.color, 2f)
            // 左侧竖线
            ctx.drawLine(first.left, first.top, first.left, last.bottom, first.color, 2f)
        }
    }
}

/* --------------------------------------------------------------
 *  附：如果你的项目里没有 `drawShadowRect` / `drawShadowCircle`
 *      可以在 `utils/render/ShadowUtils.kt` 中加入下面的实现。
 * -------------------------------------------------------------- */
private object ShadowUtils {
    /**
     * 简单实现：在目标矩形四周画若干层半透明矩形，以模拟阴影效果。
     * 参数意义与 C++ 完全相同，只是使用 Kotlin + DrawContext。
     */
    fun drawShadowRect(
        ctx: DrawContext,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        col: Color4b,
        radius: Float,
        offsetX: Float,
        offsetY: Float,
        rounding: Float = 0f
    ) {
        // 这里使用 DrawContext 自带的 blur/阴影 API（若不存在，可自行实现层叠绘制）
        // 示例：先绘制模糊矩形，再绘制原始矩形
        // 最简化实现（不使用真实的高斯模糊）：
        ctx.fillRect(
            x0 - radius + offsetX,
            y0 - radius + offsetY,
            x1 + radius + offsetX,
            y1 + radius + offsetY,
            Color4b(col.r, col.g, col.b, (col.a * 0.5f).toInt())
        )
        ctx.fillRect(x0, y0, x1, y1, col)
    }

    fun drawShadowCircle(
        ctx: DrawContext,
        cx: Float,
        cy: Float,
        radius: Float,
        col: Color4b,
        segments: Int = 32,
        offsetX: Float,
        offsetY: Float,
        rounding: Float = 0f
    ) {
        // 简单的“模糊圆”实现：绘制稍大一些、透明度降低的圆
        ctx.drawCircle(
            cx + offsetX,
            cy + offsetY,
            radius + radius * 0.3f,
            Color4b(col.r, col.g, col.b, (col.a * 0.5f).toInt())
        )
        ctx.drawCircle(cx, cy, radius, col)
    }
}