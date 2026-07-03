package de.bewotec.ui

import com.vaadin.swingbridge.SwingBridge
import java.net.URLClassLoader
import java.util.function.Supplier

/**
 * A [SwingBridge] that launches with BOTH custom main-method arguments and a custom
 * [URLClassLoader] provider
 */
class LaunchableSwingBridge(
    mainClass: String,
    private val arguments: Array<out String>,
    classLoaderSupplier: Supplier<URLClassLoader>,
) : SwingBridge(mainClass, classLoaderSupplier) {

    override fun mainMethodArgs(): Array<String> = Array(arguments.size) { arguments[it] }
}
