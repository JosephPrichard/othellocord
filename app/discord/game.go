package discord

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"github.com/allegro/bigcache/v2"
	cache2 "github.com/eko/gocache/lib/v4/cache"
	"log/slog"
	"othellocord/app/othello"
	"sync"
)

type Game struct {
	othello.Board
	WhitePlayer        Player
	BlackPlayer        Player
	CurrPotentialMoves []othello.Tile
}

func (game *Game) LoadPotentialMoves() []othello.Tile {
	if game.CurrPotentialMoves == nil {
		game.CurrPotentialMoves = game.FindCurrentMoves()
	}
	return game.CurrPotentialMoves
}

func (game *Game) CurrentPlayer() Player {
	if game.Board.IsBlackMove {
		return game.BlackPlayer
	} else {
		return game.WhitePlayer
	}
}

func (game *Game) OtherPlayer() Player {
	if game.Board.IsBlackMove {
		return game.WhitePlayer
	} else {
		return game.BlackPlayer
	}
}

type GameState struct {
	Game
	mu sync.Mutex
}

type GameResult struct {
	Winner Player
	Loser  Player
	IsDraw bool
}

type GameStore = cache2.Cache[*GameState]

func CreateGame(ctx context.Context, s GameStore, whitePlayer Player, blackPlayer Player) (Game, error) {
	trace := ctx.Value("trace")

	game := Game{WhitePlayer: whitePlayer, BlackPlayer: blackPlayer, Board: othello.InitialBoard()}
	state := &GameState{Game: game}

	if err := s.Set(ctx, whitePlayer.Id, state); err != nil {
		slog.Error("failed to set game state for whitePlayer in cache", "trace", trace, "error", err)
		return Game{}, err
	}
	if err := s.Set(ctx, blackPlayer.Id, state); err != nil {
		slog.Error("failed to set game state for blackPlayer in cache", "trace", trace, "error", err)
		_ = s.Delete(ctx, whitePlayer.Id)
		return Game{}, err
	}

	return game, nil
}

func CreateBotGame(ctx context.Context, s GameStore, blackPlayer Player, level uint64) (Game, error) {
	trace := ctx.Value("trace")

	game := Game{
		WhitePlayer: Player{Id: level, Name: GetBotName(level)},
		BlackPlayer: blackPlayer,
		Board:       othello.InitialBoard(),
	}
	state := &GameState{Game: game}

	if err := s.Set(ctx, blackPlayer.Id, state); err != nil {
		slog.Error("failed to set game state for blackPlayer in cache", "trace", trace, "error", err)
		return Game{}, err
	}

	return game, nil
}

var ErrGameNotFound = errors.New("game not found")

func GetGame(ctx context.Context, s GameStore, playerId uint64) (Game, error) {
	trace := ctx.Value("trace")

	state, err := s.Get(ctx, playerId)
	if errors.Is(err, bigcache.ErrEntryNotFound) {
		return Game{}, ErrGameNotFound
	}
	if err != nil || state == nil {
		slog.Error("failed to get game state from cache", "trace", trace, "player", playerId, "state", state, "error", err)
		return Game{}, err
	}

	state.mu.Lock()
	defer state.mu.Unlock()

	return state.Game, nil
}

func DeleteGame(ctx context.Context, s GameStore, game Game) {
	trace := ctx.Value("trace")
	if err := s.Delete(ctx, game.WhitePlayer.Id); err != nil {
		slog.Error("failed to delete game from cache", "trace", trace, "player", game.WhitePlayer.Id, "error", err)
	}
	if err := s.Delete(ctx, game.BlackPlayer.Id); err != nil {
		slog.Error("failed to delete game state from cache", "trace", trace, "player", game.BlackPlayer.Id, "error", err)
	}
}

var ErrNotPlaying = errors.New("not playing")
var ErrTurn = errors.New("not players turn")
var ErrInvalidMove = errors.New("invalid move")

func MakeMove(ctx context.Context, s GameStore, playerId uint64, move othello.Tile) (Game, error) {
	trace := ctx.Value("trace")

	state, err := s.Get(ctx, playerId)
	if err != nil || state == nil {
		slog.Error("failed to get game state from cache", "trace", trace, "player", playerId, "state", state, "error", err)
		return Game{}, ErrNotPlaying
	}

	state.mu.Lock()
	defer state.mu.Unlock()

	if state.CurrentPlayer().Id != playerId {
		return Game{}, ErrTurn
	}

	state.LoadPotentialMoves()
	for _, m := range state.LoadPotentialMoves() {
		if m == move {
			state.MakeMove(move)
			state.CurrPotentialMoves = nil

			if len(state.LoadPotentialMoves()) == 0 {
				state.IsBlackMove = !state.IsBlackMove
				state.CurrPotentialMoves = nil
			}

			if len(state.LoadPotentialMoves()) == 0 {
				DeleteGame(ctx, s, state.Game)
			}

			return state.Game, nil
		}
	}

	return Game{}, ErrInvalidMove
}

func ExpireGame(db *sql.DB, game Game) {
	trace := fmt.Sprintf("expire-game-task-%d-%d", game.WhitePlayer.Id, game.BlackPlayer.Id)
	ctx := context.WithValue(context.Background(), "trace", trace)

	gr := GameResult{Winner: game.CurrentPlayer(), Loser: game.CurrentPlayer(), IsDraw: false}
	sr, err := UpdateStats(ctx, db, gr)
	if err != nil {
		slog.Error("failed to update stats in expire game task", "trace", trace, "error", err)
	}
	slog.Info("updated sr in expire game task with result", "trace", trace, "result", sr)
}

func CreateResult(game Game) GameResult {
	diff := game.BlackScore() - game.WhiteScore()
	if diff > 0 {
		return GameResult{Winner: game.BlackPlayer, Loser: game.WhitePlayer, IsDraw: false}
	} else if diff < 0 {
		return GameResult{Winner: game.WhitePlayer, Loser: game.BlackPlayer, IsDraw: false}
	} else {
		return GameResult{Winner: game.BlackPlayer, Loser: game.WhitePlayer, IsDraw: true}
	}
}

var ErrForfeit = errors.New("player is not a member of game, cannot forfeit")

func CreateForfeitResult(game Game, forfeitId uint64) (GameResult, error) {
	if game.WhitePlayer.Id == forfeitId {
		return GameResult{Winner: game.BlackPlayer, Loser: game.WhitePlayer, IsDraw: false}, nil
	} else if game.BlackPlayer.Id == forfeitId {
		return GameResult{Winner: game.WhitePlayer, Loser: game.BlackPlayer, IsDraw: false}, nil
	} else {
		return GameResult{}, ErrForfeit
	}
}
