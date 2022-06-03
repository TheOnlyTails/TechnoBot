package technobot.handlers;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.bson.Document;
import org.bson.conversions.Bson;
import technobot.TechnoBot;
import technobot.data.Database;
import technobot.data.GuildData;
import technobot.util.EmbedUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the suggestion board for a guild.
 *
 * @author TechnoVision
 */
public class SuggestionHandler {

    /** Ordered list of suggestion message IDs. */
    private final List<Long> messages;

    /** Ordered list of authors for each suggestion ID. */
    private final List<Long> authors;

    /** The ID of the TextChannel where suggestions are displayed. */
    private Long channel;

    /** The number of the next suggestion. */
    private long number;

    /** Whether or not responses DM the suggestion author */
    private boolean responseDM;

    /** Whether or not suggestions are anonymous */
    private boolean isAnonymous;

    /** Items necessary for mongoDB data access */
    private final TechnoBot bot;
    private final Bson filter;

    /**
     * Sets up the local cache for this guild's suggestions from MongoDB.
     *
     * @param bot Instance of TechnoBot shard.
     * @param guildID ID for the guild.
     * @param data MongoDB data file for suggestions.
     */
    public SuggestionHandler(TechnoBot bot, long guildID, Document data) {
        this.filter = Filters.eq("guild", guildID);
        this.bot = bot;

        // Retrieve data from MongoDB
        this.channel = data.getLong("channel");
        this.number = data.getLong("number");
        this.responseDM = data.getBoolean("response_dm");
        this.isAnonymous = data.getBoolean("is_anonymous");
        this.messages = data.getList("messages", Long.class);
        this.authors = data.getList("authors", Long.class);
    }

    /**
     * Sets up a brand new suggestions board local cache.
     * Also acts as the point of creation for the MongoDB data file.
     *
     * @param bot Instance of TechnoBot shard.
     * @param guildID ID for the guild.
     * @param channel ID of the channel to set as suggestion board
     */
    public SuggestionHandler(TechnoBot bot, long guildID, Long channel) {
        this.filter = Filters.eq("guild", guildID);
        this.bot = bot;

        // Setup default local cache
        this.channel = channel;
        this.number = 1;
        this.responseDM = false;
        this.isAnonymous = false;
        this.messages = new ArrayList<>();
        this.authors = new ArrayList<>();

        // Create MongoDB data file
        Document data = new Document("guild", guildID);
        data.put("channel", channel);
        data.put("number", number);
        data.put("response_dm", false);
        data.put("is_anonymous", false);
        data.put("messages", messages);
        data.put("authors", authors);
        bot.database.suggestions.insertOne(data);
    }

    /**
     * Sets the suggestion board channel.
     *
     * @param channelID ID of the new channel.
     */
    public void setChannel(long channelID) {
        channel = channelID;
        bot.database.suggestions.updateOne(filter, Updates.set("channel", channel), Database.UPSERT);
    }

    /**
     * Adds a suggestion message to the list.
     *
     * @param messageID the ID of the suggestion embed.
     */
    public void add(long messageID, long author) {
        // Update local cache
        messages.add(messageID);
        authors.add(author);
        number++;

        // Update MongoDB data file
        bot.database.suggestions.updateOne(filter, Updates.set("messages", messages), Database.UPSERT);
        bot.database.suggestions.updateOne(filter, Updates.set("authors", authors), Database.UPSERT);
        bot.database.suggestions.updateOne(filter, Updates.set("number", number), Database.UPSERT);
    }

    /**
     * Resets all suggestion data locally and in MongoDB.
     */
    public void reset(Guild guild) {
        bot.database.suggestions.deleteOne(Filters.eq("guild", guild.getIdLong()));
        Document data = new Document("guild", guild.getIdLong());
        data.put("channel", channel);
        data.put("number", number);
        data.put("response_dm", false);
        data.put("is_anonymous", false);
        data.put("messages", messages);
        data.put("authors", authors);
        bot.database.suggestions.insertOne(data);

        channel = null;
        number = 0;
        responseDM = false;
        isAnonymous = false;
        messages.clear();
        authors.clear();
    }

    /**
     * Checks if suggestion board has a channel set.
     *
     * @return true if channel set, otherwise false.
     */
    public boolean isSetup() {
        return channel != null;
    }

    /**
     * Checks if anonymous mode is turned on/off.
     *
     * @return anonymous mode boolean.
     */
    public boolean isAnonymous() { return isAnonymous; }

    /**
     * Checks if response DMs are enabled/disabled.
     *
     * @return response DMs boolean.
     */
    public boolean hasResponseDM() { return responseDM; }

    /**
     * Gets the number of the next suggestion.
     *
     * @return Next suggestion number.
     */
    public long getNumber() {
        return number;
    }

    /**
     * Gets the channel ID of the suggestion board.
     *
     * @return ID of the suggestion channel.
     */
    public Long getChannel() {
        return channel;
    }

    /**
     * Gets the list of suggestion message IDs.
     *
     * @return list of suggestion message IDs.
     */
    public List<Long> getMessages() { return messages; }

    /**
     * Switches on/off anonymous mode and returns the result.
     *
     * @return the resulting boolean of toggling anonymous mode.
     */
    public boolean toggleAnonymous() {
        isAnonymous = !isAnonymous;
        bot.database.suggestions.updateOne(filter, Updates.set("is_anonymous", isAnonymous), Database.UPSERT);
        return isAnonymous;
    }

    /**
     * Switches on/off response DMs.
     *
     * @return the resulting boolean of toggling DMs.
     */
    public boolean toggleResponseDM() {
        responseDM = !responseDM;
        bot.database.suggestions.updateOne(filter, Updates.set("response_dm", responseDM), Database.UPSERT);
        return responseDM;
    }

    /**
     * Responds to a suggestion by editing the embed and responding to the author.
     *
     * @param event The slash command event that triggered this method.
     * @param id the id number of the suggestion to respond to.
     * @param reasonOption the reason option passed in by user.
     * @param responseType the type of response (approve, deny, etc).
     */
    public void respond(SlashCommandInteractionEvent event, int id, OptionMapping reasonOption, SuggestionResponse responseType) {
        String reason = (reasonOption != null) ? reasonOption.getAsString() : "No reason given";
        try {
            SuggestionHandler suggestionHandler = GuildData.get(event.getGuild()).suggestionHandler;
            TextChannel channel = event.getGuild().getTextChannelById(suggestionHandler.getChannel());
            if (channel == null) { throw new NullPointerException(); }

            // Edit suggestion embed
            Message suggestionMessage = channel.retrieveMessageById(suggestionHandler.getMessages().get(id)).complete();
            MessageEmbed embed = suggestionMessage.getEmbeds().get(0);
            MessageEmbed editedEmbed = new EmbedBuilder()
                    .setAuthor(embed.getAuthor().getName(), embed.getUrl(), embed.getAuthor().getIconUrl())
                    .setTitle("Suggestion #" + (id+1) + " " + responseType.response)
                    .setDescription(embed.getDescription())
                    .addField("Reason from " + event.getUser().getAsTag(), reason.toString(), false)
                    .setColor(responseType.color)
                    .build();
            suggestionMessage.editMessageEmbeds(editedEmbed).queue();

            String lowercaseResponse = responseType.response.toLowerCase();
            String text = "Suggestion #" + (id+1) + " has been " + lowercaseResponse + "!";
            event.getHook().sendMessageEmbeds(EmbedUtils.createDefault(text)).queue();

            // DM Author if response DMs are turned on
            if (responseDM) {
                User author = event.getJDA().getUserById(authors.get(id));
                if (author != null) {
                    author.openPrivateChannel().queue(dm -> {
                        String dmText = "Your suggestion has been " + lowercaseResponse + " by " + event.getUser().getAsTag();
                        dm.sendMessage(dmText).setEmbeds(editedEmbed).queue();
                    });
                }
            }

        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            // Invalid ID format
            String text = "Could not find a suggestion with that id number.";
            event.getHook().sendMessageEmbeds(EmbedUtils.createError(text)).queue();
        } catch (ErrorResponseException | NullPointerException e) {
            // Invalid channel
            String text = "Could not find that message, was the channel deleted or changed?";
            event.getHook().sendMessageEmbeds(EmbedUtils.createError(text)).queue();
        }
    }

    /**
     * Suggestion Response Types.
     * Includes the correct color scheme and wording.
     */
    public enum SuggestionResponse {
        APPROVE("Approved", 0xd2ffd0),
        DENY("Denied", 0xffd0ce),
        CONSIDER("Considered", 0xfdff91),
        IMPLEMENT("Implemented", 0x91fbff);

        private final String response;
        private final int color;

        SuggestionResponse(String response, int color) {
            this.response = response;
            this.color = color;
        }
    }
}
