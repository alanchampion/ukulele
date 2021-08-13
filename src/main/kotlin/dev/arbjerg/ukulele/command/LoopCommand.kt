package dev.arbjerg.ukulele.command

import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.arbjerg.ukulele.features.HelpContext
import dev.arbjerg.ukulele.jda.Command
import dev.arbjerg.ukulele.jda.CommandContext
import org.springframework.stereotype.Component

@Component
class LoopCommand : Command ("loop") {
    override suspend fun CommandContext.invoke() {
        if (player.isLooped){
            player.stopLoop()
            return reply("Player has stopped looping.")
        }

        if (player.tracks.isEmpty()) {
            return reply("No tracks queued to loop.")
        }

        when {
            argumentText == "single" -> loopSingle()
            argumentText.isBlank() -> loopSingle()
            // argumentText.toIntOrNull() != null -> loopSingle(argumentText.toInt())
            // argumentText.split("\\s+".toRegex()).size == 2 -> loopRange()
        }
    }

    private fun CommandContext.loopSingle() {
        if (!player.isLooped)
            printLooped(player.loop())
        else
            reply("Player is already looping.")
    }

    /*private fun CommandContext.loopRange() {
        val args = argumentText.split("\\s+".toRegex())

        val n1 = (args[0].toInt() - 1).coerceAtLeast(0)
        val n2 = (args[1].toInt() - 1).coerceAtLeast(0)
        printLooped(player.loop(n1..n2))
    }*/

    private fun CommandContext.printLooped(looped: List<AudioTrack>) = when(looped.size) {
        0 -> replyHelp()
        1 -> reply("Looping `${looped.first().info.title}`")
        else -> reply("Looping `${looped.size} tracks`")
    }

    override fun HelpContext.provideHelp() {
        addUsage("[single]")
        addDescription("Loops the current track.")
        addUsage("<all>")
        addDescription("Loops the entire queue.")
        addUsage("<stop>")
        addDescription("Stops any looping.")
    }
}