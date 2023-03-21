package be.solidlab.sdx.client.lib.backends.ldp

import java.net.URL

interface TargetResolverContext

interface TargetResolver {

    suspend fun resolve(classIri: String, context: TargetResolverContext): URL?

}

class StaticTargetResolver(targetUrl: String) : TargetResolver {

    private val target = toURLOrNull(targetUrl)

    init {
        requireNotNull(target) { "Target must be a valid URL!" }
    }

    override suspend fun resolve(classIri: String, context: TargetResolverContext): URL? {
        return target
    }

    private fun toURLOrNull(urlStr: String): URL? {
        return try {
            URL(urlStr)
        } catch (err: Throwable) {
            null
        }
    }
}
