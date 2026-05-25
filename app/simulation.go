package app

import (
	"context"
	"errors"
	"log/slog"
	"time"

	"github.com/jellydator/ttlcache/v3"
	"go.uber.org/atomic"
)

const SimulationTtl = time.Hour

type SimState struct {
	Cancel   func()
	IsPaused atomic.Bool
}

type SimCache = *ttlcache.Cache[string, *SimState]

func MakeSimCache() SimCache {
	cache := ttlcache.New[string, *SimState]()
	cache.OnEviction(func(_ context.Context, _ ttlcache.EvictionReason, item *ttlcache.Item[string, *SimState]) {
		slog.Info("cancelling simulation", "key", item.Key())
		state := item.Value()
		if state.Cancel != nil {
			state.Cancel()
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

func generateSimulation(ctx context.Context, sh NTestShellAPI, initialGame OthelloGame, simChan chan SimStep) {
	defer close(simChan)

	var game = initialGame
	var move RankTile

	for i := 0; ; i++ {
		if game.HasMoves() {
			resp, err := sh.FindBestMove(ctx, game, game.CurrentPlayer().LevelToSearchDepth())
			if errors.Is(err, context.Canceled) || errors.Is(err, context.DeadlineExceeded) {
				slog.InfoContext(ctx, "cancelled simulation", "index", i, "move", move)
				return
			} else if err != nil {
				slog.InfoContext(ctx, "simulation encountered an error", "err", err)
				simChan <- SimStep{Ok: false}
				return
			}

			move = resp.Move

			game.MakeMove(move.Tile)
			simChan <- SimStep{Game: game, Move: move.Tile, Ok: true}
		} else {
			slog.InfoContext(ctx, "finished simulation", "move", move)
			simChan <- SimStep{Game: game, Move: move.Tile, Finished: true, Ok: true}
			return
		}
	}
}
