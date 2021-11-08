package model;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;

public class WrappedAudioTrack {
    private final GuildMessageReceivedEvent associatedEvent;
    private final AudioTrack audioTrack;

    public WrappedAudioTrack(GuildMessageReceivedEvent event, AudioTrack audioTrack) {
        this.associatedEvent = event;
        this.audioTrack = audioTrack;
    }

    public GuildMessageReceivedEvent getAssociatedEvent() {
        return associatedEvent;
    }

    public AudioTrack getAudioTrack() {
        return audioTrack;
    }
}
