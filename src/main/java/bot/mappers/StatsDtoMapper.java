package bot.mappers;

import bot.JDASingleton;
import bot.dtos.PlayerDto;
import bot.dtos.StatsDto;
import bot.entities.StatsEntity;
import net.dv8tion.jda.api.entities.User;
import org.modelmapper.Converter;
import org.modelmapper.ModelMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

public class StatsDtoMapper
{
    private final Logger logger = Logger.getLogger("mapper.stats");
    private final ModelMapper modelMapper = new ModelMapper();

    public StatsDtoMapper() {
        modelMapper.typeMap(StatsEntity.class, StatsDto.class).addMappings(mapper -> {
            Converter<Long, PlayerDto> converter = (ctx) -> new PlayerDto(ctx.getSource());
            mapper.using(converter).map(StatsEntity::getPlayerId, StatsDto::setPlayer);
        });

        modelMapper.validate();
    }

    public StatsDto map(StatsEntity entity) {
        // fetch tag from jda and assign
        String tag = JDASingleton.fetchUser(entity.getPlayerId()).complete().getAsTag();
        // map entity to dto
        StatsDto dto = modelMapper.map(entity, StatsDto.class);
        dto.getPlayer().setName(tag);
        return dto;
    }

    public List<StatsDto> mapAll(List<StatsEntity> entityList) {
        // fetch each tag from jda using futures
        List<CompletableFuture<User>> futures = new ArrayList<>();
        for (StatsEntity entity : entityList) {
            futures.add(JDASingleton.fetchUser(entity.getPlayerId()).submit());
        }
        CompletableFuture.allOf((futures.toArray(new CompletableFuture[0]))).join();
        // map each entity to dto
        List<StatsDto> dtoList = new ArrayList<>();
        try {
            for(int i = 0; i < futures.size(); i++){
                CompletableFuture<User> future = futures.get(i);
                StatsDto dto = modelMapper.map(entityList.get(i), StatsDto.class);
                dto.getPlayer().setName(future.get().getAsTag());
                dtoList.add(dto);
            }
        } catch (ExecutionException | InterruptedException e) {
            logger.severe("Failed to fetch tags for game mapping.");
        }
        return dtoList;
    }
}
