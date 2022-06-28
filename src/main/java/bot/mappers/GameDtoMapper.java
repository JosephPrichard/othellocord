package bot.mappers;

import bot.JDASingleton;
import bot.dtos.GameDto;
import bot.dtos.PlayerDto;
import bot.entities.GameEntity;
import org.modelmapper.Converter;
import org.modelmapper.ModelMapper;
import othello.utils.BoardUtils;
import othello.utils.BotUtils;

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

        boolean isWhiteBot = BotUtils.isBotId(entity.getWhitePlayerId());
        boolean isBlackBot = BotUtils.isBotId(entity.getBlackPlayerId());

        // for players, fetch users from discord, for bot names, calculate
        if (!isWhiteBot) {
            String tag = JDASingleton.fetchUser(entity.getWhitePlayerId()).complete().getAsTag();
            dto.getWhitePlayer().setName(tag);
        } else {
            String whiteBotName = BotUtils.getBotName(entity.getWhitePlayerId());
            dto.getWhitePlayer().setName(whiteBotName);
        }
        if (!isBlackBot) {
            String tag = JDASingleton.fetchUser(entity.getBlackPlayerId()).complete().getAsTag();
            dto.getBlackPlayer().setName(tag);
        } else {
            String blackBotName = BotUtils.getBotName(entity.getBlackPlayerId());
            dto.getBlackPlayer().setName(blackBotName);
        }

        return dto;
    }
}
