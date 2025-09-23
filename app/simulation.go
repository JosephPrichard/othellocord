package app

import (
	"context"
	"log/slog"
	"time"

	"github.com/jellydator/ttlcache/v3"
	"go.uber.org/atomic"
)

const SimulationTtl = time.Hour

type SimState struct {
	StopChan chan struct{}
	IsPaused atomic.Bool
}

type SimCache = *ttlcache.Cache[string, *SimState]

func MakeSimCache() SimCache {
	cache := ttlcache.New[string, *SimState]()
	cache.OnEviction(func(_ context.Context, _ ttlcache.EvictionReason, item *ttlcache.Item[string, *SimState]) {
		slog.Info("stopping simulation", "key", item.Key())
		state := item.Value()
		if state.StopChan != nil {
			state.StopChan <- struct{}{}
		}
	})
	return cache
}

type SimStep struct {
	Game     OthelloGame
	Move     Tile
	Finished bool
	Ok       bool
}

const MaxSimCount = BoardSize * BoardSize // maximum number of possible simulation states

func GenerateSimulation(ctx context.Context, sh *NTestShell, initialGame OthelloGame, simChan chan SimStep) {
	trace := ctx.Value(TraceKey)

	defer close(simChan)

	var game = initialGame
	var move RankTile

	for i := 0; ; i++ {
		respCh := sh.FindBestMove(game, game.CurrentPlayer().LevelToDepth())
		var resp MoveResp

		select {
		case resp = <-respCh:
		case <-ctx.Done():
			slog.Info("cancelled simulation", "index", i, "trace", trace, "move", move)
			return
		}

		if !resp.Ok {
			simChan <- SimStep{Ok: false}
			return
		}
		if len(resp.Moves) >= 1 {
			move = resp.Move
			game.MakeMove(move.Tile)
			game.TrySkipTurn()

			simChan <- SimStep{Game: game, Move: move.Tile, Ok: true}
		} else {
			slog.Info("finished simulation", "trace", trace, "moves", resp.Moves, "move", move)

			simChan <- SimStep{Game: game, Move: move.Tile, Finished: true, Ok: true}
			return
		}
	}
}
