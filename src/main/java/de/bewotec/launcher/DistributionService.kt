package de.bewotec.launcher

import de.bewotec.distribution.DistributionIdentifier
import de.bewotec.distribution.manifest.*
import de.bewotec.distribution.manifest.condition.ConditionMatcher
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.outputStream

private val PLACEHOLDER_REGEX = Regex("\\$\\{([a-zA-Z0-9_-]+)}")

@Service
class DistributionService(
    private val client: HttpClient,
    @Value($$"${bewotec.distribution.url}")
    private val distributionUrl: String,
    @Value($$"${bewotec.distribution.directory:distributions}")
    private val distributionDirectory: Path,
) {

    private val conditionMatcher = ConditionMatcher.builder()
        .withSystem(OperatingSystem.PLATFORM, Architecture.PLATFORM)
        .withFeatures("thirdGeneration")
        .build()

    fun prepare(
        identifier: DistributionIdentifier,
        url: String,
        token: String
    ): ExecutableDistribution = runBlocking {
        val manifest = Manifest.read(client.get("${distributionUrl}/distributions/${identifier.location}").bodyAsText())
        if (manifest !is ApplicationManifest) error("distribution $identifier is not an application")

        val mainClass = manifest.entrypoint
        val arguments = provideArguments(
            manifest,
            "url" to url,
            "token" to token,
        )
        val classpath = provideClasspath(manifest)

        ExecutableDistribution(
            mainClass,
            arguments,
            classpath,
        )
    }

    private fun provideArguments(
        manifest: ApplicationManifest,
        vararg variables: Pair<String, String>
    ): Array<out String> {
        val variableLookup = variables.toMap()
        return manifest.arguments
            .filter { conditionMatcher.matches(it) }
            .flatMap { it.values }
            .map {
                it.replace(PLACEHOLDER_REGEX) { match ->
                    variableLookup[match.groupValues[1]] ?: error("missing variable \"${match.groupValues[1]}\"")
                }
            }
            .toTypedArray()
    }

    private suspend fun provideClasspath(manifest: ApplicationManifest): List<Path> {
        val fileProviders = (manifest.resources + manifest.libraries)
            .asSequence()
            .filter { conditionMatcher.matches(it) }
            .map { downloadable ->
                val download = downloadable.download ?: error("$downloadable has no download")

                val path = when (downloadable) {
                    is Resource -> "distributions/${manifest.identifier.locator}resources/${download.path}"
                    is Library -> "libraries/${download.path}"
                    else -> error("unsupported downloadable $downloadable (${downloadable::class.qualifiedName})")
                }

                fileProvider(path, download.length, download.sha1)
            }
            .toList()

        return coroutineScope {
            val semaphore = Semaphore(4)
            fileProviders.map { fileProvider ->
                async {
                    semaphore.withPermit {
                        fileProvider()
                    }
                }
            }.awaitAll()
        }
    }

    private fun fileProvider(
        path: String,
        length: Long,
        sha1: String
    ): FileProvider = {
        withContext(Dispatchers.IO) {
            val file = distributionDirectory.resolve(path)
            if (!file.exists() || file.fileSize() != length || file.sha1() != sha1) {
                LOG.info("downloading {} to {}", path, file)

                file.parent.createDirectories()

                client.prepareGet("${distributionUrl}/$path") {
                    accept(ContentType.Application.OctetStream)
                }.execute { response ->
                    response.bodyAsChannel()
                        .toInputStream()
                        .expecting(length, sha1)
                        .use { inputStream ->
                            file.outputStream().use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                }
            }
            file
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(DistributionService::class.java)
    }
}

private typealias FileProvider = suspend () -> Path

class ExecutableDistribution(
    val mainClass: String,
    val arguments: Array<out String>,
    val classpath: List<Path>,
)
