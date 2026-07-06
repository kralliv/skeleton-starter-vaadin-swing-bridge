package de.bewotec.ui

import java.net.URL
import java.net.URLClassLoader

class IsolatingURLClassLoader(
    urls: Array<URL>,
    parent: ClassLoader? = null
) : URLClassLoader(urls, parent) {

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        synchronized(getClassLoadingLock(name)) {
            var c = findLoadedClass(name)
            if (c == null) {
                c = if (preferParent(name)) {
                    loadClassFromParent(name) ?: findClassIgnoringNotFound(name)
                } else {
                    findClassIgnoringNotFound(name) ?: loadClassFromParent(name)
                }
                if (c == null) {
                    throw ClassNotFoundException(name)
                }
            }
            return c
        }
    }

    private fun findClassIgnoringNotFound(name: String): Class<*>? {
        return try {
            findClass(name)
        } catch (_: ClassNotFoundException) {
            null
        }
    }

    private fun loadClassFromParent(name: String?): Class<*>? {
        if (this.parent == null) {
            return null
        }
        return try {
            Class.forName(name, false, this.parent)
        } catch (_: ClassNotFoundException) {
            null
        }
    }

    private fun preferParent(name: String): Boolean {
        if (name.startsWith("org.apache.logging")) return true
        if (name.startsWith("org.slf4j")) return true
        return false
    }
}