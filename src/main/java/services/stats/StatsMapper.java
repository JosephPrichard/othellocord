/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.stats;

import net.dv8tion.jda.api.entities.User;
import org.modelmapper.Converter;
import org.modelmapper.ModelMapper;
import services.game.Player;
import utils.Bot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class StatsMapper
{
    private final ModelMapper modelMapper = new ModelMapper();
    private final UserFetcher userFetcher;

    public StatsMapper(UserFetcher userFetcher) {
        this.userFetcher = userFetcher;
        modelMapper.typeMap(StatsEntity.class, Stats.class).addMappings(mapper -> {
            Converter<Long, Player> playerConverter = (ctx) -> new Player(ctx.getSource());
            mapper.using(playerConverter).map(StatsEntity::getPlayerId, Stats::setPlayer);
        });
        modelMapper.validate();
    }

    public Stats map(StatsEntity entity) {
        // fetch tag from jda and assign
        var tag = userFetcher.fetchUser(entity.getPlayerId()).complete().getAsTag();
        // map entity to dto
        var stats = modelMapper.map(entity, Stats.class);
        stats.getPlayer().setName(tag);
        return stats;
    }

    public List<Stats> mapAll(List<StatsEntity> entityList) {
        // fetch each tag from jda using futures, for the bots return null and map bot name instead
        List<CompletableFuture<User>> futures = new ArrayList<>();
        for (var entity : entityList) {
            if (!Bot.isBotId(entity.getPlayerId())) {
                futures.add(userFetcher.fetchUser(entity.getPlayerId()).submit());
            } else {
                futures.add(CompletableFuture.completedFuture(null));
            }
        }
        CompletableFuture.allOf((futures.toArray(new CompletableFuture[0]))).join();
        // map each entity to dto
        List<Stats> statsList = new ArrayList<>();
        for (var i = 0; i < futures.size(); i++){
            // retrieve tag from completed future
            var user = futures.get(i).join();
            var tag = user != null ? user.getAsTag() : Bot.getBotName(entityList.get(i).getPlayerId());
            // map entity to dto and add to dto list
            var stats = modelMapper.map(entityList.get(i), Stats.class);
            stats.getPlayer().setName(tag);
            statsList.add(stats);
        }
        return statsList;
    }
}
