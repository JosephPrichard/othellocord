package bot

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"log/slog"
	"othellocord/app/othello"
	"strconv"
	"sync"
	"time"

	"github.com/jellydator/ttlcache/v3"
)

type Game struct {
	othello.Board
	WhitePlayer        Player
	BlackPlayer        Player
	CurrPotentialMoves []othello.Tile // do not mutate this directly, instead call LoadPotentialMoves which will reassign
}

func (g *Game) LoadPotentialMoves() []othello.Tile {
	if g.CurrPotentialMoves == nil {
		g.CurrPotentialMoves = g.FindCurrentMoves()
	}
	return g.CurrPotentialMoves
}

func (g *Game) TrySkipTurn() {
	if len(g.LoadPotentialMoves()) == 0 {
		g.IsBlackMove = !g.IsBlackMove
		g.CurrPotentialMoves = nil
	}
}

func (g *Game) IsGameOver() bool {
	return len(g.LoadPotentialMoves()) == 0
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
	if g.WhitePlayer.ID == forfeitId {
		return GameResult{Winner: g.BlackPlayer, Loser: g.WhitePlayer, IsDraw: false}
	} else if g.BlackPlayer.ID == forfeitId {
		return GameResult{Winner: g.WhitePlayer, Loser: g.BlackPlayer, IsDraw: false}
	} else {
		return GameResult{IsDraw: true}
	}
}

type GameState struct {
	Game
	sync.Mutex
}

type GameResult struct {
	Winner Player
	Loser  Player
	IsDraw bool
}

const GameStoreTtl = time.Hour * 24

type GameCache = ttlcache.Cache[string, *GameState]

func WithEviction(db *sql.DB) *GameCache {
	cache := ttlcache.New[string, *GameState]()
	cache.OnEviction(func(ctx context.Context, r ttlcache.EvictionReason, item *ttlcache.Item[string, *GameState]) {
		state := item.Value()

		state.Lock()
		defer state.Unlock()

		ExpireGame(db, state.Game)
	})
	return cache
}

var ErrAlreadyPlaying = errors.New("one or more players are already in a game")

func CreateGame(ctx context.Context, cache *GameCache, blackPlayer Player, whitePlayer Player) (Game, error) {
	trace := ctx.Value(TraceKey)

	itemB := cache.Get(whitePlayer.ID)
	itemW := cache.Get(blackPlayer.ID)
	if itemB != nil || itemW != nil {
		return Game{}, ErrAlreadyPlaying
	}

	game := Game{WhitePlayer: whitePlayer, BlackPlayer: blackPlayer, Board: othello.InitialBoard()}
	state := &GameState{Game: game}

	cache.Set(whitePlayer.ID, state, GameStoreTtl)
	cache.Set(blackPlayer.ID, state, GameStoreTtl)

	slog.Info("created game and set into store", "trace", trace, "game", game)
	return game, nil
}

func CreateBotGame(ctx context.Context, cache *GameCache, blackPlayer Player, level int) (Game, error) {
	trace := ctx.Value(TraceKey)

	itemB := cache.Get(blackPlayer.ID)
	if itemB != nil {
		return Game{}, ErrAlreadyPlaying
	}

	game := Game{
		WhitePlayer: Player{ID: strconv.Itoa(level), Name: GetBotLevelFmt(level)},
		BlackPlayer: blackPlayer,
		Board:       othello.InitialBoard(),
	}
	state := &GameState{Game: game}

	cache.Set(blackPlayer.ID, state, GameStoreTtl)

	slog.Info("created bot game and set into store", "trace", trace, "game", game)
	return game, nil
}

var ErrGameNotFound = errors.New("game not found")

func GetGame(ctx context.Context, cache *GameCache, playerId string) (Game, error) {
	trace := ctx.Value(TraceKey)

	item := cache.Get(playerId)
	if item == nil {
		return Game{}, ErrGameNotFound
	}
	state := item.Value()

	state.Lock()
	defer state.Unlock()

	slog.Info("retrieved game from store", "trace", trace, "game", state.Game)

	// it is safe to copy this across boundaries, CurrPotentialMoves is reassigned but never modified directly
	return state.Game, nil
}

func DeleteGame(cache *GameCache, game Game) {
	cache.Delete(game.WhitePlayer.ID)
	cache.Delete(game.BlackPlayer.ID)
}

var ErrTurn = errors.New("not players turn")
var ErrInvalidMove = errors.New("invalid move")

func MakeMoveValidated(cache *GameCache, playerId string, move othello.Tile) (Game, error) {
	item := cache.Get(playerId)
	if item == nil {
		return Game{}, ErrGameNotFound
	}
	state := item.Value()

	state.Lock()
	defer state.Unlock()

	if state.CurrentPlayer().ID != playerId {
		return Game{}, ErrTurn
	}

	for _, m := range state.LoadPotentialMoves() {
		if m == move {
			return MakeMoveState(cache, state, move), nil
		}
	}
	return Game{}, ErrInvalidMove
}

func MakeMoveUnchecked(cache *GameCache, playerId string, move othello.Tile) Game {
	item := cache.Get(playerId)
	if item == nil {
		slog.Error("expected game State to be found", "player", playerId)
		return Game{}
	}
	state := item.Value()

	state.Lock()
	defer state.Unlock()

	for _, m := range state.LoadPotentialMoves() {
		if m == move {
			return MakeMoveState(cache, state, move)
		}
	}
	slog.Error("attempted to make a move that was not valid", "move", move, "game", state.Game)
	return Game{}
}

func MakeMoveState(cache *GameCache, state *GameState, move othello.Tile) Game {
	state.MakeMove(move)
	state.CurrPotentialMoves = nil

	state.TrySkipTurn()

	// no moves twice in a row means the game is over
	if len(state.LoadPotentialMoves()) == 0 {
		DeleteGame(cache, state.Game)
	}

	// it is safe to copy this across boundaries, CurrPotentialMoves is reassigned but never modified directly
	return state.Game
}

func ExpireGame(db *sql.DB, game Game) {
	trace := fmt.Sprintf("expire-game-task-%s-%s", game.WhitePlayer.ID, game.BlackPlayer.ID)
	ctx := context.WithValue(context.Background(), TraceKey, trace)

	gr := GameResult{Winner: game.CurrentPlayer(), Loser: game.CurrentPlayer(), IsDraw: false}
	sr, err := UpdateStats(ctx, db, gr)
	if err != nil {
		slog.Error("failed to update stats in expire game task", "trace", trace, "err", err)
	}
	slog.Info("updated stats result in expire game task with result", "trace", trace, "result", sr)
}
