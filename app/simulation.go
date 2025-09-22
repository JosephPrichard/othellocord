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

type SimPanel struct {
	Game     OthelloGame
	Move     Tile
	Finished bool
	Ok       bool
}

const SimCount = BoardSize * BoardSize // maximum number of possible simulation states

type BeginSimInput struct {
	Sh          *NTestShell
	InitialGame OthelloGame
	SimChan     chan SimPanel
}

func BeginSimulate(ctx context.Context, input BeginSimInput) {
	trace := ctx.Value(TraceKey)

	defer close(input.SimChan)

	var game = input.InitialGame
	var move RankTile

	for i := 0; ; i++ {
		respCh := input.Sh.FindBestMove(game, game.CurrentPlayer().LevelToDepth())
		var resp MoveResp

		select {
		case resp = <-respCh:
		case <-ctx.Done():
			slog.Info("cancelled simulation", "index", i, "trace", trace, "move", move)
			return
		}

		if !resp.ok {
			input.SimChan <- SimPanel{Ok: false}
			return
		}
		if len(resp.moves) > 1 {
			move = resp.moves[0]

			game.MakeMove(move.Tile)
			game.TrySkipTurn()

			input.SimChan <- SimPanel{Game: game, Move: move.Tile, Ok: true}
		} else {
			slog.Info("finished simulation", "trace", trace, "moves", resp.moves, "move", move)

			input.SimChan <- SimPanel{Game: game, Move: move.Tile, Finished: true, Ok: true}
			return
		}
	}
}

type RecvSimInput struct {
	State        *SimState
	RecvChan     chan SimPanel
	HandleCancel func()
	HandleSend   func(SimPanel)
	Delay        time.Duration
}

func ReceiveSimulate(ctx context.Context, input RecvSimInput) {
	trace := ctx.Value(TraceKey)

	ticker := time.NewTicker(input.Delay)
	for index := 0; ; index++ {
		select {
		case <-ticker.C:
			if input.State.IsPaused.Load() {
				continue
			}
			msg, ok := <-input.RecvChan
			if !ok {
				slog.Info("simulation receiver complete", "trace", trace)
				return
			}
			input.HandleSend(msg)
		case <-input.State.StopChan:
			input.HandleCancel()
			slog.Info("simulation receiver stopped", "trace", trace)
			return
		case <-ctx.Done():
			input.HandleCancel()
			slog.Info("simulation receiver timed out", "trace", trace, "err", ctx.Err())
			return
		}
	}
}
