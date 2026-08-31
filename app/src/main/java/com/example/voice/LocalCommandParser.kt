package com.example.voice

data class ParsedCommand(
    val action: String, // "NAVIGATE", "YOUTUBE_PLAY", "GENERAL_TALK"
    val destination: String? = null,
    val query: String? = null,
    val responseText: String
)

object LocalCommandParser {
    fun processVoiceCommand(command: String): ParsedCommand {
        val lowerCmd = command.lowercase().trim()

        // Match Navigation
        val navRegex = Regex("^(navigate to|take me to|directions to|route to|where is|drive to|go to)\\s+(.+)")
        val navMatch = navRegex.find(lowerCmd)
        if (navMatch != null) {
            val destination = navMatch.groupValues[2].trim()
            return ParsedCommand(
                action = "NAVIGATE",
                destination = destination,
                responseText = "Setting destination to $destination."
            )
        }
        
        if (lowerCmd.contains("navigate") || lowerCmd.contains("directions")) {
            val destination = lowerCmd.replace("navigate", "").replace("directions", "").replace("to", "").trim()
            if (destination.isNotEmpty()) {
                return ParsedCommand(
                    action = "NAVIGATE",
                    destination = destination,
                    responseText = "Setting destination to $destination."
                )
            }
        }

        // Match YouTube/Media Play
        val playRegex = Regex("^(play|listen to|put on|start)\\s+(.+)")
        val playMatch = playRegex.find(lowerCmd)
        
        if (playMatch != null) {
            var query = playMatch.groupValues[2].trim()
            query = query.replace("on youtube", "").trim()
            return ParsedCommand(
                action = "YOUTUBE_PLAY",
                query = query,
                responseText = "Playing $query on YouTube."
            )
        }
        
        if (lowerCmd.contains("youtube")) {
            val query = lowerCmd.replace("youtube", "").replace("play", "").trim()
            if (query.isNotEmpty()) {
                 return ParsedCommand(
                    action = "YOUTUBE_PLAY",
                    query = query,
                    responseText = "Playing $query on YouTube."
                )
            }
        }

        // Default General Action
        return ParsedCommand(
            action = "GENERAL_TALK",
            responseText = "I didn't catch a navigation or media command. Try saying 'Play some music' or 'Navigate to the airport'."
        )
    }
}
