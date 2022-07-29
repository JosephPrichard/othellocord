package bot.services;

import bot.dao.GameDao;
import bot.dtos.PlayerDto;
import bot.dtos.GameDto;
import bot.entities.GameEntity;
import bot.mappers.GameDtoMapper;
import bot.services.exceptions.AlreadyPlayingException;
import bot.services.exceptions.InvalidMoveException;
import bot.services.exceptions.NotPlayingException;
import bot.services.exceptions.TurnException;
import othello.board.OthelloBoard;
import othello.board.Tile;
import othello.utils.BoardUtils;
import othello.utils.BotUtils;

import javax.annotation.Nullable;
import javax.persistence.PersistenceException;
import java.util.List;

public class GameService
{
    private final GameDao gameDao;
    private final GameDtoMapper dtoMapper = new GameDtoMapper();

    public GameService(GameDao gameDao) {
        this.gameDao = gameDao;
    }

    public GameDto createGame(PlayerDto blackPlayer, PlayerDto whitePlayer) throws AlreadyPlayingException {
        GameDto gameDto = new GameDto();
        gameDto.setBlackPlayer(blackPlayer);
        gameDto.setWhitePlayer(whitePlayer);
        gameDto.setBoard(new OthelloBoard());

        if (isPlaying(blackPlayer) || isPlaying(whitePlayer)) {
            throw new AlreadyPlayingException();
        }

        try {
            gameDao.saveGame(blackPlayer.getId(), whitePlayer.getId(), BoardUtils.serialize(gameDto.getBoard()));
        } catch (PersistenceException ex) {
            throw new AlreadyPlayingException();
        }

        return gameDto;
    }

    public GameDto createBotGame(PlayerDto blackPlayer, int level) throws AlreadyPlayingException {
        PlayerDto whitePlayer = BotUtils.Bot(level);

        GameDto gameDto = new GameDto();
        gameDto.setBlackPlayer(blackPlayer);
        gameDto.setWhitePlayer(whitePlayer);
        gameDto.setBoard(new OthelloBoard());

        if (isPlaying(blackPlayer)) {
            throw new AlreadyPlayingException();
        }

        try {
            gameDao.saveGame(blackPlayer.getId(), whitePlayer.getId(), BoardUtils.serialize(gameDto.getBoard()));
        } catch (PersistenceException ex) {
            throw new AlreadyPlayingException();
        }
        return gameDto;
    }

    @Nullable
    public GameDto getGame(PlayerDto player) {
        GameEntity gameEntity = gameDao.getGame(player.getId());
        if (gameEntity == null) {
            return null;
        }
        return dtoMapper.map(gameEntity);
    }

    public GameDto makeMove(PlayerDto player, String move) throws NotPlayingException, InvalidMoveException, TurnException {
        GameEntity gameEntity = gameDao.getGame(player.getId());
        if (gameEntity == null) {
            throw new NotPlayingException();
        }
        GameDto gameDto = dtoMapper.map(gameEntity);

        if (!gameDto.getCurrentPlayer().equals(player)) {
            throw new TurnException();
        }

        // calculate the potential moves
        List<Tile> potentialMoves = gameDto.getBoard().findPotentialMoves();
        // check if the move being requested is any of the potential moves, if so make the move
        for (Tile potentialMove : potentialMoves) {
            if (potentialMove.equalsNotation(move)) {
                gameDto.getBoard().makeMove(potentialMove);
                return gameDto;
            }
        }

        throw new InvalidMoveException();
    }

    public void updateGame(GameDto gameDto) {
        gameDao.updateGame(gameDto.getBlackPlayer().getId(), BoardUtils.serialize(gameDto.getBoard()));
    }

    public boolean isPlaying(PlayerDto player) {
        return getGame(player) != null;
    }

    public void deleteGame(GameDto game) {
        gameDao.deleteGame(game.getBlackPlayer().getId());
    }
}
