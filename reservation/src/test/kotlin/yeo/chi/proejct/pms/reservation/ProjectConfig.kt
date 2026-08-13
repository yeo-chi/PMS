package yeo.chi.proejct.pms.reservation

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.extensions.spring.SpringAutowireConstructorExtension
import io.kotest.extensions.spring.SpringExtension

class ProjectConfig : AbstractProjectConfig() {
    override fun extensions() = listOf(SpringExtension, SpringAutowireConstructorExtension)
}
