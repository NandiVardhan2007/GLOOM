package com.gloom.island.automation

import com.gloom.island.settings.GloomIslandSettings

/**
 * Pre-execution safety net.
 *
 * Every automation request and every element a plan wants to touch is checked
 * here before a gesture is injected. Financial and irreversible actions are
 * refused outright; they are exactly the actions a misheard voice command can
 * trigger and the user cannot undo.
 *
 * The check is deliberately textual and local: it must never depend on the
 * model that proposed the action.
 */
object SafetyGuard {

    data class Verdict(
        val allowed: Boolean,
        val reason: String = "",
        val category: String = "",
    ) {
        companion object {
            val Allow = Verdict(true)
        }
    }

    private val financial = listOf(
        "pay now", "pay ", "make a payment", "send money", "transfer money",
        "wire transfer", "bank transfer", "purchase", "buy now", "place order",
        "checkout", "check out", "confirm payment", "confirm order", "add to cart",
        "subscribe now", "start subscription", "renew subscription", "donate",
        "withdraw", "top up", "recharge", "invest", "trade stock", "crypto"
    )

    private val irreversible = listOf(
        "delete account", "delete all", "delete everything", "delete this chat",
        "delete conversation", "clear all data", "clear app data", "erase all",
        "factory reset", "reset phone", "format storage", "uninstall",
        "remove account", "deactivate account", "close account", "cancel subscription",
        "block contact", "report and block", "permanently delete"
    )

    private val sensitiveAccount = listOf(
        "change password", "reset password", "change pin", "disable two-factor",
        "sign out of all", "log out of all", "revoke access", "remove device"
    )

    /**
     * True when the phrase is an explicit confirmation. A destructive command
     * may only run when the user has repeated it with this prefix.
     */
    fun isConfirmation(text: String): Boolean {
        val lower = text.trim().lowercase()
        return lower.startsWith("confirm ") ||
            lower.startsWith("yes, ") ||
            lower.startsWith("yes ") ||
            lower.startsWith("i confirm ")
    }

    fun assessRequest(text: String): Verdict {
        if (!GloomIslandSettings.instance.automationSafetyEnabled) return Verdict.Allow
        val lower = text.lowercase()
        // Typing utilities legitimately include the word "delete".
        val typingOnly = lower.startsWith("delete text") ||
            lower.startsWith("clear text") ||
            lower.startsWith("clear the field")
        if (typingOnly) return Verdict.Allow
        financial.firstOrNull { lower.contains(it) }?.let {
            return Verdict(false, "I won't make payments or purchases from voice.", "financial")
        }
        irreversible.firstOrNull { lower.contains(it) }?.let {
            return Verdict(false, "That action can't be undone, so I won't run it automatically.", "destructive")
        }
        sensitiveAccount.firstOrNull { lower.contains(it) }?.let {
            return Verdict(false, "Account security changes need to be made by you.", "account")
        }
        return Verdict.Allow
    }

    /**
     * Whether a plan may tap this element. Labels are compared after
     * normalisation because UI text often carries state words around the verb
     * ("Delete conversation", "Buy with Prime").
     */
    fun assessElement(label: String, viewId: String = ""): Verdict {
        if (!GloomIslandSettings.instance.automationSafetyEnabled) return Verdict.Allow
        val haystack = "$label $viewId".lowercase().trim()
        if (haystack.isEmpty()) return Verdict.Allow
        financial.firstOrNull { haystack.contains(it) }?.let {
            return Verdict(false, "I won't tap a payment control.", "financial")
        }
        irreversible.firstOrNull { haystack.contains(it) }?.let {
            return Verdict(false, "I won't tap a control that can't be undone.", "destructive")
        }
        return Verdict.Allow
    }

    /** Short, honest refusal for the voice response. */
    fun refusal(verdict: Verdict): String =
        verdict.reason.ifBlank { "I won't do that automatically." }
}
