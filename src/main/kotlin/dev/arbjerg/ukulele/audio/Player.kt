package dev.arbjerg.ukulele.audio

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame
import dev.arbjerg.ukulele.data.GuildProperties
import dev.arbjerg.ukulele.data.GuildPropertiesService
import net.dv8tion.jda.api.audio.AudioSendHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.Buffer
import java.nio.ByteBuffer


class Player(val beans: Beans, guildProperties: GuildProperties) : AudioEventAdapter(), AudioSendHandler {
    @Component
    class Beans(
            val apm: AudioPlayerManager,
            val guildProperties: GuildPropertiesService
    )

    private val guildId = guildProperties.guildId
    private val queue = TrackQueue()
    private var loopTrack: AudioTrack? = null
    val looping: Boolean
        get() = loopTrack != null
    private val player = beans.apm.createPlayer().apply {
        addListener(this@Player)
        volume = guildProperties.volume
    }
    private val buffer = ByteBuffer.allocate(1024)
    private val frame: MutableAudioFrame = MutableAudioFrame().apply { setBuffer(buffer) }
    private val log: Logger = LoggerFactory.getLogger(Player::class.java)
    var volume: Int
        get() = player.volume
        set(value) {
            player.volume = value
            beans.guildProperties.transform(guildId) {
                it.volume = player.volume
            }.subscribe()
        }

    val tracks: List<AudioTrack> get() {
        val tracks = queue.tracks.toMutableList()
        player.playingTrack?.let { tracks.add(0, it) }
        return tracks
    }

    val remainingDuration: Long get() {
        var duration = 0L
        if (player.playingTrack != null && !player.playingTrack.info.isStream)
            player.playingTrack?.let { duration = it.info.length - it.position }
        return duration + queue.duration
    }

    val isPaused : Boolean
        get() = player.isPaused

    /**
     * @return whether or not we started playing
     */
    fun add(vararg tracks: AudioTrack): Boolean {
        queue.add(*tracks)
        if (player.playingTrack == null) {
            player.playTrack(queue.take()!!)
            return true
        }
        return false
    }

    fun skip(range: IntRange): List<AudioTrack> {
        stopLoop()
        val rangeFirst = range.first.coerceAtMost(queue.tracks.size)
        val rangeLast = range.last.coerceAtMost(queue.tracks.size)
        val skipped = mutableListOf<AudioTrack>()
        var newRange = rangeFirst .. rangeLast 
        // Skip the first track if it is stored here
        if (newRange.contains(0) && player.playingTrack != null) {
            skipped.add(player.playingTrack)
            // Reduce range if found
            newRange = 0 .. rangeLast - 1
        } else {
            newRange = newRange.first - 1 .. newRange.last - 1
        }
        if (newRange.last >= 0) skipped.addAll(queue.removeRange(newRange))
        if (skipped.first() == player.playingTrack) player.stopTrack()
        if (!tracks.isEmpty())
            loop()
        return skipped
    }

    fun pause() {
        player.isPaused = true
    }

    fun resume() {
        player.isPaused = false
    }

    fun stop() {
        stopLoop()
        queue.clear()
        player.stopTrack()
    }

    fun loop(): AudioTrack {
        val looped = tracks[0]
        loopTrack = looped.makeClone()
        return looped
    }

    fun stopLoop() {
        loopTrack = null
    }

    override fun onTrackEnd(player: AudioPlayer, track: AudioTrack, endReason: AudioTrackEndReason) {
        if(looping) {
            player.playTrack(loopTrack?.makeClone())
        } else {
            val new = queue.take() ?: return
            player.playTrack(new)
        }
    }

    override fun onTrackException(player: AudioPlayer, track: AudioTrack, exception: FriendlyException) {
        log.error("Track exception", exception)
    }

    override fun onTrackStuck(player: AudioPlayer, track: AudioTrack, thresholdMs: Long) {
        log.error("Track $track got stuck!")
    }

    override fun canProvide(): Boolean {
        return player.provide(frame)
    }

    override fun provide20MsAudio(): ByteBuffer {
        // flip to make it a read buffer
        (buffer as Buffer).flip()
        return buffer
    }

    override fun isOpus() = true
}
