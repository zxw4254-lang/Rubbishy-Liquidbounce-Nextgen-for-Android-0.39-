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

import net.ccbluex.liquidbounce.LiquidBounce.CLIENT_NAME
import net.minecraft.client.Minecraft
import net.minecraft.util.Util
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import java.net.URI

/**
 * Get a [Logger] with client name prefix
 */
internal fun clientLogger(name: String): Logger = LogManager.getLogger("$CLIENT_NAME/$name")

val logger: Logger = LogManager.getLogger(CLIENT_NAME)

val inGame: Boolean
    get() = Minecraft.getInstance()?.let { mc -> mc.player != null && mc.level != null } == true

inline val clientStartDurationMs: Long
    get() = System.currentTimeMillis() - mc.clientStartTimeMs

/**
 * Open uri in browser
 *
 * On Android there is no `xdg-open` / AWT `Desktop` support, so we fall back
 * to launching an `ACTION_VIEW` intent through the `am` shell tool.
 */
fun browseUrl(url: String) {
    if (Platform.IS_ANDROID) {
        runCatching {
            ProcessBuilder("am", "start", "-a", "android.intent.action.VIEW", "-d", url)
                .redirectErrorStream(true)
                .start()
        }.onFailure {
            logger.warn("Failed to open URL '$url' on Android: ${it.message}")
        }
        return
    }

    Util.getPlatform().openUri(url)
}

/**
 * Open uri in browser ([URI] overload).
 */
fun browseUrl(url: URI) = browseUrl(url.toString())

/**
 * Open a directory in the system file manager.
 *
 * Not supported on Android (no desktop file manager); logs a warning instead.
 */
fun openFolder(directory: File) {
    if (Platform.IS_ANDROID) {
        logger.warn("Cannot open folder on Android: ${directory.absolutePath}")
        return
    }

    Util.getPlatform().openFile(directory)
}

/**
 * Get environment variable or system property.
 */
fun env(name: String, property: String) =
    (System.getenv(name) ?: System.getProperty(property))?.takeIf { string -> string.isNotBlank() }
