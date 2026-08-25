package pigeon.message;

import com.google.gson.annotations.SerializedName;
import pigeon.PigeonConfig;
import pigeon.domain.AccountType;
import pigeon.message.templating.Template;
import pigeon.notifiers.data.NotificationData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.With;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Image;
import java.util.LinkedList;
import java.util.List;

@Value
@With
@Builder(toBuilder = true)
@AllArgsConstructor
public class NotificationBody<T extends NotificationData> {

    static final int MAX_THREAD_NAME_LENGTH = 100; // not explicitly documented by Discord

    /*
     * Core notification fields
     */
    @NotNull
    NotificationType type;
    String playerName;
    AccountType accountType;
    String dinkAccountHash;
    @Nullable
    String clanName;
    @Nullable
    String groupIronClanName;
    boolean seasonalWorld;
    @Nullable
    Integer world;
    @Nullable
    Integer regionId;
    @Nullable
    T extra;
    @NotNull
    @EqualsAndHashCode.Include
    transient Template text;

    /*
     * Discord fields
     */

    /**
     * Filled in with the text of the notifier (e.g., {@link #getText()} is "Forsen has levelled Attack to 100")
     * <p>
     * This is done by {@link DiscordMessageHandler#createMessage} if {@link pigeon.PigeonConfig#discordRichEmbeds()} is disabled.
     */
    @Nullable
    @SerializedName("content")
    String computedDiscordContent;

    /**
     * An optional title to override that of {@link NotificationType#getThumbnail}
     * within the embed constructed by {@link DiscordMessageHandler#createMessage}
     */
    @Nullable
    @EqualsAndHashCode.Include
    transient String customTitle;

    /**
     * An optional footer text to override that of {@link PigeonConfig#embedFooterText}
     * within the embed constructed by {@link DiscordMessageHandler#createMessage}
     */
    @Nullable
    @EqualsAndHashCode.Include
    transient String customFooter;

    /**
     * An optional thumbnail to override that of {@link NotificationType#getThumbnail}
     * within the embed constructed by {@link DiscordMessageHandler#createMessage}
     */
    @Nullable
    @EqualsAndHashCode.Exclude
    transient String thumbnailUrl;

    @Builder.Default
    List<Embed> embeds = new LinkedList<>();

    /**
     * The thread name; must only be specified for forum channels when thread_id is not present
     *
     * @see #MAX_THREAD_NAME_LENGTH
     */
    @Nullable
    @SerializedName("thread_name")
    String threadName;

    /**
     * The IDs of the set of tags that have been applied to a thread in a GUILD_FORUM or a GUILD_MEDIA channel
     */
    @Nullable
    @SerializedName("applied_tags")
    Long[] appliedTags;

    /**
     * An optional screenshot to use, instead of capturing one from {@link DiscordMessageHandler}
     */
    @Nullable
    @EqualsAndHashCode.Exclude
    transient Image screenshotOverride;

}
