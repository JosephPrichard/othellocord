package modules.stats;

import discord.JDASingleton;
import modules.player.Player;
import net.dv8tion.jda.api.entities.User;
import org.modelmapper.Converter;
import org.modelmapper.ModelMapper;
import utils.BotUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class StatsMapper
{
    private final ModelMapper modelMapper = new ModelMapper();

    public StatsMapper() {
        modelMapper.typeMap(StatsEntity.class, Stats.class).addMappings(mapper -> {
            Converter<Long, Player> converter = (ctx) -> new Player(ctx.getSource());
            mapper.using(converter).map(StatsEntity::getPlayerId, Stats::setPlayer);
        });

        modelMapper.validate();
    }

    public Stats map(StatsEntity entity) {
        // fetch tag from jda and assign
        String tag = JDASingleton.fetchUser(entity.getPlayerId()).complete().getAsTag();
        // map entity to dto
        Stats stats = modelMapper.map(entity, Stats.class);
        stats.getPlayer().setName(tag);
        return stats;
    }

    public List<Stats> mapAll(List<StatsEntity> entityList) {
        // fetch each tag from jda using futures, for the bots return null and map bot name instead
        List<CompletableFuture<User>> futures = new ArrayList<>();
        for (StatsEntity entity : entityList) {
            if (!BotUtils.isBotId(entity.getPlayerId())) {
                futures.add(JDASingleton.fetchUser(entity.getPlayerId()).submit());
            } else {
                futures.add(CompletableFuture.completedFuture(null));
            }
        }
        CompletableFuture.allOf((futures.toArray(new CompletableFuture[0]))).join();
        // map each entity to dto
        List<Stats> statsList = new ArrayList<>();
        for(int i = 0; i < futures.size(); i++){
            // retrieve tag from completed future
            User user = futures.get(i).join();
            String tag = user != null ? user.getAsTag() : BotUtils.getBotName(entityList.get(i).getPlayerId());
            // map entity to dto and add to dto list
            Stats dto = modelMapper.map(entityList.get(i), Stats.class);
            dto.getPlayer().setName(tag);
            statsList.add(dto);
        }
        return statsList;
    }
}
