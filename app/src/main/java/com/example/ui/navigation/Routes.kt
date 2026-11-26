package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object InboxRoute

@Serializable
data object BlockedRoute

@Serializable
data object SettingsRoute

@Serializable
data class ConversationRoute(val threadKey: String)
