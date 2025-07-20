package discord

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"github.com/jellydator/ttlcache/v3"
	"log/slog"
	"othellocord/app/othello"
	"strconv"
	"sync"
	"time"
)

type Game struct {
	othello.Board
	WhitePlayer        Player
	BlackPlayer        Player
	CurrPotentialMoves []othello.Tile
}

func (g *Game) LoadPotentialMoves() []othello.Tile {
	if g.CurrPotentialMoves == nil {
		g.CurrPotentialMoves = g.FindCurrentMoves()
	}
	return g.CurrPotentialMoves
}

func (g *Game) CurrentPlayer() Player {
	if g.Board.IsBlackMove {
		return g.BlackPlayer
	} else {
		return g.WhitePlayer
	}
}

func (g *Game) OtherPlayer() Player {
	if g.Board.IsBlackMove {
		return g.WhitePlayer
	} else {
		return g.BlackPlayer
	}
}

func (g *Game) CreateResult() GameResult {
	diff := g.BlackScore() - g.WhiteScore()
	if diff > 0 {
		return GameResult{Winner: g.BlackPlayer, Loser: g.WhitePlayer, IsDraw: false}
	} else if diff < 0 {
		return GameResult{Winner: g.WhitePlayer, Loser: g.BlackPlayer, IsDraw: false}
	} else {
		return GameResult{Winner: g.BlackPlayer, Loser: g.WhitePlayer, IsDraw: true}
	}
}

func (g *Game) CreateForfeitResult(forfeitId string) GameResult {
	if g.WhitePlayer.Id == forfeitId {
		return GameResult{Winner: g.BlackPlayer, Loser: g.WhitePlayer, IsDraw: false}
	} else if g.BlackPlayer.Id == forfeitId {
		return GameResult{Winner: g.WhitePlayer, Loser: g.BlackPlayer, IsDraw: false}
	} else {
		return GameResult{IsDraw: true}
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

var GameStoreTtl = time.Hour * 24

type GameStore struct {
	cache *ttlcache.Cache[string, *GameState]
}

func NewGameStore(db *sql.DB) GameStore {
	cache := ttlcache.New[string, *GameState]()
	cache.OnEviction(func(ctx context.Context, r ttlcache.EvictionReason, item *ttlcache.Item[string, *GameState]) {
		state := item.Value()

		state.mu.Lock()
		defer state.mu.Unlock()

		ExpireGame(db, state.Game)
	})
	return GameStore{cache: cache}
}

var ErrAlreadyPlaying = errors.New("one or more players are already in a game")

func (s GameStore) CreateGame(ctx context.Context, blackPlayer Player, whitePlayer Player) (Game, error) {
	trace := ctx.Value("trace")

	itemB := s.cache.Get(whitePlayer.Id)
	itemW := s.cache.Get(blackPlayer.Id)
	if itemB != nil || itemW != nil {
		return Game{}, ErrAlreadyPlaying
	}

	game := Game{WhitePlayer: whitePlayer, BlackPlayer: blackPlayer, Board: othello.InitialBoard()}
	state := &GameState{Game: game}

	s.cache.Set(whitePlayer.Id, state, GameStoreTtl)
	s.cache.Set(blackPlayer.Id, state, GameStoreTtl)

	slog.Info("created game and set into store", "trace", trace, "game", game)
	return game, nil
}

func (s GameStore) CreateBotGame(ctx context.Context, blackPlayer Player, level int) (Game, error) {
	trace := ctx.Value("trace")

	itemB := s.cache.Get(blackPlayer.Id)
	if itemB != nil {
		return Game{}, ErrAlreadyPlaying
	}

	game := Game{
		WhitePlayer: Player{Id: strconv.Itoa(level), Name: GetBotLevel(level)},
		BlackPlayer: blackPlayer,
		Board:       othello.InitialBoard(),
	}
	state := &GameState{Game: game}

	s.cache.Set(blackPlayer.Id, state, GameStoreTtl)

	slog.Info("created bot game and set into store", "trace", trace, "game", game)
	return game, nil
}

var ErrGameNotFound = errors.New("game not found")

func (s GameStore) GetGame(ctx context.Context, playerId string) (Game, error) {
	trace := ctx.Value("trace")

	item := s.cache.Get(playerId)
	if item == nil {
		return Game{}, ErrGameNotFound
	}
	state := item.Value()
	if state == nil {
		slog.Error("expected game state to not nil", "trace", trace, "player", playerId, "state", state)
		return Game{}, fmt.Errorf("game not found")
	}

	state.mu.Lock()
	defer state.mu.Unlock()

	slog.Info("retrieved game from store", "trace", trace, "game", state.Game)
	return state.Game, nil
}

func (s GameStore) DeleteGame(game Game) {
	s.cache.Delete(game.WhitePlayer.Id)
	s.cache.Delete(game.BlackPlayer.Id)
}

var ErrTurn = errors.New("not players turn")
var ErrInvalidMove = errors.New("invalid move")

func (s GameStore) MakeMove(ctx context.Context, playerId string, move othello.Tile) (Game, error) {
	trace := ctx.Value("trace")

	item := s.cache.Get(playerId)
	if item == nil {
		return Game{}, ErrGameNotFound
	}
	state := item.Value()
	if state == nil {
		slog.Error("expected game state to not nil", "trace", trace, "player", playerId, "state", state)
		return Game{}, ErrGameNotFound
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
				s.DeleteGame(state.Game)
			}
			return state.Game, nil
		}
	}
	return Game{}, ErrInvalidMove
}

func ExpireGame(db *sql.DB, game Game) {
	trace := fmt.Sprintf("expire-game-task-%s-%s", game.WhitePlayer.Id, game.BlackPlayer.Id)
	ctx := context.WithValue(context.Background(), "trace", trace)

	gr := GameResult{Winner: game.CurrentPlayer(), Loser: game.CurrentPlayer(), IsDraw: false}
	sr, err := UpdateStats(ctx, db, gr)
	if err != nil {
		slog.Error("failed to update stats in expire game task", "trace", trace, "error", err)
	}
	slog.Info("updated stats result in expire game task with result", "trace", trace, "result", sr)
}
