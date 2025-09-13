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
}

const SimCount = BoardSize * BoardSize // maximum number of possible simulation states

type BeginSimInput struct {
	InitialGame OthelloGame
	SimChan     chan SimPanel
}

func BeginSimulate(ctx context.Context, input BeginSimInput) {
	//trace := ctx.Value(TraceKey)
	//
	//var game = input.InitialGame
	//var move othello.Tile
	//
	//for i := 0; ; i++ {
	//	select {
	//	case <-ctx.Done():
	//		slog.Info("cancelled simulation", "index", i, "trace", trace, "move", move)
	//		return
	//	default:
	//	}
	//
	//	depth := LevelToDepth(GetBotLevel(game.CurrentPlayer().ID))
	//	request := WorkerRequest{
	//		OthelloBoard:    game.OthelloBoard,
	//		Depth:    depth,
	//		Kind:     GetMoveRequestKind,
	//		RespChan: make(chan []othello.RankTile, 1),
	//	}
	//	input.Wq <- request
	//
	//	moves := <-request.RespChan
	//
	//	if len(moves) > 1 {
	//		panic("expected exactly engine to no more than one moves") // we only requested one move
	//	}
	//	if len(moves) == 1 {
	//		move = moves[0].Tile
	//
	//		game.MakeMoveValidated(move)
	//		game.TrySkipTurn()
	//		game.CurrPotentialMoves = nil
	//
	//		input.SimChan <- SimPanel{OthelloGame: game, Move: move}
	//	} else {
	//		slog.Info("finished simulation", "trace", trace, "moves", moves, "move", move)
	//
	//		input.SimChan <- SimPanel{OthelloGame: game, Move: move, Finished: true}
	//		close(input.SimChan)
	//		return
	//	}
	//}
}

type RecvSimInput struct {
	State        *SimState
	RecvChan     chan SimPanel
	DoCancel     func()
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
			input.DoCancel()
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
