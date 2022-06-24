package bot.mappers;

import bot.JDASingleton;
import bot.dtos.GameDto;
import bot.dtos.PlayerDto;
import bot.entities.GameEntity;
import net.dv8tion.jda.api.entities.User;
import org.modelmapper.Converter;
import org.modelmapper.ModelMapper;
import othello.utils.BoardUtils;

import java.util.concurrent.CompletableFuture;

public class GameDtoMapper
{
    private final ModelMapper modelMapper = new ModelMapper();

    public GameDtoMapper() {
        modelMapper.typeMap(GameEntity.class, GameDto.class).addMappings(mapper -> {
            Converter<Long, PlayerDto> converter = (ctx) -> new PlayerDto(ctx.getSource());

            mapper.using(ctx -> BoardUtils.deserialize((String) ctx.getSource()))
                .map(GameEntity::getBoard, GameDto::setBoard);

            mapper.using(converter).map(GameEntity::getBlackPlayerId, GameDto::setBlackPlayer);

            mapper.using(converter).map(GameEntity::getWhitePlayerId, GameDto::setWhitePlayer);
        });

        modelMapper.validate();
    }

    public GameDto map(GameEntity entity) {
        // map entity to dto
        GameDto dto = modelMapper.map(entity, GameDto.class);

        // fetch both white player tag and black player tag at the same time
        CompletableFuture<User> whiteUserFuture = JDASingleton.fetchUser(entity.getWhitePlayerId()).submit();
        CompletableFuture<User> blackUserFuture = JDASingleton.fetchUser(entity.getBlackPlayerId()).submit();
        CompletableFuture.allOf(whiteUserFuture, blackUserFuture).join();

        // assign tags from completed futures
        dto.getWhitePlayer().setName(whiteUserFuture.join().getAsTag());
        dto.getBlackPlayer().setName(blackUserFuture.join().getAsTag());

        return dto;
    }
}
