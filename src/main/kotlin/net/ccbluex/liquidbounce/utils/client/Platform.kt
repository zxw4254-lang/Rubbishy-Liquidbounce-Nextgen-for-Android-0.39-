/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.utils.client

/**
 * Platform related information and detection helpers.
 *
 * Android support: LiquidBounce can run inside Android launchers such as
 * PojavLauncher / ZalithLauncher. Those launchers run the real desktop JVM
 * (openjdk 17/21 for aarch64/arm) on top of Android, which changes a few
 * assumptions that the desktop client makes:
 *
 * - `java.awt` exists but is headless-ish; `Toolkit`/`Desktop`/`TinyFileDialogs`
 *   are unavailable or unusable.
 * - `Util.getPlatform()` reports [net.minecraft.util.Util.OS.LINUX], so
 *   `xdg-open` based "open in browser / file manager" calls fail.
 * - The browser backend (JCEF/MCEF, based on Chromium) has no Android build,
 *   so the web-based ClickGUI/HUD/theme UI cannot be used.
 * - Physical fonts like "DejaVu Sans" are not registered; Android fonts
 *   (Roboto / Noto Sans CJK) are used instead.
 */
object Platform {

    /**
     * True when running inside an Android environment.
     *
     * Detected through a combination of markers that are always present on
     * Android (regardless of the specific launcher):
     * - `ANDROID_ROOT` is set by Android for every app process.
     * - PojavLauncher (and forks such as ZalithLauncher) keep the
     *   `net.kdt.pojavlaunch` package name and mark their JVM runtime.
     */
    @JvmField
    val IS_ANDROID: Boolean = runCatching {
        System.getenv("ANDROID_ROOT") != null ||
            System.getProperty("java.vendor")?.contains("Pojav", ignoreCase = true) == true ||
            System.getProperty("java.vm.vendor")?.contains("Pojav", ignoreCase = true) == true ||
            System.getProperty("java.runtime.name")?.contains("Pojav", ignoreCase = true) == true ||
            runCatching { Class.forName("net.kdt.pojavlaunch.Tools") }.isSuccess
    }.getOrDefault(false)

}
