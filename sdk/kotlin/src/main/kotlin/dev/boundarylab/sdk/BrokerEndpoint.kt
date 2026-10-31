package dev.boundarylab.sdk

import android.content.ComponentName

/**
 * Explicit location of a same-publisher broker. Configuration never bypasses signer checks.
 * The client app must request [bindPermission] and declare visibility of [component]'s package
 * in its manifest. The service class is fully qualified and need not share the APK application ID.
 */
data class BrokerEndpoint(val component: ComponentName, val bindPermission: String) {
    init {
        require(QUALIFIED_NAME.matches(component.packageName)) { "Broker package must be fully qualified" }
        require(QUALIFIED_CLASS.matches(component.className)) { "Broker service class must be fully qualified" }
        require(QUALIFIED_NAME.matches(bindPermission)) { "Broker permission must be fully qualified" }
    }

    companion object {
        private val QUALIFIED_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")
        private val QUALIFIED_CLASS = Regex("[A-Za-z_][A-Za-z0-9_$]*(\\.[A-Za-z_][A-Za-z0-9_$]*)+")

        /** Existing BoundaryLab demonstration endpoint; retained for existing consumers. */
        @JvmField
        val DEFAULT = BrokerEndpoint(
            ComponentName("dev.boundarylab.broker", "dev.boundarylab.broker.BoundaryBrokerService"),
            "dev.boundarylab.permission.BIND_BROKER"
        )
    }
}
