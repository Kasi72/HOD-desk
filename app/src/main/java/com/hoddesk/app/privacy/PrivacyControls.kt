package com.hoddesk.app.privacy

enum class ConsentScope { CALLS, MESSAGES, SCREENSHOTS, DOCUMENTS, CALENDAR }
data class PrivacyPolicy(val granted: Set<ConsentScope> = emptySet(), val retentionDays: Int = 90, val redactIdentifiers: Boolean = true) {
    fun canImport(scope: ConsentScope): Boolean = scope in granted
    fun redact(text: String): String = if (!redactIdentifiers) text else text.replace(Regex("\\b\\d{10}\\b"), "[PHONE REDACTED]")
}

data class DeploymentReview(val approved: Boolean, val controls: List<String>)
fun clinicalGovernmentDeploymentReview(policy: PrivacyPolicy): DeploymentReview = DeploymentReview(
    approved = policy.granted.isNotEmpty() && policy.retentionDays in 1..365 && policy.redactIdentifiers,
    controls = listOf("human review before task creation", "audit trail for imports and approvals", "encrypted local database", "least-privilege source consent", "retention and deletion enforcement")
)
