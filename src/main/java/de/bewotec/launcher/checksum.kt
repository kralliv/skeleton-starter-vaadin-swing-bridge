package de.bewotec.launcher

import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.security.MessageDigest

fun InputStream.expecting(length: Long, hash: String): InputStream = IntegrityCheckingInputSteam(this, length, hash)

class IntegrityCheckingInputSteam(
    private val delegate: InputStream,
    private val expectedLength: Long,
    private val expectedHash: String,
) : InputStream() {

    private val digest = MessageDigest.getInstance("SHA-1")
    private var length = 0L

    override fun read(): Int {
        val byte = delegate.read()
        digest.update(byte.toByte())
        length += 1
        return byte
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val read = delegate.read(b, off, len)
        if (read > 0) {
            digest.update(b, off, read)
            length += read
        }
        return read
    }

    override fun close() {
        delegate.close()

        val hash = digest.digest().toHexString(HexFormat { upperCase = true })

        if (length != expectedLength || hash != expectedHash) {
            throw InputStreamIntegrityException(length, hash, expectedLength, expectedHash)
        }
    }

    override fun available(): Int = delegate.available()
    override fun reset() = delegate.reset()

    override fun markSupported(): Boolean = delegate.markSupported()
    override fun mark(readlimit: Int) = delegate.mark(readlimit)
}

class InputStreamIntegrityException(
    length: Long,
    hash: String,
    expectedLength: Long,
    expectedHash: String,
) : IOException("expected $expectedLength bytes (fingerprint $expectedHash) but received $length bytes (fingerprint $hash)")


fun Path.sha1(): String {
    val md = MessageDigest.getInstance("SHA-1")
    toFile().forEachBlock { buffer, count ->
        md.update(buffer, 0, count)
    }
    return md.digest().toHexString(HexFormat { upperCase = true })
}