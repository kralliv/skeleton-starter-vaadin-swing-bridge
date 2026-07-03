package de.bewotec.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.PasswordField
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.router.Route
import com.vaadin.swingbridge.internal.runtime.SwingBridgeRunner
import de.bewotec.launcher.DistributionService
import de.bewotec.launcher.LoginService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value

@Route(value = "")
class MyjackView(
    private val loginService: LoginService,
    private val distributionService: DistributionService,
    @Value($$"${bewotec.launch.environment}")
    private val targetEnvironment: String,
    @Value($$"${bewotec.launch.agency}")
    private val targetAgency: Long,
) : VerticalLayout() {
    init {
        val container = VerticalLayout()

        val usernameField = TextField("Username")
        val passwordField = PasswordField("Password")

        val launchButton = Button("Launch")

        val errorLabel = Span()

        container.add(usernameField, passwordField, launchButton, errorLabel)

        add(container)

        launchButton.addClickListener {
            errorLabel.text = ""

            val authentication = try {
                loginService.authenticate(usernameField.value, passwordField.value)
            } catch (e: Exception) {
                LOG.error("failed to authenticate user", e)
                errorLabel.text = "Incorrect username or password"

                return@addClickListener
            }

            try {
                val (distribution, url, token) = loginService.requestLaunchAuthorization(

                    authentication,
                    targetEnvironment,
                    targetAgency,
                )

                val executable = distributionService.prepare(
                    distribution,
                    url,
                    token
                )

                remove(container)
                add(
                    LaunchableSwingBridge(executable.mainClass, executable.arguments) {
                        SwingBridgeRunner.createClassLoader(
                            executable.classpath,
                            Thread.currentThread().contextClassLoader
                        )
                    }
                )
            } catch (e: Exception) {
                LOG.error("failed to launch", e)
                errorLabel.text = "Launch failed, check logs"
            }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(MyjackView::class.java)
    }
}
