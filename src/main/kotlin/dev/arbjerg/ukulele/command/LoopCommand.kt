package dev.arbjerg.ukulele.command

import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.arbjerg.ukulele.features.HelpContext
import dev.arbjerg.ukulele.jda.Command
import dev.arbjerg.ukulele.jda.CommandContext
import org.springframework.stereotype.Component

@Component
class LoopCommand : Command ("loop") {
    override suspend fun CommandContext.invoke() {
        if (player.tracks.isEmpty()) {
            return reply("No tracks queued to loop.")
        }

        if (!player.looping)
            printLoop(player.loop())
        else {
            player.stopLoop()
            return reply("Player has stopped looping.")
        }
    }

    private fun CommandContext.printLoop(looped: AudioTrack) {
        reply("Looping `${looped.info.title}`")
    }

    override fun HelpContext.provideHelp() {
        addUsage("")
        addDescription("Loops the current track or stops looping if currently looping.")
        addUsage("[stop]")
        addDescription("Stops looping.")
    }
}