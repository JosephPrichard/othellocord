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
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

public class GameDtoMapper
{
    private final Logger logger = Logger.getLogger("mapper.game");
    private final ModelMapper modelMapper = new ModelMapper();

    public GameDtoMapper() {
        modelMapper.typeMap(GameEntity.class, GameDto.class).addMappings(mapper -> {
            Converter<Long, PlayerDto> converter = (ctx) -> new PlayerDto(ctx.getSource());

            mapper.using(ctx -> BoardUtils.deserialize((String) ctx.getSource()))
                .map(GameEntity::getBoard, GameDto::setBoard);

            mapper.using(converter)
                .map(GameEntity::getBlackPlayerId, GameDto::setBlackPlayer);

            mapper.using(converter)
                .map(GameEntity::getWhitePlayerId, GameDto::setWhitePlayer);
        });

        modelMapper.validate();
    }

    public GameDto map(GameEntity entity) {
        // fetch both white player tag and black player tag at the same time
        CompletableFuture<User> whiteUserFuture = JDASingleton.fetchUser(entity.getWhitePlayerId()).submit();
        CompletableFuture<User> blackUserFuture = JDASingleton.fetchUser(entity.getBlackPlayerId()).submit();
        CompletableFuture.allOf(whiteUserFuture, blackUserFuture).join();
        // map entity to dto
        GameDto dto = modelMapper.map(entity, GameDto.class);
        try {
            dto.getWhitePlayer().setName(whiteUserFuture.get().getAsTag());
            dto.getBlackPlayer().setName(blackUserFuture.get().getAsTag());
        } catch (ExecutionException | InterruptedException e) {
            logger.severe("Failed to fetch tags for game mapping.");
        }
        return dto;
    }
}
