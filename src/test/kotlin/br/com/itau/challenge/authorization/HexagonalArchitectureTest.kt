package br.com.itau.challenge.authorization

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Test

class HexagonalArchitectureTest {

    private val scope = Konsist.scopeFromPackage("br.com.itau.challenge.authorization..")

    private val domain = Layer("Domain", "..authorization.domain..")
    private val port = Layer("Port", "..authorization.port..")
    private val application = Layer("Application", "..authorization.application..")
    private val adapter = Layer("Adapter", "..authorization.adapter..")

    @Test
    fun `hexagonal layers respect dependency direction`() {
        scope.assertArchitecture {
            domain.dependsOnNothing()
            port.doesNotDependOn(application, adapter)
            application.doesNotDependOn(adapter)
        }
    }

    @Test
    fun `domain does not depend on the Spring framework`() {
        Konsist
            .scopeFromPackage("br.com.itau.challenge.authorization.domain..")
            .files
            .assertFalse { it.hasImport { import -> import.name.startsWith("org.springframework") } }
    }
}
