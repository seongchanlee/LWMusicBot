package helper;

import model.WrappedAudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

public class EmbedHelper {
    private EmbedHelper() {
        // private constructor to avoid object instantiation
    }

    public static MessageEmbed getTrackQueueEmbed(WrappedAudioTrack wrappedTrack) {
        EmbedBuilder embedBuilder = new EmbedBuilder();

        String message = "[" +
                wrappedTrack.getAudioTrack().getInfo().title +
                "]" +
                "(" +
                wrappedTrack.getAudioTrack().getInfo().uri +
                ")" +
                " [<@" +
                wrappedTrack.getAssociatedEvent().getAuthor().getId() +
                ">]";

        embedBuilder.setDescription("Queued " + message);

        return embedBuilder.build();
    }

    public static MessageEmbed getTrackPlayEmbed(WrappedAudioTrack wrappedEvent) {
        EmbedBuilder embedBuilder = new EmbedBuilder();

        String requesterStr = "Requested by: " + "<@" + wrappedEvent.getAssociatedEvent().getAuthor().getId() + ">";
        String thumbnailUri = "https://i3.ytimg.com/vi/"
                + wrappedEvent.getAudioTrack().getInfo().identifier
                + "/maxresdefault.jpg";

        embedBuilder.setAuthor("Now Playing");
        embedBuilder.setTitle(wrappedEvent.getAudioTrack().getInfo().title,
                wrappedEvent.getAudioTrack().getInfo().uri);
        embedBuilder.setThumbnail(thumbnailUri);
        embedBuilder.setDescription(requesterStr);

        return embedBuilder.build();
    }
}
